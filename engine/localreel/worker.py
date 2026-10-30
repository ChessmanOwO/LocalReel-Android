"""One disposable subprocess per inspect/download attempt. stdin carries private URLs."""
import json
import os
import shutil
from pathlib import Path
import sys
import time
from urllib.parse import urlsplit
from yt_dlp import YoutubeDL
from yt_dlp.downloader import get_suitable_downloader
from yt_dlp.downloader.http import HttpFD
from yt_dlp.downloader.hls import HlsFD
from yt_dlp.downloader.dash import DashSegmentsFD
from yt_dlp.downloader.external import FFmpegFD
from yt_dlp.networking._urllib import UrllibRH
from yt_dlp.globals import plugin_dirs
from .errors import Failure, classify_error
from .network import install_guard, validate_media_url
from .platforms import parse_post
from .media import mux, probe


def emit(value):
    print(json.dumps(value, ensure_ascii=True), flush=True)


class Quiet:
    def debug(self, *args, **kwargs): pass
    def warning(self, *args, **kwargs): pass
    def error(self, *args, **kwargs): pass


# Keep individual CDN requests short on the bundled mobile TLS runtime. HttpFD
# validates Content-Range and safely restarts if a server does not support ranges.
HTTP_CHUNK_BYTES = 1 << 20


class SafeYDL(YoutubeDL):
    def __init__(self, params=None, **kwargs):
        os.environ["YTDLP_NO_PLUGINS"] = "1"
        plugin_dirs.value = []
        params = dict(params or {})
        enable_js = params.pop("local_youtube_js", False)
        runtime = shutil.which("deno") if enable_js else None
        if enable_js and not runtime:
            raise Failure("dependency_missing")
        # yt-dlp's Deno provider runs bundled EJS via stdin with --no-remote,
        # no permissions for network/files/processes. Never enable Node or remote
        # components: those would escape the Python socket boundary.
        super().__init__({**params, "js_runtimes": {"deno": {"path": runtime}} if runtime else {},
                          "remote_components": []}, **kwargs)

    def build_request_director(self, handlers, preferences=None):
        # Do not let a separately installed curl-cffi bypass the Python socket guard.
        return super().build_request_director([UrllibRH])

    def dl(self, name, info, subtitle=False, test=False):
        validate_media_url(info.get("url", ""))
        downloader = get_suitable_downloader(info, self.params)
        if downloader not in (HttpFD, HlsFD, DashSegmentsFD):
            raise Failure("unsupported_url")
        return super().dl(name, info, subtitle=subtitle, test=test)


def deny_external(*args, **kwargs):
    raise Failure("unsupported_url")


def resolve_post(ydl, post):
    """Resolve aliases not understood directly by the selected site extractor.

    Called only inside the disposable worker after install_guard(). All redirect
    connections use SafeYDL's urllib handler and the public-IP socket guard.
    Do not read the body, infer a media URL from text, or enable Generic extraction.
    """
    parts = urlsplit(post.url)
    facebook_alias = post.platform == "facebook" and (parts.hostname == "fb.watch" or parts.path.startswith("/share/"))
    douyin_alias = post.platform == "douyin" and parts.hostname == "v.douyin.com"
    xhs_alias = post.platform == "xiaohongshu" and parts.hostname == "xhslink.com"
    tiktok_alias = post.platform == "tiktok" and post.extractor == "TikTokVM"
    if not (facebook_alias or douyin_alias or xhs_alias or tiktok_alias):
        return post
    with ydl.urlopen(post.url) as response:
        target = response.url
    final = urlsplit(target)
    if final.hostname in ("facebook.com", "www.facebook.com", "m.facebook.com", "web.facebook.com") and (
        final.path.rstrip("/") == "/login" or final.path.startswith(("/login/", "/checkpoint/"))
    ):
        raise Failure("login_required")
    resolved = parse_post(target)
    if resolved.platform != post.platform:
        raise Failure("unsupported_url")
    # A page that did not expand the alias must not be handed to an unsupported
    # extractor or treated as a successful download.
    from yt_dlp.extractor.facebook import FacebookIE, FacebookReelIE
    from yt_dlp.extractor.tiktok import DouyinIE, TikTokIE
    from yt_dlp.extractor.xiaohongshu import XiaoHongShuIE
    extractors = {"facebook": (FacebookIE, FacebookReelIE), "douyin": (DouyinIE,),
                  "xiaohongshu": (XiaoHongShuIE,), "tiktok": (TikTokIE,)}
    if not any(extractor.suitable(resolved.url) for extractor in extractors[post.platform]):
        raise Failure("extraction_failed")
    return resolved


