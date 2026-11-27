---
name: Ed-Cam
description: Native Android personal camera with local Sakuga controls and five palettes
colors:
  film-background: "#F7F4ED"
  film-surface: "#FFFDF8"
  film-ink: "#292440"
  film-muted: "#6B647E"
  film-accent: "#6554A4"
  film-on-accent: "#FFFFFF"
  film-line: "#E1D9F1"
  night-background: "#191922"
  night-surface: "#242432"
  night-ink: "#F4EFFC"
  night-muted: "#BEB5CF"
  night-accent: "#CDC0F4"
  night-on-accent: "#27203C"
  night-line: "#444052"
  modern-background: "#F3F6FC"
  modern-surface: "#FFFFFF"
  modern-ink: "#1D2B44"
  modern-muted: "#55647B"
  modern-accent: "#2756BD"
  modern-on-accent: "#FFFFFF"
  modern-line: "#D4DDEB"
  rose-background: "#FBEFF2"
  rose-surface: "#FFF8FA"
  rose-ink: "#492E3A"
  rose-muted: "#805568"
  rose-accent: "#A3355F"
  rose-on-accent: "#FFFFFF"
  rose-line: "#E7CAD5"
  sage-background: "#EDF2E9"
  sage-surface: "#F8FBF4"
  sage-ink: "#273D31"
  sage-muted: "#566D5C"
  sage-accent: "#356449"
  sage-on-accent: "#FFFFFF"
  sage-line: "#CDDACA"
  viewfinder: "#121212"
  camera-background: "#000000"
  camera-active: "#FFD541"
  overlay: "#00000099"
  preview-ink: "#FFFFFF"
  sakuga-card: "#FFFDF8"
  sakuga-ink: "#292440"
  sakuga-soft: "#D1C5EF"
  sakuga-primary: "#B6A6ED"
  sakuga-accent: "#EC83AD"
  sakuga-track: "#E5DDF3"
  sakuga-dark-card: "#302C42"
  sakuga-dark-ink: "#F3ECFF"
  sakuga-dark-soft: "#68557F"
typography:
  headline:
    fontFamily: "sans-serif-medium"
    fontSize: "23sp"
    fontWeight: 500
  title:
    fontFamily: "sans-serif-medium"
    fontSize: "20sp"
    fontWeight: 500
  body:
    fontFamily: "sans-serif"
    fontSize: "14sp"
    fontWeight: 400
  label:
    fontFamily: "sans-serif"
    fontSize: "11sp"
    fontWeight: 400
  web-tab:
    fontFamily: "system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 400
rounded:
  tile: "10dp"
  control: "12dp"
  preview: "16dp"
  icon-button: "24dp"
spacing:
  small: "8dp"
  control: "12dp"
  inset: "16dp"
  page: "18dp"
components:
  button-primary:
    backgroundColor: "{colors.film-accent}"
    textColor: "{colors.film-on-accent}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "8dp 12dp"
  button-secondary:
    backgroundColor: "transparent"
    textColor: "{colors.film-accent}"
    rounded: "{rounded.control}"
    padding: "8dp 12dp"
  camera-preview:
    backgroundColor: "{colors.viewfinder}"
  camera-shutter:
    backgroundColor: "transparent"
    textColor: "{colors.preview-ink}"
    size: "76dp"
  camera-dock:
    backgroundColor: "{colors.camera-background}"
    height: "160dp"
  navigation:
    backgroundColor: "{colors.film-surface}"
    height: "76dp"
  preset-tile:
    backgroundColor: "{colors.film-surface}"
    rounded: "{rounded.tile}"
---

# Design System: Ed-Cam

## Overview

**Creative North Star: "A personal photographic notebook"**

A personal photographic notebook implemented with Android Views. The current hybrid implementation gives shooting a black, photograph-led workspace with white controls and yellow active states. The selected photo aspect ratio determines the viewfinder geometry; the surrounding controls stay secondary.

Five persisted palettes remain available on secondary pages: cream and purple, dark lavender, clean cobalt, rose and berry, and sage and green. This document records source values, not rendered acceptance or hardware validation. Physical camera quality, GPS accuracy, and performance require separate verification.

**Key Characteristics:**
- Black shooting workspace with white controls and yellow active states
- Five persisted semantic palettes on secondary pages
- Native Android sans typography and density-independent geometry
- Aspect-ratio-preserving preview with controls overlaid at the top

