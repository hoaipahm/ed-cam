# Ed-Cam 1.3.0

## Cài đặt

Mở `Ed-Cam-1.3.0.apk` trên điện thoại Android 10 trở lên. Bản này dùng cùng chứng chỉ debug với 1.2.2 trong repo, cho phép cài đè khi bản trên máy có cùng chữ ký. Album riêng được giữ khi cập nhật; xuất ảnh/video quan trọng trước khi gỡ app hoặc xóa dữ liệu.

## Chụp ảnh

- Chọn **Ảnh**, chạm khung ngắm để lấy nét và bấm nút tròn trắng. Có thể dùng phím âm lượng để chụp.
- Chụm hai ngón để zoom, chạm một mức zoom bên dưới khung ngắm, hoặc chạm số zoom phía phải để mở thanh zoom và bù sáng.
- Các mức zoom phụ thuộc camera. Nếu Android không cho ứng dụng truy cập camera siêu rộng hoặc mức 20×, app không thêm mức giả.
- **Bộ màu** mở thư viện 180 preset. Chạm tên preset/cường độ trên khung ngắm để chỉnh cường độ và so sánh màu gốc.
- Thanh trên cùng có flash, hẹn giờ 3/10 giây, tỷ lệ ảnh và cài đặt. Flash cần camera có đèn thật.

## Quay video

- Chọn **Video**. Bật/tắt micro trên thanh công cụ; quyền micro chỉ được xin khi bắt đầu quay có âm thanh.
- Bấm nút đỏ để quay, nút vuông để dừng. Có thể **Tạm dừng** rồi **Tiếp tục** trong cùng video.
- Video dùng màu gốc, MP4/H.264, tối đa Full HD 30 fps theo camera. Preset, hạt film và dấu ngày của ảnh không áp vào video.
- Khi app vào nền hoặc camera bị đóng, app cố hoàn tất video đang quay. Lượt quay quá ngắn có thể bị từ chối để tránh lưu một file hỏng.

## Album và cài đặt

Ảnh và video nằm trong album riêng. Chạm ảnh để đánh dấu 0–5 sao, chỉnh lại từ bản gốc, xuất hoặc xóa. Chạm video để phát, tạm dừng, tua, xuất hoặc xóa.

Ảnh xuất nằm trong `Pictures/Ed-Cam`, video xuất nằm trong `Movies/Ed-Cam`. Xóa bản trong Ed-Cam không xóa bản đã xuất.

Trong cài đặt có 5 giao diện, lưới bố cục, âm thanh video, giữ bản gốc, dấu ngày giờ và vị trí tùy chọn. Ảnh chỉnh vẫn giới hạn cạnh dài 2560px; bật giữ bản gốc để lưu thêm dữ liệu gốc.

Bản này đã được build, kiểm tra tích hợp và xem giao diện trên giả lập. Flash, các ống kính phụ, chất lượng hình/âm thanh và độ mượt trên từng điện thoại vẫn cần thử thực tế. Chi tiết trong `docs/VERIFICATION-1.3.0.md`.
