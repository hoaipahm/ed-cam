# Ed-Cam implementation
1. Establish native Android toolchain and reproducible build.
2. Build camera preview and full-resolution capture with shared color transforms.
3. Add 60 named presets, favorites, intensity, timer, flash, grid, aspect ratios, focus and front/rear switching.
4. Add local album, image import/editor, safe deletion, time/location options and MediaStore export.
5. Add five persisted interface themes and Vietnamese permission/error/empty states.
6. Build APK, run unit and Android lint checks, install and inspect on an available emulator/device. Record hardware limitations.

Architecture: Activity owns navigation and UI state; CameraController owns Camera2 lifecycle; Presets/PhotoProcessor own transforms; PhotoStore owns image and metadata persistence; Palette and IconView own appearance.
No backend is needed. Camera2 avoids runtime third-party dependencies and gives control over preview/capture. Tradeoff: more camera lifecycle/rotation code to verify than CameraX.


## 1.2.0 follow-up
- Implemented: Camera2 AE precapture and bounded fallback; clear unsupported-flash message.
- Implemented: 120 expressive additions (180 total), stable legacy IDs, cached transforms, bounded grain texture.
- Implemented: original gallery button/toggle/slider/checkbox/radio/input/rating/progress/toast/tooltip controls; original tabs retained.
- Implemented: compare original color, active preset label and private photo ratings.
- Verified: native pixel/storage checks, browser interaction checks and Android touch flows. See VERIFICATION-1.2.0.md.
- Pending external hardware: physical flash, front/rear camera quality, GPS, long-run performance, TalkBack.
