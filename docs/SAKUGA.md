# Sakuga trong Ed-Cam 1.1.0

Nguồn người dùng chọn: https://sakuga-vfx.vercel.app/tabs/tabs.js . File được lưu nguyên vẹn tại `app/src/main/assets/tabs.js`, không tách core, không sửa hiệu ứng, không thêm dependency runtime. SHA-256: `0467AC7BB687D8A6F8834B6E5EA7BD36133C2E126FB7231C916DAAC2A5D2FEF5`.

`app/src/main/assets/sakuga.html` nạp bằng `<script src="tabs.js"></script>` và khai báo các `<option>` trong hai `<sakuga-tabs>`. `sakuga-host.css` chỉ chỉnh bố cục, kích thước và biến `--sk-*`. License MIT nằm cạnh file gốc.

## Ai nghe sự kiện?

`sakuga-host.js`: listener `control.addEventListener('change', ...)` đọc `event.detail.index/value/label`, đồng bộ giá trị form, rồi gọi `AndroidTabs.change(mode, value, index)`. Khi chạy riêng trong trình duyệt, cùng listener bật panel minh họa tương ứng.

`SakugaTabsLayer.java`: bridge kiểm tra bộ mode/value/index trước khi gọi listener. `MainActivity.sakugaChanged(...)` mở màn camera, bộ màu, album, cài đặt hoặc hộp cường độ màu native. `synchronizeSelection` → `configureTabs` đặt `el.selected` để đồng bộ yên lặng, không phát hiệu ứng hạ cánh mới.

## Tích hợp Android

Camera, chụp/lưu/xử lý ảnh vẫn là Android native. Một WebView trong suốt phủ cửa sổ chỉ nhận chạm trong vùng tab; vùng còn lại truyền đến nút chụp, camera và danh sách native. Canvas của component phủ toàn WebView nên hiệu ứng có thể bay ra ngoài tab. Asset nằm trong APK, không cần Internet; WebView chặn điều hướng và yêu cầu ngoài android_asset. Khi renderer lỗi, điều hướng native được hiện lại.

WebView được dùng lại giữa các trang. Sau 1,8 giây **theo đồng hồ mô phỏng Sakuga**, host tạm dừng timer WebView khi không còn giữ chạm. Dùng `SakugaFX.later` để hiệu ứng hoàn tất cả trên giả lập chậm; không cắt bằng thời gian thực. Chạm/phím/mở trang đánh thức lại. File hiệu ứng giữ nguyên. Hoạt động này không loại bỏ chi phí RAM của WebView.

## Đã kiểm tra

- Trình duyệt: CDP gửi nhấn chuột, chạm và kéo thật; Tab/Arrow/Home/End; FormData; đặt selected không phát change; hai chiều rộng 390/1280, không tràn ngang; reduced-motion đổi trạng thái và không tạo canvas hiệu ứng. Xem `qa/sakuga-browser-results.json`.
- `look.mjs` bước sáu mốc riêng biệt, xem `qa/sakuga-frames.png`; đây là kiểm tra từng khung, tách với luồng nhập CDP tốc độ thực. Hiệu ứng gốc có chồng lên nhãn ngắn lúc hạ cánh; không sửa hiệu ứng theo yêu cầu người dùng.
- APK: kiểm tra hình ảnh và thao tác ADB sau khi để yên, điều hướng sang trang native và nút chụp. Chi tiết kết quả cuối xem `VERIFICATION.md`.

Skill được cài bằng `npx skills add finktheartist/sakuga-vfx --skill sakuga-vfx --agent codex --global --yes --copy`, tại `C:/Users/THE HOAI/.agents/skills/sakuga-vfx`. Đã đọc và theo các phần Wire it in / Check it. Tham chiếu: https://sakuga-vfx.vercel.app/skills/sakuga-vfx/SKILL.md .

Chưa xác minh TalkBack trên máy thật và ánh xạ cài đặt giảm chuyển động Android sang media query của từng phiên bản Android System WebView. Kiểm tra reduced-motion nói trên là emulation trong Chrome, không đại diện cho mọi máy Android.


## 1.2.0: mở rộng toàn bộ điều khiển

Gallery tham chiếu: https://sakuga-vfx.vercel.app/gallery/ . Các script `button`, `toggle`, `slider`, `checkbox`, `radio`, `input`, `progress`, `rating`, `toast`, `tooltip` được tải nguyên file và dùng cùng `tabs.js` cũ qua script tag thường. Danh sách URL/hash: `SAKUGA-ASSETS.json`. Không sửa nguồn hiệu ứng hoặc tách core.

`SakugaTabsLayer.collect` tìm các anchor native đang thấy, gửi vị trí/giá trị và vùng clip đến `configureTabs` / `configureControls`. `AndroidTabs.control(epoch,id,value)` kiểm tra epoch và native View trước khi gọi listener thật. Ô nhập nghe trực tiếp input bên trong shadow root để tương thích Android WebView. Chạm ngoài hitbox đi đến camera/native UI; vuốt trên nút trong vùng cuộn chuyển đến scroller native. Khi WebView renderer lỗi, trả lại các điều khiển native.

Ứng dụng dùng một lớp chính; dialog dùng một lớp tạm và giải phóng khi đóng. Lớp chính tạm nghỉ khi dialog mở. Cơ chế nghỉ chờ đồng hồ hiệu ứng gốc; thông báo có thời gian chờ dài hơn để không đóng băng giữa chừng. Không khẳng định mọi điện thoại đều có cùng hiệu năng hoặc hỗ trợ TalkBack/giảm chuyển động giống Chrome.

Nút thao tác app và xác nhận app dùng Sakuga. Hộp xin quyền Android, trình chọn tệp và chọn ngày của hệ điều hành vẫn native.
