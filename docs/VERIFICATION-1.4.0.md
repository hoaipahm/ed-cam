# Ed-Cam 1.4.0 verification

Android package com.edcam, version code 9. The personal test APK uses the same debug certificate as 1.3.0. SHA-256 of the APK with the adaptive aperture icon is `949bd11ee52a10f78d42d9697217b488b0cc986444a28674121489ad6e09c6f4`.

## Checks

- Build and Android lint on SDK 35. Final lint report contains 0 errors and 21 warnings, including localization, orientation and custom view warnings.
- Host tests passed 4,946 preset checks and 108 camera core checks.
- Pixel instrumentation verifies processing, metadata, persistence, export, deletion and camera layout. Upgrade instrumentation verifies front/rear capture, zoom, pause/resume, silent/audio video, lifecycle finalization and storage cleanup. A regression check now decodes a recorded Mono frame and checks both scene detail and channel differences.
- Native touch review on the isolated API 35 emulator checks the center zoom tab against the shutter axis, swipes to another zoom, captures a photo, opens the Android Sharesheet, previews three looks, saves a variant, opens the framing guide and reads the lens report. A first pass found the guide control underneath the toolbar. After moving it, the touch review passed, including opening the slider and removing the guide.
- The Android link integration uses a temporary HTTPS certificate trusted only by test instrumentation, a loopback server and synthetic JPEGs. A second test uses a real Chromium mobile viewport to open the viewer, decode a JPEG, vote for a named look, check owner results and verify HTTP 404 after revocation. Service tests cover missing upload and owner credentials, voting twice from the same browser, invalid choices, expiry and revocation.
- Settings benchmark on the API 35 software-GPU emulator at 720 × 1280, density 320, eight scroll round trips. One run recorded 262 frames, median 29.10 ms, p95 53.13 ms, 251 frames above 16.67 ms and 41,749 KB app PSS. The previous 1.3.0 run was 28.18/50.22 ms on this profile. Host load varies. These results do not establish smoother settings or sustained phone frame rates.

## Device and service boundaries

No physical Redmi was attached. The user's device description is Redmi 11. Confirm the exact model, exposed range, actual 0.6× field of view, forced flash timing, assist lamp, video torch, sustained color recording, thermals and camera quality on the handset. Android Sharesheet was exercised on the emulator. Individual Messenger, Zalo and note-app integrations depend on the installed receivers. Public web links need a configured HTTPS host. No user photos were uploaded during testing.

## Re-run

Use the Gradle build and runner selection in the repository README. Run PixelInstrumentation, UpgradeInstrumentation and ShareInstrumentation with the corresponding runner installed. Run `python qa/review-sharing.py` on the isolated emulator and follow `qa/review-links.py` for the local HTTPS and browser integration. The latter uses test-only Python packages cryptography, Pillow and Playwright. An existing Chromium executable can be supplied via EDCAM_BROWSER_PATH.
