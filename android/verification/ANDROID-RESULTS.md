# Kết quả kiểm thử Android — 29/10/2026

## Môi trường và phạm vi

APK 0.2-ondevice, applicationId `dev.localreel.android`, Android 11/API 30 AOSP x86_64, máy giả lập WHPX trên Windows. Dùng engine Python/yt-dlp/FFmpeg thật bên trong Android, tải dữ liệu thực từ nguồn công khai. Không quay màn hình, không dùng video giả hoặc bản đã có để khai báo download thành công. `reused_existing=false` trong các ca live thành công.

Không có điện thoại Android thật kết nối. ARM64 có trong APK nhưng chưa chạy trên điện thoại. Chưa cài và thao tác từng app Facebook/Instagram/TikTok/Douyin/Xiaohongshu/YouTube chính thức: test tạo ACTION_SEND qua Android framework tới LocalReel, rồi bấm giao diện LocalReel. Chưa thử Android 12–16, pin yếu/Doze/OEM kill, TalkBack và phát mọi codec trên mọi thiết bị. Không đọc IPA đã ký.

## Tải thật theo nền tảng

| Ca | URL công khai đã thử | Kết quả cuối | MP4 thực tế |
|---|---|---|---|
| YouTube video | https://youtu.be/jNQXAC9IVRw | Thành công | 320×240, H.264 + AAC, 19.06 giây, 635.479 byte; remux |
| YouTube Shorts | https://www.youtube.com/shorts/BGQWPY4IigY | Thành công | 720×1280, H.264 + AAC, 14.40 giây, 5.346.308 byte; chuyển mã |
| Facebook video | https://www.facebook.com/cnn/videos/10155529876156509/ | Thành công sau sửa fallback | 1080×1080, H.264 + AAC, 44.33 giây, 20.546.330 byte; chuyển mã, có ghi chú dùng nguồn dự phòng |
| Instagram Reel | https://www.instagram.com/reel/Dcea3BiPTBm/ | Thành công | 1080×1920, H.264 + AAC, 41.08 giây, 19.478.386 byte |
| TikTok video | https://www.tiktok.com/@hankgreen1/video/7047596209028074758 | Thành công | 576×1024, H.264 + AAC, 21.27 giây, 5.076.763 byte |
| Douyin | https://www.douyin.com/video/6961737553342991651 | Thất bại: `login_required` | Không có file; app báo cần đăng nhập, bản này chưa có phiên phù hợp |
| Xiaohongshu | https://www.xiaohongshu.com/explore/6411cf99000000001300b6d9 | Thất bại: `extraction_failed` | Không có file; chưa xác định được nguồn truy cập được |
| TikTok link rút gọn cũ | https://vm.tiktok.com/ZTR45GpSF/ | Thất bại: `unsupported_url` | Link thử nghiệm không trả về đích video hợp lệ cho phiên này |

Tổng: **5 ca tải thật thành công, 3 ca tải thật thất bại**. Các ca thất bại không được tính thành test tải thành công. Đây là bằng chứng cho các URL cụ thể tại thời điểm thử, không chứng minh mọi video trên nền tảng đều tải được. Facebook Reels và các loại link chưa có mẫu trong bảng chưa được kiểm chứng riêng.

MediaExtractor Android xác minh có cả track hình và âm thanh trước khi đánh dấu complete. Các MP4 thật được chép về máy tính rồi chạy FFprobe để đối chiếu codec/duration/hash trong `downloaded-files-probe.json`. Video không được đóng gói trong ZIP bàn giao. Chiều cao Shorts 1280 thể hiện video dọc; không phải nhãn chất lượng ngang 1280p.

## Lỗi phát hiện và đã sửa

