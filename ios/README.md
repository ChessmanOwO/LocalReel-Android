# LocalReel iOS

Tool cá nhân chạy trực tiếp trên iPhone: **dán liên kết → tải video → tự lưu vào Photos → báo lưu thành công**. Bản hiện tại 0.2.2. Không cần backend Mac khi sử dụng.

App tự đọc nguồn trực tiếp và thử metadata tải động bằng WebKit khi cần. Không yêu cầu mở trang nguồn hoặc phát video thủ công. Chỉ đánh dấu hoàn tất sau khi giao dịch lưu Photos thành công. Nếu lưu lỗi, giữ MP4 riêng trong app để thử lưu lại. Không có giao diện xem video, nút Photos thủ công hay xuất Tệp/Chia sẻ.

## Mở source

Sao chép `Config/Local.xcconfig.example` thành `Config/Local.xcconfig`, điền Team ID và Bundle ID cá nhân vào file local bị Git bỏ qua. Mở `LocalReel.xcodeproj` bằng Xcode trên Mac, chọn iPhone rồi Run. Dùng đúng Team/Bundle ID cũ nếu cập nhật app đã cài. Source YouTubeKit và giấy phép nằm trong `Vendor/YouTubeKit`.

Xem [hướng dẫn đầy đủ](README-OnDevice.txt), [thay đổi](CHANGES-Direct-Link.md) và [phạm vi kiểm chứng](VALIDATION.json).

## Kiểm chứng

```sh
node Tests/check-capture.cjs
node Tests/check-photos-flow.cjs
```

Đã đạt 13 assertion JavaScript và 12 kiểm tra source của luồng Photos trên Windows. Chưa compile Xcode, chạy XCTest hoặc thử tải/lưu Photos thật bằng bản sửa iOS này. Kết quả Android không chứng minh iOS chạy được.

Trên Mac có thể chạy `sh verify-mac.sh` để build simulator không ký và `swift test` để chạy core tests. Các test có mạng chỉ chạy khi truyền biến môi trường ghi trong source test. Build thành công không thay thế kiểm thử trên iPhone.

## Giới hạn

Nguồn riêng tư, cần đăng nhập, DRM, nguồn chặn truy cập hoặc đã thay đổi có thể không tải được. Không dùng dịch vụ tải video bên thứ ba, không hứa mọi URL hoạt động và không tuyên bố MP4 là file gốc người đăng. Giữ app/bảng chia sẻ mở trong lúc xử lý. Cần cấp quyền thêm video vào Photos; không báo thành công nếu lưu thất bại.

## Cập nhật bằng Git

Đây là repository gốc để tiếp tục các thay đổi, không tạo lại repository cho mỗi bản cập nhật.

```sh
git pull --ff-only
# Sửa source, kiểm tra và build trên Mac.
git add <cac-file-da-sua>
git commit -m "fix: mo ta thay doi"
git push
```

Không commit IPA, chứng chỉ/khóa ký, provisioning profile, dữ liệu Xcode cá nhân hoặc sản phẩm build. Project công khai chỉ có cấu hình mẫu; giữ thiết lập cá nhân trong `Config/Local.xcconfig`, không commit file này.
