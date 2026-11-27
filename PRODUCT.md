# Ed-Cam
<!-- impeccable:product-schema 1 -->
## Platform
android
## Stack
Android native. Java 17, Android Views, Camera2, MediaRecorder, local JSON photo metadata, private MP4 files and MediaStore. Native controls own interaction; original Sakuga sources remain archived and unchanged.
## Users
Vietnamese-speaking individual taking and keeping personal photographs on an Android phone.
## Product Purpose
A personal photo and video camera with live photo styles, hardware-aware zoom, optional capture time and location, a local media album and selectable interface themes.
## Capabilities and Constraints
No accounts, social feed, analytics or advertising. Media stays on-device. Location and video audio are optional. Original and edited photos can both be retained. Android 10+ is the target. Zoom comes from Camera2 capabilities; do not invent 0.3x or 20x support. Video is MP4/H.264, up to 1080p/30fps depending on camera support, and uses original camera colors. Photo output remains bounded to 2560px on the long edge, with original bytes kept separately when enabled.
## Product Principles
Camera first. Keep originals safe. Explain permissions. Never invent photo or location data. Persist user choices.
## Open Decisions
The user supplied a 33-second screen recording showing narrow buttons, changing layouts and settings lag; it is the defect reference for 1.3.0. No physical phone is connected. Flash, vendor auxiliary lenses, sustained video, heat, battery, GPS and image quality require device verification. Five themes and 180 presets do not establish user acceptance.
