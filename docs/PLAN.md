# Ed-Cam implementation
1. Establish native Android toolchain and reproducible build.
2. Build camera preview and full-resolution capture with shared color transforms.
3. Add 60 named presets, favorites, intensity, timer, flash, grid, aspect ratios, focus and front/rear switching.
4. Add local album, image import/editor, safe deletion, time/location options and MediaStore export.
5. Add five persisted interface themes and Vietnamese permission/error/empty states.
6. Build APK, run unit and Android lint checks, install and inspect on an available emulator/device. Record hardware limitations.

Architecture: Activity owns navigation and UI state; CameraController owns Camera2 lifecycle; Presets/PhotoProcessor own transforms; PhotoStore owns image and metadata persistence; Palette and IconView own appearance.
No backend is needed. Camera2 avoids runtime third-party dependencies and gives control over preview/capture. Tradeoff: more camera lifecycle/rotation code to verify than CameraX.
