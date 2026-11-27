# Camera and personal album
Mode: Operate. Android phone portrait first, supports system font scaling and scrollable secondary screens.
## Direction contract
THESIS: A personal photographic notebook with the camera taking most of the first viewport; changing a style changes the visible image, not a decorative label.
OWN-WORLD: Five distinct selectable palettes: warm film-paper with terracotta, graphite with lime, clean white with cobalt, rose with berry, sage with deep green. Android typography roles, 48dp controls, consistent custom line icons.
STORY: Open camera, choose a color mood, take a photograph, revisit it in the album. Tools remain secondary to the image.
FIRST VIEWPORT: Camera reference supplied by the user governs the shooting screen: immersive black camera workspace, five 48dp tools across the top, wide aspect-preserving preview, and one 144dp bottom dock with STYLE/ẢNH/MÀU above a white 76dp shutter flanked by album and flip. The camera omits the app header and custom navigation. Secondary pages retain the themed header and navigation. Android system bars can be revealed by swiping; display cutout safe insets remain respected. Missing permission has an actionable explanation inside the preview.
FORM: Native camera workspace, code-first. User delegated immediate implementation; multiple interfaces are explicitly requested. seed: none (no concept roll; directly implementing requested variants).
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

User rejected the first UI as ugly/misaligned and then rejected the small viewfinder. The user supplied a classic camera reference with a large preview, top tools and bottom shutter; this supersedes the earlier notebook camera layout. Authoritative camera captures use reference-camera-*.png. Native regression tests require at least 90 percent screen width and 55 percent screen height for 3:4 on the tested phone profiles, including compact and 130 percent fonts.
