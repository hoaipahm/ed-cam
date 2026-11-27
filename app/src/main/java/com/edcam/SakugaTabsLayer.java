package com.edcam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Local-only transparent web controls; touches outside the tab hitbox reach native camera/UI. */
public final class SakugaTabsLayer extends WebView {
  public interface Listener {
    void selected(String mode, String value, int index);
  }

  private static final String PAGE = "file:///android_asset/sakuga.html";
  private final RectF hitbox = new RectF();
  private final Listener listener;
  private View anchor, nativeRoot;
  private final Map<Integer, View> controls = new HashMap<>();
  private final List<RectF> controlHitboxes = new ArrayList<>();
  private String lastLayout = "";
  private final ViewTreeObserver.OnPreDrawListener layoutObserver =
      () -> {
        place();
        return true;
      };
  private String mode = "primary";
  private Palette palette;
  private int selected, epoch, idleToken;
  private boolean loaded, tracking, disposed, scrolling, toastActive;
  private float lastX, lastY, downX, downY;
  private View scrollTarget;
  private final RectF toastHitbox = new RectF();

  private void scheduleRest() {
    int token = ++idleToken;
    if (loaded && !disposed)
      evaluateJavascript(
          "window.SakugaFX&&SakugaFX.later("
              + (toastActive ? 7500 : 1800)
              + ",()=>AndroidTabs.rest("
              + token
              + "))",
          null);
  }

  // Let the original animation settle, then stop the idle WebView clock. No effect code is changed.
  @Override
  public void onResume() {
    if (disposed) return;
    resumeTimers();
    super.onResume();
    scheduleRest();
  }

  @Override
  public void onPause() {
    idleToken++;
    super.onPause();
    pauseTimers();
  }

