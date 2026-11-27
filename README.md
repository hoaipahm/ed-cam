<p align="center">
  <img src="docs/images/ed-cam-icon.svg" alt="Ed-Cam camera icon" width="112" height="112">
</p>

<h1 align="center">Ed-Cam</h1>

<p align="center">An offline Android photo and video camera with hardware-aware zoom, live photo presets and a private album.</p>

<p align="center">
  <img src="docs/images/java.svg" alt="Java 17">
  <img src="docs/images/android.svg" alt="Android 10+">
  <img src="docs/images/camera.svg" alt="Camera2">
  <img src="docs/images/ui-effects.svg" alt="Sakuga VFX">
</p>

<p align="center">
  <a href="delivery/Ed-Cam-1.3.0.apk">Download APK 1.3.0</a> ·
  <a href="#technology">Technology</a> ?
  <a href="CHANGELOG.md">Changelog</a>
</p>

## Download

| Version | APK | Highlights |
| --- | --- | --- |
| 1.3.0 | [Download](delivery/Ed-Cam-1.3.0.apk) | Native controls, hardware-aware zoom, exposure, MP4 recording/playback and compact settings |
| 1.2.2 | [Download](delivery/Ed-Cam-1.2.2.apk) | Darker camera controls, narrower mode tabs, and a prominent circular shutter |
| 1.2.1 | [Download](delivery/Ed-Cam-1.2.1.apk) | Compact flash label, responsive star ratings, and a lower effects layer |
| 1.2.0 | [Download](delivery/Ed-Cam-1.2.0.apk) | 180 presets, expanded Sakuga controls, original-color comparison, and revised flash handling |
| 1.1.0 | [Download](delivery/Ed-Cam-1.1.0.apk) | Original Sakuga tabs, rebuilt from the recorded source baseline |
| 1.0.2 | [Download](delivery/Ed-Cam-1.0.2.apk) | Full-screen camera with a larger viewfinder |
| 1.0.1 | [Download](delivery/Ed-Cam-1.0.1.apk) | Early camera build |

These APKs are debug-signed builds for personal testing. Open the APK on your Android device and follow the installation prompt. To preserve your private album, install a newer APK over the existing app when the signing certificate matches. Export important photos to your device gallery before uninstalling or clearing app data.

See [APK checksums](delivery/SHA256SUMS.txt) and the [changelog](CHANGELOG.md).

## Features

- Apply 180 color presets with live preview, adjustable intensity, search, and favorites.
- Use front and rear cameras, tap to focus, set a timer, and choose a capture aspect ratio. Flash depends on the selected camera's hardware.
- Pinch to zoom, choose supported lens/zoom stops, and adjust zoom or exposure with native sliders. Ultrawide and high zoom values appear only when Camera2 exposes them; some manufacturers restrict auxiliary lenses in third-party apps.
- Record MP4/H.264 video at up to 1080p/30 fps, with optional microphone audio, pause/resume, private playback, deletion and export to `Movies/Ed-Cam`. Video uses original camera colors; photo presets do not apply to video.
- Import and edit images, keep originals, rate photos privately, and export to `Pictures/Ed-Cam`.
- Choose among five interface themes and optionally add a date stamp or capture location. The app works without an account or internet access.

## Technology

| Layer | Technology | Role |
| --- | --- | --- |
| Android app | Java 17 and Android Views | Screens, camera controls, and photo editing workflow |
| Camera | Android Camera2 | Live viewfinder, capture, focus, and hardware flash requests |
| Image processing | Android Bitmap, Canvas, and ColorMatrix | Preset previews and on-device photo rendering |
| Interaction | Native Android controls | One interactive layer, system text scaling, ripple and haptic feedback |
| Video | Camera2, MediaRecorder, VideoView | Recording, optional audio, pause/resume and local playback |
| Storage | Local app files and Android MediaStore | Private album storage and export to the device gallery |
| Build and verification | Gradle, Android lint, and instrumentation | Builds APKs and checks Android behavior |

## Build from source

Install JDK 17, Android SDK Platform 35, and Android SDK Build Tools 35.0.0. Open the project in Android Studio or configure the SDK through `ANDROID_HOME` or a local `local.properties` file.

On Windows, run:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\qa\test-presets.ps1
```

On macOS or Linux, run:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For upgrade tests on an isolated QA emulator:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug '-Prunner=com.edcam.UpgradeInstrumentation'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant com.edcam android.permission.CAMERA
adb shell pm grant com.edcam android.permission.RECORD_AUDIO
adb shell am instrument -w com.edcam.test/com.edcam.UpgradeInstrumentation
python qa/review-device.py
```

Set `ANDROID_HOME` and optionally `ANDROID_SERIAL` for the Python checks. They change theme/font settings and create then delete a QA video; use an isolated emulator. See [1.3.0 verification](docs/VERIFICATION-1.3.0.md). Earlier `v120-*` scripts target the archived WebView interface, not the native 1.3.0 interface.

## Project layout

- `app/src/main/java/com/edcam/` contains the Android UI, Camera2 controller, color transforms, and local photo storage.
- `app/src/main/assets/` retains the original offline Sakuga sources and license. Version 1.3.0 does not attach these archived WebView components to shipping screens.
- `app/src/androidTest/` contains Android instrumentation checks.
- `qa/` contains visual and interaction check scripts.

The camera layout and interactions have been exercised on an Android emulator. The physical flash and performance on specific phone models still need device testing.

The Sakuga components are distributed under the [upstream MIT license](app/src/main/assets/Sakuga-LICENSE.txt). See the [asset manifest](docs/SAKUGA-ASSETS.json) for component sources and checksums.