def usable_formats(info):
    if info.get("_type") in ("playlist", "multi_video") or info.get("entries") is not None:
        raise Failure("playlist")
    if info.get("is_live") or info.get("live_status") == "is_live":
        raise Failure("live")
    if info.get("has_drm"):
        raise Failure("drm")
    formats = [f for f in info.get("formats", []) if not f.get("has_drm")
               and f.get("protocol") in ("http", "https", "m3u8_native", "http_dash_segments")
               and f.get("url")]
    if not formats:
        raise Failure("extraction_failed")
    return formats


def choose(formats, quality):
    videos = [f for f in formats if f.get("vcodec") != "none"]
    if quality != "best":
        videos = [f for f in videos if int(f.get("height") or 0) == int(quality)]
    if not videos:
        raise Failure("quality_unavailable")
    audios = [f for f in formats if f.get("acodec") not in (None, "none") and f.get("vcodec") == "none"]
    # Facebook can expose DASH video but omit its audio representation. A progressive
    # SD/HD stream with unknown codecs can supply audio, verified later by ffprobe.
    if not audios:
        audios = [f for f in formats if f.get("vcodec") is None and f.get("acodec") is None]
    # Retain yt-dlp's source-specific quality ordering as a final tie breaker.
    order = {id(f): i for i, f in enumerate(formats)}
    # Some Instagram progressive MP4s omit acodec metadata. Download then inspect
    # the actual streams; missing metadata is not proof that the audio is absent.
    candidates = [f for f in videos if f.get("acodec") != "none" or audios]
    if not candidates:
        raise Failure("no_audio")
    video = max(candidates, key=lambda f: (f.get("height") or 0, f.get("width") or 0,
                f.get("fps") or 0, f.get("tbr") or 0, order[id(f)]))
    if video.get("acodec") != "none":
        return [video]
    audio = max(audios, key=lambda f: (f.get("abr") or f.get("tbr") or 0, f.get("asr") or 0, order[id(f)]))
    return [video, audio]


def download_selected(ydl, info, selected, work, max_bytes):
    inputs = []
    for index, fmt in enumerate(selected):
        if (fmt.get("filesize") or fmt.get("filesize_approx") or 0) > max_bytes:
            raise Failure("limit")
        # Never trust title, extension, format ID or URL as part of a filesystem path.
        path = work / f"stream-{index}.bin"
        single = {**info, **fmt}
        for key in ("requested_formats", "requested_downloads", "entries", "formats"):
            single.pop(key, None)
        native = getattr(ydl, "native_http_downloader", None)
        if native and fmt.get("protocol") in ("http", "https"):
            validate_media_url(single.get("url", ""))
            native(index, single)
            success = True
        else:
            success, _ = ydl.dl(str(path), single)
        if not success or not path.is_file():
            raise Failure("extraction_failed")
        inputs.append(path)
    return inputs


def progressive_fallback(platform, formats, error):
    if platform != "facebook" or classify_error(error) not in ("network", "blocked", "extraction_failed"):
        raise error
    progressive = [f for f in formats if f.get("vcodec") is None and f.get("acodec") is None
                   and f.get("protocol") in ("https", "http")]
    if not progressive:
        raise error
    return choose(progressive, "best")


def clear_streams(work):
    # Only the current job's generated names, never source-derived filenames.
    for path in work.glob("stream-*"):
        if path.is_file():
            path.unlink()


def download_attempt(ydl, post, info, quality, work, max_bytes, ffprobe):
    formats = usable_formats(info)
    selected = choose(formats, quality)
    try:
        return download_selected(ydl, info, selected, work, max_bytes), False
    except Exception as error:
        alternate = progressive_fallback(post.platform, formats, error)
        if [f["url"] for f in alternate] == [f["url"] for f in selected]:
            raise
        clear_streams(work)
        emit({"state": "downloading", "progress": None,
              "note": "Đang tự thử bản MP4 khả dụng từ cùng nguồn."})
        inputs = download_selected(ydl, info, alternate, work, max_bytes)
        if quality != "best":
            video = next((stream for stream in probe(inputs[0], ffprobe).get("streams", [])
                          if stream.get("codec_type") == "video"), {})
            if video.get("height") != int(quality):
                raise Failure("quality_unavailable")
        return inputs, True


