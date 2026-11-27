# Ed-Cam

Ed-Cam is an offline Android camera and photo editor for Android 10 and later. It offers live color presets, a private on-device album, and animated controls from the Sakuga VFX gallery.

**Latest build:** [Download Ed-Cam 1.2.2](delivery/Ed-Cam-1.2.2.apk)

## Download

| Version | APK | Highlights |
| --- | --- | --- |
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
- Import and edit images, keep originals, rate photos privately, and export to `Pictures/Ed-Cam`.
- Choose among five interface themes and optionally add a date stamp or capture location. The app works without an account or internet access.

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

## Project layout

- `app/src/main/java/com/edcam/` contains the Android UI, Camera2 controller, color transforms, and local photo storage.
- `app/src/main/assets/` contains the offline Sakuga controls and their Android bridge.
- `app/src/androidTest/` contains Android instrumentation checks.
- `qa/` contains visual and interaction check scripts.

The camera layout and interactions have been exercised on an Android emulator. The physical flash and performance on specific phone models still need device testing.

The Sakuga components are distributed under the [upstream MIT license](app/src/main/assets/Sakuga-LICENSE.txt). See the [asset manifest](docs/SAKUGA-ASSETS.json) for component sources and checksums.
