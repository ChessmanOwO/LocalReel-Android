# Tải trực tiếp từ liên kết — 29/10/2026

Dán link → Tải video trên iPhone → tự đọc metadata → nếu chưa có nguồn, tự tải và đọc dữ liệu trang động bằng WebKit → chọn chất lượng (nếu bật) → tải/ghép MP4.

Bỏ nút Mở trang video, nút Mở trang nguồn khi lỗi và màn hình SourceBrowser. Không cần xem/phát trước. Giữ file SourceBrowser.swift chỉ chứa chú thích để LocalReel.xcodeproj giữ tham chiếu hợp lệ. App chính và Share Extension gọi chung resolveAutomatically. YouTube tiếp tục dùng YouTubeKit local, MP4 trực tiếp tiếp tục dùng đường tải trực tiếp.

WebKit tự động dùng WKWebsiteDataStore.nonPersistent, giới hạn thời gian 20 giây, chặn tự phát, không hiển thị màn hình web, không tự nhấn nút, chỉ điều hướng trang chính trong cùng nền tảng. Hủy tác vụ sẽ dừng WebKit và giải phóng tài nguyên. Nguồn cần đăng nhập/DRM/challenge có thể thất bại, được báo rõ.

Tham khảo API Apple: https://developer.apple.com/documentation/webkit/wkwebsitedatastore .

Đã kiểm tra 13 assertion JavaScript/Node và đường nối source. Đã viết thêm 4 XCTest, chưa chạy do Windows không có Xcode/Apple frameworks. Chưa kiểm chứng tải thật trên iPhone cho bản sửa. Không dùng kết quả Android để tuyên bố iOS chạy được.

Không có IPA hay key ký trong gói bàn giao. README-OnDevice.txt có cách build và các bước kiểm tra trên Mac/iPhone.

Gói ZIP chỉ chứa LocalReel.xcodeproj đang dùng. LocalReel 2.xcodeproj trong đầu vào còn tham chiếu API.swift/Vault.swift không có trong source on-device, nên không đưa bản project cũ đó vào gói sửa.


## Photos-only — cập nhật theo yêu cầu tiếp theo

Đã bỏ xem, nút Photos thủ công, Tệp/Chia sẻ, tự lưu tùy chọn; không còn trình phát hay export sheet. App/extension luôn lưu Photos. Complete/alert chỉ được đặt sau await Photos transaction thành công. Có savingPhotos và photosSaved để phân biệt MP4 đã tải với đã lưu Photos; không đưa kết quả cũ chưa xác nhận thành lưu thành công. Photos lỗi giữ MP4 cho thử lại, lưu thành công mới dọn MP4. Đã tắt file sharing trong Info.plist. Chưa build/chạy iPhone cho bản sửa.
