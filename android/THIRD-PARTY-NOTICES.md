# Thành phần mã nguồn mở

Source app Android này được phân phối theo GPL-3.0 tương thích thư viện youtubedl-android; xem COPYING. Không đóng gói mã hay IPA đã ký của người dùng.

- youtubedl-android library/ffmpeg **0.18.1**, GPL-3.0: https://github.com/yausername/youtubedl-android . Source artifacts và native build instructions: https://repo.maven.apache.org/maven2/io/github/junkfood02/youtubedl-android/ ; BUILD_PYTHON.md, BUILD_FFMPEG.md tại upstream.
- yt-dlp **2026.10.26** (Unlicense và các thành phần kèm giấy phép riêng): https://github.com/yt-dlp/yt-dlp/tree/2026.10.26 . Pure-Python source được đóng trong assets/engine.zip.
- yt-dlp-ejs **0.8.0** (Unlicense): https://github.com/yt-dlp/ejs . Đóng kèm source nhưng Android hiện tắt JS runtime; không tải remote components.
- FFmpeg cùng các codec native do thư viện 0.18.1 cung cấp; giấy phép tương ứng và hướng dẫn build nằm trong upstream. Native binaries gồm cả FFprobe, Python và QuickJS (QuickJS không được bật trong pipeline).
- AndroidX core **1.13.1**, Apache-2.0; Kotlin BOM **1.9.24**, Apache-2.0, các dependency được Gradle giải từ Maven Central/Google.

Đây là tool cá nhân; không có dịch vụ tải video bên thứ ba. Các URL mạng xã hội chỉ đi trực tiếp tới nguồn/CDN, còn các URL trên là nguồn dependency/build, không phải dịch vụ tải.