## Colors

### Primary
On secondary pages each theme's accent identifies primary actions; on-accent supplies their foreground. Film uses purple, Night uses pale lavender, Modern uses cobalt, Rose uses berry, and Sage uses deep green. Palette.java is the source of truth for the five complete sets recorded above. Film is the initial selection; the theme preference is persisted.

### Neutral
Background is the screen canvas, surface supports navigation and rows, ink carries primary text, muted carries supporting labels, and line supplies selected backgrounds. Shooting ignores palette fills for its black canvas, white shutter and tools, and yellow active indicators. The viewfinder is dark gray while opening. The top tool row uses a translucent black backing.

**The Complete Palette Rule.** Apply background, surface, ink, muted, accent, on-accent, and line from the same selected Palette instance.

## Typography

The Android sans family supplies body text. Medium sans marks the header and page titles. Reused roles are recorded in frontmatter; page headings use headline, the compact brand bar uses title, descriptions and buttons use body, and secondary navigation captions use label. Shooting dock labels use 12sp; the aspect-ratio control uses 13sp. Settings rows additionally use 16sp text and 12sp support text; section headings use 19sp bold. Countdown text is a transient 64sp status treatment, not a display-face rule.

TextView sizes use Android sp and omit extra font padding. Body paragraphs add 4dp line spacing. The local WebView tabs use system-ui at 14 CSS pixels with a minimum 48px tab height; selected text uses weight 800 and rises 5px. Android fontScale is passed through WebView text zoom. There is no imported typeface. Native platform font behavior remains authoritative.

## Layout

Shooting omits the brand header and bottom app navigation. A full-size black FrameLayout contains an aspect-ratio-preserving stage, a 48dp top overlay toolbar, and a bottom 160dp black dock. The stage reserves only that dock and centers the preview in all remaining space. It fits the selected 3:4, 1:1, or 9:16 ratio without distortion; there are no rounded preview corners or outer stage margins. Preview width is min(available width, available height * selected ratio). No universal screen-percentage guarantee follows from this formula.

The dock combines a 64dp STYLE / photo / color tab region and a 96dp capture row. STYLE opens the style library, the color tab opens intensity, and the album action opens the personal gallery. A white 76dp shutter sits between equally weighted album and flip containers with 56dp controls. There is no camera preset thumbnail strip. The five equal-width top tools are flash, timer, grid, aspect ratio, and settings.

Secondary pages retain the 52dp header, 76dp four-item navigation where applicable, persisted theme colors, scrollable content, and 18dp page insets. Shooting enables Android immersive sticky fullscreen: the status and navigation bars are hidden, and an edge swipe temporarily reveals the system bars using Android behavior. Its root retains DisplayCutout safe top and bottom insets rather than reserving ordinary system-bar height. Secondary pages restore visible system bars and pad by their top and bottom window insets. The shooting navigation-bar backing remains black when revealed. No active compact-height breakpoint controls the shooting composition. Small-screen and large-font rendering require separate verification.

## Elevation & Depth

Native content surfaces use palette tones and spacing. Sakuga tab controls deliberately use a dark 2.4px outline and a hard 3px/4px offset shadow in the soft purple token; this is part of their illustrated control material, not a native card shadow. Buttons disable their state-list elevation animator. Native dialogs retain platform presentation. Over-image stamps use a black text shadow (radius 3, offset 0/1 in the source call); countdown uses radius 6 and offset 0/2. These are text legibility aids, not reusable card-shadow tokens.

## Shapes

Secondary action controls and notices use 12dp corners, and secondary text fields use 10dp corners. Shooting has a rectangular, unclipped viewfinder. The shutter has a transparent 76dp circular outer target with a 3dp white ring and centered 60dp white core. Camera icon actions use circular ripple masks. Sakuga tabs use 12px outer corners and a raised selected indicator with 13px top corners. Native navigation markers remain as fallback controls when the WebView layer is unavailable.

## Components

### Buttons
Primary buttons use accent and on-accent; secondary buttons are transparent with accent text. Both have mixed-case 14sp text, a minimum 48dp height, control-radius corners, and native ripple feedback. Button and icon-button taps invoke virtual-key haptics. Ripple ink is translucent black in light palettes and translucent white in Night; there is no custom hover animation.

