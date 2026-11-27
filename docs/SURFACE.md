# Camera and private album

Mode: Operate. Android portrait phone, compact layouts, gesture navigation and system font scaling.

The reference is the user's 32.97-second 576×1280 screen recording supplied on 2026-11-28. It documents defects rather than a layout to reproduce. Camera, style, album and settings samples plus 250ms settings-transition frames are in the ignored local qa/video-review/ folder.

The replacement uses one native interaction layer, equal-width tools, an 80dp shutter, Photo/Video modes, real zoom capabilities, native settings and a private media album. DESIGN.md records the structure. Original effect sources remain unchanged but are not mounted at runtime.

Acceptance: preserve photo editing/originals; record/replay real MP4; bound zoom to hardware; preserve preview geometry on the tested compact profile; readable controls at 100%, 130% and 150% text. Separate emulator evidence from physical focus/flash/GPS/zoom/performance acceptance. See VERIFICATION-1.3.0.md.
