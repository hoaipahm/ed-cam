# Hiệu năng và giới hạn

## 1.3.0 (2026-11-28)

Current settings measurements and the exact re-run harness are in [VERIFICATION-1.3.0.md](VERIFICATION-1.3.0.md). On the same API35 software-GPU emulator, one eight-round-trip settings scroll run measured median/p95 62.68/169.72ms and app-process PSS 86,847KB for 1.2.2, versus 28.18/50.22ms and 39,160KB for the final native settings layout. The layout also became more compact. Host-load variation is material; this is not physical-phone performance or a 60fps claim. No WebView is attached to 1.3 screens.

The remaining sections are historical measurements of earlier builds.

## 1.2.0 (2026-11-27)

- Cache ma trận màu hoàn chỉnh cho 180 style; khi đổi cường độ chỉ nội suy với ma trận gốc.
- Giới hạn texture hạt ở 512×512 thay vì tăng theo toàn bộ ảnh; dùng PRNG số nguyên xác định để tránh gọi Random nhiều lần cho mỗi pixel.
- Chỉ tạo component cho anchor nhìn thấy, dùng lại node và một WebView chính. Không gửi lại cấu hình nếu vị trí/giá trị không thay đổi; dialog tạm được giải phóng khi đóng.
- Những thay đổi này đã được kiểm tra chức năng; không suy ra phần trăm tăng tốc hoặc FPS trên điện thoại từ việc đọc mã. Phản hồi người dùng cho biết bản 1.1 chạy khá mượt trên máy của họ. Bản mới vẫn cần thử thực tế vì đã thêm nhiều điều khiển có hiệu ứng.

Các số đo bên dưới là lịch sử **1.0.2 so với 1.1.0**, không phải benchmark 1.2.0.

# Hiệu năng Ed-Cam 1.1.0

Chưa thể xác nhận app mượt trên điện thoại thật. Có cải thiện thao tác camera, đồng thời lớp Sakuga WebView làm tăng RAM và thời gian khung hình trên giả lập GPU phần mềm. Đây là đánh đổi thực tế của bản tích hợp nguyên hiệu ứng người dùng yêu cầu.

## Phép đo đã chạy

Android 15/API35 x86_64, WHPX, 720×1280, 320dpi, chữ100%, GPU SwiftShader; cùng máy và profile. So sánh APK1.0.2 với1.1.0. Harness `PerformanceInstrumentation.java` dùng thao tác native performClick/setText, ba lượt mở style/tìm/cuộn và sáu thao tác hẹn giờ/lưới. Thời gian dispatch không phải độ trễ cảm ứng tới hình ảnh. Chạm thật được kiểm tra riêng bằng ADB và CDP.

| Chỉ số | 1.0.2 | 1.1.0 cuối |
|---|---:|---:|
| Dispatch hẹn giờ/lưới, trung vị, 6 mẫu | 343 ms | 6 ms |
| Chờ camera sẵn sàng sau thao tác, trung vị | 571 ms | 146 ms |
| Đổi đối tượng camera khi hẹn giờ/lưới | 6 | 0 |
| Mở trang style, trung vị, 3 mẫu | 982 ms | 1423 ms |
| Dispatch tìm kiếm, trung vị, 12 mẫu | 44 ms | 76 ms |
| PSS tiến trình app sau khởi tạo | 33 MB | 82 MB |
| PSS sau điều hướng | 80 MB | 93 MB |
| Xử lý ảnh tổng hợp1920×2560 có grain, trung vị5 mẫu | 265 ms | 932 ms |
| UI FrameMetrics trung vị / p95 | 63 / 220 ms | 125 / 467 ms |
| Khung UI quá deadline / số nhận | 148 / 256 | 224 / 243 |

PSS trên là tiến trình app, chưa cộng riêng mọi tiến trình renderer WebView. Không phải tổng RAM toàn hệ thống. FrameMetrics không phải FPS cảm biến camera. Mẫu ít và giả lập chịu cạnh tranh CPU/GPU với WebView, Chrome và công cụ build; không coi kết quả là cam kết hoặc benchmark điện thoại. Lần đo trước dùng ScrollView; lần sau dùng ListView với cùng khoảng cuộn/đợi, nên chuỗi frame không hoàn toàn giống nhau.

Log: `qa/performance-before.log`, `qa/performance-final.log`. Hai log `performance-after.log` và `performance-after-idle.log` là thử nghiệm trung gian, không dùng để công bố kết quả cuối. Cách tạm dừng theo thời gian thực của thử nghiệm giữa đã được thay bằng đồng hồ Sakuga để không đóng băng hiệu ứng còn đang chạy trên giả lập chậm.

## Các tối ưu đã làm

- Hẹn giờ, lưới, flash và tỷ lệ cập nhật điều khiển tại chỗ; không dựng lại toàn màn camera chỉ để đổi nhãn.
- Sao chép JPEG từ ImageReader trên HandlerThread; xử lý/lưu ảnh trên worker, kiểm tra generation trước khi cập nhật UI.
- ListView tái sử dụng hàng style; tìm kiếm có debounce90ms, cache chuỗi bỏ dấu.
- Cache thumbnail album8MB; bỏ công việc thumbnail của trang đã rời.
- Dùng lại một WebView; tạm dừng khi rời màn/vào nền. Chỉ tạm nghỉ sau1,8 giây đồng hồ mô phỏng gốc, đánh thức bằng chạm/phím/trang mới. Không sửa tabs.js.

## Cần xác minh tiếp trên máy thật

Đo preview FPS, dropped frames khi chuyển tab/lưu ảnh liên tiếp, cold start, RAM cả renderer và nhiệt sau chụp nhiều phút. Kiểm tra camera trước/sau, flash, lấy nét và GPS trên thiết bị mục tiêu. Bản hiện tại đã qua kiểm tra chức năng nhưng **không đạt cơ sở để tuyên bố 60fps hoặc mượt trên mọi máy**.
