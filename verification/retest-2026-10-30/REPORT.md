# Kiểm thử lại Android — 30/10/2026

Đã thử **đúng APK 0.2-ondevice đang đăng trên GitHub Releases**, SHA-256 `28db3eddcbcc08f99c9c56c2aa531b53e851d4a753a2a38dba720bff574c3c48`. Không sửa app runtime hay phát hành APK mới trong lượt kiểm thử này. Máy giả lập Android 11/API30 x86_64, WHPX; chưa thử điện thoại thật.

## Kết quả tải thật

| Trường hợp | Cách đưa link | Kết quả lần này |
|---|---|---|
| YouTube video | Dán clipboard → chọn chất lượng → tải | Thành công, 320×240, H.264 + AAC, 635.479 byte |
| YouTube Shorts | ACTION_SEND → tự chọn chất lượng và tải | Thành công, 720×1280, H.264 + AAC, 5.346.308 byte |
| Facebook video | Dán clipboard → tự tải | Lỗi `network`, vẫn lỗi khi thử lại lần hai |
| Instagram Reel | Dán clipboard → tự tải | Lỗi `network`, vẫn lỗi khi thử lại lần hai |
| TikTok video | Dán clipboard → tự tải | Thành công, 576×1024, H.264 + AAC, 5.076.763 byte |
| Douyin | Dán clipboard → tự tải | Lỗi `login_required` |
| Xiaohongshu | Dán clipboard → tự tải | Lỗi `extraction_failed` |
| Link TikTok rút gọn cũ | Dán clipboard → tự tải | Lỗi `unsupported_url` |

**3/8 ca tải thành công, 5/8 thất bại.** Thử thêm Facebook và Instagram mỗi nguồn một lần đều thất bại. Không tính nguồn từ chối là test tải thành công. Kết quả Facebook/Instagram khác ngày 29/10: hôm đó tải được nhưng hôm nay gặp lỗi kết nối giữa lúc nhận dữ liệu. Chỉ ghi nhận nhóm lỗi `network`; chưa đủ bằng chứng phân biệt do kết nối mạng, TLS/CDN hay runtime Android. Chưa sửa/khắc phục nguyên nhân trong lượt kiểm thử này.

Các MP4 thành công được tạo từ tải mạng mới (`reused_existing=false`), kiểm tra track bằng Android MediaExtractor rồi FFprobe trên bản file chép từ máy giả lập. Có cả hình và âm thanh; không dùng video giả hay quay màn hình. File video không được commit.

## Có phải mở trang nguồn không?

**Không.** Các ca dán link thực sự đi qua ClipboardManager → nút Dán liên kết → Tìm nguồn và tải. Bộ kiểm thử giám sát Intent ACTION_VIEW: **0 lần app cố mở trình duyệt nguồn trong cả 10 lượt tải**. Ca YouTube video có chọn chất lượng; các ca còn lại tắt hỏi chất lượng để tự tải. ACTION_SEND là intent kiểm thử qua Android framework, không phải thao tác trên từng app mạng xã hội chính thức.

Không mở trang gốc không có nghĩa mọi nguồn đều tải thành công: các lỗi trong bảng vẫn được app báo thất bại, không giả lập hoàn tất.

## Các kiểm tra khác

- Python engine unit: 20/20 đạt.
- Java URL policy unit: 6/6 đạt.
- Android LocalFlowsTest + UiScreensTest: 8/8 đạt, 28,652 giây. Gồm clipboard/lỗi URL, link trùng, snapshot không lộ URL, phát MP4 thật, lưu Thư viện thật, hủy/thử lại/xóa và vòng đời foreground service; giao diện sáng/tối, chữ lớn, xoay ngang, kích thước nút và tràn ngang.
- Chữ hệ thống 200%: 1/1 UiScreensTest đạt, 6,447 giây; đã khôi phục 100%.
- Build test APK và lint: đạt, 0 lỗi và 6 cảnh báo.
- Xuất Files qua trình chọn thư mục hệ thống chưa được tự động kiểm thử trọn luồng. Chưa thử ARM64/điện thoại thật, Android mới hơn, OEM quản lý pin và giao diện các app nguồn chính thức.

`live-results.json` lưu nguồn cụ thể, trạng thái, byte, hash và FFprobe. `facebook-instagram-retries.json` giữ lần thử lại riêng, không ghi đè thất bại ban đầu. Các log test nằm cạnh báo cáo.

## Chạy lại ca dán link

Cài APK app và APK androidTest được build cùng khóa debug phù hợp, rồi chạy:

```sh
adb shell am instrument -w -e class dev.localreel.android.LiveDownloadTest -e url https://youtu.be/jNQXAC9IVRw -e input_mode paste -e ask_quality false dev.localreel.android.test/androidx.test.runner.AndroidJUnitRunner
```

Dùng dữ liệu app thử nghiệm sạch hoặc link chưa tải. Live test nay tìm tác vụ mới thay vì lấy nhầm tác vụ lỗi cũ cùng URL. Kịch bản test có thêm kiểm tra clipboard và giám sát mở trình duyệt; không thay đổi cách app tải video.
