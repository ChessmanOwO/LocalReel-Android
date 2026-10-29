# LocalReel

Tool cá nhân tải video từ liên kết bài viết, xử lý trên thiết bị. Source iOS và Android là hai ứng dụng riêng, không dùng dịch vụ tải video trung gian.

## Android — tải APK

[Tải LocalReel-Android.apk trong Releases](https://github.com/ChessmanOwO/LocalReel/releases/tag/android-v0.2-ondevice).

Android 8+; ARM64 cho điện thoại và x86_64 cho giả lập. Bản debug ký phát triển, cài cá nhân ngoài Play Store. File APK là bản tải thực, không phải demo giao diện. [Hướng dẫn Android](android/README.md) · [Báo cáo kiểm thử](android/verification/ANDROID-RESULTS.md).

Đã tải thật trên giả lập Android 11 với 5 ca: YouTube video, YouTube Shorts, Facebook video, Instagram Reel, TikTok. Ba ca thất bại: Douyin cần đăng nhập, Xiaohongshu chưa trích xuất được, link TikTok rút gọn cũ không hợp lệ. Chưa kiểm thử điện thoại ARM64 thật. Không cam kết mọi video trên các nền tảng đều tải được.

## iOS — tự lưu Photos

Source ở `ios/`. Dán link → Tải → app tự tìm nguồn → lưu Photos → báo thành công sau khi Photos xác nhận. Không có màn hình xem hoặc xuất/chia sẻ video thủ công. Xem [hướng dẫn iOS](ios/README-OnDevice.txt).

1. Trên Mac, sao chép `ios/Config/Local.xcconfig.example` thành `ios/Config/Local.xcconfig`.
2. Điền Team ID và Bundle ID của riêng bạn vào file local này; nó đã được Git bỏ qua. Nếu cập nhật app đã cài, dùng đúng Team và Bundle ID cũ.
3. Mở `ios/LocalReel.xcodeproj`, chọn iPhone và Run. Không cần Mac/backend khi sử dụng app.

**Bản sửa iOS chưa được compile Xcode hoặc kiểm thử tải/lưu Photos thật trong phiên này.** Các kiểm tra JavaScript/source không chứng minh app hoạt động trên iPhone. Repo công khai dùng cấu hình mẫu; không chứa signing key, chứng chỉ phát triển, token hay thiết lập Team/Bundle ID cá nhân.

## Cập nhật bằng Git

```sh
git clone https://github.com/ChessmanOwO/LocalReel.git
cd LocalReel
git pull --ff-only
# Sửa source, kiểm tra và build.
git status
git diff
git add <cac-file-da-sua>
git commit -m "fix: mo ta thay doi"
git push
```

Repo `LocalReel` này là bản công khai với lịch sử sạch. Repo iOS riêng tư ban đầu được giữ riêng để không công khai lịch sử có thông tin thiết lập cá nhân. Từ nay cập nhật source công khai tại repo này. APK được đăng dưới Releases thay vì commit binary lớn vào Git.

## Giới hạn và giấy phép

Không vượt DRM, paywall hoặc quyền truy cập. Nội dung riêng tư, đã xóa, bị chặn hoặc nguồn đổi cách phát có thể không tải được. Không cam kết xóa watermark hay MP4 là file gốc người đăng tải.

- Source iOS của LocalReel: [MIT](ios/LICENSE). YouTubeKit và tài nguyên đi kèm giữ giấy phép upstream.
- Source/app Android: [GPL-3.0](android/COPYING); [thành phần và nguồn dependency](android/THIRD-PARTY-NOTICES.md). Source tương ứng của APK được cung cấp trong cùng release.