### Inputs / Fields
The optional place-name field is a native EditText with 14sp text, surface fill, tile corners, 12dp horizontal and 10dp vertical padding, and a 60-character limit. Native focus and editing behavior apply; no custom focus-ring token is defined.

### Chips
Category chips on secondary pages reuse primary and secondary buttons with a 48dp height. Style selection belongs to the library; shooting contains no preset thumbnail strip.

### Navigation
A transparent full-window local WebView renders only Sakuga tab controls and their effects. Camera preview, photos, processing, and native pages remain Android Views. The layer anchors to the 64dp camera modes region or the 76dp secondary navigation region. It forwards only touches within the reported tab hitbox to the web controls, letting outside touches reach the native interface. A successful bounds handshake hides the native fallback anchor; renderer failure restores it.

The primary control has four tabs for camera, styles, album, and settings. The camera control has three tabs for styles, photo, and color intensity. A validated Java bridge opens native screens/dialogs and synchronizes selection. The host uses the unchanged supplied tabs.js; sakuga-host.js handles the app-specific wiring. Native selection remains authoritative.

The light control uses cream fill, purple outline and shadow, and pink effect accent; Night supplies darker card/ink/soft values. These control tokens complement the five native palettes rather than replacing the whole app with HTML. Host width is the anchored region minus 28 CSS pixels. Each tab flexes equally with 10px/4px padding and minimum 48px height.

### Sakuga motion
The supplied component uses spring-driven indicator movement and its shared VFX effects layer. Reduced-motion preference is handled by the component core. Keep original tabs.js unchanged; add application behavior in the host wiring. This records source behavior, not a claim of frame-by-frame visual acceptance.

### Shooting controls and overlays
The top toolbar contains white 24dp custom line icons, with yellow for active flash, grid, and timer. Active timer adds a 10sp seconds badge. Aspect ratio is white 13sp text. The native fallback dock labels are 12sp; the visible Sakuga controls use their web typography. The intensity dialog title names the current preset. Camera tools and capture actions retain ripple and haptic feedback, plus content descriptions.

Permission and failure explanations are centered inside the viewfinder and can be tapped to request permission or retry. Optional metadata is 11sp white at the bottom, hidden when stamp and location are both off. Countdown is centered and transient. Style and intensity are not floating preview overlays in this version.

## Do's and Don'ts

### Do:
- Do substitute all seven semantic palette roles together when changing theme.
- Do keep camera tool icons white with yellow active states, preserve the photo aspect ratio, and use outlined Sakuga tabs for mode selection.
- Do retain native ripple feedback and content descriptions on actionable controls.
- Do use sp/dp in native source and the established CSS pixels inside the local tab component.

### Don't:
- Don't treat the rejected first UI or older captures as visual acceptance.
- Don't replace photo content with decorative theme illustrations.
- Don't claim a visual match to Moc: no corresponding source or reference was found.
- Don't claim hardware validation from source inspection or emulator review.


## Ed-Cam 1.2.0 control update (2026-11-27)

The user pinned the Sakuga gallery as the visual and motion authority. Native camera geometry, photo processing, album storage and five palette choices remain. Local unmodified standalone scripts now supply all eleven gallery component types. Primary shooting/save/import actions keep the original primary impact; quieter actions and category filters use secondary buttons. Do not override a primary button's impact-frame background with selection styling.

The single Activity overlay positions components over native anchors, clipping only partially visible controls inside scrollers. Native controls remain attached for business logic and failure fallback, and are hidden from accessibility when web counterparts take over. Swipes starting on web controls scroll their native ScrollView/ListView/HorizontalScrollView; range drags stay with the slider. App-owned alerts and the intensity dialog use one additional temporary overlay, released on dismissal; OS permission sheets and Android's date/file pickers keep platform UI.

Light controls use gallery ink #1D1A4A, white card, soft #B8B3FF, primary #8F8DF7 and accent #FF5FA8; Night keeps readable dark surfaces. The original drawn toggle is scaled to fit its native target without changing any painted frame. Native fontScale is passed into WebView text zoom. Tooltips are positioned inside the viewport. Toasts sit above the camera dock so they cannot cover tab buttons.

The camera names the active preset and intensity. The MÀU dialog offers a temporary original-color comparison; it does not silently change saved strength. Photo details include a private 0–5 star marker. Real task phases (decode, color render, persistence) drive progress; success is reported only after persistence returns. No fake demo progress ships.
