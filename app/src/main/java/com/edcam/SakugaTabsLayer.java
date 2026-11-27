package com.edcam;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.*;
import android.webkit.*;
import org.json.*;

/** Local-only transparent web controls; touches outside the tab hitbox reach native camera/UI. */
public final class SakugaTabsLayer extends WebView {
  public interface Listener {
    void selected(String mode, String value, int index);
  }

  private static final String PAGE = "file:///android_asset/sakuga.html";
  private final RectF hitbox = new RectF();
  private final Listener listener;
  private View anchor;
  private String mode = "primary";
  private Palette palette;
  private int selected, epoch, idleToken;
  private boolean loaded, tracking, disposed;

  private void scheduleRest() {
    int token = ++idleToken;
    if (loaded && !disposed)
      evaluateJavascript(
          "window.SakugaFX&&SakugaFX.later(1800,()=>AndroidTabs.rest(" + token + "))", null);
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
            loaded = false;
            disposed = true;
            if (anchor != null) anchor.setVisibility(VISIBLE);
            if (getParent() instanceof ViewGroup)
              ((ViewGroup) getParent()).removeView(SakugaTabsLayer.this);
            view.destroy();
            return true;
          }
        });
    loadUrl(PAGE);
  }

  public void target(View anchor, String mode, int selected, Palette palette) {
    this.anchor = anchor;
    this.mode = mode;
    this.selected = selected;
    this.palette = palette;
    epoch++;
    hitbox.setEmpty();
    getSettings().setTextZoom(Math.round(getResources().getConfiguration().fontScale * 100));
    setVisibility(VISIBLE);
    onResume();
    anchor.post(this::place);
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
    if (!loaded
        || disposed
        || anchor == null
        || anchor.getWidth() == 0
        || getWidth() == 0
        || palette == null) return;
    int[] point = new int[2], origin = new int[2];
    anchor.getLocationInWindow(point);
    getLocationInWindow(origin);
    try {
      JSONObject config = new JSONObject();
      config.put("mode", mode);
      config.put("selected", selected);
      config.put("epoch", epoch);
      config.put("viewport", getWidth());
      config.put(
          "rect",
          new JSONArray(
              new int[] {
                point[0] - origin[0], point[1] - origin[1], anchor.getWidth(), anchor.getHeight()
              }));
      JSONObject colors = new JSONObject();
      colors.put("card", palette.dark ? "#302C42" : "#FFFDF8");
      colors.put("ink", palette.dark ? "#F3ECFF" : "#292440");
      colors.put("soft", palette.dark ? "#68557F" : "#D1C5EF");
      colors.put("acc", "#EC83AD");
      config.put("colors", colors);
      evaluateJavascript("window.configureTabs&&window.configureTabs(" + config + ")", null);
      scheduleRest();
    } catch (JSONException failure) {
      anchor.setVisibility(VISIBLE);
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
      tracking = hitbox.contains(event.getX(), event.getY());
      if (!tracking) return false;
      onResume();
    }
    if (!tracking) return false;
    boolean handled = super.dispatchTouchEvent(event);
    if (event.getActionMasked() == MotionEvent.ACTION_UP
        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
      tracking = false;
      onResume();
    }
    return handled;
  }

  public void release() {
    if (disposed) return;
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

  private final class Bridge {
    @JavascriptInterface
    public void rest(int token) {
      post(
          () -> {
            if (!disposed && !tracking && token == idleToken) {
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
