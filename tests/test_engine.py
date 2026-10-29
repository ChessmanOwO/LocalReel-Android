"""Offline engine regression tests; fixture bytes are not social-download proof."""
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"engine"))
import pytest
from localreel.errors import Failure
from localreel.worker import progressive_fallback, download_selected
from localreel.network import public_ip, checked_answers
from localreel.platforms import parse_post


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
