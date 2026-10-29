"""Small platform adapters: accepted post shapes, normalization and extractor choice."""
from dataclasses import dataclass
import re
from urllib.parse import parse_qs, urlencode, urlsplit, urlunsplit
from .errors import Failure


@dataclass(frozen=True)
class Post:
    url: str
    platform: str
    extractor: str

    @property
    def identity(self) -> str:
        parts = urlsplit(self.url)
        if self.platform == "xiaohongshu" and parts.hostname == "www.xiaohongshu.com":
            return "xiaohongshu:" + parts.path.rstrip("/").rsplit("/", 1)[-1]
        if self.platform == "tiktok" and self.extractor == "TikTok":
            return "tiktok:" + parts.path.rstrip("/").rsplit("/", 1)[-1]
        return self.url


class Instagram:
    hosts = {"instagram.com", "www.instagram.com", "m.instagram.com"}

    @staticmethod
    def parse(parts) -> Post:
        path = parts.path
        match = re.fullmatch(r"/(?:[\w.]+/)?(reel|reels|p|tv)/([A-Za-z0-9_-]+)/?", path)
        if match:
            kind, ident = match.groups()
            kind = "reel" if kind == "reels" else kind
            return Post(f"https://www.instagram.com/{kind}/{ident}/", "instagram", "Instagram")
        # Instagram shortened links are resolved by the official extractor under the same guard.
        if re.fullmatch(r"/share/(?:reel/)?[A-Za-z0-9_-]+/?", path):
            return Post(urlunsplit(("https", "www.instagram.com", path, "", "")), "instagram", "Instagram")
        raise Failure("unsupported_url")


class Facebook:
    hosts = {"facebook.com", "www.facebook.com", "m.facebook.com", "web.facebook.com", "fb.watch"}

    @staticmethod
    def parse(parts) -> Post:
        path = parts.path
        query = parse_qs(parts.query, max_num_fields=30)
        if parts.hostname == "fb.watch":
            if not re.fullmatch(r"/[A-Za-z0-9_-]+/?", path):
                raise Failure("unsupported_url")
            return Post(f"https://fb.watch{path}", "facebook", "Facebook")
        if path.rstrip("/") in ("/watch", "/video.php", "/video/video.php") and re.fullmatch(r"\d+", query.get("v", [""])[0]):
            return Post("https://www.facebook.com/watch/?" + urlencode({"v": query["v"][0]}), "facebook", "Facebook")
        if re.fullmatch(r"/reel/\d+/?", path) or re.fullmatch(r"/share/(?:r|v)/[A-Za-z0-9_-]+/?", path):
            return Post("https://www.facebook.com" + path, "facebook", "Facebook")
        if re.fullmatch(r"/[A-Za-z0-9._-]+/(?:videos|posts)/(?:[A-Za-z0-9._-]+/)?[A-Za-z0-9_-]+/?", path):
            return Post("https://www.facebook.com" + path, "facebook", "Facebook")
        if path in ("/story.php", "/permalink.php") and re.fullmatch(r"\d+|pfbid[A-Za-z0-9]+", query.get("story_fbid", [""])[0]):
            clean = {k: query[k][0] for k in ("story_fbid", "id") if k in query}
            return Post("https://www.facebook.com" + path + "?" + urlencode(clean), "facebook", "Facebook")
        raise Failure("unsupported_url")


class TikTok:
    hosts = {"tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com"}

    @staticmethod
    def parse(parts) -> Post:
        path = parts.path
        if parts.hostname in {"vm.tiktok.com", "vt.tiktok.com"} and re.fullmatch(r"/[A-Za-z0-9]+/?", path):
            return Post(f"https://{parts.hostname}{path}", "tiktok", "TikTokVM")
        if re.fullmatch(r"/t/[A-Za-z0-9]+/?", path):
            return Post("https://www.tiktok.com" + path, "tiktok", "TikTokVM")
        if re.fullmatch(r"/(?:@[\w.-]+/video/\d+|share/video/\d+|embed/\d+)/?", path):
            return Post("https://www.tiktok.com" + path, "tiktok", "TikTok")
        raise Failure("unsupported_url")


class Douyin:
    hosts = {"douyin.com", "www.douyin.com", "v.douyin.com", "www.iesdouyin.com"}

    @staticmethod
    def parse(parts) -> Post:
        path = parts.path
        if parts.hostname == "v.douyin.com" and re.fullmatch(r"/[A-Za-z0-9_-]+/?", path):
            return Post("https://v.douyin.com" + path, "douyin", "Douyin")
        match = re.fullmatch(r"/(?:video|share/video)/(\d+)/?", path)
        if match:
            return Post("https://www.douyin.com/video/" + match[1], "douyin", "Douyin")
        raise Failure("unsupported_url")


class Xiaohongshu:
    hosts = {"xiaohongshu.com", "www.xiaohongshu.com", "xhslink.com", "www.xhslink.com"}

    @staticmethod
    def parse(parts) -> Post:
        if parts.hostname in {"xhslink.com", "www.xhslink.com"}:
            if re.fullmatch(r"/(?:a/|o/)?[A-Za-z0-9]+/?", parts.path):
                return Post("https://xhslink.com" + parts.path, "xiaohongshu", "XiaoHongShu")
        elif re.fullmatch(r"/(?:explore|discovery/item)/[0-9a-f]+/?", parts.path):
            # This source may require its access token. Keep only relevant keys;
            # never log the URL or expose it in job responses.
            query = parse_qs(parts.query, max_num_fields=30)
            clean = {key: query[key][0] for key in ("xsec_token", "xsec_source") if key in query}
            return Post(urlunsplit(("https", "www.xiaohongshu.com", parts.path, urlencode(clean), "")),
                        "xiaohongshu", "XiaoHongShu")
        raise Failure("unsupported_url")


class YouTube:
    hosts = {"youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "www.youtube-nocookie.com"}

    @staticmethod
    def parse(parts) -> Post:
        query = parse_qs(parts.query, max_num_fields=30)
        ident = None
        if parts.hostname == "youtu.be":
            ident = parts.path.strip("/")
        elif parts.path.rstrip("/") == "/watch":
            values = query.get("v", [])
            ident = values[0] if len(values) == 1 else None
        else:
            match = re.fullmatch(r"/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})/?", parts.path)
            if match:
                ident = match[1]
        if ident and re.fullmatch(r"[A-Za-z0-9_-]{11}", ident):
            return Post("https://www.youtube.com/watch?v=" + ident, "youtube", "Youtube")
        raise Failure("unsupported_url")


ADAPTERS = (Facebook, Instagram, TikTok, Douyin, Xiaohongshu, YouTube)


def parse_post(value: str) -> Post:
    if len(value) > 4096 or any(ord(c) <= 32 or ord(c) == 127 for c in value) or "\\" in value:
        raise Failure("unsafe_url")
    try:
        parts = urlsplit(value)
        if parts.scheme != "https" or parts.username or parts.password or parts.port not in (None, 443):
            raise Failure("unsafe_url")
        for adapter in ADAPTERS:
            if parts.hostname in adapter.hosts:
                return adapter.parse(parts)
    except (ValueError, UnicodeError):
        raise Failure("unsafe_url") from None
    raise Failure("unsupported_url")
