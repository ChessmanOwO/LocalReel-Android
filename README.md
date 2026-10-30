# LocalReel Android

## Cài APK

1. [Tải LocalReel-Android.apk](https://github.com/ChessmanOwO/LocalReel-Android/releases/download/android-v0.3-direct/LocalReel-Android.apk).
2. Mở APK trên điện thoại Android 8 trở lên, CPU ARM64. Cho phép ứng dụng Files/trình duyệt cài từ nguồn này nếu Android hỏi.
3. Mở LocalReel, bấm **Dán link và tải** để tự tải ngay (hoặc dán bằng bàn phím), hoặc chọn **Chia sẻ → LocalReel** từ app khác.
4. Theo dõi ở **Tiến trình**. Tải xong, mở **Đã lưu** để xem và lưu video vào Thư viện hoặc Files.

APK debug ký phát triển để cài cá nhân; đã thử máy giả lập Android 11, chưa thử điện thoại ARM64 thật.

## Chạy từ source

1. Cài Android Studio, **JDK 17**, SDK Platform 35 và Build Tools 34.0.0.
2. Tải hoặc clone repo này, mở bằng Android Studio và chọn Gradle JDK 17.
3. Chọn điện thoại hoặc máy giả lập rồi bấm **Run**. Hoặc build APK bằng terminal:

   ```sh
   # macOS/Linux
   chmod +x gradlew
   ./gradlew assembleDebug
   ```

   ```powershell
   # Windows
   .\gradlew.bat assembleDebug
   ```

APK build xong nằm ở `app/build/outputs/apk/debug/app-debug.apk`.
