# Android 0.3-direct — 30/10/2026

Bấm **Dán link và tải** hoặc dán bằng bàn phím: tự gửi tác vụ, mặc định chọn chất lượng tốt nhất truy cập được. Có thể bật hỏi chất lượng. Bỏ toàn bộ nút Mở bài gốc và bridge mở nguồn; không cần xem/phát trang nguồn. Share Intent vẫn cho người dùng bấm Tải.

## Sửa bộ tải

Facebook/Instagram dùng yt-dlp tìm nguồn rồi chuyển MP4 trực tiếp qua OkHttp/Android TLS. Khắc phục lỗi SSL record-layer của Python khi nhận file. Giữ xác minh chứng chỉ và hostname; không dùng cookie, proxy, dịch vụ tải ngoài, DRM bypass. DNS và URL số/redirect đều chặn nội bộ/metadata. File slot cố định, giới hạn byte/thời gian, hủy network theo từng tác vụ, kiểm tra phản hồi đủ toàn bộ file. Nếu lỗi mạng tạm thời, tự lấy lại metadata một lần; lỗi đăng nhập/DRM/TLS verification/URL không an toàn không tự bỏ qua.

## Kết quả trên APK bàn giao

Giả lập Android 11/API30 x86_64 WHPX; 5 ca tải thật thành công: Facebook 1080×1080, Instagram 1080×1920, YouTube video 320×240, Shorts 720×1280 và TikTok 576×1024. Mọi file có hình và tiếng, xác minh Android MediaExtractor + FFprobe. Facebook/Instagram/YouTube/TikTok dùng clipboard thật, chỉ click Paste, không click Submit sau đó. Shorts thử ACTION_SEND và chọn chất lượng. Mỗi ca tạo file mới, không lấy lại file đã tải.

3 ca thất bại được giữ đúng là lỗi: Douyin login_required, Xiaohongshu extraction_failed, link TikTok rút gọn cũ unsupported_url. Tất cả 8 ca có 0 lần cố mở trình duyệt nguồn. URL/hash/codec/byte/thời gian ghi trong live-results.json; không đóng gói video tải từ nguồn.

34 Python test, 9 Java unit test và 16 Node UI assertion đạt. Bộ native flows/UI thử clipboard lỗi, link trùng, phát thật, lưu Gallery thật, hủy/thử lại, service stop, sáng/tối, xoay ngang và chữ lớn; kết quả cuối trong device-flows.txt. Lint 0 lỗi, 7 cảnh báo.

Chưa thử điện thoại ARM64 thật hoặc giao diện của các app mạng xã hội chính thức. Không cam kết mọi URL được hỗ trợ, mọi video là bản gốc hay mọi nguồn đều tải được. Test Share là Android Intent thực qua harness. Nguồn cần quyền riêng/đăng nhập/DRM vẫn có thể thất bại.
