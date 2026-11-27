"""Native screen and touch checks on an isolated emulator (standard library only).

Set ANDROID_HOME, install the debug APK, then run python qa/review-device.py.
Screenshots and UI dumps go to gitignored qa/video-review/.
"""
import os
import sys
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'qa' / 'video-review'
ADB = Path(os.environ['ANDROID_HOME']) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')
SERIAL = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
if not SERIAL.startswith('emulator-'):
    raise SystemExit('Use an isolated QA emulator; this script changes themes and font scale.')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.check_output([str(ADB), '-s', SERIAL, *map(str, args)], timeout=40)

def ui():
    for _ in range(3):
        try:
            adb('shell', 'rm', '-f', '/data/local/tmp/edcam-review.xml')
            output = adb('shell', 'uiautomator', 'dump', '/data/local/tmp/edcam-review.xml')
            if b'ERROR' in output:
                raise ET.ParseError('UI is still changing')
            return ET.fromstring(adb('shell', 'cat', '/data/local/tmp/edcam-review.xml'))
        except (ET.ParseError, subprocess.CalledProcessError):
            time.sleep(.5)
    raise AssertionError('Cannot read native UI')

def tap(label, prefix=False):
    for node in ui().iter('node'):
        values = [node.get('text', ''), node.get('content-desc', '')]
        if any(value.casefold().startswith(label.casefold()) if prefix else value.casefold() == label.casefold() for value in values):
            import re
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            assert x2 > x1 and y2 > y1, label
            adb('shell', 'input', 'tap', (x1+x2)//2, (y1+y2)//2)
            time.sleep(.7)
            return
    raise AssertionError('Missing native control: ' + label)

def shot(name):
    tree = ui()
    if name.startswith('camera') or name in ['video-ready', 'video-recording']:
        until = time.monotonic() + 20
        while not any(node.get('content-desc','').startswith('Khung ngắm.') for node in tree.iter('node')):
            assert time.monotonic() < until, 'Camera UI not ready: ' + name
            time.sleep(.5)
            tree = ui()
    time.sleep(.5)
    adb('shell', 'screencap', '-p', '/data/local/tmp/edcam-review.png')
    adb('pull', '/data/local/tmp/edcam-review.png', OUT / (name+'.png'))
    ET.ElementTree(tree).write(OUT / (name+'.xml'), encoding='utf-8')
    assert not any('WebView' in node.get('class','') for node in tree.iter('node')), name
    print('Captured native screen:', name, flush=True)

def rate_photo():
    import re
    files=adb('shell','run-as','com.edcam','ls','-t','files/photos').decode().splitlines()
    photo_id=next(name[:-5] for name in files if name.endswith('.json'))
    node = next(node for node in ui().iter('node') if node.get('class') == 'android.widget.RatingBar')
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds')))
    assert x2>x1 and y2>y1, 'Visible native rating target'
    adb('shell','input','tap',int(x1+(x2-x1)*.7),(y1+y2)//2)
    until=time.monotonic()+8
    while time.monotonic()<until:
        prefs=ET.fromstring(adb('shell','run-as','com.edcam','cat','shared_prefs/edcam.xml'))
        if any(n.get('name')=='rating-'+photo_id and n.get('value')=='4' for n in prefs):
            print('PASS: native four-star rating persisted',flush=True)
            return
        time.sleep(.5)
    raise AssertionError('Four-star rating was not saved')

def main():
    original_scale = adb('shell', 'settings', 'get', 'system', 'font_scale').decode().strip()
    try:
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        adb('shell', 'am', 'force-stop', 'com.edcam')
        adb('shell', 'am', 'start', '-n', 'com.edcam/.MainActivity')
        time.sleep(2)
        shot('camera-100')
        tap('Chụp ảnh ngay')
        time.sleep(2)
        tap('Album')
        tap('Ảnh ', prefix=True)
        rate_photo()
        shot('photo-detail-100')
        adb('shell', 'input', 'swipe', 360, 1060, 360, 390, 400)
        shot('photo-detail-actions-100')
        tap('Chỉnh lại từ ảnh gốc')
        shot('photo-editor-100')
        tap('Hủy')
        tap('Ảnh ', prefix=True)
        adb('shell', 'input', 'swipe', 360, 1060, 360, 390, 400)
        tap('Xóa khỏi album Ed-Cam')
        tap('Xóa ảnh')
        tap('Chụp ảnh')
        if '--photos-only' in sys.argv:
            print('PASS: native photo capture, rating, edit and delete.',flush=True)
            return
        tap('Chỉnh zoom liên tục')
        shot('zoom-exposure')
        tap('Xong')
        tap('Cường độ style')
        shot('color-intensity')
        tap('Xong')
        tap('Video')
        shot('video-ready')
        tap('Bắt đầu quay video')
        time.sleep(3)
        shot('video-recording')
        tap('Dừng quay video')
        time.sleep(3)
        tap('Album')
        tap('Xem video ', prefix=True)
        time.sleep(2)
        shot('video-playback')
        tap('Xóa video')
        tap('Xóa video')
        tap('Chụp ảnh')
        tap('Ảnh')
        tap('Bộ màu')
        shot('styles-100')
        tap('Album')
        shot('album-100')
        tap('Cài đặt')
        shot('settings-100')
        for name in ['Đêm tối', 'Hiện đại', 'Hồng phấn', 'Xanh sage', 'Film kem']:
            tap('Giao diện · ', prefix=True)
            tap(name)
            shot('theme-' + str(['Đêm tối', 'Hiện đại', 'Hồng phấn', 'Xanh sage', 'Film kem'].index(name)))
        for scale in ['1.3', '1.5']:
            adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
            adb('shell', 'am', 'force-stop', 'com.edcam')
            adb('shell', 'am', 'start', '-n', 'com.edcam/.MainActivity')
            time.sleep(1.5)
            shot('camera-font-' + scale)
            tap('Cài đặt')
            shot('settings-font-' + scale)
            adb('shell', 'input', 'swipe', 360, 1060, 360, 390, 400)
            shot('settings-scrolled-font-' + scale)
        print('PASS: native navigation, five themes and 100%/130%/150% font captures.', flush=True)
    finally:
        adb('shell', 'settings', 'put', 'system', 'font_scale', original_scale if original_scale != 'null' else '1.0')


if __name__ == "__main__":
    main()