  @SuppressLint("SetJavaScriptEnabled")
  public SakugaTabsLayer(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    setBackgroundColor(Color.TRANSPARENT);
    setOverScrollMode(OVER_SCROLL_NEVER);
    WebView.setWebContentsDebuggingEnabled(
        (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
            != 0);
    setVerticalScrollBarEnabled(false);
    setHorizontalScrollBarEnabled(false);
    WebSettings settings = getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(false);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(false);
    settings.setAllowFileAccessFromFileURLs(false);
    settings.setAllowUniversalAccessFromFileURLs(false);
    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    addJavascriptInterface(new Bridge(), "AndroidTabs");
    setWebChromeClient(
        new WebChromeClient() {
          @Override
          public boolean onConsoleMessage(ConsoleMessage message) {
            if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
              android.util.Log.e("EdCamTabs", message.message());
            return true;
          }
        });
    setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true;
          }

          @Override
          public WebResourceResponse shouldInterceptRequest(
              WebView view, WebResourceRequest request) {
            if (!request.getUrl().toString().startsWith("file:///android_asset/"))
              return new WebResourceResponse(
                  "text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
            return null;
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            if (PAGE.equals(url)) {
              loaded = true;
              place();
            }
          }

          @Override
          public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            detachObserver();
            loaded = false;
            disposed = true;
            restoreNative();
            if (getParent() instanceof ViewGroup)
              ((ViewGroup) getParent()).removeView(SakugaTabsLayer.this);
            view.destroy();
            return true;
          }
        });
    loadUrl(PAGE);
  }

  public void target(View anchor, String mode, int selected, Palette palette) {
    target(anchor, mode, selected, palette, anchor == null ? null : anchor.getRootView());
  }

  public void target(View anchor, String mode, int selected, Palette palette, View nativeRoot) {
    detachObserver();
    restoreNative();
    controls.clear();
    controlHitboxes.clear();
    this.nativeRoot = nativeRoot;
    if (nativeRoot != null) nativeRoot.getViewTreeObserver().addOnPreDrawListener(layoutObserver);
    lastLayout = "";
    this.anchor = anchor;
    this.mode = mode;
    this.selected = selected;
    this.palette = palette;
    epoch++;
    hitbox.setEmpty();
    getSettings().setTextZoom(Math.round(getResources().getConfiguration().fontScale * 100));
    setVisibility(VISIBLE);
    onResume();
    post(this::place);
  }

  public void synchronizeSelection(int selected) {
    this.selected = selected;
    place();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    post(this::place);
  }

  private void place() {
    if (!loaded || disposed || nativeRoot == null || getWidth() == 0 || palette == null) return;
    int[] point = new int[2], origin = new int[2];
    if (anchor != null) anchor.getLocationInWindow(point);
    getLocationInWindow(origin);
    try {
      JSONObject config = new JSONObject();
      config.put("mode", anchor == null ? "none" : mode);
      config.put("selected", selected);
      config.put("epoch", epoch);
      config.put("viewport", getWidth());
      config.put(
          "rect",
          new JSONArray(
              new int[] {
                point[0] - origin[0],
                point[1] - origin[1],
                anchor == null ? 0 : anchor.getWidth(),
                anchor == null ? 0 : anchor.getHeight()
              }));
      JSONObject colors = new JSONObject();
      colors.put("card", palette.dark ? "#302C42" : "#FFFFFF");
      colors.put("ink", palette.dark ? "#F3ECFF" : "#1D1A4A");
      colors.put("soft", palette.dark ? "#68557F" : "#B8B3FF");
      colors.put("acc", "#FF5FA8");
      config.put("colors", colors);
      JSONArray nodes = new JSONArray();
      controlHitboxes.clear();
      collect(nativeRoot, nodes, origin);
      config.put("controls", nodes);
      String layout = config.toString();
      if (!layout.equals(lastLayout)) {
        lastLayout = layout;
        resumeTimers();
        evaluateJavascript("window.configureTabs&&window.configureTabs(" + layout + ")", null);
        scheduleRest();
      }
    } catch (JSONException failure) {
      restoreNative();
    }
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    onResume();
    return super.dispatchKeyEvent(event);
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
      tracking =
          hitbox.contains(event.getX(), event.getY())
              || toastHitbox.contains(event.getX(), event.getY());
      downX = lastX = event.getX();
      downY = lastY = event.getY();
      scrolling = false;
      scrollTarget = null;
      for (View view : controls.values()) {
        android.graphics.Rect rect = new android.graphics.Rect();
        if (!view.getGlobalVisibleRect(rect)
            || view instanceof SeekBar
            || view instanceof RatingBar) continue;
        int[] location = new int[2];
        getLocationOnScreen(location);
        if (!rect.contains((int) event.getX() + location[0], (int) event.getY() + location[1]))
          continue;
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
          if (parent instanceof ScrollView
              || parent instanceof HorizontalScrollView
              || parent instanceof AbsListView) {
            scrollTarget = (View) parent;
            break;
          }
          parent = parent.getParent();
        }
      }
      for (RectF box : controlHitboxes) tracking |= box.contains(event.getX(), event.getY());
      if (!tracking) return false;
      onResume();
    }
    if (!tracking) return false;
    if (event.getActionMasked() == MotionEvent.ACTION_MOVE && scrollTarget != null) {
      boolean horizontal = scrollTarget instanceof HorizontalScrollView;
      float distance = horizontal ? event.getX() - downX : event.getY() - downY;
      if (!scrolling && Math.abs(distance) > 10 * getResources().getDisplayMetrics().density) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
        scrolling = true;
      }
      if (scrolling) {
        int delta = Math.round(horizontal ? lastX - event.getX() : lastY - event.getY());
        if (scrollTarget instanceof AbsListView) ((AbsListView) scrollTarget).scrollListBy(delta);
        else scrollTarget.scrollBy(horizontal ? delta : 0, horizontal ? 0 : delta);
        place();
      }
      lastX = event.getX();
      lastY = event.getY();
    }
    boolean handled = scrolling || super.dispatchTouchEvent(event);
    if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      tracking = false;
      onResume();
    }
    return handled;
  }

  public void release() {
    if (disposed) return;
    detachObserver();
    restoreNative();
    disposed = true;
    idleToken++;
    resumeTimers();
    anchor = null;
    removeJavascriptInterface("AndroidTabs");
    stopLoading();
    destroy();
  }

  public boolean unavailable() {
    return disposed;
  }

  private void detachObserver() {
    if (nativeRoot != null && nativeRoot.getViewTreeObserver().isAlive())
      nativeRoot.getViewTreeObserver().removeOnPreDrawListener(layoutObserver);
  }

  private void restoreNative() {
    if (anchor != null) anchor.setVisibility(VISIBLE);
    for (View view : controls.values()) {
      view.setAlpha(1);
      view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
  }

  public void success(String label) {
    if (!loaded || disposed) return;
    onResume();
    evaluateJavascript("window.edcamDone&&window.edcamDone(" + JSONObject.quote(label) + ")", null);
  }

  public void toast(String message) {
    if (!loaded || disposed) return;
    toastActive = true;
    onResume();
    evaluateJavascript(
        "window.edcamToast&&window.edcamToast(" + JSONObject.quote(message) + ")", null);
  }

  private void collect(View view, JSONArray nodes, int[] origin) throws JSONException {
    if (view == this || view == anchor || view.getVisibility() != VISIBLE) return;
    String kind = null;
    if (view instanceof RadioGroup) kind = "radio";
    else if (view instanceof Switch) kind = "toggle";
    else if (view instanceof CheckBox) kind = "checkbox";
    else if (view instanceof SeekBar) kind = "slider";
    else if (view instanceof RatingBar) kind = "rating";
    else if (view instanceof ProgressBar) kind = "progress";
    else if (view instanceof EditText) kind = "input";
    else if (view instanceof Button) kind = "button";
    else if (view instanceof FrameLayout
        && view.isClickable()
        && view.getContentDescription() != null) kind = "button";
    else if ("sakuga-ratio".equals(view.getTag())) kind = "button";
    if ("sakuga-tooltip".equals(view.getTag())) kind = "tooltip";
    if ("sakuga-favorite".equals(view.getTag())) kind = "checkbox";
    if (kind == null) {
      if (view instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), nodes, origin);
      }
      return;
    }
    android.graphics.Rect visible = new android.graphics.Rect();
    if (!view.getGlobalVisibleRect(visible) || visible.width() < 2 || visible.height() < 2) return;
    int id = System.identityHashCode(view);
    controls.put(id, view);
    int[] point = new int[2];
    view.getLocationInWindow(point);
    JSONObject node = new JSONObject();
    node.put("id", id);
    node.put("kind", kind);
    String label =
        view.getContentDescription() == null ? "" : view.getContentDescription().toString();
    if (view instanceof TextView
        && !(view instanceof EditText)
        && !(view instanceof CompoundButton)) label = ((TextView) view).getText().toString();
    node.put("label", label);
    node.put("disabled", !view.isEnabled());
    node.put("selected", view.isSelected());
    node.put(
        "rect",
        new JSONArray(
            new int[] {
              point[0] - origin[0], point[1] - origin[1], view.getWidth(), view.getHeight()
            }));
    node.put(
        "clip",
        new JSONArray(
            new int[] {
              visible.left - origin[0],
              visible.top - origin[1],
              visible.right - origin[0],
              visible.bottom - origin[1]
            }));
    String value = "";
    if (view instanceof CompoundButton) value = String.valueOf(((CompoundButton) view).isChecked());
    else if (view instanceof SeekBar) value = String.valueOf(((SeekBar) view).getProgress());
    else if (view instanceof RatingBar) value = String.valueOf(((RatingBar) view).getRating());
    else if (view instanceof ProgressBar)
      value = String.valueOf(((ProgressBar) view).getProgress() / 100f);
    else if (view instanceof EditText) value = ((EditText) view).getText().toString();
    else if ("sakuga-favorite".equals(view.getTag()))
      value = String.valueOf(label.startsWith("Bỏ"));
    if (view instanceof RadioGroup) {
      RadioGroup group = (RadioGroup) view;
      JSONArray options = new JSONArray();
      for (int i = 0; i < group.getChildCount(); i++) {
        RadioButton option = (RadioButton) group.getChildAt(i);
        options.put(new JSONObject().put("value", option.getId()).put("label", option.getText()));
      }
      node.put("options", options);
      value = String.valueOf(group.getCheckedRadioButtonId());
    }
    if (view instanceof EditText) {
      CharSequence hint = ((EditText) view).getHint();
      node.put("placeholder", hint == null ? "" : hint.toString());
    }
    node.put("value", value);
    nodes.put(node);
    controlHitboxes.add(
        new RectF(
            visible.left - origin[0],
            visible.top - origin[1],
            visible.right - origin[0],
            visible.bottom - origin[1]));
    // Preserve native controls as a fallback until the local page confirms its rendered bounds.
    if (loaded) {
      view.setAlpha(0);
      view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
  }

  private final class Bridge {
    @JavascriptInterface
    public void toastBounds(double left, double top, double right, double bottom, double viewport) {
      post(
          () -> {
            if (disposed
                || viewport <= 0
                || !Double.isFinite(viewport)
                || !Double.isFinite(left)
                || !Double.isFinite(top)
                || !Double.isFinite(right)
                || !Double.isFinite(bottom)) return;
            float scale = getWidth() / (float) viewport;
            toastHitbox.set(
                (float) left * scale,
                (float) top * scale,
                (float) right * scale,
                (float) bottom * scale);
          });
    }

    @JavascriptInterface
    public void control(int receivedEpoch, int id, String value) {
      post(
          () -> {
            if (disposed || receivedEpoch != epoch || value == null || value.length() > 160) return;
            View view = controls.get(id);
            if (view == null || !view.isAttachedToWindow() || !view.isEnabled()) return;
            try {
              if (view instanceof CompoundButton)
                ((CompoundButton) view).setChecked(Boolean.parseBoolean(value));
              else if (view instanceof SeekBar)
                ((SeekBar) view).setProgress(Math.max(0, Math.min(100, Integer.parseInt(value))));
              else if (view instanceof RatingBar)
                ((RatingBar) view).setRating(Math.max(0, Math.min(5, Float.parseFloat(value))));
              else if (view instanceof RadioGroup)
                ((RadioGroup) view).check(Integer.parseInt(value));
              else if (view instanceof EditText) {
                EditText input = (EditText) view;
                if (!input.getText().toString().equals(value)) input.setText(value);
              } else if (view instanceof ProgressBar && value.equals("done")) {
                if (((ProgressBar) view).getProgress() == 100) view.setVisibility(GONE);
              } else view.performClick();
            } catch (NumberFormatException invalid) {
              android.util.Log.w("EdCamTabs", "Rejected invalid control value");
            }
            place();
          });
    }

    @JavascriptInterface
    public void rest(int token) {
      post(
          () -> {
            if (!disposed && !tracking && token == idleToken) {
              toastActive = false;
              toastHitbox.setEmpty();
              SakugaTabsLayer.super.onPause();
              pauseTimers();
            }
          });
    }

    @JavascriptInterface
    public void ready() {
      post(
          () -> {
            if (!disposed) {
              loaded = true;
              place();
            }
          });
    }

    @JavascriptInterface
    public void bounds(
        int receivedEpoch, double left, double top, double right, double bottom, double viewport) {
      post(
          () -> {
            if (disposed
                || receivedEpoch != epoch
                || viewport <= 0
                || !Double.isFinite(viewport)
                || !Double.isFinite(left)
                || !Double.isFinite(top)
                || !Double.isFinite(right)
                || !Double.isFinite(bottom)) return;
            float scale = getWidth() / (float) viewport;
            hitbox.set(
                (float) left * scale,
                (float) top * scale,
                (float) right * scale,
                (float) bottom * scale);
            if (anchor != null) anchor.setVisibility(INVISIBLE);
          });
    }

    @JavascriptInterface
    public void change(String kind, String value, int index) {
      post(
          () -> {
            if (disposed || !mode.equals(kind)) return;
            String[] allowed =
                kind.equals("camera")
                    ? new String[] {"styles", "camera", "color"}
                    : new String[] {"camera", "styles", "album", "settings"};
            if (index < 0 || index >= allowed.length || !allowed[index].equals(value)) return;
            if (!java.util.Arrays.asList("camera", "styles", "album", "settings", "color")
                .contains(value)) return;
            listener.selected(kind, value, index);
          });
    }
  }
}
