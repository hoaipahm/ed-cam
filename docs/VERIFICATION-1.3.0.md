# Ed-Cam 1.3.0 verification — 2026-11-28

## User reference and scope

Reviewed the supplied 32.97-second, 576×1280, 30fps screen recording. Whole-video samples cover camera, styles, album, photo details and settings. Additional 250ms samples cover 13–17.75s and 28–32.75s: controls appear in stages during navigation/scrolling, the text/control replicas move independently, and camera tools are visually narrow. Local contact sheets are `qa/video-review/contact.jpg` and `settings-reference.jpg` (not committed).

The shipped change replaces the runtime WebView replicas with native controls, rebuilds the shooting layout, adds hardware-aware zoom/exposure and a private video workflow. Original Sakuga component sources and license remain unchanged. No server, network permission, account, analytics or social features were added.

## Automated evidence

Environment: JDK 17, Gradle 8.9, Android SDK/API35, isolated `EdCamReview130` x86_64 emulator, WHPX and software GPU, 720×1280 at 320dpi.

| Check | Result |
| --- | --- |
| Debug app, AndroidTest build and Android lint | Build succeeded; 0 lint errors, 13 warnings |
| `qa/test-presets.ps1` | 4,946 preset checks plus 108 camera-core checks passed |
| `PixelInstrumentation`, font 100% | 382 pixel, crop/EXIF, original bytes, metadata, MediaStore export/delete and camera geometry checks passed |
| `PixelInstrumentation`, font 130% | Same 382 checks passed |
| `UpgradeInstrumentation` | 26 checks passed |
| Java formatter / Python syntax / whitespace | Checked with google-java-format, py_compile and git diff --check |

The upgrade suite covers native screens without WebViews, supported zoom bounds, two-pointer zoom through the real TextureView, exposure clamping, rear/front captures and mirror metadata, H.264/MP4 video with a decodable frame and audio track, pause/resume, silent recording without an audio track, export, finalization when the camera stops, immediate preparation cancellation and no remaining `.pending` files. It deletes only its own newly created media and export.

Visual review also caught nearly invisible native small-style rating stars in the light palette. The final build uses a standard five-star RatingBar with explicit foreground/background tints; the photo-only ADB flow checks that tapping the fourth star persists a rating of four.

The initial synthetic pinch was below the effective gesture threshold and failed its assertion. The corrected test expands two touchscreen pointers across a realistic span and passes without bypassing ScaleGestureDetector. That failed run is not counted as passed evidence.

## Native interaction and visual review

`qa/review-device.py` uses ADB touch and fresh UI dumps, never WebView DOM clicks. Captures cover camera, zoom/exposure, intensity/compare, photo detail/actions/editor, video ready/recording/playback, style library, album, five themes, settings scrolling and 100%/130%/150% system text. Camera captures wait for the actual viewfinder accessibility node; stale `uiautomator` files are removed before each dump. Earlier splash-only captures and transient null-root dump failures are not accepted as camera evidence.

Screen artifacts are local under `qa/video-review/`; the final harness log is `.tools/native-ui-final.log`. No new camera screenshot was added to README. Lint warnings include legacy fixed-orientation/backup configuration and string-resource localization work; zero lint errors is not a claim that every warning was resolved.

## Settings performance

Same emulator/profile and one harness: eight settings scroll round trips. One measured run per build; results vary with host load. The settings structure also changed from five inline radio choices to a compact picker.

| Metric | 1.2.2 | Native 1.3 candidate |
| --- | ---: | ---: |
| Collected frame samples | 131 | 264 |
| Median UI frame duration | 62.68ms | 28.18ms |
| p95 UI frame duration | 169.72ms | 50.22ms |
| App-process PSS | 86,847KB | 39,160KB |
| Frames over 16.67ms | 131/131 | 257/264 |

Logs: `.tools/settings-before.log` and `.tools/settings-after-final.log`. Earlier native candidates measured 35.81ms and 48.20ms median; the table reports the final measured settings layout. Separate WebView renderer memory is excluded from old app-process PSS. These data support a reduction on this emulator; they do **not** establish 60fps, physical touch latency, sensor FPS or a stable percentage speedup on a phone. The final camera-surface cleanup does not change settings layout or its measured path.

## Re-run

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\qa\test-presets.ps1
# Install the default AndroidTest APK, then:
adb shell am instrument -w com.edcam.test/com.edcam.PixelInstrumentation

.\gradlew.bat :app:assembleDebugAndroidTest '-Prunner=com.edcam.UpgradeInstrumentation'
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.edcam.test/com.edcam.UpgradeInstrumentation
adb shell am instrument -w -e benchmark true com.edcam.test/com.edcam.UpgradeInstrumentation
python qa/review-device.py
```

Set `ANDROID_HOME` and grant CAMERA/RECORD_AUDIO on an isolated QA emulator. The Python harness changes themes/font scale and creates/deletes test media. Earlier `v120-*` device scripts target the retired web controls. The old broad PerformanceInstrumentation also expects a grid button on the photo toolbar; use the new settings benchmark for this layout.

## Hardware and product limits

- No physical phone is connected. LED flash, auxiliary/ultrawide/telephoto access, focus accuracy, GPS, heat, battery, sustained recording and device-specific smoothness remain unverified.
- Zoom uses advertised ranges; API30+ uses CONTROL_ZOOM_RATIO, while Android 10 uses sensor cropping and exposed camera IDs. Lens field-of-view ratios are normalized to the first exposed lens. Manufacturers may hide auxiliary lenses. 0.3x, 0.5x and 20x are not universal promises. During recording, zoom remains on the current camera rather than switching camera devices mid-file.
- Video uses original camera colors. It does not encode photo presets, grain, vignette, photo stamps or a mirrored front-camera effect. Resolution is selected from advertised recording sizes, up to 1080p; 30fps is requested, not a guarantee of measured frame rate in all lighting. Files finalize at the configured 2GB safety limit. Starting/stopping very short recordings can fail gracefully rather than leaving a corrupt playable entry.
- Photo processing still limits the long edge to 2560px. Original bytes are separate when enabled. Private photos and videos are lost if app data is cleared or the app is uninstalled; export first.
- Portrait phone layout is the current scope. Physical TalkBack, full landscape/tablet support, optical transitions across vendor camera HALs and runtime permission UI on every Android/vendor version are not certified.
- APK is debug-signed for personal testing, not a store release. See `delivery/SHA256SUMS.txt` for the delivered file hash.

Official API references: [Camera2 zoom capabilities](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#CONTROL_ZOOM_RATIO_RANGE), [MediaRecorder lifecycle](https://developer.android.com/reference/android/media/MediaRecorder).

## Delivered APK

- File: `delivery/Ed-Cam-1.3.0.apk`; 1027786 bytes.
- SHA-256: `02ddfe2191b9aa65ddc2448b10566eede6de91889d24778026d991d82a2d726a`.
- Build output, delivery copy and Downloads handoff match byte-for-byte.
- Debug signing certificate SHA-256: `d6c94110ab51d3504d8dd33c9baa58a6afe71e18a02612021dce0b7b62a53a2d`, matching the repo 1.2.2 APK.
