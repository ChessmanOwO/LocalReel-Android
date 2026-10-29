# LocalReel Android 0.2 — chạy trực tiếp trên điện thoại

App Android tải từ liên kết bài/video, không cần Mac/backend hay dịch vụ tải video bên thứ ba. Giao diện HTML/JavaScript đóng trong APK, chạy trong WebView bị giới hạn ở asset nội bộ; phần tải, hàng đợi, phát video và xuất file chạy bằng Android/Java + Python/yt-dlp + FFmpeg native. Đây là bản Android riêng, không chuyển hay đọc IPA đã ký của người dùng.

## Cài APK

1. Chép `LocalReel-Android.apk` sang điện thoại Android 8.0 trở lên, CPU ARM64. APK cũng có x86_64 cho máy giả lập; không có ARM 32-bit.
2. Mở APK và cho phép ứng dụng Files/trình duyệt cài ứng dụng từ nguồn này. Đây là APK debug ký bằng khóa phát triển, dành cho cài cá nhân và kiểm thử, không phải bản phát hành trên Play Store. Không cần root.
3. Mở LocalReel, dán liên kết HTTPS rồi bấm **Tìm nguồn và tải**. Hoặc dùng Chia sẻ → LocalReel từ ứng dụng khác. Lần chạy đầu giải nén runtime vào vùng dữ liệu riêng của app.
4. Nếu bật **Cho tôi chọn chất lượng**, chờ phân tích rồi chọn một mức nguồn cung cấp. Theo dõi ở **Tiến trình**, xem video ở **Đã lưu**, chọn lưu vào Thư viện hoặc chọn vị trí file.
5. Cho phép thông báo nếu muốn thấy tiến trình tải. Tải dùng foreground service; vẫn có thể bị hệ điều hành/nhà sản xuất dừng. Khi bị ngắt, app hiển thị trạng thái để người dùng thử lại; không hứa tiếp tục sau force-stop/khởi động lại máy.

## Những gì bản này làm

- Nhận liên kết YouTube/video Shorts, Facebook video/Reels, Instagram Reels, TikTok, Douyin, Xiaohongshu bằng bộ xử lý riêng của yt-dlp. Có bộ xử lý không đồng nghĩa mọi link đều hoạt động; xem [kết quả thử thực tế](verification/ANDROID-RESULTS.md).
- Một tác vụ chạy tại một thời điểm; hàng đợi, chọn chất lượng, hủy, thử lại, phát trước, phát hiện link trùng, chia sẻ MP4 và xuất file.
- Ưu tiên chất lượng cao nhất trong các định dạng truy cập được; ghép/remux MP4 trước, chuyển mã khi cần để tương thích và ghi rõ trên tác vụ. Không cam kết đây là file gốc người đăng, không xóa watermark.
- Với Facebook, nếu định dạng ưu tiên bị lỗi mạng, thử progressive MP4 công khai từ cùng nguồn, hiển thị ghi chú và kích thước thực. Nếu người dùng chọn chiều cao cụ thể, không âm thầm nhận bản thấp khác mức đã chọn.
- Giao diện tiếng Việt, nút lớn, sáng/tối và chữ lớn, dựa trên hướng dẫn [UI/UX Pro Max](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill). Xem `design-system/MASTER.md`.

**Không phải video nào cũng cần phát trước trên trang nguồn.** Hãy thử dán URL trực tiếp. Nút **Mở bài gốc** hiện chỉ mở trình duyệt ngoài để kiểm tra bài còn truy cập được; bản này chưa đọc luồng phát/cookie từ trình duyệt đó. Không quảng cáo đây là phương án bắt mọi video đang phát.

## Cài đặt dung lượng và an toàn

Mặc định bật chế độ bỏ giới hạn dung lượng/thời gian do app tự đặt. Khi tắt chế độ này, app giới hạn 100 tác vụ lưu trong danh sách, khoảng 512 MiB mỗi tác vụ kể cả file tạm, 2 GiB dữ liệu app và 10 phút mỗi lượt xử lý. Cài đặt áp dụng vào lượt xử lý tiếp theo. Dù ở chế độ nào, app vẫn giữ một phần dung lượng trống để bảo vệ thiết bị (128 MiB khi nhận tác vụ, dừng khi còn dưới 32 MiB), xử lý tuần tự và chịu giới hạn Android/mạng/nguồn. Việc chuyển mã có thể lâu và làm nóng máy.

Tác vụ kết thúc cũ hơn 7 ngày có thể được dọn khi mở/nhận tác vụ; hãy xuất video muốn giữ lâu sang Thư viện/Files. Gỡ app xóa dữ liệu riêng. App tắt sao lưu dữ liệu cloud/device transfer. Xuất file dùng MediaStore hoặc trình chọn file hệ thống; không xin quyền đọc toàn bộ bộ nhớ.