- Facebook: định dạng DASH ưu tiên gặp lỗi SSL record layer trên runtime Android. Thêm fallback progressive công khai từ cùng nguồn, chỉ cho lỗi mạng/trích xuất phù hợp; không hạ kiểm tra TLS, không thử vượt login/DRM, không fallback cho lỗi URL không an toàn. Chạy lại chính URL đó đạt trong 95,31 giây; nguồn dự phòng và việc chuyển mã hiện rõ trên tác vụ. Không âm thầm thay lựa chọn chiều cao cụ thể bằng mức khác.
- Instagram: chuyển mã preset medium quá chậm trên giả lập. Dùng veryfast CRF 18 và truyền đúng cài đặt thời gian vào bước mux; ca cuối thành công trong 122,17 giây. Không suy ra đây là file gốc, chuyển mã có thể giảm chất lượng.
- Race giữa service dừng lúc rảnh và yêu cầu hủy/thử lại mới: sửa token thế hệ, revision hàng đợi và planned idle stop. Kiểm thử retry → cancel ngay, stop service khi có hàng đợi và khởi chạy lại đạt.
- UI bị cắt đầu/cuối do WebView padding không làm giảm viewport CSS: chuyển system insets sang FrameLayout cha. Chụp lại giao diện thật và chạy lại bộ kiểm thử cùng chữ hệ thống 200% đạt.
- Không đổi action của Intent nhận chia sẻ; giữ trạng thái consumed riêng. Listener và native video dừng đúng vòng đời Activity.

## Kiểm thử tự động cuối

| Nhóm | Kết quả | Phạm vi |
|---|---|---|
| Python engine unit | 20/20 đạt | URL/hostname, IP riêng/metadata/DNS, tên file, giới hạn byte, fallback Facebook và trường hợp không được fallback |
| Java URL policy unit | 6/6 đạt | URL/platform allowlist, văn bản chia sẻ, dedup, UUID/chất lượng hợp lệ |
| Android LocalFlowsTest + UiScreensTest | 8/8 đạt, 26,626 giây | Paste, lỗi URL, dedup, snapshot không chứa URL, phát thật, lưu thư viện thật, hủy/thử lại/xóa, lifecycle service, cài đặt, kích thước điều khiển và các ảnh UI |
| Android system font scale 2.0 | 1/1 đạt, 6,585 giây | UiScreensTest sau sửa insets; khôi phục font_scale=1.0 sau thử |
| Gradle assembleDebug / testDebugUnitTest / lintDebug | Đạt | Lint: 0 lỗi, 6 cảnh báo; không tuyên bố hết mọi cảnh báo |
| Live download | 5 thành công / 3 thất bại | Bảng nguồn phía trên; chạy tuần tự, có cả app engine native |

Xuất Files dùng ACTION_CREATE_DOCUMENT đã triển khai nhưng chưa kiểm thử tự động trọn luồng chọn thư mục hệ thống. Lưu Gallery và phát video đã kiểm tra bằng API Android thực, không chỉ kiểm tra nút hiện ra.

Ba vòng review source độc lập đã hoàn tất trong workflow agentic; không có chứng cứ Antigravity chạy review bản Android này nên không gắn nhãn Antigravity đã duyệt. Các kiểm thử runtime phía trên mới là bằng chứng chạy được. Đây không phải audit bảo mật toàn diện.

## Bằng chứng và giới hạn

- `live-results.json`: kết quả cuối từng URL; Facebook thay bằng lần chạy lại có fallback, giữ nguồn gốc lịch sử trong `history-pre-facebook-fix.json`.
- `facebook-fallback-android.json`, `downloaded-files-probe.json`: tác vụ Facebook thực và thông số MP4 thực.
- `android-verified-flows.txt`, `android-font200-final.txt`, `url-policy-unit.xml`: kết quả các bộ test cuối.
- `ui-home-light.png`, `ui-home-dark.png`, `ui-library-large.png`, `ui-landscape.png`, `ui-font200-home.png`, `ui-font200-landscape.png`: ảnh chụp UI native Android sau sửa insets. Không phải ảnh dựng.

Giới hạn nguồn vẫn còn: không vượt DRM, nội dung riêng tư, paywall hay giới hạn tài khoản/vùng. Không nhập mật khẩu/cookie. Android hiện tắt JS runtime của yt-dlp nên không cam kết lấy được mọi format YouTube. Nút Mở bài gốc mở trình duyệt ngoài, chưa trích xuất nguồn từ playback đó. Chế độ bỏ quota không loại bỏ giới hạn mạng/OS/bộ nhớ vật lý. Nguồn có thể đổi bất kỳ lúc nào.
