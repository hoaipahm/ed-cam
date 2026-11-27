# Kiểm chứng Ed-Cam 1.1.0

## Đã xác minh

- Build APK, instrumentation APK và lint thành công: `qa/sakuga-final-build.log`. Lint:0 lỗi,14 cảnh báo; không gọi là sạch mọi cảnh báo.
- 1.574 kiểm tra công thức màu thành công: `qa/sakuga-presets.log`.
- APK cuối qua21 kiểm tra tích hợp trên giả lập API35,720×1280/320dpi, ở chữ100% và130%: `qa/sakuga-phone-tests.log`, `qa/sakuga-font130-tests.log`. Gồm pixel/crop/EXIF90, lưu nguyên bản, metadata, xuất/xóa dữ liệu thử, khung camera3:4 ít nhất90% rộng và55% cao cửa sổ.
- Chạm ADB sau khi để yên: camera→Style→Cài đặt→đổi Film kem→camera. Native panel thực sự đổi, không chỉ tab web. Hình `qa/sakuga-native-styles.png`, `qa/sakuga-native-cream.png`, `qa/sakuga-camera-font130.png`. Màu sage trong ảnh style là theme đang chọn lúc chụp, không phải màu mặc định.
- Chạm nút chụp native qua lớp trong suốt tạo bộ JPEG/original/JSON mới. Metadata `qa/sakuga-camera-capture.json`: source camera,1392×1856,tỷ lệ0.75. Đây là ảnh camera giả lập, không đại diện chất ảnh phần cứng thật.
- Component trình duyệt qua CDP mouse/touch/drag, bàn phím, form, quiet selected,390/1280px, reduced-motion: `qa/sakuga-browser-results.json`. Đã xem riêng frame sheet6mốc `qa/sakuga-frames.png` do look.mjs tạo.
- File tabs.js được giữ nguyên hash trong `SAKUGA.md`. Không dùng module, không tách core, không sửa hiệu ứng.

## Hiệu năng và giới hạn

Kết quả cuối trong [PERFORMANCE.md](PERFORMANCE.md): nút hẹn giờ/lưới không mở lại camera, dispatch nhanh hơn; nhưng WebView tăng RAM và giả lập vẫn giật, mở style/xử lý ảnh/khung UI có số đo chậm hơn bản cũ. Không tuyên bố mượt trên điện thoại thật.

Log Chromium của giả lập có cảnh báo seed và vượt tile memory ở một số lượt. Không thấy AndroidRuntime fatal hoặc lỗi JavaScript EdCamTabs trong lượt kiểm tra cuối; cảnh báo tài nguyên vẫn là giới hạn cần theo dõi. Bản app dùng chữ ký debug để cài thử cá nhân.

Chưa xác minh trên máy thật: FPS cảm biến, nhiệt, flash, lấy nét, GPS, TalkBack và reduced-motion của Android System WebView. Chưa tìm thấy mã Mộc cũ nên không tuyên bố sao chép đúng giao diện Mộc. Hướng mới theo Sakuga người dùng gửi.

Bằng chứng1.0.2 được giữ riêng trong `VERIFICATION-1.0.2.md`, không dùng ảnh hoặc kết quả cũ làm xác nhận phiên bản mới.
