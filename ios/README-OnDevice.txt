LOCALREEL iOS 0.2.2 — DÁN LINK → TẢI → PHOTOS
Bản sửa 29/10/2026 dựa trên source LocalReel-OnDevice-Xcode.zip anh gửi.
Không đọc hay đóng gói IPA đã ký.

CÁCH DÙNG
1. Mở LocalReel, dán liên kết rồi bấm Tải video trên iPhone.
2. Cấp quyền thêm ảnh/video vào Photos khi iOS hỏi. App kiểm tra quyền trước khi tải.
3. Nếu bật Chọn chất lượng, chọn mức muốn tải. Không cần mở hay phát trang nguồn.
4. App tự tải, ghép MP4 và lưu vào Photos.
5. Khi Photos xác nhận xong, app báo “Lưu thành công”. Mở Photos để xem video.

ĐÃ BỎ
- Nút Xem và trình phát trong app.
- Nút Photos thủ công, Tệp/Chia sẻ và trình chọn file xuất.
- Toggle tự lưu: nay lưu Photos luôn là bước bắt buộc.
- Nút mở trang nguồn và yêu cầu người dùng phát video thủ công.
- Chia sẻ thư mục dữ liệu app qua Files/iTunes.

XỬ LÝ LỖI
- Chưa cấp quyền Photos: vào Cài đặt → LocalReel → Ảnh, cho phép thêm ảnh/video,
  sau đó Thử lại. Không báo thành công nếu Photos chưa lưu.
- Tải xong nhưng Photos báo lỗi: giữ MP4 riêng trong app, Thử lại sẽ lưu file đã tải
  thay vì tải lại từ nguồn. Sau khi Photos lưu thành công thì dọn file trung gian.
- Tác vụ hoàn tất từ bản cũ chưa có cờ xác nhận Photos được chuyển về tạm dừng;
  bấm Tiếp tục để lưu bản trong app. Nếu anh đã lưu thủ công bằng bản cũ thì việc
  lưu lại có thể tạo một bản nữa; app không xin quyền đọc toàn bộ Photos để dò trùng.
- Trong lúc Photos đang ghi, không cho hủy/xóa tác vụ để tránh báo hủy trong khi
  Photos thực tế đã nhận video. Bảng Share Extension giữ thông báo thành công
  đến khi anh bấm Xong.
- Nếu iOS đóng app đúng sau khi Photos đã ghi nhưng trước khi hàng đợi được lưu,
  Thử lại có thể tạo bản sao. Không cam kết exactly-once qua crash/force-stop.

GIỚI HẠN
- App hoạt động trực tiếp trên iPhone, không cần Mac/backend khi sử dụng.
- URLSession/YouTubeKit đọc nguồn trực tiếp; trang xã hội chưa có metadata thì thử
  WebKit nội bộ tối đa 20 giây. Không click hay gọi phát video. Không nhập cookie,
  không vượt DRM/đăng nhập/challenge. Không hứa mọi link đều tải được.
- Giữ app/bảng chia sẻ mở. iOS có thể đình chỉ app khi chạy nền hoặc đóng app.
- Share Extension tải trong sandbox riêng; khi còn mở có thể Thử lại lưu Photos.
  Bản này không giả định extension chạy nền lâu; video dài nên tải trong app chính.

BUILD VÀ CÀI LẠI
1. Sao chép Config/Local.xcconfig.example thành Config/Local.xcconfig, điền Team
   và Bundle ID cũ để cập nhật app đã cài. File local này bị Git bỏ qua. Mở
   LocalReel.xcodeproj trên Mac với Xcode, chọn iPhone rồi Run.
2. Không mở LocalReel 2.xcodeproj cũ: project đó trong đầu vào tham chiếu file
   không có trong bản on-device, nên gói này chỉ bàn giao LocalReel.xcodeproj.
3. Có thể dùng Build-iPhone.command 'Tên iPhone' hoặc ./verify-mac.sh để build
   simulator không ký. Cài Personal Team miễn phí thường phải ký lại sau 7 ngày.
4. Thư viện YouTubeKit local nằm trong Vendor; giữ LICENSE của upstream.

KIỂM CHỨNG TRONG PHIÊN NÀY
- Node Tests/check-capture.cjs: 13 assertion đạt cho JS đọc metadata và nối luồng.
- Node Tests/check-photos-flow.cjs: 12 kiểm tra source đạt cho bỏ UI/thứ tự lưu.
- Thêm 2 XCTest về trạng thái lưu/khả năng đọc hàng đợi cũ; cùng 4 XCTest ở bản sửa
  trực tiếp trước. Đã viết, chưa chạy XCTest vì Windows không có Apple frameworks.
- Chưa compile Xcode, chưa thử lưu Photos thật hoặc tải thật bằng bản sửa iOS này.
  Kết quả Android/bản iOS cũ không được coi là bằng chứng bản sửa này chạy thực tế.
- Cần thử trên iPhone: cấp/từ chối quyền Photos; tải link công khai; lưu thành công
  rồi xem trong Photos; hết bộ nhớ/lưu lỗi rồi Thử lại; hủy khi tìm nguồn; chia sẻ URL.
  Chỉ đạt khi Photos có video thật với hình và âm thanh, không chỉ khi UI báo xong.
