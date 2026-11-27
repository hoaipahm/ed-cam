# Ed-Cam

Ứng dụng camera cá nhân Android, giao diện tiếng Việt. Android 10 trở lên.

Bản mới: `delivery/Ed-Cam-1.1.0.apk`. Thanh tab Sakuga nguyên bản, màu kem–tím–hồng; màn chụp lớn và xử lý ảnh vẫn native. Cách nối sự kiện nằm trong [docs/SAKUGA.md](docs/SAKUGA.md). Số đo và giới hạn hiệu năng nằm trong [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Có gì trong bản này

- 60 preset màu thuộc 10 nhóm; tìm tên không dấu, lưu style yêu thích, chỉnh cường độ.
- 5 giao diện lưu theo lựa chọn: Film kem, Đêm tối, Hiện đại, Hồng phấn, Xanh sage.
- Camera trước/sau, màu xem trực tiếp, lấy nét bằng chạm, flash khi phần cứng hỗ trợ, hẹn giờ 3/10 giây, lưới, tỷ lệ 3:4 / 1:1 / 9:16.
- Lưu album nội bộ, giữ nguyên bytes ảnh gốc nếu bật tùy chọn, nhập ảnh bằng trình chọn tệp của Android.
- Chỉnh lại từ ảnh gốc, xuất bản chỉnh hoặc bản gốc vào `Pictures/Ed-Cam`.
- In ngày giờ và tên địa điểm tự nhập; tọa độ/sai số GPS chỉ lưu khi bật quyền vị trí và có tín hiệu mới.
- Lọc album theo ngày hoặc ảnh có tọa độ. Xóa có xác nhận.
- Không tài khoản, không quảng cáo, không backend, không quyền Internet.

## Cài APK

Chép APK trong thư mục `delivery` sang điện thoại. Mở tệp APK và cho phép trình quản lý tệp cài ứng dụng khi Android yêu cầu. Mở Ed-Cam, chạm khung ngắm để cấp quyền camera. Vị trí không bắt buộc.

Ảnh trong album Ed-Cam nằm trong dữ liệu ứng dụng. Trước khi gỡ app hoặc xóa dữ liệu, xuất các ảnh cần giữ vào thư viện máy. Bản gốc nhập từ ngoài có thể còn thông tin EXIF, bao gồm vị trí.

## Build từ mã nguồn

Yêu cầu JDK 17, Android SDK platform 35 và build-tools 35.0.0. Mở thư mục bằng Android Studio hoặc đặt `sdk.dir` trong `local.properties` (không đưa đường dẫn máy cá nhân vào Git).

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\qa\test-presets.ps1
```

APK mặc định ở `app/build/outputs/apk/debug/app-debug.apk`. Bản debug có chữ ký dành cho thử nghiệm; phát hành lâu dài cần giữ khóa ký riêng, không thay khóa giữa các bản cập nhật.

Để chạy kiểm tra Android trên thiết bị thử nghiệm:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.edcam.test/com.edcam.PixelInstrumentation
```

Kiểm tra Android tạo một ảnh tổng hợp trong dữ liệu ứng dụng, kiểm tra pixel/lưu bản gốc/xuất/xóa rồi xóa dữ liệu thử đó. Chỉ chạy trên thiết bị thử nghiệm.

## Các giới hạn cần biết

- Màu là xem trực tiếp; hạt film và tối góc được thêm khi lưu. Đây là preset sáng tạo, không phải tuyên bố tái tạo chính xác một dòng máy ảnh hay loại film thương mại.
- Ảnh xử lý giới hạn cạnh dài tối đa 2560 px để kiểm soát bộ nhớ. Ảnh nhập giới hạn 40 MB. Không tự động nâng chất lượng camera phần cứng.
- Ảnh nhập dùng thời gian chỉnh, được ghi nhãn riêng; không bịa thời gian/địa điểm chụp ban đầu.
- Địa điểm GPS hiển thị tọa độ, không tự tra tên địa danh. Tên nơi chụp do người dùng nhập. Xem bản đồ cần ứng dụng bản đồ trên máy.
- Chưa có Live Photo, video, xóa vật thể AI, làm đẹp khuôn mặt, photobooth, bản đồ nhúng hoặc đồng bộ cloud.
- Hiệu năng, lấy nét, flash, màu camera, xoay ảnh và độ chính xác GPS cần kiểm tra trên điện thoại thật; giả lập không thay thế được bước này.

## Cấu trúc

`MainActivity`: màn hình và điều hướng. `CameraController`: Camera2 và vòng đời. `Presets`: công thức màu thuần Java. `PhotoProcessor`: giải mã, xoay EXIF, crop, màu, hạt, tối góc, dấu ảnh. `PhotoStore`: lưu ảnh, metadata và MediaStore. `Palette` / `IconView`: giao diện và biểu tượng.

Tham khảo kỹ thuật chính thức: [Camera2](https://developer.android.com/media/camera/camera2), [TextureView](https://developer.android.com/reference/android/view/TextureView), [MediaStore](https://developer.android.com/training/data-storage/shared/media), [Android app signing](https://developer.android.com/studio/publish/app-signing).
