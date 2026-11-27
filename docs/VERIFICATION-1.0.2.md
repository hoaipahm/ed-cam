# Ed-Cam 1.0.2 — kiểm chứng

## Đã chạy

- Build `assembleDebug`, `assembleDebugAndroidTest` và `lintDebug`: thành công. Báo cáo cuối: `qa/immersive-camera-build.log`, `app/build/reports/lint-results-debug.html`. Lint có 0 lỗi và 10 cảnh báo; không tuyên bố sạch mọi cảnh báo.
- Bộ công thức màu: 1.574 kiểm tra thành công, gồm 60 công thức khác nhau, 10 nhóm, mức cường độ và giữ nguyên alpha/ảnh ở cường độ 0.
- Giả lập Android 15 / API 35, camera giả lập và WHPX: APK cuối chạy 21 kiểm tra tích hợp thành công trên cấu hình thường và nhỏ/cỡ chữ 130% (`qa/immersive-camera-phone-tests.log`, `qa/immersive-camera-compact-tests.log`). Hai kiểm tra mới yêu cầu khung ngắm 3:4 chiếm ít nhất 90% chiều rộng và 55% chiều cao cửa sổ trên các cấu hình đã thử.
- Các kiểm tra tích hợp gồm pixel, crop, giải mã JPEG, từ chối dữ liệu ảnh lỗi, xoay EXIF 90 độ, giữ tỷ lệ ảnh nhập, lưu metadata/bản gốc không đổi, giữ thông số crop/lật/cường độ, xuất MediaStore và xóa dữ liệu thử.
- Đã kiểm tra chữ ký APK bằng `apksigner verify`.

## Phạm vi xem giao diện

Ảnh camera cuối là `reference-camera-phone.png`, `reference-camera-compact.png`, `reference-camera-font130.png` và `reference-camera-album.png` trong `.impeccable/review/`. Máy giả lập dùng 720×1600 ở 280 dpi và 720×1280 ở 320 dpi, có cỡ chữ 130%. Ảnh `redesign-styles.png`, `redesign-settings-0..4.png`, `redesign-dark-dialog.png` vẫn mô tả các trang phụ không đổi. Ảnh camera `phone-*` và `redesign-phone/compact/font130` đã bị thay thế, không dùng để xác nhận bản cuối.

Hình camera có nhân vật pixel là cảnh do **camera giả lập Android** cung cấp, không phải ảnh mẫu cài trong APK hoặc chất lượng ảnh của điện thoại thật.

## Giới hạn

- Chưa thử trên điện thoại thật: chất ảnh, hiệu năng, flash, lấy nét, độ chính xác GPS và tương thích từng hãng cần kiểm tra riêng.
- Kiểm tra metadata thông số chỉnh lại không thay thế kiểm thử thủ công mọi tổ hợp sửa ảnh và cấp/từ chối quyền.
- APK dùng chữ ký debug phục vụ cài thử cá nhân. Phát hành lâu dài cần quản lý khóa ký riêng.
- Không có backend/cloud. Bản xuất ra thư viện máy tách khỏi album nội bộ.

## Lỗi giao diện đã sửa theo phản hồi

Nhãn trong các ô dọc trước đây có chiều rộng lấp đầy ô nhưng còn căn trái, trong khi icon căn giữa. Bản sửa căn giữa cả chữ và icon, dùng vùng điều hướng đều nhau; thu gọn header, bỏ các hàng điều khiển trùng lặp dưới khung ngắm, đưa chọn style/cường độ lên ảnh và giữ ba nút nhập/chụp/đổi camera cân xứng.

Theo ảnh tham khảo mới của người dùng, màn camera đã chuyển thành toàn màn hình nền đen, bỏ header và điều hướng app, công cụ ở trên, dock STYLE/ẢNH/MÀU và nút chụp trắng ở dưới. Khung ngắm giữ tỷ lệ ảnh và sử dụng toàn bộ vùng còn lại; phần camera nhỏ trước đó đã được thay thế.
