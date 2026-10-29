"""Worker-only egress guard. Validate DNS answers AND the numeric address at connect.

No curl-cffi, external network downloaders, proxies or plugins are enabled. The guard
covers urllib/requests sockets including every redirect and native HLS/DASH segment.
It is defense in depth, not an OS sandbox for an exploited Python interpreter.
"""
import ipaddress
import socket
from urllib.parse import urlsplit
from .errors import Failure


def public_ip(value: str) -> bool:
    try:
        ip = ipaddress.ip_address(value.split("%")[0])
        if isinstance(ip, ipaddress.IPv6Address) and (ip.ipv4_mapped or ip.sixtofour or ip.teredo):
            return False
        # IPv6 NAT64 may map back to a private IPv4 service.
        if ip.version == 6 and ip in ipaddress.ip_network("64:ff9b::/96"):
            return False
        return ip.is_global and not (ip.is_private or ip.is_multicast or ip.is_reserved or ip.is_unspecified
                                    or getattr(ip, "is_site_local", False))
    except ValueError:
        return False


def checked_answers(host, port, resolver=socket.getaddrinfo, **kwargs):
    if port not in (80, 443):
        raise Failure("unsafe_url")
    answers = resolver(host, port, **kwargs)
    if not answers or any(not public_ip(a[4][0]) for a in answers):
        raise Failure("unsafe_url")
    return answers


def validate_media_url(value: str):
    try:
        u = urlsplit(value)
        if u.scheme not in ("http", "https") or not u.hostname or u.username or u.password or "\\" in value:
            raise Failure("unsafe_url")
        checked_answers(u.hostname, u.port or (443 if u.scheme == "https" else 80))
    except (ValueError, UnicodeError):
        raise Failure("unsafe_url") from None


def install_guard():
    original_resolve = socket.getaddrinfo
    original_connect = socket.socket.connect
    original_connect_ex = socket.socket.connect_ex

    def resolve(host, port, family=0, type=0, proto=0, flags=0):
        return checked_answers(host, port, original_resolve, family=family, type=type, proto=proto, flags=flags)

    def check(sock, address):
        if sock.family not in (socket.AF_INET, socket.AF_INET6) or not isinstance(address, tuple):
            raise Failure("unsafe_url")
        # Clients resolve through getaddrinfo. Requiring numeric connect prevents DNS rebinding.
        if address[1] not in (80, 443) or not public_ip(address[0]):
            raise Failure("unsafe_url")

    def connect(sock, address):
        check(sock, address)
        return original_connect(sock, address)

    def connect_ex(sock, address):
        check(sock, address)
        return original_connect_ex(sock, address)

    socket.getaddrinfo = resolve
    socket.socket.connect = connect
    socket.socket.connect_ex = connect_ex
