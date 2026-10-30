"""Offline engine regression tests; fixture bytes are not social-download proof."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"engine"))
import pytest
from localreel.errors import Failure
from localreel.worker import progressive_fallback, download_selected
from localreel.network import public_ip, checked_answers
from localreel.platforms import parse_post
from localreel import worker
from localreel.errors import classify_error
from types import SimpleNamespace


def test_facebook_fallback_uses_only_source_progressive_candidates():
    low={"url":"https://cdn.test/low","protocol":"https"}
    high={"url":"https://cdn.test/high","protocol":"https"}
    dash={"url":"https://cdn.test/dash","vcodec":"h264","acodec":"none","height":1080,"protocol":"https"}
    assert progressive_fallback("facebook",[low,high,dash],Failure("network"))==[high]


@pytest.mark.parametrize("platform,code",[("instagram","network"),("facebook","drm"),("facebook","login_required"),("facebook","unsafe_url"),("facebook","limit")])
def test_fallback_never_overrides_access_or_security_failure(platform,code):
    with pytest.raises(Failure,match=code):
        progressive_fallback(platform,[{"url":"https://cdn.test/a","protocol":"https"}],Failure(code))


def test_fallback_without_progressive_does_not_invent_url():
    with pytest.raises(Failure,match="network"):
        progressive_fallback("facebook",[{"url":"https://cdn.test/dash","vcodec":"h264","protocol":"https"}],Failure("network"))


def test_download_paths_ignore_title_and_format_id(tmp_path):
    class Client:
        def dl(self,path,info):
            assert Path(path)==tmp_path/"stream-0.bin"
            assert "formats" not in info
            Path(path).write_bytes(b"fixture-not-a-video")
            return True,None
    fmt={"url":"https://cdn.test/video","format_id":"../../escape"}
    assert download_selected(Client(),{"title":"../escape","formats":[fmt]},[fmt],tmp_path,100)==[tmp_path/"stream-0.bin"]
    with pytest.raises(Failure,match="limit"):
        download_selected(Client(),{},[{**fmt,"filesize":101}],tmp_path,100)


@pytest.mark.parametrize("address",["127.0.0.1","10.0.0.1","192.168.1.1","169.254.169.254","::1","fc00::1","fe80::1","64:ff9b::a00:1"])
def test_private_egress_remains_blocked(address):
    assert not public_ip(address)


def test_mixed_dns_answer_is_rejected():
    import socket
    def resolver(*a,**k):return [(socket.AF_INET,socket.SOCK_STREAM,6,"",("8.8.8.8",443)),(socket.AF_INET,socket.SOCK_STREAM,6,"",("127.0.0.1",443))]
    with pytest.raises(Failure,match="unsafe_url"):checked_answers("cdn.test",443,resolver)


@pytest.mark.parametrize("url",["https://youtube.com.evil.test/watch?v=jNQXAC9IVRw","https://user:pass@youtube.com/watch?v=jNQXAC9IVRw","https://169.254.169.254/latest/meta-data"])
def test_post_boundary_still_applies(url):
    with pytest.raises(Failure):parse_post(url)


def test_transfer_refresh_discards_partial_bytes_and_uses_new_metadata(monkeypatch, tmp_path):
    old={"generation":"old"};fresh={"generation":"fresh"};calls=[]
    (tmp_path/"stream-0.bin.part").write_bytes(b"old-range-bytes")
    (tmp_path/"keep.txt").write_text("keep")
    class Client:
        def extract_info(self,url,download):
            assert url=="https://www.instagram.com/reel/test/" and download is False
            return fresh
    def attempt(client,post,info,quality,work,cap,probe):
        calls.append(info)
        assert quality=="1080"
        if len(calls)==1:raise Failure("network")
        assert not (work/"stream-0.bin.part").exists()
        assert (work/"keep.txt").read_text()=="keep"
        return [work/"stream-0.bin"],False
    monkeypatch.setattr(worker,"download_attempt",attempt)
    result=worker.download_recovering(Client(),SimpleNamespace(url="https://www.instagram.com/reel/test/"),old,"1080",tmp_path,100,"probe")
    assert calls==[old,fresh] and result==([tmp_path/"stream-0.bin"],False)


@pytest.mark.parametrize("code",["drm","login_required","unsafe_url","limit","blocked","tls_verification_failed","quality_unavailable"])
def test_access_security_and_quality_failures_are_not_refreshed(monkeypatch,tmp_path,code):
    def attempt(*args):raise Failure(code)
    class Client:
        def extract_info(self,*args,**kwargs):pytest.fail("Non-transient failure must not cause re-extraction")
    monkeypatch.setattr(worker,"download_attempt",attempt)
    with pytest.raises(Failure,match=code):worker.download_recovering(Client(),SimpleNamespace(url="https://www.instagram.com/reel/test/"),{},"best",tmp_path,100,"probe")


def test_transfer_refresh_is_bounded(monkeypatch,tmp_path):
    calls=[];refresh=[]
    def attempt(*args):calls.append(1);raise Failure("network")
    class Client:
        def extract_info(self,*args,**kwargs):refresh.append(1);return {"fresh":True}
    monkeypatch.setattr(worker,"download_attempt",attempt)
    with pytest.raises(Failure,match="network"):worker.download_recovering(Client(),SimpleNamespace(url="https://www.instagram.com/reel/test/"),{},"best",tmp_path,100,"probe")
    assert len(calls)==2 and len(refresh)==1


@pytest.mark.parametrize("message",["[SSL: CERTIFICATE_VERIFY_FAILED]", "certificate verify failed", "certificate verification failed"])
def test_certificate_errors_are_not_transient_network_errors(message):
    assert classify_error(Exception(message))=="tls_verification_failed"


def test_native_media_handoff_validates_url_and_keeps_generated_path(monkeypatch,tmp_path):
    seen=[]
    monkeypatch.setattr(worker,"validate_media_url",lambda url:seen.append(url))
    class Client:
        def native_http_downloader(self,slot,info):
            assert slot==0 and info["url"] in seen
            assert "formats" not in info
            (tmp_path/f"stream-{slot}.bin").write_bytes(b"fixture-only")
        def dl(self,*args):pytest.fail("Direct media should use the requested native transport")
    fmt={"url":"https://cdn.test/video.mp4","protocol":"https","format_id":"../../ignored"}
    assert download_selected(Client(),{"formats":[fmt]},[fmt],tmp_path,100)==[tmp_path/"stream-0.bin"]
    assert seen==[fmt["url"]]


def test_native_handoff_does_not_override_egress_failure(monkeypatch,tmp_path):
    def reject(url):raise Failure("unsafe_url")
    monkeypatch.setattr(worker,"validate_media_url",reject)
    class Client:
        def native_http_downloader(self,*args):pytest.fail("Unsafe URL reached native transport")
    with pytest.raises(Failure,match="unsafe_url"):
        download_selected(Client(),{},[{"url":"https://127.0.0.1/video.mp4","protocol":"https"}],tmp_path,100)
