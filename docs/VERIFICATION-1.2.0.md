# Ed-Cam 1.2.0 — kiểm chứng ngày 27/11/2026

## Đã chạy

- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: thành công. Lint: **0 lỗi, 13 cảnh báo**. Log `qa/v120-build.log`, báo cáo `app/build/reports/lint-results-debug.html`.
- `qa/test-presets.ps1`: **4.946 kiểm tra đạt**, 180 tên/ma trận khác nhau, cường độ 0 giữ màu gốc, clamp/NaN, alpha, số lượng từng nhóm. 120 style mới được kiểm tra độ khác biệt trên bốn mẫu RGB; đây là tiêu chí trên mẫu tổng hợp, không phải đánh giá mọi ảnh ngoài thực tế.
- `PixelInstrumentation`: **382 kiểm tra đạt ở cả font 100% và 130%** trên giả lập Android 15/API35, 720×1280, 320dpi. Có render thật cả 180 preset, pixel cyanotype, crop/EXIF, bản gốc, metadata, xuất/xóa và hình học camera. Log `qa/v120-instrumentation.log`, `qa/v120-instrumentation-font130.log`.
- `qa/v120-controls-test.cjs`: CDP touch, keyboard slider, input tiếng Việt, form, bridge và reduced-motion ở **390/1280px**; không lỗi JavaScript. `qa/sakuga-browser-test.cjs`: hồi quy tab mouse/touch/drag/keyboard/form và reduced-motion đạt.
- `qa/v120-device-test.cjs`: ADB chạm thực trong APK → mở Style → tìm Cyanotype → chọn hàng native → mở MÀU → kéo slider → mở/cuộn Cài đặt. Preferences xác nhận `style=62`, `strength=0.5`; radio có đủ 5 giao diện, công tắc xuất hiện sau cuộn. Kết quả `qa/v120-device-results.json`.
- `qa/v120-capture-test.cjs`: chạm nút flash trên giả lập không có LED (nhánh không hỗ trợ), chạm chụp → có JPEG/original/JSON mới, source camera, style 62, strength 0.5, 1392×1856. Mở chi tiết → đánh dấu 4 sao được lưu đúng ID → mở xác nhận xóa có hai nút Sakuga → xóa đúng ảnh vừa tạo, bảo toàn danh sách ảnh trước kiểm tra. Kết quả `qa/v120-capture-results.json`.
- Đã xem ảnh thật từ giả lập: camera, style search, cyanotype, intensity, settings/scrolled, photo-rating, delete-confirm và camera-font130 trong `qa/v120-*.png`. Ảnh pixel là cảnh camera giả lập; không đại diện chất ảnh điện thoại.
- Đã bước sáu mốc phản ứng với `look.mjs` và xem frame sheet cho primary button, sprite toggle và slider (`qa/v120-frames-*.png`). Đây là kiểm tra từng khung, tách với input CDP thời gian thực. Hiệu ứng gốc có thể che nhãn ngắn ở hit frame; không sửa source hiệu ứng.
- `google-java-format --dry-run --set-exit-if-changed`, `node --check`, `git diff --check`: đạt.

## Thay đổi được kiểm chứng bằng mã và phép thử

180 màu / 22 nhóm; giữ nguyên 60 ID cũ. Ma trận màu tính sẵn và dùng chung cho camera/thumbnail/export. Hạt film được giới hạn texture 512×512. Chưa đo phần trăm tăng tốc trước/sau trên cùng thiết bị nên không công bố FPS hay phần trăm nhanh hơn.

Các component Sakuga giữ nguyên source, có URL/hash trong `SAKUGA-ASSETS.json`. Host sửa riêng để nối native state, clip vùng cuộn, chuyển vuốt đến scroller, giảm clock lúc nghỉ và giải phóng WebView dialog. Đã sửa vùng toast ẩn chặn tab và chuyển toast lên trên dock. Native status/date/file/permission UI của Android không bị giả lập bằng HTML.

## Chưa xác minh / giới hạn

- **Flash LED trên điện thoại thật chưa kiểm tra.** Đã bổ sung AE precapture, đợi trạng thái có giới hạn, chọn AE mode hỗ trợ, yêu cầu FLASH_MODE_SINGLE và khôi phục preview. Giả lập chỉ xác nhận nhánh không có flash và chụp bình thường, không chứng minh LED đã sáng. Cần cài APK lên thiết bị người dùng để xác nhận lỗi thực tế đã hết.
- Camera trước, flash từng hãng, lấy nét, GPS, nhiệt, pin, cold start và độ mượt của bản mới chưa đo trên máy thật. Phản hồi người dùng rằng 1.1 mượt không thay thế kiểm thử 1.2.
- TalkBack và Remove animations trên Android System WebView chưa kiểm tra trực tiếp. Reduced-motion nêu trên là Chrome emulation.
- APK debug dành cho cài thử cá nhân; chưa tạo khóa phát hành riêng hay đưa lên cửa hàng. Chưa push Git lên remote.
- Một số lần `uiautomator dump` trong harness trả `null root node` khi UI đang thay đổi; không coi các lượt lỗi là đạt. Lượt kiểm tra cuối xác nhận trạng thái bằng metadata/preferences và danh sách file. Các lỗi harness trung gian đã được tách khỏi kết quả đạt cuối.

## Tái chạy

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\qa\test-presets.ps1
# Thiết bị giả lập thử nghiệm đã kết nối, APK app và AndroidTest đã cài:
.\.tools\android-sdk\platform-tools\adb.exe shell am instrument -w com.edcam.test/com.edcam.PixelInstrumentation
# Playwright đã có trong .tools/browser; đặt CHROME tới Chromium cài trên máy:
node qa/v120-controls-test.cjs
node qa/sakuga-browser-test.cjs
node qa/v120-device-test.cjs
node qa/v120-capture-test.cjs
node qa/v120-frames.cjs
```

Các test thiết bị chỉ chạy trên giả lập thử nghiệm; test capture mặc định kiểm tra preset Cyanotype/cường độ 50% sau device-test. Không chạy tự động trên điện thoại chứa album cá nhân.

## APK bàn giao

- `delivery/Ed-Cam-1.2.0.apk`: 953.846 bytes; bản sao trùng SHA-256 với APK vừa build.
- SHA-256: `62C15F3A47C9E9F83A2EA79D46581709F6340C533CE18C644EC1998EEA171B50`.
- `apksigner verify --print-certs`: đạt, chứng chỉ Android Debug. Log `qa/v120-signature.log`.
