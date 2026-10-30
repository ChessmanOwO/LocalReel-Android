MESSAGES = {
    "tls_verification_failed": "Không xác minh được kết nối bảo mật tới nguồn. Kiểm tra ngày giờ và mạng rồi thử lại.",
    "network": "Kết nối tới nguồn bị lỗi. Thử lại hoặc đổi mạng.",
    "unsupported_url": "Liên kết chưa được hỗ trợ. Hãy dùng URL bài video/Reel Facebook, Instagram, TikTok, Douyin, Xiaohongshu hoặc YouTube.",
    "unsafe_url": "Địa chỉ bị chặn vì không an toàn hoặc trỏ vào mạng nội bộ.",
    "login_required": "Nguồn yêu cầu đăng nhập. MVP chỉ tải nội dung truy cập công khai; không nhập mật khẩu vào app.",
    "unavailable": "Nguồn báo nội dung không còn khả dụng, đã xóa hoặc không có quyền xem.",
    "blocked": "Nguồn chặn truy cập hoặc giới hạn yêu cầu. Hãy thử lại sau.",
    "extraction_failed": "Chưa đọc được nguồn video. Website có thể đã thay đổi hoặc giới hạn truy cập.",
    "no_audio": "Nguồn không cung cấp luồng âm thanh truy cập được. Không tạo file thành công giả.",
    "drm": "Nội dung có bảo vệ DRM; tool không vượt cơ chế bảo vệ.",
    "live": "MVP chưa hỗ trợ video đang phát trực tiếp.",
    "playlist": "Bài chứa nhiều mục. Hãy chia sẻ liên kết của một video cụ thể.",
    "quality_unavailable": "Chất lượng đã chọn không còn khả dụng. Thử lại hoặc chọn chất lượng khác.",
    "limit": "Tác vụ vượt giới hạn dung lượng, số mục hoặc dung lượng đĩa đã cấu hình.",
    "timeout": "Đã hết thời gian xử lý. Có thể thử lại khi mạng ổn định hơn.",
    "processing_failed": "Không ghép/chuyển MP4 được hoặc file không có đủ hình và tiếng.",
    "dependency_missing": "Backend thiếu FFmpeg/ffprobe hoặc Deno (cần cho YouTube). Cài dependency rồi khởi động lại.",
    "interrupted": "Backend đã khởi động lại. Hãy thử lại tác vụ này.",
    "expired": "File đã được dọn theo thời hạn lưu. Hãy tạo lại tác vụ nếu cần.",
    "internal": "Backend gặp lỗi xử lý. Tác vụ chưa hoàn tất.",
    "busy": "Tác vụ đang hủy hoặc dọn file. Chờ hoàn tất trước khi thử lại.",
}


class Failure(Exception):
    def __init__(self, code: str):
        self.code = code if code in MESSAGES else "internal"
        super().__init__(self.code)


def classify_error(error: Exception) -> str:
    if isinstance(error, Failure):
        return error.code
    # Never expose the raw exception: yt-dlp often embeds signed URLs and headers.
    value = str(error).lower()
    if any(s in value for s in ("certificate_verify_failed", "certificate verify failed", "certificate verification failed")):
        return "tls_verification_failed"
    if any(s in value for s in ("unsafe_url", "non-public", "blocked address")):
        return "unsafe_url"
    if any(s in value for s in ("[ssl]", "sslerror", "record layer", "connection reset", "timed out", "network is unreachable")):
        return "network"
    if any(s in value for s in ("login", "log in", "sign in", "cookies", "private video")):
        return "login_required"
    if any(s in value for s in ("429", "403", "rate-limit", "rate limit", "blocked")):
        return "blocked"
    if any(s in value for s in ("removed", "deleted", "404", "not available", "unavailable")):
        return "unavailable"
    if "drm" in value:
        return "drm"
    return "extraction_failed"
