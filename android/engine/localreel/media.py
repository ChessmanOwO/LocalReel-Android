import json
import os
from pathlib import Path
import subprocess
from .errors import Failure

DEMUXERS = "mov,matroska,webm,mpegts,aac,mp3,ogg,flac"


def probe(path: Path, executable: str) -> dict:
    try:
        run = subprocess.run([
            executable, "-v", "error", "-protocol_whitelist", "file,pipe",
            "-format_whitelist", DEMUXERS, "-show_streams", "-show_format",
            "-of", "json", str(path),
        ], capture_output=True, timeout=30, check=True)
        return json.loads(run.stdout)
    except FileNotFoundError:
        raise Failure("dependency_missing") from None
    except (subprocess.SubprocessError, ValueError):
        raise Failure("processing_failed") from None


def mux(inputs: list[Path], output: Path, ffmpeg: str, ffprobe: str, notify=lambda _: None) -> dict:
    streams = [probe(p, ffprobe).get("streams", []) for p in inputs]
    video = next(((i, s) for i, ss in enumerate(streams) for s in ss if s.get("codec_type") == "video"), None)
    audio = next(((i, s) for i, ss in enumerate(streams) for s in ss if s.get("codec_type") == "audio"), None)
    if not video:
        raise Failure("processing_failed")
    if not audio:
        raise Failure("no_audio")
    base = [ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y"]
    for path in inputs:
        base += ["-protocol_whitelist", "file,pipe", "-format_whitelist", DEMUXERS, "-i", str(path)]
    base += ["-map", f"{video[0]}:{video[1]['index']}", "-map", f"{audio[0]}:{audio[1]['index']}",
             "-map_metadata", "-1", "-map_chapters", "-1"]
    # H.264/HEVC + AAC are the conservative Android playback targets.
    vcopy = video[1].get("codec_name") in ("h264", "hevc")
    acopy = audio[1].get("codec_name") == "aac"

    def execute(copy_video, copy_audio):
        args = base + ["-c:v", "copy" if copy_video else "libx264", "-c:a", "copy" if copy_audio else "aac"]
        if not copy_video:
            args += ["-crf", "18", "-preset", "veryfast", "-pix_fmt", "yuv420p", "-vf", "pad=ceil(iw/2)*2:ceil(ih/2)*2"]
        if not copy_audio:
            args += ["-b:a", "192k"]
        if copy_video and video[1].get("codec_name") == "hevc":
            args += ["-tag:v", "hvc1"]
        args += ["-movflags", "+faststart", str(output)]
        try:
            return subprocess.run(args, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                  stderr=subprocess.DEVNULL, timeout=(int(os.environ.get("LOCALREEL_MUX_TIMEOUT","600")) or None)).returncode == 0
        except FileNotFoundError:
            raise Failure("dependency_missing") from None
        except subprocess.TimeoutExpired:
            raise Failure("timeout") from None

    # Always try lossless streamcopy first, even for codecs that may need conversion for iOS.
    notify({"state": "processing", "progress": 0.9, "note": "Đang remux MP4 không giảm chất lượng."})
    remux_ok = execute(True, True)
    transcoded = not remux_ok or not (vcopy and acopy)
    if transcoded:
        notify({"state": "processing", "progress": 0.92,
                "note": "Cần chuyển mã để tạo MP4 tương thích Android; có thể giảm chất lượng."})
        if not execute(vcopy if remux_ok else False, acopy if remux_ok else False):
            raise Failure("processing_failed")
    result = probe(output, ffprobe)
    kinds = {s.get("codec_type") for s in result.get("streams", [])}
    if not {"video", "audio"}.issubset(kinds) or output.stat().st_size == 0:
        raise Failure("processing_failed")
    final_video = next(stream for stream in result["streams"] if stream.get("codec_type") == "video")
    return {"width": final_video.get("width"), "height": final_video.get("height"), "transcoded": transcoded, "size": output.stat().st_size,
            "note": "Đã chuyển mã cho MP4 tương thích Android; có thể giảm chất lượng." if transcoded
            else "Đã remux MP4 bằng streamcopy; không mã hóa lại hình/tiếng.",
            "provenance": "Bản nguồn cho phép truy cập; chưa xác minh là file gốc người đăng."}
