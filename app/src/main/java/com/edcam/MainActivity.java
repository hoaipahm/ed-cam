package com.edcam;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private android.content.SharedPreferences prefs;
  private PhotoStore store;
  private Palette palette;
  private LinearLayout root, body, presetRow;
  private FrameLayout content, viewfinder;
  private TextureView texture;
  private TextView status, styleTitle, countdown, notice, stampView;
  private GridOverlay gridOverlay;
  private CameraController camera;
  private SakugaTabsLayer sakuga;
  private View tabsAnchor;
  private LocationManager locationManager;
  private Location location;
  private LocationListener locationListener;
  private int tab = 0, styleId = 0, themeId = 0, timer = 0, ratioId = 0;
  private float strength = 1;
  private boolean frontLens,
      resumed,
      busy,
      flash,
      grid,
      locationEnabled,
      stampEnabled,
      keepOriginal,
      destroyed,
      editor;
  private String group = "Tất cả", query = "", place = "";
  private String albumDay = "";
  private boolean albumWithLocation;
  private int albumLimit = 40;
  private Bitmap editSource, sample;
  private byte[] editBytes;
  private PhotoStore.Photo detail;
  private PhotoStore.Photo editContext;
  private CaptureSettings pending;
  private volatile long uiGeneration;
  private final android.util.LruCache<String, Bitmap> thumbnails =
      new android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
          return value.getAllocationByteCount();
        }
      };
  private final Set<String> favorites = new HashSet<>();
  private final Runnable clock =
      new Runnable() {
        public void run() {
          if (stampView != null) {
            String next = previewStamp();
            if (!next.contentEquals(stampView.getText())) stampView.setText(next);
          }
          if (resumed) main.postDelayed(this, 1000);
        }
      };
  private static final int CAMERA_PERMISSION = 10, LOCATION_PERMISSION = 11, IMPORT_PHOTO = 12;
  private static final float[] RATIOS = {.75f, 1f, 9f / 16f};
  private static final String[] RATIO_NAMES = {"3:4", "1:1", "9:16"};

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = getSharedPreferences("edcam", MODE_PRIVATE);
    store = new PhotoStore(this);
    styleId = prefs.getInt("style", 0);
    themeId = prefs.getInt("theme", 0);
    strength = prefs.getFloat("strength", 1);
    grid = prefs.getBoolean("grid", false);
    stampEnabled = prefs.getBoolean("stamp", false);
    locationEnabled = prefs.getBoolean("location", false);
    keepOriginal = prefs.getBoolean("original", true);
    ratioId = Math.max(0, Math.min(2, prefs.getInt("ratio", 0)));
    place = prefs.getString("place", "");
    favorites.addAll(prefs.getStringSet("favorites", Collections.emptySet()));
    if (state != null) {
      tab = state.getInt("tab", 0);
      styleId = state.getInt("style", styleId);
    }
    render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (sakuga != null) sakuga.onResume();
    if (camera != null) camera.start();
    if (locationEnabled) startLocation();
    main.removeCallbacks(clock);
    clock.run();
  }

  @Override
  protected void onPause() {
    if (sakuga != null) sakuga.onPause();
    resumed = false;
    main.removeCallbacks(clock);
    stopLocation();
    if (camera != null) camera.stop();
    if (busy && pending != null && !pending.processing) {
      busy = false;
      pending = null;
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("tab", tab);
    state.putInt("style", styleId);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy() {
    if (sakuga != null) {
      sakuga.release();
      sakuga = null;
    }
    thumbnails.evictAll();
    destroyed = true;
    uiGeneration++;
    main.removeCallbacksAndMessages(null);
    worker.shutdown();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (busy) {
      message("Đang xử lý ảnh, bạn chờ một chút nhé.");
      return;
    }
    if (editor) {
      closeEditor();
      return;
    }
    if (detail != null) {
      detail = null;
      render();
      return;
    }
    if (tab != 0) {
      tab = 0;
      render();
      return;
    }
    super.onBackPressed();
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void remember() {
    prefs
        .edit()
        .putInt("style", styleId)
        .putInt("theme", themeId)
        .putFloat("strength", strength)
        .putInt("ratio", ratioId)
        .putBoolean("grid", grid)
        .putBoolean("stamp", stampEnabled)
        .putBoolean("location", locationEnabled)
        .putBoolean("original", keepOriginal)
        .putString("place", place)
        .putStringSet("favorites", new HashSet<>(favorites))
        .apply();
  }

  private void render() {
    if (camera != null) {
      camera.stop();
      camera = null;
    }
    uiGeneration++;
    texture = null;
    stampView = null;
    status = null;
    styleTitle = null;
    presetRow = null;
    countdown = null;
    viewfinder = null;
    notice = null;
    tabsAnchor = null;
    palette = Palette.get(themeId);
    setTheme(
        new int[] {
              R.style.AppTheme,
              R.style.NightTheme,
              R.style.ModernTheme,
              R.style.RoseTheme,
              R.style.SageTheme
            }
            [Math.max(0, Math.min(4, themeId))]);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    boolean shooting = !editor && detail == null && tab == 0;
    root.setBackgroundColor(shooting ? Color.BLACK : palette.background);
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    getWindow().setNavigationBarColor(shooting ? Color.BLACK : palette.background);
    int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
    if (shooting)
      flags |=
          View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    if (!palette.dark && !shooting)
      flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(flags);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          android.view.DisplayCutout cutout = insets.getDisplayCutout();
          int top =
              shooting
                  ? (cutout == null ? 0 : cutout.getSafeInsetTop())
                  : insets.getSystemWindowInsetTop();
          int bottom =
              shooting
                  ? (cutout == null ? 0 : cutout.getSafeInsetBottom())
                  : insets.getSystemWindowInsetBottom();
          v.setPadding(0, top, 0, bottom);
          return insets;
        });
    FrameLayout scene = new FrameLayout(this);
    scene.addView(root, new FrameLayout.LayoutParams(-1, -1));
    setContentView(scene);
    root.requestApplyInsets();
    if (!shooting) header();
    content = new FrameLayout(this);
    root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    content.addView(body, new FrameLayout.LayoutParams(-1, -1));
    if (editor) editorScreen();
    else if (detail != null) detailScreen();
    else if (tab == 0) cameraScreen();
    else if (tab == 1) stylesScreen();
    else if (tab == 2) albumScreen();
    else settingsScreen();
    if (!editor && detail == null && !shooting) navigation();
    notice = text("", 13, palette.onAccent);
    notice.setPadding(dp(16), dp(12), dp(16), dp(12));
    notice.setBackground(round(palette.accent, 12));
    notice.setVisibility(View.GONE);
    notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    np.setMargins(dp(16), 0, dp(16), dp(12));
    content.addView(notice, np);
    if (tabsAnchor != null) {
      try {
        if (sakuga == null || sakuga.unavailable())
          sakuga = new SakugaTabsLayer(this, this::sakugaChanged);
        if (sakuga.getParent() instanceof ViewGroup)
          ((ViewGroup) sakuga.getParent()).removeView(sakuga);
        scene.addView(sakuga, new FrameLayout.LayoutParams(-1, -1));
        sakuga.target(tabsAnchor, shooting ? "camera" : "primary", shooting ? 1 : tab, palette);
      } catch (RuntimeException unavailable) {
        sakuga = null;
        tabsAnchor.setVisibility(View.VISIBLE);
      }
    } else if (sakuga != null) {
      if (sakuga.getParent() instanceof ViewGroup)
        ((ViewGroup) sakuga.getParent()).removeView(sakuga);
      sakuga.onPause();
    }
    remember();
  }

  private void sakugaChanged(String mode, String value, int index) {
    if (busy) {
      if (sakuga != null) sakuga.synchronizeSelection(mode.equals("camera") ? 1 : tab);
      return;
    }
    if (mode.equals("camera") && value.equals("color")) {
      new AlertDialog.Builder(this)
          .setTitle("Cường độ · " + Presets.get(styleId).name)
          .setView(strengthControl())
          .setPositiveButton("Xong", null)
          .setOnDismissListener(
              d -> {
                if (sakuga != null) sakuga.synchronizeSelection(1);
              })
          .show();
      return;
    }
    int target =
        value.equals("camera")
            ? 0
            : value.equals("styles")
                ? 1
                : value.equals("album") ? 2 : value.equals("settings") ? 3 : -1;
    if (target < 0 || target == tab) return;
    if (target == 1) snapshotStylePreview();
    tab = target;
    detail = null;
    render();
  }

  private void header() {
    LinearLayout bar = row();
    bar.setPadding(dp(16), 0, dp(12), 0);
    if (editor || detail != null)
      bar.addView(iconButton("back", "Quay lại", () -> onBackPressed(), false));
    TextView name =
        text(editor ? "Chỉnh ảnh" : detail != null ? "Khoảnh khắc" : "Ed-Cam", 20, palette.ink);
    name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    name.setGravity(Gravity.CENTER_VERTICAL);
    name.setMaxLines(1);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    bar.addView(name, new LinearLayout.LayoutParams(0, dp(52), 1));
    if (!editor
        && detail == null
        && tab == 0
        && getResources().getConfiguration().screenHeightDp < 700) {
      bar.addView(
          iconButton(
              "styles",
              "Cường độ style",
              () -> {
                if (!busy)
                  new AlertDialog.Builder(this)
                      .setTitle("Cường độ style")
                      .setView(strengthControl())
                      .setPositiveButton("Xong", null)
                      .show();
              },
              false));
    }
    bar.addView(
        iconButton(
            "theme",
            "Chọn giao diện",
            () -> {
              if (!busy) themeDialog();
            },
            false));
    root.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
  }

  private void navigation() {
    LinearLayout bar = row();
    bar.setPadding(dp(8), dp(4), dp(8), dp(4));
    bar.setBackgroundColor(palette.surface);
    String[] names = {"Chụp ảnh", "Style", "Album", "Cài đặt"},
        icons = {"camera", "styles", "album", "settings"};
    for (int i = 0; i < 4; i++) {
      final int target = i;
      boolean selected = tab == i;
      LinearLayout item = new LinearLayout(this);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER);
      item.setContentDescription(names[i]);
      item.setBackground(ripple(Color.TRANSPARENT, 12));
      FrameLayout marker = new FrameLayout(this);
      marker.setBackground(round(selected ? palette.line : Color.TRANSPARENT, 16));
      marker.addView(
          new IconView(this, icons[i], selected ? palette.accent : palette.muted),
          new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
      item.addView(marker, new LinearLayout.LayoutParams(dp(54), dp(30)));
      TextView label = text(names[i], 11, selected ? palette.accent : palette.muted);
      label.setGravity(Gravity.CENTER);
      label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
      label.setMaxLines(1);
      label.setTypeface(
          Typeface.create(selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(22));
      lp.topMargin = dp(2);
      item.addView(label, lp);
      item.setSelected(selected);
      item.setOnClickListener(
          v -> {
            if (busy) {
              message("Đang xử lý ảnh…");
              return;
            }
            if (tab != target) {
              tab = target;
              detail = null;
              render();
            }
          });
      bar.addView(item, new LinearLayout.LayoutParams(0, dp(56), 1));
    }
    root.addView(bar, new LinearLayout.LayoutParams(-1, dp(76)));
    tabsAnchor = bar;
  }

  private void cameraScreen() {
    FrameLayout workspace = new FrameLayout(this);
    workspace.setBackgroundColor(Color.BLACK);
    body.addView(workspace, new LinearLayout.LayoutParams(-1, -1));
    viewfinder = new FrameLayout(this);
    viewfinder.setBackgroundColor(Color.rgb(18, 18, 18));
    FrameLayout stage =
        new FrameLayout(this) {
          @Override
          protected void onMeasure(int ws, int hs) {
            super.onMeasure(ws, hs);
            int w = getMeasuredWidth(), h = getMeasuredHeight();
            float ratio = RATIOS[ratioId];
            int cw = Math.min(w, Math.round(h * ratio)), ch = Math.min(h, Math.round(cw / ratio));
            if (getChildCount() > 0)
              getChildAt(0)
                  .measure(
                      MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                      MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
          }
        };
    stage.setBackgroundColor(Color.BLACK);
    stage.addView(viewfinder, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
    FrameLayout.LayoutParams stageParams = new FrameLayout.LayoutParams(-1, -1);
    stageParams.bottomMargin = dp(160);
    workspace.addView(stage, stageParams);
    FocusTextureView focusTexture = new FocusTextureView(this);
    texture = focusTexture;
    viewfinder.addView(texture, new FrameLayout.LayoutParams(-1, -1));
    gridOverlay = new GridOverlay(this);
    viewfinder.addView(gridOverlay, new FrameLayout.LayoutParams(-1, -1));
    gridOverlay.setVisibility(grid ? View.VISIBLE : View.GONE);
    stampView = text(previewStamp(), 11, Color.WHITE);
    stampView.setShadowLayer(3, 0, 1, Color.BLACK);
    stampView.setPadding(dp(16), dp(8), dp(16), dp(14));
    viewfinder.addView(stampView, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    if (!stampEnabled && !locationEnabled) stampView.setVisibility(View.GONE);
    status = text("Đang mở camera…", 14, Color.WHITE);
    status.setGravity(Gravity.CENTER);
    status.setPadding(dp(24), dp(20), dp(24), dp(20));
    viewfinder.addView(status, new FrameLayout.LayoutParams(-1, -1));
    status.setOnClickListener(v -> requestCamera());
    countdown = text("", 64, Color.WHITE);
    countdown.setGravity(Gravity.CENTER);
    countdown.setShadowLayer(6, 0, 2, Color.BLACK);
    countdown.setVisibility(View.GONE);
    countdown.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE);
    viewfinder.addView(countdown, new FrameLayout.LayoutParams(-1, -1));
    focusTexture.setFocusListener(
        (x, y) -> {
          if (camera != null) camera.focus(x, y);
          gridOverlay.focus(x * texture.getWidth(), y * texture.getHeight());
        });
    texture.setContentDescription("Khung ngắm camera. Chạm để lấy nét.");

    LinearLayout tools = row();
    tools.setPadding(dp(8), 0, dp(8), 0);
    tools.setBackgroundColor(0x99000000);
    tools.addView(
        cameraAction(
            "flash",
            flash ? "Flash bật" : "Flash tắt",
            () -> {
              if (camera == null) return;
              flash = camera.setFlash(!flash);
              if (!flash) message("Flash đã tắt hoặc máy không hỗ trợ flash.");
              remember();
              updateCameraActions();
            }));
    tools.addView(
        cameraAction(
            "timer",
            timer == 0 ? "Hẹn giờ" : timer + " giây",
            () -> {
              timer = timer == 0 ? 3 : timer == 3 ? 10 : 0;
              remember();
              updateCameraActions();
            }));
    tools.addView(
        cameraAction(
            "grid",
            grid ? "Lưới bật" : "Lưới tắt",
            () -> {
              grid = !grid;
              if (gridOverlay != null) gridOverlay.setVisibility(grid ? View.VISIBLE : View.GONE);
              remember();
              updateCameraActions();
            }));
    TextView ratio = text(RATIO_NAMES[ratioId], 13, Color.WHITE);
    ratio.setGravity(Gravity.CENTER);
    ratio.setBackground(ripple(Color.TRANSPARENT, 24));
    ratio.setContentDescription("Tỷ lệ " + RATIO_NAMES[ratioId]);
    ratio.setOnClickListener(
        v -> {
          if (!busy) {
            ratioId = (ratioId + 1) % 3;
            ratio.setText(RATIO_NAMES[ratioId]);
            ratio.setContentDescription("Tỷ lệ " + RATIO_NAMES[ratioId]);
            remember();
            stage.requestLayout();
          }
        });
    tools.addView(ratio, new LinearLayout.LayoutParams(0, dp(48), 1));
    tools.addView(
        cameraAction(
            "settings",
            "Cài đặt",
            () -> {
              if (!busy) {
                tab = 3;
                render();
              }
            }));
    workspace.addView(tools, new FrameLayout.LayoutParams(-1, dp(48), Gravity.TOP));

    LinearLayout dock = new LinearLayout(this);
    dock.setOrientation(LinearLayout.VERTICAL);
    dock.setBackgroundColor(Color.BLACK);
    LinearLayout modes = row();
    modes.setPadding(dp(14), 0, dp(14), 0);
    TextView styles = text("STYLE", 12, Color.WHITE);
    styles.setGravity(Gravity.CENTER);
    styles.setContentDescription("Mở thư viện style");
    styles.setBackground(ripple(Color.TRANSPARENT, 12));
    styles.setOnClickListener(
        v -> {
          if (!busy) {
            snapshotStylePreview();
            tab = 1;
            render();
          }
        });
    modes.addView(styles, new LinearLayout.LayoutParams(0, dp(48), 1));
    styleTitle = text("ẢNH", 12, Color.rgb(255, 213, 65));
    styleTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    styleTitle.setGravity(Gravity.CENTER);
    modes.addView(styleTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
    TextView intensity = text("MÀU " + Math.round(strength * 100) + "%", 12, Color.WHITE);
    intensity.setGravity(Gravity.CENTER);
    intensity.setContentDescription("Cường độ style");
    intensity.setBackground(ripple(Color.TRANSPARENT, 12));
    intensity.setOnClickListener(
        v -> {
          if (!busy)
            new AlertDialog.Builder(this)
                .setTitle(Presets.get(styleId).name)
                .setView(strengthControl())
                .setPositiveButton(
                    "Xong", (d, w) -> intensity.setText("MÀU " + Math.round(strength * 100) + "%"))
                .show();
        });
    modes.addView(intensity, new LinearLayout.LayoutParams(0, dp(48), 1));
    dock.addView(modes, new LinearLayout.LayoutParams(-1, dp(64)));
    tabsAnchor = modes;
    LinearLayout capture = row();
    capture.setGravity(Gravity.CENTER);
    capture.setPadding(dp(22), 0, dp(22), dp(8));
    FrameLayout left = new FrameLayout(this), right = new FrameLayout(this);
    left.addView(
        whiteAction(
            "album",
            "Album ảnh",
            () -> {
              if (!busy) {
                tab = 2;
                render();
              }
            }),
        new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
    right.addView(
        whiteAction(
            "flip",
            "Đổi camera trước hoặc sau",
            () -> {
              if (!busy && camera != null) {
                flash = false;
                frontLens = !frontLens;
                camera.flip();
              }
            }),
        new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
    capture.addView(left, new LinearLayout.LayoutParams(0, dp(88), 1));
    FrameLayout shutter = new FrameLayout(this);
    GradientDrawable ring = round(Color.TRANSPARENT, 40);
    ring.setStroke(dp(3), Color.WHITE);
    shutter.setBackground(
        new RippleDrawable(ColorStateList.valueOf(0x66ffffff), ring, round(Color.WHITE, 40)));
    shutter.setContentDescription("Chụp ảnh ngay");
    View core = new View(this);
    core.setBackground(round(Color.WHITE, 32));
    shutter.addView(core, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
    shutter.setOnClickListener(
        v -> {
          v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
          beginCapture();
        });
    capture.addView(shutter, new LinearLayout.LayoutParams(dp(76), dp(76)));
    capture.addView(right, new LinearLayout.LayoutParams(0, dp(88), 1));
    dock.addView(capture, new LinearLayout.LayoutParams(-1, dp(96)));
    workspace.addView(dock, new FrameLayout.LayoutParams(-1, dp(160), Gravity.BOTTOM));
    camera =
        new CameraController(
            this,
            texture,
            frontLens,
            new CameraController.Listener() {
              public void ready() {
                flash = camera.setFlash(flash);
                if (status != null) status.setVisibility(View.GONE);
                applyLive();
                main.postDelayed(
                    () -> {
                      if (texture != null && texture.isAvailable() && sample == null) {
                        sample = texture.getBitmap(96, 128);
                        populateStrip();
                      }
                    },
                    900);
              }

              public void error(String error) {
                boolean wasBusy = busy;
                busy = false;
                pending = null;
                if (countdown != null) countdown.setVisibility(View.GONE);
                if (status != null && !camera.isReady()) {
                  status.setVisibility(View.VISIBLE);
                  status.setText(error);
                }
                if (wasBusy || camera.isReady()) message(error);
              }

              public void captured(byte[] jpeg, boolean mirrored) {
                if (pending == null) {
                  busy = false;
                  return;
                }
                CaptureSettings request = pending;
                request.processing = true;
                processPhoto(jpeg, mirrored, request);
              }
            });
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      status.setText(
          "Bắt đầu một khoảnh khắc\n\n"
              + "Chạm để cho phép dùng camera.\n"
              + "Ảnh được xử lý trên điện thoại của bạn.");
    } else if (resumed) camera.start();
  }

  private FrameLayout whiteAction(String icon, String label, Runnable action) {
    FrameLayout box = new FrameLayout(this);
    box.setContentDescription(label);
    box.setTag("camera-" + icon);
    box.setBackground(ripple(Color.TRANSPARENT, 28));
    boolean active =
        (icon.equals("flash") && flash)
            || (icon.equals("grid") && grid)
            || (icon.equals("timer") && timer > 0);
    box.addView(
        new IconView(this, icon, active ? Color.rgb(255, 213, 65) : Color.WHITE),
        new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
    if (icon.equals("timer") && timer > 0) {
      TextView badge = text(timer + "s", 10, Color.rgb(255, 213, 65));
      badge.setGravity(Gravity.CENTER);
      FrameLayout.LayoutParams badgeParams =
          new FrameLayout.LayoutParams(dp(24), dp(20), Gravity.BOTTOM | Gravity.END);
      badgeParams.rightMargin = dp(4);
      box.addView(badge, badgeParams);
    }
    box.setOnClickListener(
        v -> {
          if (!busy) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            action.run();
          }
        });
    return box;
  }

  private View cameraAction(String icon, String label, Runnable action) {
    FrameLayout box = whiteAction(icon, label, action);
    box.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
    return box;
  }

  private void updateCameraActions() {
    for (String icon : new String[] {"flash", "timer", "grid"}) {
      FrameLayout existing = body.findViewWithTag("camera-" + icon);
      if (existing == null) continue;
      String label =
          icon.equals("flash")
              ? (flash ? "Flash bật" : "Flash tắt")
              : icon.equals("timer")
                  ? (timer == 0 ? "Hẹn giờ" : timer + " giây")
                  : (grid ? "Lưới bật" : "Lưới tắt");
      FrameLayout fresh = whiteAction(icon, label, () -> {});
      existing.removeAllViews();
      existing.setContentDescription(label);
      while (fresh.getChildCount() > 0) {
        View child = fresh.getChildAt(0);
        fresh.removeViewAt(0);
        existing.addView(child);
      }
    }
  }

  private void requestCamera() {
    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
      if (camera != null) camera.start();
      return;
    }
    if (prefs.getBoolean("askedCamera", false)
        && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
      new AlertDialog.Builder(this)
          .setTitle("Cho phép dùng camera")
          .setMessage(
              "Mở Cài đặt → Quyền → Camera để Ed-Cam có thể chụp ảnh. Bạn vẫn có thể nhập ảnh để"
                  + " chỉnh.")
          .setPositiveButton(
              "Mở cài đặt",
              (d, w) ->
                  startActivity(
                      new Intent(
                          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                          Uri.parse("package:" + getPackageName()))))
          .setNegativeButton("Để sau", null)
          .show();
    } else {
      prefs.edit().putBoolean("askedCamera", true).apply();
      requestPermissions(new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }
  }

  private void populateStrip() {
    if (presetRow == null) return;
    presetRow.removeAllViews();
    int from = Math.max(0, (styleId / 6) * 6);
    for (int i = from; i < Math.min(from + 6, Presets.ALL.size()); i++) {
      Presets.Style style = Presets.get(i);
      LinearLayout tile = new LinearLayout(this);
      tile.setOrientation(LinearLayout.VERTICAL);
      tile.setGravity(Gravity.CENTER);
      tile.setBackground(ripple(style.id == styleId ? palette.line : palette.surface, 10));
      if (sample != null) {
        ImageView image = new ImageView(this);
        image.setImageBitmap(sample);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setColorFilter(
            new ColorMatrixColorFilter(new ColorMatrix(Presets.matrix(style, strength))));
        tile.addView(image, new LinearLayout.LayoutParams(dp(68), dp(30)));
      } else {
        View swatch = new View(this);
        swatch.setBackground(
            new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {swatch(style), palette.surface}));
        tile.addView(swatch, new LinearLayout.LayoutParams(dp(68), dp(24)));
      }
      TextView title = text(style.name, 11, palette.ink);
      title.setGravity(Gravity.CENTER);
      title.setMaxLines(1);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      tile.addView(title, new LinearLayout.LayoutParams(-1, dp(24)));
      tile.setContentDescription(style.name + (styleId == style.id ? ", đang chọn" : ""));
      tile.setOnClickListener(
          v -> {
            if (!busy) {
              styleId = style.id;
              remember();
              applyLive();
              populateStrip();
            }
          });
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(84), dp(60));
      p.rightMargin = dp(8);
      presetRow.addView(tile, p);
    }
  }

  private int swatch(Presets.Style style) {
    float[] m = Presets.matrix(style, 1);
    return Color.rgb(
        channel(m, 0, 176, 151, 112), channel(m, 5, 176, 151, 112), channel(m, 10, 176, 151, 112));
  }

  private int channel(float[] m, int o, int r, int g, int b) {
    return Math.max(
        0, Math.min(255, Math.round(m[o] * r + m[o + 1] * g + m[o + 2] * b + m[o + 4])));
  }

  private View strengthControl() {
    LinearLayout line = row();
    line.setPadding(dp(20), 0, dp(16), 0);
    TextView label = text("Cường độ", 12, palette.muted);
    line.addView(label);
    SeekBar slider = new SeekBar(this);
    slider.setMax(100);
    slider.setProgress(Math.round(strength * 100));
    slider.setProgressTintList(ColorStateList.valueOf(palette.accent));
    slider.setThumbTintList(ColorStateList.valueOf(palette.accent));
    slider.setContentDescription("Cường độ style");
    line.addView(slider, new LinearLayout.LayoutParams(0, dp(48), 1));
    TextView value = text(Math.round(strength * 100) + "%", 12, palette.ink);
    line.addView(value, new LinearLayout.LayoutParams(dp(38), -2));
    slider.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (user && !busy) {
              strength = p / 100f;
              value.setText(p + "%");
              applyLive();
            }
          }

          public void onStartTrackingTouch(SeekBar s) {}

          public void onStopTrackingTouch(SeekBar s) {
            remember();
            populateStrip();
          }
        });
    return line;
  }

  private void applyLive() {
    if (styleTitle != null) styleTitle.setText("ẢNH");
    if (camera != null) camera.color(Presets.get(styleId), strength);
    if (editor && editSource != null) {
      ImageView image = body.findViewWithTag("editorImage");
      if (image != null)
        image.setColorFilter(
            new ColorMatrixColorFilter(
                new ColorMatrix(Presets.matrix(Presets.get(styleId), strength))));
    }
  }

  private void beginCapture() {
    if (busy) return;
    if (camera == null || !camera.isReady()) {
      requestCamera();
      return;
    }
    busy = true;
    pending = snapshot("camera");
    tick(timer);
  }

  private void tick(int remaining) {
    if (!resumed || pending == null) {
      busy = false;
      return;
    }
    if (remaining == 0) {
      countdown.setVisibility(View.GONE);
      pending.time = System.currentTimeMillis();
      camera.capture();
      return;
    }
    countdown.setText(String.valueOf(remaining));
    countdown.setVisibility(View.VISIBLE);
    main.postDelayed(() -> tick(remaining - 1), 1000);
  }

  private static final class CaptureSettings {
    int style;
    float strength, ratio;
    boolean original, stamp, processing;
    long time;
    double lat = Double.NaN, lon = Double.NaN;
    float accuracy;
    String place, source;
  }

  private CaptureSettings snapshot(String source) {
    CaptureSettings s = new CaptureSettings();
    s.style = styleId;
    s.strength = strength;
    s.ratio = "import".equals(source) ? 0 : RATIOS[ratioId];
    s.original = keepOriginal;
    s.stamp = stampEnabled;
    s.time = System.currentTimeMillis();
    s.place = place;
    s.source = source;
    if ("import".equals(source) && editContext != null) {
      s.time = editContext.time;
      s.place = editContext.place;
      s.source = editContext.source;
      s.lat = editContext.lat;
      s.lon = editContext.lon;
      s.accuracy = editContext.accuracy;
      s.ratio = editContext.cropRatio;
    }
    if ("camera".equals(source) && locationEnabled && freshLocation()) {
      s.lat = location.getLatitude();
      s.lon = location.getLongitude();
      s.accuracy = location.getAccuracy();
    }
    return s;
  }

  private void processPhoto(byte[] bytes, boolean mirror, CaptureSettings s) {
    message("Đang tạo ảnh và lưu vào album…");
    worker.execute(
        () -> {
          Bitmap input = null, output = null;
          try {
            input = PhotoProcessor.decode(bytes);
            String stamp =
                s.stamp
                    ? date(s.time, "dd.MM.yyyy  HH:mm") + (s.place.isEmpty() ? "" : "\n" + s.place)
                    : "";
            if (s.stamp && !Double.isNaN(s.lat))
              stamp += "\n" + String.format(Locale.US, "%.4f, %.4f", s.lat, s.lon);
            output =
                PhotoProcessor.render(
                    input, Presets.get(s.style), s.strength, s.ratio, mirror, stamp);
            PhotoStore.Photo saved =
                store.save(
                    output,
                    s.original ? bytes : null,
                    Presets.get(s.style),
                    s.time,
                    s.place,
                    s.source,
                    s.lat,
                    s.lon,
                    s.accuracy,
                    mirror,
                    s.ratio,
                    s.strength);
            main.post(
                () -> {
                  if (destroyed) return;
                  busy = false;
                  pending = null;
                  if (editor) {
                    clearEditor();
                    detail = saved;
                    tab = 2;
                    render();
                  }
                  message("Đã lưu ảnh vào album Ed-Cam.");
                });
          } catch (Exception | OutOfMemoryError e) {
            main.post(
                () -> {
                  if (destroyed) return;
                  busy = false;
                  pending = null;
                  message("Không lưu được ảnh. Kiểm tra dung lượng trống rồi thử lại.");
                });
          } finally {
            if (input != null) input.recycle();
            if (output != null) output.recycle();
          }
        });
  }

  private void snapshotStylePreview() {
    if (texture != null && texture.isAvailable()) sample = texture.getBitmap(144, 192);
  }

  private void stylesScreen() {
    LinearLayout top = new LinearLayout(this);
    top.setOrientation(LinearLayout.VERTICAL);
    top.setPadding(dp(18), dp(8), dp(18), 0);
    title(top, "Bộ màu của bạn", "Chọn một sắc thái cho khoảnh khắc hôm nay.");
    EditText search = new EditText(this);
    search.setSingleLine(true);
    search.setTextSize(15);
    search.setTextColor(palette.ink);
    search.setHintTextColor(palette.muted);
    search.setHint("Tìm film, phố đêm, chân dung…");
    search.setContentDescription("Tìm kiếm style");
    search.setPadding(dp(14), dp(8), dp(14), dp(8));
    search.setBackground(round(palette.surface, 12));
    top.addView(search, new LinearLayout.LayoutParams(-1, dp(50)));
    search.setText(query);
    HorizontalScrollView categories = new HorizontalScrollView(this);
    categories.setHorizontalScrollBarEnabled(false);
    LinearLayout chips = row();
    Map<String, Button> buttons = new LinkedHashMap<>();
    List<String> groups = new ArrayList<>();
    groups.add("Yêu thích");
    groups.addAll(Arrays.asList(Presets.GROUPS));
    ListView list = new ListView(this);
    list.setBackgroundColor(Color.TRANSPARENT);
    list.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setDividerHeight(dp(8));
    list.setPadding(dp(18), dp(4), dp(18), dp(16));
    list.setClipToPadding(false);
    TextView count = text("", 12, palette.muted);
    count.setPadding(0, dp(2), 0, dp(10));
    StyleAdapter adapter = new StyleAdapter(count);
    list.setAdapter(adapter);
    for (String name : groups) {
      Button option =
          (Button)
              chip(
                  name,
                  group.equals(name),
                  () -> {
                    group = name;
                    for (Map.Entry<String, Button> entry : buttons.entrySet()) {
                      boolean selected = group.equals(entry.getKey());
                      entry.getValue().setTextColor(selected ? palette.onAccent : palette.accent);
                      entry
                          .getValue()
                          .setBackground(ripple(selected ? palette.accent : Color.TRANSPARENT, 12));
                    }
                    adapter.refresh();
                    list.setSelection(0);
                  });
      buttons.put(name, option);
      chips.addView(option);
    }
    categories.addView(chips);
    top.addView(categories, new LinearLayout.LayoutParams(-1, dp(62)));
    top.addView(count);
    body.addView(top);
    FrameLayout rows = new FrameLayout(this);
    rows.addView(list, new FrameLayout.LayoutParams(-1, -1));
    TextView empty =
        text(
            "Chưa có bộ màu phù hợp.\nThử nhóm khác hoặc thêm style vào yêu thích.",
            15,
            palette.muted);
    empty.setGravity(Gravity.CENTER);
    empty.setPadding(dp(30), 0, dp(30), 0);
    rows.addView(empty, new FrameLayout.LayoutParams(-1, -1));
    list.setEmptyView(empty);
    body.addView(rows, new LinearLayout.LayoutParams(-1, 0, 1));
    adapter.refresh();
    final long token = uiGeneration;
    search.addTextChangedListener(
        new android.text.TextWatcher() {
          private Runnable pending;

          public void beforeTextChanged(CharSequence s, int start, int before, int after) {}

          public void onTextChanged(CharSequence text, int start, int before, int after) {
            query = text.toString();
            if (pending != null) main.removeCallbacks(pending);
            pending =
                () -> {
                  if (token == uiGeneration) {
                    adapter.refresh();
                    list.setSelection(0);
                  }
                };
            main.postDelayed(pending, 90);
          }

          public void afterTextChanged(android.text.Editable editable) {}
        });
  }

  private final class StyleAdapter extends BaseAdapter {
    private final List<Presets.Style> visible = new ArrayList<>();
    private final String[] searchable = new String[Presets.ALL.size()];
    private final TextView count;

    StyleAdapter(TextView count) {
      this.count = count;
      for (Presets.Style style : Presets.ALL)
        searchable[style.id] = normalize(style.name + " " + style.group);
    }

    void refresh() {
      visible.clear();
      String term = normalize(query);
      for (Presets.Style style : Presets.ALL)
        if ((group.equals("Tất cả")
                || group.equals(style.group)
                || (group.equals("Yêu thích") && favorites.contains("" + style.id)))
            && searchable[style.id].contains(term)) visible.add(style);
      count.setText(visible.size() + " bộ màu · Giữ ảnh gốc, thử màu thoải mái");
      notifyDataSetChanged();
    }

    @Override
    public int getCount() {
      return visible.size();
    }

    @Override
    public Presets.Style getItem(int position) {
      return visible.get(position);
    }

    @Override
    public long getItemId(int position) {
      return getItem(position).id;
    }

    @Override
    public boolean hasStableIds() {
      return true;
    }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
      StyleRow holder;
      if (recycled == null) {
        LinearLayout row = row();
        row.setPadding(dp(10), dp(10), dp(8), dp(10));
        ImageView preview = new ImageView(MainActivity.this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(round(palette.line, 10));
        preview.setClipToOutline(true);
        row.addView(preview, new LinearLayout.LayoutParams(dp(64), dp(72)));
        LinearLayout words = new LinearLayout(MainActivity.this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(14), 0, dp(4), 0);
        TextView name = text("", 16, palette.ink);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView category = text("", 12, palette.muted);
        category.setPadding(0, dp(4), 0, 0);
        words.addView(name);
        words.addView(category);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout heart = new FrameLayout(MainActivity.this);
        row.addView(heart, new LinearLayout.LayoutParams(dp(48), dp(48)));
        holder = new StyleRow(row, preview, name, category, heart);
        row.setTag(holder);
        recycled = row;
      } else holder = (StyleRow) recycled.getTag();
      Presets.Style style = getItem(position);
      boolean selected = style.id == styleId, starred = favorites.contains("" + style.id);
      holder.row.setBackground(ripple(selected ? palette.line : palette.surface, 14));
      holder.name.setText(style.name);
      holder.category.setText(style.group + (selected ? " · Đang dùng" : ""));
      if (sample != null) {
        holder.preview.setImageBitmap(sample);
        holder.preview.setColorFilter(
            new ColorMatrixColorFilter(new ColorMatrix(Presets.matrix(style, strength))));
        holder.preview.setContentDescription("Xem màu " + style.name + " trên khung ngắm gần nhất");
      } else {
        holder.preview.clearColorFilter();
        holder.preview.setImageDrawable(
            new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[] {swatch(style), palette.surface}));
        holder.preview.setContentDescription("Mẫu sắc độ " + style.name);
      }
      holder.heart.removeAllViews();
      holder.heart.addView(
          new IconView(MainActivity.this, "heart", starred ? palette.accent : palette.muted),
          new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
      holder.heart.setBackground(ripple(starred ? palette.line : Color.TRANSPARENT, 24));
      holder.heart.setContentDescription((starred ? "Bỏ yêu thích " : "Yêu thích ") + style.name);
      holder.heart.setOnClickListener(
          v -> {
            String id = "" + style.id;
            if (!favorites.add(id)) favorites.remove(id);
            remember();
            refresh();
          });
      holder.row.setOnClickListener(
          v -> {
            styleId = style.id;
            query = "";
            tab = 0;
            render();
            message("Đang dùng " + style.name);
          });
      return recycled;
    }
  }

  private static final class StyleRow {
    final LinearLayout row;
    final ImageView preview;
    final TextView name, category;
    final FrameLayout heart;

    StyleRow(
        LinearLayout row, ImageView preview, TextView name, TextView category, FrameLayout heart) {
      this.row = row;
      this.preview = preview;
      this.name = name;
      this.category = category;
      this.heart = heart;
    }
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
        .toLowerCase(Locale.ROOT);
  }

  private void albumScreen() {
    ScrollView scroll = scrollBody();
    LinearLayout page = page(scroll);
    title(page, "Album của bạn", "Những ngày thường, đáng để giữ lại.");
    Button add = button("Nhập ảnh để chỉnh", () -> importPhoto(), true);
    page.addView(add, new LinearLayout.LayoutParams(-1, dp(52)));
    HorizontalScrollView filters = new HorizontalScrollView(this);
    filters.setHorizontalScrollBarEnabled(false);
    LinearLayout filterRow = row();
    filterRow.addView(
        chip(
            albumDay.isEmpty() ? "Chọn ngày" : albumDay,
            !albumDay.isEmpty(),
            () -> {
              Calendar today = Calendar.getInstance();
              new DatePickerDialog(
                      this,
                      (picker, year, month, day) -> {
                        albumDay = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                        albumLimit = 40;
                        render();
                      },
                      today.get(Calendar.YEAR),
                      today.get(Calendar.MONTH),
                      today.get(Calendar.DAY_OF_MONTH))
                  .show();
            }));
    filterRow.addView(
        chip(
            "Có vị trí",
            albumWithLocation,
            () -> {
              albumWithLocation = !albumWithLocation;
              albumLimit = 40;
              render();
            }));
    if (!albumDay.isEmpty() || albumWithLocation)
      filterRow.addView(
          chip(
              "Xóa bộ lọc",
              false,
              () -> {
                albumDay = "";
                albumWithLocation = false;
                albumLimit = 40;
                render();
              }));
    filters.addView(filterRow);
    page.addView(filters);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    page.addView(list);
    TextView loading = text("Đang đọc album…", 14, palette.muted);
    list.addView(loading);
    long token = uiGeneration;
    worker.execute(
        () -> {
          try {
            List<PhotoStore.Photo> photos = store.list();
            photos.removeIf(
                photo ->
                    (!albumDay.isEmpty() && !date(photo.time, "yyyy-MM-dd").equals(albumDay))
                        || (albumWithLocation && Double.isNaN(photo.lat)));
            main.post(
                () -> {
                  if (token != uiGeneration || destroyed) return;
                  list.removeAllViews();
                  if (photos.isEmpty()) {
                    empty(
                        list,
                        "Chưa có ảnh phù hợp",
                        "Chụp tấm đầu tiên, nhập ảnh có sẵn hoặc thử xóa bộ lọc ngày và vị trí.");
                    list.addView(
                        button(
                            "Mở camera",
                            () -> {
                              tab = 0;
                              render();
                            },
                            true));
                    return;
                  }
                  TextView count = text(photos.size() + " ảnh · Mới nhất trước", 13, palette.muted);
                  count.setPadding(0, dp(18), 0, dp(12));
                  list.addView(count);
                  int shown = Math.min(albumLimit, photos.size());
                  for (int i = 0; i < shown; i += 2) {
                    LinearLayout row = row();
                    for (int j = i; j < Math.min(i + 2, shown); j++)
                      albumTile(row, photos.get(j), token);
                    if (i + 1 >= shown)
                      row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
                    list.addView(row);
                  }
                  if (shown < photos.size())
                    list.addView(
                        button(
                            "Xem thêm 40 ảnh",
                            () -> {
                              albumLimit += 40;
                              render();
                            },
                            false));
                });
          } catch (IOException e) {
            main.post(
                () -> {
                  if (token == uiGeneration) {
                    list.removeAllViews();
                    empty(list, "Không đọc được album", "Hãy đóng và mở lại ứng dụng.");
                  }
                });
          }
        });
  }

  private void albumTile(LinearLayout row, PhotoStore.Photo photo, long token) {
    LinearLayout tile = new LinearLayout(this);
    tile.setOrientation(LinearLayout.VERTICAL);
    tile.setPadding(dp(4), dp(4), dp(4), dp(12));
    ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setBackground(round(palette.line, 12));
    image.setClipToOutline(true);
    image.setContentDescription("Ảnh " + photo.style + ", " + date(photo.time, "dd/MM/yyyy HH:mm"));
    tile.addView(image, new LinearLayout.LayoutParams(-1, dp(178)));
    TextView name = text(photo.style, 14, palette.ink);
    name.setPadding(dp(3), dp(8), 0, 0);
    tile.addView(name);
    tile.addView(text(date(photo.time, "dd.MM.yyyy · HH:mm"), 11, palette.muted));
    tile.setOnClickListener(
        v -> {
          detail = photo;
          render();
        });
    row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
    worker.execute(
        () -> {
          if (token != uiGeneration || destroyed) return;
          String cacheKey = photo.image.getPath();
          Bitmap cached = thumbnails.get(cacheKey);
          Bitmap bitmap = cached != null ? cached : PhotoStore.thumbnail(photo.image, 360);
          if (bitmap != null && cached == null) thumbnails.put(cacheKey, bitmap);
          main.post(
              () -> {
                if (token == uiGeneration && !destroyed) image.setImageBitmap(bitmap);
                // Cached thumbnails may still be used by a subsequent album view.
              });
        });
  }

  private void detailScreen() {
    PhotoStore.Photo photo = detail;
    ScrollView scroll = scrollBody();
    LinearLayout page = page(scroll);
    ImageView image = new ImageView(this);
    image.setAdjustViewBounds(true);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setBackground(round(palette.line, 14));
    image.setClipToOutline(true);
    image.setContentDescription("Ảnh " + photo.style);
    page.addView(image, new LinearLayout.LayoutParams(-1, dp(380)));
    long token = uiGeneration;
    worker.execute(
        () -> {
          Bitmap bitmap = PhotoStore.thumbnail(photo.image, 1400);
          main.post(
              () -> {
                if (token == uiGeneration && !destroyed) image.setImageBitmap(bitmap);
                else if (bitmap != null) bitmap.recycle();
              });
        });
    title(page, photo.style, date(photo.time, "dd MMMM yyyy · HH:mm"));
    page.addView(
        text(
            photo.width
                + " × "
                + photo.height
                + " px · "
                + ("import".equals(photo.source)
                    ? "Ảnh nhập · thời gian chỉnh"
                    : "Ảnh chụp bằng Ed-Cam"),
            13,
            palette.muted));
    if (!photo.place.isEmpty()) paragraph(page, photo.place);
    if (!Double.isNaN(photo.lat)) {
      paragraph(
          page,
          String.format(
              Locale.US,
              "Vị trí: %.5f, %.5f · sai số khoảng %.0f m",
              photo.lat,
              photo.lon,
              photo.accuracy));
      page.addView(
          button(
              "Xem vị trí trên bản đồ",
              () -> {
                try {
                  startActivity(
                      new Intent(
                          Intent.ACTION_VIEW,
                          Uri.parse(
                              String.format(
                                  Locale.US,
                                  "geo:%.6f,%.6f?q=%.6f,%.6f",
                                  photo.lat,
                                  photo.lon,
                                  photo.lat,
                                  photo.lon))));
                } catch (ActivityNotFoundException e) {
                  message("Máy chưa có ứng dụng bản đồ.");
                }
              },
              false));
    }
    page.addView(
        button("Lưu ảnh vào thư viện máy", () -> exportPhoto(photo, false), true),
        new LinearLayout.LayoutParams(-1, dp(54)));
    if (photo.hasOriginal) {
      page.addView(button("Chỉnh lại từ ảnh gốc", () -> loadEditor(photo), false));
      page.addView(button("Lưu bản gốc vào thư viện máy", () -> exportPhoto(photo, true), false));
    }
    paragraph(
        page,
        "Album Ed-Cam nằm trong ứng dụng. Hãy lưu những ảnh muốn giữ lâu dài vào thư viện máy trước"
            + " khi gỡ app. Bản gốc có thể chứa vị trí EXIF từ ảnh nhập.");
    page.addView(
        button(
            "Xóa khỏi album Ed-Cam",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Xóa khoảnh khắc này?")
                    .setMessage(
                        "Xóa ảnh và bản gốc trong Ed-Cam. Ảnh đã xuất ra thư viện máy vẫn được"
                            + " giữ.")
                    .setNegativeButton("Giữ lại", null)
                    .setPositiveButton("Xóa ảnh", (d, w) -> deletePhoto(photo))
                    .show(),
            false));
  }

  private void exportPhoto(PhotoStore.Photo photo, boolean original) {
    if (busy) return;
    busy = true;
    message("Đang lưu vào Pictures/Ed-Cam…");
    worker.execute(
        () -> {
          try {
            store.export(photo, original);
            main.post(
                () -> {
                  busy = false;
                  if (!destroyed) message("Đã lưu vào thư viện máy · Pictures/Ed-Cam.");
                });
          } catch (Exception e) {
            main.post(
                () -> {
                  busy = false;
                  if (!destroyed) message("Không xuất được ảnh. Kiểm tra dung lượng và thử lại.");
                });
          }
        });
  }

  private void deletePhoto(PhotoStore.Photo photo) {
    busy = true;
    worker.execute(
        () -> {
          try {
            store.delete(photo);
            main.post(
                () -> {
                  busy = false;
                  if (!destroyed) {
                    detail = null;
                    render();
                    message("Đã xóa khỏi album Ed-Cam.");
                  }
                });
          } catch (IOException e) {
            main.post(
                () -> {
                  busy = false;
                  if (!destroyed) {
                    detail = null;
                    render();
                    message("Chưa xóa được toàn bộ tệp. Hãy thử lại sau.");
                  }
                });
          }
        });
  }

  private void importPhoto() {
    if (busy) return;
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.setType("image/*");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    try {
      startActivityForResult(intent, IMPORT_PHOTO);
    } catch (ActivityNotFoundException e) {
      message("Thiết bị không có trình chọn ảnh.");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == IMPORT_PHOTO && result == RESULT_OK && data != null && data.getData() != null)
      loadEditor(data.getData());
  }

  private void loadEditor(PhotoStore.Photo photo) {
    loadEditorSource(() -> new FileInputStream(photo.original), photo);
  }

  private void loadEditor(Uri uri) {
    loadEditorSource(() -> getContentResolver().openInputStream(uri), null);
  }

  private interface InputSource {
    InputStream open() throws IOException;
  }

  private void loadEditorSource(InputSource source, PhotoStore.Photo context) {
    if (busy) return;
    busy = true;
    message("Đang đọc ảnh…");
    worker.execute(
        () -> {
          try (InputStream in = source.open()) {
            if (in == null) throw new IOException("Không mở được ảnh.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int length, total = 0;
            while ((length = in.read(buffer)) != -1) {
              total += length;
              if (total > 40 * 1024 * 1024) throw new IOException("Ảnh vượt quá 40 MB.");
              bytes.write(buffer, 0, length);
            }
            byte[] raw = bytes.toByteArray();
            Bitmap decoded = PhotoProcessor.decode(raw);
            Bitmap bitmap =
                context == null
                    ? decoded
                    : PhotoProcessor.render(
                        decoded, Presets.get(0), 0, context.cropRatio, context.mirrored, "");
            if (bitmap != decoded) decoded.recycle();
            main.post(
                () -> {
                  busy = false;
                  if (destroyed) {
                    bitmap.recycle();
                    return;
                  }
                  clearEditor();
                  editContext = context;
                  if (context != null) {
                    styleId = context.styleId;
                    strength = context.strength;
                  }
                  editBytes = raw;
                  editSource = bitmap;
                  editor = true;
                  detail = null;
                  render();
                });
          } catch (Exception | OutOfMemoryError e) {
            main.post(
                () -> {
                  busy = false;
                  if (!destroyed) message("Không đọc được ảnh. Hãy chọn ảnh JPEG/PNG dưới 40 MB.");
                });
          }
        });
  }

  private void editorScreen() {
    ImageView image = new ImageView(this);
    image.setTag("editorImage");
    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
    image.setImageBitmap(editSource);
    image.setBackgroundColor(Color.rgb(24, 27, 26));
    image.setContentDescription(
        "Ảnh đang chỉnh. Màu xem trước; hạt và tối góc sẽ được thêm khi lưu.");
    LinearLayout.LayoutParams preview = new LinearLayout.LayoutParams(-1, 0, 1);
    preview.setMargins(dp(14), dp(4), dp(14), dp(8));
    body.addView(image, preview);
    TextView caption =
        text("Màu xem trực tiếp · hạt và tối góc được thêm khi lưu", 11, palette.muted);
    caption.setGravity(Gravity.CENTER);
    body.addView(caption);
    Spinner selector = new Spinner(this);
    String[] names = new String[Presets.ALL.size()];
    for (int i = 0; i < names.length; i++)
      names[i] = Presets.get(i).group + " · " + Presets.get(i).name;
    ArrayAdapter<String> adapter =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
    selector.setAdapter(adapter);
    selector.setSelection(styleId);
    selector.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onItemSelected(
              android.widget.AdapterView<?> parent, View view, int position, long id) {
            styleId = position;
            if (view instanceof TextView) ((TextView) view).setTextColor(palette.ink);
            applyLive();
            remember();
          }

          public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    body.addView(selector, new LinearLayout.LayoutParams(-1, dp(54)));
    body.addView(strengthControl());
    LinearLayout actions = row();
    actions.setPadding(dp(14), dp(8), dp(14), dp(14));
    actions.addView(
        button("Hủy", () -> closeEditor(), false), new LinearLayout.LayoutParams(0, dp(54), 1));
    actions.addView(
        button(
            "Lưu bản chỉnh",
            () -> {
              if (!busy && editBytes != null) {
                busy = true;
                CaptureSettings s = snapshot("import");
                s.processing = true;
                processPhoto(editBytes, editContext != null && editContext.mirrored, s);
              }
            },
            true),
        new LinearLayout.LayoutParams(0, dp(54), 2));
    body.addView(actions);
    applyLive();
  }

  private void closeEditor() {
    if (busy) return;
    clearEditor();
    render();
  }

  private void clearEditor() {
    editContext = null;
    editor = false;
    editBytes = null;
    if (editSource != null) {
      editSource.recycle();
      editSource = null;
    }
  }

  private void settingsScreen() {
    ScrollView scroll = scrollBody();
    LinearLayout page = page(scroll);
    title(page, "Theo cách của bạn", "Giao diện, dấu ảnh và quyền riêng tư.");
    section(page, "Giao diện");
    for (int i = 0; i < Palette.ALL.length; i++) {
      final int index = i;
      Palette p = Palette.ALL[i];
      LinearLayout line = row();
      line.setPadding(dp(12), dp(9), dp(8), dp(9));
      line.setBackground(ripple(palette.surface, 12));
      View color = new View(this);
      color.setBackground(round(p.accent, 18));
      line.addView(color, new LinearLayout.LayoutParams(dp(34), dp(34)));
      LinearLayout text = new LinearLayout(this);
      text.setOrientation(LinearLayout.VERTICAL);
      text.setPadding(dp(14), 0, 0, 0);
      text.addView(text(p.name, 16, palette.ink));
      text.addView(text(p.description, 12, palette.muted));
      line.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      if (i == themeId)
        line.addView(
            new IconView(this, "check", palette.accent),
            new LinearLayout.LayoutParams(dp(24), dp(24)));
      line.setContentDescription("Giao diện " + p.name + (i == themeId ? ", đang dùng" : ""));
      line.setOnClickListener(
          v -> {
            themeId = index;
            render();
          });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.bottomMargin = dp(7);
      page.addView(line, lp);
    }
    section(page, "Khi lưu ảnh");
    toggle(
        page,
        "Giữ bản gốc",
        "Lưu thêm ảnh chưa áp dụng style để chỉnh lại sau.",
        keepOriginal,
        value -> {
          keepOriginal = value;
          remember();
        });
    toggle(
        page,
        "In ngày giờ lên ảnh",
        "Dùng thời gian chụp; ảnh nhập dùng thời gian chỉnh.",
        stampEnabled,
        value -> {
          stampEnabled = value;
          remember();
        });
    toggle(
        page,
        "Ghi vị trí chụp",
        "Tùy chọn. Chỉ ghi khi đã có vị trí mới; có thể dùng vị trí gần đúng.",
        locationEnabled,
        value -> {
          locationEnabled = value;
          remember();
          if (value) requestLocation();
          else {
            stopLocation();
            location = null;
          }
        });
    EditText label = new EditText(this);
    label.setText(place);
    label.setTextColor(palette.ink);
    label.setHintTextColor(palette.muted);
    label.setHint("Tên nơi chụp do bạn nhập (tùy chọn)");
    label.setTextSize(14);
    label.setMaxLines(2);
    label.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(60)});
    label.setContentDescription("Tên nơi chụp do bạn tự nhập");
    label.setPadding(dp(12), dp(10), dp(12), dp(10));
    label.setBackground(round(palette.surface, 10));
    page.addView(label, new LinearLayout.LayoutParams(-1, -2));
    label.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            place = s.toString().trim();
            remember();
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    section(page, "Riêng tư từ đầu");
    paragraph(
        page,
        "Không tài khoản. Không quảng cáo. Không gửi ảnh lên máy chủ. Ed-Cam không yêu cầu quyền"
            + " Internet.");
    paragraph(
        page,
        "Ảnh trong album sẽ mất khi gỡ ứng dụng hoặc xóa dữ liệu app. Dùng nút lưu vào thư viện máy"
            + " để giữ bản sao riêng. Tắt vị trí chỉ áp dụng cho ảnh chụp tiếp theo.");
    section(page, "Về Ed-Cam");
    paragraph(
        page,
        "Phiên bản 1.1.0 · 60 preset màu · 5 giao diện\n"
            + "Ảnh đầu ra tối đa 2560 px ở cạnh dài. Màu hiển thị trực tiếp; hạt film và tối góc"
            + " được thêm khi lưu. Chất ảnh phụ thuộc camera và ánh sáng.");
  }

  private interface ToggleAction {
    void changed(boolean value);
  }

  private void toggle(
      LinearLayout page, String name, String description, boolean checked, ToggleAction action) {
    LinearLayout row = row();
    row.setPadding(0, dp(10), 0, dp(10));
    LinearLayout words = new LinearLayout(this);
    words.setOrientation(LinearLayout.VERTICAL);
    words.addView(text(name, 16, palette.ink));
    TextView desc = text(description, 12, palette.muted);
    desc.setPadding(0, dp(4), dp(12), 0);
    words.addView(desc);
    row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
    Switch toggle = new Switch(this);
    toggle.setChecked(checked);
    toggle.setContentDescription(name);
    toggle.setMinimumWidth(dp(54));
    toggle.setMinimumHeight(dp(48));
    toggle.setThumbTintList(
        new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {palette.accent, palette.muted}));
    toggle.setOnCheckedChangeListener((b, value) -> action.changed(value));
    row.addView(toggle);
    page.addView(row);
  }

  private void themeDialog() {
    String[] names = new String[Palette.ALL.length];
    for (int i = 0; i < names.length; i++) names[i] = Palette.ALL[i].name;
    new AlertDialog.Builder(this)
        .setTitle("Giao diện Ed-Cam")
        .setSingleChoiceItems(
            names,
            themeId,
            (dialog, index) -> {
              themeId = index;
              dialog.dismiss();
              render();
            })
        .setNegativeButton("Đóng", null)
        .show();
  }

  private boolean freshLocation() {
    return location != null
        && (SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos())
            < 120_000_000_000L;
  }

  private String previewStamp() {
    if (!stampEnabled && !locationEnabled) return "Chạm khung ngắm để lấy nét";
    String result = stampEnabled ? date(System.currentTimeMillis(), "dd.MM.yyyy  HH:mm") : "";
    if (!place.isEmpty()) result += (result.isEmpty() ? "" : "\n") + place;
    if (locationEnabled)
      result +=
          (result.isEmpty() ? "" : "\n")
              + (freshLocation()
                  ? String.format(
                      Locale.US, "%.4f, %.4f", location.getLatitude(), location.getLongitude())
                  : "Đang chờ vị trí · không ghi vị trí khi chưa có");
    return result;
  }

  private void requestLocation() {
    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        == PackageManager.PERMISSION_GRANTED) {
      startLocation();
      return;
    }
    requestPermissions(
        new String[] {
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        },
        LOCATION_PERMISSION);
  }

  private void startLocation() {
    if (!resumed || !locationEnabled || locationListener != null) return;
    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      locationEnabled = false;
      remember();
      return;
    }
    try {
      locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
      locationListener =
          new LocationListener() {
            @Override
            public void onLocationChanged(Location value) {
              location = value;
              if (stampView != null) stampView.setText(previewStamp());
            }

            @Override
            public void onProviderDisabled(String provider) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
          };
      boolean any = false;
      boolean fine =
          checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
              == PackageManager.PERMISSION_GRANTED;
      for (String provider : locationManager.getProviders(true)) {
        if (LocationManager.PASSIVE_PROVIDER.equals(provider)
            || (!fine && LocationManager.GPS_PROVIDER.equals(provider))) continue;
        try {
          locationManager.requestLocationUpdates(
              provider, 15000, 10, locationListener, Looper.getMainLooper());
          any = true;
        } catch (IllegalArgumentException ignored) {
          /* Provider may disappear between enumeration and request. */
        }
      }
      if (!any) {
        stopLocation();
        message("Định vị đang tắt. Bạn có thể bật trong cài đặt điện thoại.");
      }
    } catch (SecurityException e) {
      stopLocation();
      locationEnabled = false;
      remember();
      message("Chưa được cấp quyền vị trí. Camera vẫn dùng bình thường.");
    }
  }

  private void stopLocation() {
    if (locationManager != null && locationListener != null)
      locationManager.removeUpdates(locationListener);
    locationListener = null;
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request == CAMERA_PERMISSION) {
      if (camera != null
          && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        camera.start();
      else if (status != null)
        status.setText(
            "Chưa có quyền camera\n\n"
                + "Chạm để thử cấp quyền lại.\n"
                + "Bạn vẫn có thể nhập ảnh bằng nút +.");
    } else if (request == LOCATION_PERMISSION) {
      locationEnabled =
          checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
              == PackageManager.PERMISSION_GRANTED;
      remember();
      if (locationEnabled) startLocation();
      if (tab == 3) render();
      message(
          locationEnabled
              ? "Đã bật vị trí. Chờ nhận tín hiệu định vị."
              : "Không bật vị trí. Bạn vẫn chụp ảnh bình thường.");
    }
  }

  private String date(long time, String pattern) {
    return new SimpleDateFormat(pattern, new Locale("vi", "VN")).format(new Date(time));
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private TextView text(String value, float size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    t.setIncludeFontPadding(false);
    return t;
  }

  private GradientDrawable round(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private Drawable ripple(int color, int radius) {
    return new RippleDrawable(
        ColorStateList.valueOf(palette.dark ? 0x33ffffff : 0x22000000),
        round(color, radius),
        round(Color.WHITE, radius));
  }

  private FrameLayout iconButton(String icon, String label, Runnable action, boolean active) {
    FrameLayout box = new FrameLayout(this);
    box.setMinimumWidth(dp(48));
    box.setMinimumHeight(dp(48));
    box.setContentDescription(label);
    box.setBackground(ripple(active ? palette.line : Color.TRANSPARENT, 24));
    IconView image = new IconView(this, icon, active ? palette.accent : palette.ink);
    box.addView(image, new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER));
    box.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
    box.setOnClickListener(
        v -> {
          v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
          action.run();
        });
    return box;
  }

  private View tool(String icon, String label, Runnable action) {
    LinearLayout item = new LinearLayout(this);
    item.setOrientation(LinearLayout.VERTICAL);
    item.setGravity(Gravity.CENTER);
    IconView image = new IconView(this, icon, palette.ink);
    item.addView(image, new LinearLayout.LayoutParams(dp(19), dp(19)));
    TextView caption = text(label, 11, palette.muted);
    caption.setGravity(Gravity.CENTER);
    caption.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
    caption.setMaxLines(1);
    caption.setPadding(0, dp(3), 0, 0);
    item.addView(caption);
    item.setContentDescription(label);
    item.setBackground(ripple(Color.TRANSPARENT, 12));
    item.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    item.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
    return item;
  }

  private Button button(String label, Runnable action, boolean primary) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(14);
    b.setAllCaps(false);
    b.setTextColor(primary ? palette.onAccent : palette.accent);
    b.setBackground(ripple(primary ? palette.accent : Color.TRANSPARENT, 12));
    b.setPadding(dp(12), dp(8), dp(12), dp(8));
    b.setMinHeight(dp(48));
    b.setMinimumHeight(dp(48));
    b.setStateListAnimator(null);
    b.setOnClickListener(
        v -> {
          v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
          action.run();
        });
    return b;
  }

  private View chip(String label, boolean selected, Runnable action) {
    Button b = button(label, action, selected);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(48));
    lp.setMargins(0, dp(7), dp(8), dp(7));
    b.setLayoutParams(lp);
    return b;
  }

  private ScrollView scrollBody() {
    ScrollView s = new ScrollView(this);
    s.setFillViewport(true);
    s.setClipToPadding(false);
    body.addView(s, new LinearLayout.LayoutParams(-1, -1));
    return s;
  }

  private LinearLayout page(ScrollView scroll) {
    LinearLayout p = new LinearLayout(this);
    p.setOrientation(LinearLayout.VERTICAL);
    p.setPadding(dp(18), dp(12), dp(18), dp(28));
    scroll.addView(p);
    return p;
  }

  private void title(LinearLayout page, String heading, String subtitle) {
    TextView h = text(heading, 23, palette.ink);
    h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    h.setPadding(0, dp(8), 0, dp(8));
    page.addView(h);
    TextView sub = text(subtitle, 14, palette.muted);
    sub.setPadding(0, 0, 0, dp(20));
    page.addView(sub);
  }

  private void section(LinearLayout page, String heading) {
    TextView h = text(heading, 19, palette.ink);
    h.setTypeface(Typeface.DEFAULT_BOLD);
    h.setPadding(0, dp(25), 0, dp(12));
    page.addView(h);
  }

  private void paragraph(LinearLayout page, String value) {
    TextView t = text(value, 14, palette.muted);
    t.setLineSpacing(dp(4), 1);
    t.setPadding(0, dp(10), 0, dp(12));
    page.addView(t);
  }

  private void empty(LinearLayout page, String heading, String description) {
    LinearLayout e = new LinearLayout(this);
    e.setOrientation(LinearLayout.VERTICAL);
    e.setPadding(dp(10), dp(42), dp(10), dp(30));
    TextView h = text(heading, 21, palette.ink);
    h.setGravity(Gravity.CENTER);
    e.addView(h);
    TextView p = text(description, 14, palette.muted);
    p.setGravity(Gravity.CENTER);
    p.setLineSpacing(dp(4), 1);
    p.setPadding(0, dp(12), 0, 0);
    e.addView(p);
    page.addView(e);
  }

  private void message(String message) {
    if (destroyed || notice == null) return;
    notice.setText(message);
    notice.setVisibility(View.VISIBLE);
    notice.bringToFront();
    notice.removeCallbacks(hideNotice);
    notice.postDelayed(hideNotice, 5500);
  }

  private final Runnable hideNotice =
      () -> {
        if (notice != null) notice.setVisibility(View.GONE);
      };

  private final class GridOverlay extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float fx = -1, fy;

    GridOverlay(Context context) {
      super(context);
      setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      setWillNotDraw(false);
    }

    void focus(float x, float y) {
      fx = x;
      fy = y;
      setVisibility(View.VISIBLE);
      invalidate();
      main.postDelayed(
          () -> {
            fx = -1;
            setVisibility(grid ? View.VISIBLE : View.GONE);
            invalidate();
          },
          1500);
    }

    @Override
    protected void onDraw(Canvas canvas) {
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(dp(1));
      if (grid) {
        p.setColor(0x66ffffff);
        for (int i = 1; i < 3; i++) {
          canvas.drawLine(getWidth() * i / 3f, 0, getWidth() * i / 3f, getHeight(), p);
          canvas.drawLine(0, getHeight() * i / 3f, getWidth(), getHeight() * i / 3f, p);
        }
      }
      if (fx >= 0) {
        p.setColor(0xffffffaa);
        canvas.drawRoundRect(fx - dp(24), fy - dp(24), fx + dp(24), fy + dp(24), dp(6), dp(6), p);
      }
    }
  }
}
