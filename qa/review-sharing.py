"""Run on EdCamReview130 after installing and granting camera permission."""

import importlib.util
import re
import time
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "device", Path(__file__).with_name("review-device.py")
)
d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(d)


def point(label):
    for node in d.ui().iter("node"):
        if label in (node.get("text"), node.get("content-desc")):
            x, y, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
            return (x + r) // 2, (y + b) // 2
    raise AssertionError(label)


def main():
    d.adb("shell", "am", "force-stop", "com.edcam")
    d.adb("shell", "am", "start", "-n", "com.edcam/.MainActivity")
    d.shot("camera-tabs-140")
    x, y = point("Chỉnh zoom liên tục")
    assert abs(x - point("Chụp ảnh ngay")[0]) <= 2
    d.adb("shell", "input", "swipe", x + 80, y, x - 80, y, 240)
    time.sleep(0.7)
    center = next(
        n for n in d.ui().iter("node") if n.get("content-desc") == "Chỉnh zoom liên tục"
    )
    assert center.get("text") != "1×", center.attrib
    d.tap("Chụp ảnh ngay")
    time.sleep(2)
    d.tap("Album")
    d.tap("Ảnh ", prefix=True)
    d.tap("Chia sẻ")
    d.tap("Gửi ảnh qua ứng dụng")
    time.sleep(1)
    tree = d.ui()
    assert any(
        "com.android.intentresolver" in n.get("package", "")
        or "android:id/chooser" in n.get("resource-id", "")
        for n in tree.iter("node")
    )
    d.shot("sharesheet-140")
    d.adb("shell", "input", "keyevent", 4)
    time.sleep(0.6)
    d.tap("Một ảnh, ba sắc")
    time.sleep(1)
    d.shot("trio-140")
    d.tap("1/3", prefix=True)
    assert any(n.get("text", "").startswith("2/3") for n in d.ui().iter("node"))
    d.tap("Lưu bản màu")
    time.sleep(1.4)
    d.tap("Chụp tiếp cùng khung")
    d.tap("Ảnh căn khung")
    d.shot("guide-140")
    d.tap("Bỏ ảnh căn")
    d.tap("Cài đặt")
    d.tap("Thông tin camera")
    d.shot("lens-report-140")
    print(
        "PASS centered zoom, swipe, photo capture, system Sharesheet, trio, saved variant, guide, camera capability report"
    )


if __name__ == "__main__":
    main()
