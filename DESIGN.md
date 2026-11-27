# Ed-Cam 1.3 design

Mode: Operate. The user's screen recording rejects the earlier illustrated WebView control layer. Native Android controls now own interaction, bounds and typography. Preserve the five palettes in Palette.java; camera chrome is black with white tools, yellow selection and a red video shutter.

## Camera

The stage fits the selected photo ratio or portrait 9:16 video ratio without distortion. A 56dp toolbar overlays its top. Five equal-width tools use 23dp line icons, 11sp captions and at least 48dp touch targets. Photo tools are flash, timer, ratio, styles and settings. Video tools are microphone, exposure/zoom, 16:9 format, grid and settings.

The 48dp zoom control keeps the selected tab on the shutter axis. Swipe to step, hold and drag for the fine ruler, and tap the center for zoom and exposure sliders. Pinch gestures update the same Camera2 request. The preset overlay opens the color intensity and saved recipe controls. MP4 recording applies the selected RGB matrix on a GPU encoder surface.

The 88dp capture row places equally weighted Album and Flip targets around an 80dp shutter. Its ring, core and pressed state are drawn natively. Video uses a red core and square stop state. The 48dp Photo/Video row becomes an elapsed timer and Pause/Resume row during recording. Unsafe controls are visibly disabled. Hardware volume keys operate the shutter on this screen.

Respect system bars, gesture navigation and cutouts. Native messages sit above the capture dock for 2.5 seconds without covering the shutter or modes. Photo processing uses a 4dp progress indicator at the top of content.

## Secondary screens

Use each palette's background, surface, ink, muted, accent and on-accent consistently. Native sans text uses sp: 23sp headings, 19sp section titles, 14–16sp body and 12–14sp descriptions. Text wraps and settings rows size to content. Pages have 18dp horizontal margins, a 52dp header and 76dp navigation. Android ripples and haptics provide press feedback.

Settings groups are Appearance, Viewfinder, Video, Photo saving, Privacy and About. Theme selection is one row opening a five-choice dialog. Switches have at least 48dp targets; descriptions remain with their labels. The optional location name is one native EditText with the system keyboard and a 60-character limit.

Photo details retain ratings, edit-from-original, export and delete confirmation. Videos are grouped in the private album and open a native VideoView player with playback controls, export, delete confirmation and Close.

## Rendering and accessibility

One Android view owns each control's bounds, label, focus and click. Original Sakuga sources/license remain unchanged for provenance, with no WebView attached at runtime. There is no repeated native-to-HTML geometry synchronization or duplicate text overlay.

Give icon actions content descriptions, respect system font scaling and native animation settings. Inspect compact 720×1280 and 130%/150% text captures. Physical camera quality and smoothness cannot be inferred from screenshots or emulator frame metrics. See docs/VERIFICATION-1.3.0.md.