Chỉ nhận URL HTTPS trên các host nền tảng được cho phép, chặn IP riêng/metadata ở tầng DNS và kết nối, kiểm tra redirect, không chạy shell với URL người dùng. Không hỗ trợ proxy, plugin extractor tải từ xa hoặc đăng nhập mạng xã hội. App không hỏi mật khẩu, không nhập cookie và không vượt DRM/paywall/quyền truy cập. URL được lưu riêng trong dữ liệu app để thử lại/mở nguồn, không đưa vào snapshot UI hay log lệnh. Bản debug cho phép ADB `run-as` để kiểm thử; không coi thiết bị đang bật ADB là môi trường bảo mật tuyệt đối.

## Giới hạn hiện tại

- Douyin thử nghiệm yêu cầu phiên đăng nhập; Xiaohongshu thử nghiệm không trích xuất được; link TikTok rút gọn cũ thử nghiệm không dùng được. Không có cơ chế đăng nhập trong bản này.
- Private/nhóm kín, video bị xóa/hạn chế vùng hoặc nguồn chống bot có thể thất bại. Không bảo đảm tải được mọi video công khai.
- Android hiện tắt JS runtime và remote components của yt-dlp. Một số định dạng YouTube cần JavaScript/challenge sẽ không có. Chất lượng “tốt nhất” là tốt nhất trong các nguồn đọc được ở phiên đó.
- Đã chạy máy giả lập Android 11 API 30 x86_64; chưa kiểm thử điện thoại ARM64, Android phiên bản mới hơn và giao diện chia sẻ của từng app mạng xã hội chính thức. Chia sẻ đã thử bằng ACTION_SEND qua Android framework.
- OCR tìm bài từ ảnh và bắt nguồn từ trang đang phát chưa triển khai. Nháp URL và lựa chọn Aa chưa được lưu qua mọi lần xoay/đóng màn hình.

## Mở và build source

1. Cài Android Studio, SDK Platform 35, Build Tools 34.0.0 và **JDK 17**. Dự án dùng Gradle 8.9/AGP 8.7.3; JDK 25 đi kèm một số bản Studio không phù hợp với Gradle này. Chọn Gradle JDK 17 trong Settings → Build Tools → Gradle.
2. Giải nén source vào thư mục riêng, mở thư mục có `settings.gradle`. Studio sẽ tạo `local.properties` với SDK của máy; ZIP không chứa đường dẫn SDK cá nhân.
3. Build bằng Studio hoặc terminal (Windows):

   ```powershell
   $env:JAVA_HOME = 'C:\path\to\jdk-17'
   .\gradlew.bat assembleDebug testDebugUnitTest lintDebug
   ```

   macOS/Linux: `chmod +x gradlew && JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug testDebugUnitTest lintDebug`.

4. APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Dependencies lấy trực tiếp từ Google/Maven Central; lần build đầu cần mạng. App tải video không dùng dịch vụ trung gian.
5. Assets `engine.zip` đã chứa source Python của engine và yt-dlp được dùng khi kiểm thử; không cần Python trên điện thoại. Sửa Java/UI thì build lại. Khi sửa `app.js`, `index-base.html` hoặc engine Python, chạy `python tools/sync_assets.py` trước build để đồng bộ assets. Script giữ nguyên dependency đã đóng gói, không tự nâng phiên bản yt-dlp.

Thư viện native: youtubedl-android library/ffmpeg 0.18.1; yt-dlp 2026.10.26; yt-dlp-ejs 0.8.0. Xem `THIRD-PARTY-NOTICES.md` và `COPYING`. App source GPL-3.0; các thành phần có giấy phép riêng giữ theo upstream.

## Chạy kiểm thử

```powershell
python -m pip install pytest
python -m pytest tests -q
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Với máy giả lập/thiết bị ADB, cài APK app và APK test. Bộ `LocalFlowsTest` cần ít nhất một video đã tải thành công có sẵn trong dữ liệu app (dùng test live bên dưới trước); nó kiểm tra phát, lưu thư viện, hủy/thử lại và lỗi nhập. `UiScreensTest` chụp giao diện và kiểm tra kích thước điều khiển/tràn ngang; không thay cho kiểm thử TalkBack.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class dev.localreel.android.LiveDownloadTest -e url https://youtu.be/jNQXAC9IVRw dev.localreel.android.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class dev.localreel.android.LocalFlowsTest,dev.localreel.android.UiScreensTest dev.localreel.android.test/androidx.test.runner.AndroidJUnitRunner
```

Live test không chạy download khi không truyền URL. Nguồn có thể thay đổi, bị chặn hoặc xóa, nên các URL trong báo cáo không phải cam kết luôn xanh. Không đóng gói video đã tải từ mạng xã hội vào source ZIP. Báo cáo ghi riêng ca tải thành công và ca nguồn từ chối.