def download_recovering(ydl, post, info, quality, work, max_bytes, ffprobe):
    """One metadata refresh after a transient transfer failure, then fail honestly.

    New signed URLs must never append to bytes from the previous extraction.
    Native cancellation/overall time/disk limits still bound this operation.
    Authentication, unsafe egress, DRM, TLS verification and limits are not retried.
    """
    for attempt in range(2):
        try:
            return download_attempt(ydl, post, info, quality, work, max_bytes, ffprobe)
        except Exception as error:
            if attempt or classify_error(error) not in ("network", "timeout"):
                raise
            clear_streams(work)
            emit({"state": "downloading", "progress": None,
                  "note": "Kết nối tải bị ngắt. App đang tự lấy lại nguồn và thử lại một lần."})
            info = ydl.extract_info(post.url, download=False)
            if not info:
                raise Failure("extraction_failed")


def run(request):
    os.environ["LOCALREEL_MUX_TIMEOUT"] = str(max(0,int(request.get("timeout",600))))
    post = parse_post(request["url"])
    install_guard()
    # No proxy environment, plugin loading, cookies, credentials, external downloader or ffmpeg network fallback.
    for key in list(os.environ):
        if key.lower().endswith("_proxy"):
            os.environ.pop(key, None)
    FFmpegFD.real_download = deny_external
    last = [0.0]

    def hook(status):
        if status.get("downloaded_bytes", 0) > request["max_bytes"]:
            raise Failure("limit")
        now = time.monotonic()
        if now - last[0] >= 0.4 or status.get("status") == "finished":
            last[0] = now
            total = status.get("total_bytes") or status.get("total_bytes_estimate")
            ratio = min(status.get("downloaded_bytes", 0) / total, 1) if total else None
            emit({"state": "downloading", "progress": (0.1 + 0.75 * ratio) if ratio is not None else None,
                  "downloaded_bytes": status.get("downloaded_bytes", 0)})

    options = {
        "quiet": True, "no_warnings": True, "logger": Quiet(), "noprogress": True,
        "proxy": "", "cachedir": False, "noplaylist": True, "skip_download": True,
        "socket_timeout": 15, "retries": 2, "extractor_retries": 1, "fragment_retries": 2,
        "concurrent_fragment_downloads": 1, "hls_prefer_native": True, "hls_skip_ads": False,
        "skip_unavailable_fragments": False, "fixup": "never", "postprocessors": [],
        "progress_hooks": [hook], "overwrites": True, "continuedl": False,
        "http_chunk_size": HTTP_CHUNK_BYTES,
        "max_filesize": request["max_bytes"],
        "allowed_extractors": ["facebook.*", "instagram.*", "tiktok$", "vm\\.tiktok$", "douyin$", "xiaohongshu$", "youtube$"],
        "usenetrc": False, "allow_unplayable_formats": False,
        "local_youtube_js": False,
    }
    with SafeYDL(options) as ydl:
        post = resolve_post(ydl, post)
        if request.get("native_http") and post.platform in ("facebook", "instagram"):
            def native_download(slot, info):
                # Private pipe only: JobManager consumes this event, never a WebView/log.
                emit({"state": "native_download", "slot": slot, "url": info["url"],
                      "headers": info.get("http_headers") or {}})
                answer = json.loads(sys.stdin.readline(16384))
                if not answer.get("native_success"):
                    raise Failure(answer.get("error_code", "network"))
            ydl.native_http_downloader = native_download
        info = ydl.extract_info(post.url, download=False)
        if not info:
            raise Failure("extraction_failed")
        formats = usable_formats(info)
        title = str(info.get("title") or "Video")[:200]
        heights = sorted({int(f["height"]) for f in formats if f.get("height") and f.get("vcodec") != "none"}, reverse=True)
        emit({"state": "metadata", "title": title, "heights": heights, "platform": post.platform})
        if request["mode"] == "inspect":
            return
        work = Path(request["work_dir"]).resolve()
        inputs, used_fallback = download_recovering(ydl, post, info, request["quality"], work,
                                                   request["max_bytes"], request["ffprobe"])
        result = mux(inputs, work / "result.mp4", request["ffmpeg"], request["ffprobe"], emit)
        if used_fallback:
            result["note"] += " Định dạng ưu tiên không truy cập được; đã dùng bản khả dụng từ cùng nguồn."
        result["fallback"] = used_fallback
        emit({"state": "complete", **result})


def main():
    try:
        request = json.loads(sys.stdin.readline(16384))
        run(request)
    except Exception as error:
        emit({"state": "error", "error_code": classify_error(error)})
        sys.exit(1)


if __name__ == "__main__":
    main()
