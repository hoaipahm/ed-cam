package com.edcam;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.JSONObject;

/** Runs on the isolated QA emulator only; never clears the target album. */
public final class UpgradeInstrumentation extends Instrumentation {
  private MainActivity screen;
  private boolean benchmark;
  private int checks;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    benchmark = args != null && args.getString("benchmark", "false").equals("true");
    start();
  }

  private void check(boolean value, String label) {
    checks++;
    if (!value) throw new AssertionError(label);
  }

  private Object field(String name) throws Exception {
    Field f = MainActivity.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(screen);
  }

  private void set(String name, Object value) throws Exception {
    Field f = MainActivity.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(screen, value);
  }

  private void invoke(String name) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name);
    m.setAccessible(true);
    m.invoke(screen);
  }

  private void onMain(Checked action) {
    final java.util.concurrent.atomic.AtomicReference<Throwable> failure =
        new java.util.concurrent.atomic.AtomicReference<>();
    runOnMainSync(
        () -> {
          try {
            action.run();
          } catch (Throwable e) {
            failure.set(e);
          }
        });
    if (failure.get() != null) throw new AssertionError(failure.get());
  }

  interface Checked {
    void run() throws Exception;
  }

  private void await(Condition condition, String label) throws Exception {
    long until = SystemClock.elapsedRealtime() + 15000;
    while (SystemClock.elapsedRealtime() < until) {
      final boolean[] ready = {false};
      onMain(() -> ready[0] = condition.ready());
      if (ready[0]) return;
      SystemClock.sleep(80);
    }
    throw new AssertionError("Timed out: " + label);
  }

  interface Condition {
    boolean ready() throws Exception;
  }

  private static View find(View view, java.util.function.Predicate<View> test) {
    if (test.test(view)) return view;
    if (view instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
        View found = find(((ViewGroup) view).getChildAt(i), test);
        if (found != null) return found;
      }
    return null;
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      screen =
          (MainActivity)
              startActivitySync(
                  new Intent(getTargetContext(), MainActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      SystemClock.sleep(1800);
      if (benchmark) {
        result.putString("stream", settingsBenchmark().toString(2));
      } else {
        regression();
        result.putString(
            "stream",
            "PASS: "
                + checks
                + " upgrade checks (native UI, zoom, capture, video, lifecycle and storage).\n");
      }
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString(
          "stream", "FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(e));
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (screen != null) runOnMainSync(screen::finish);
    }
  }

  private JSONObject settingsBenchmark() throws Exception {
    onMain(
        () -> {
          set("tab", 3);
          invoke("render");
        });
    waitForIdleSync();
    SystemClock.sleep(2000);
    List<Double> frames = Collections.synchronizedList(new ArrayList<>());
    HandlerThread thread = new HandlerThread("SettingsFrames");
    thread.start();
    Window.OnFrameMetricsAvailableListener listener =
        (w, m, d) -> frames.add(m.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000d);
    onMain(
        () ->
            screen
                .getWindow()
                .addOnFrameMetricsAvailableListener(listener, new Handler(thread.getLooper())));
    ScrollView scroll =
        (ScrollView) find(screen.getWindow().getDecorView(), v -> v instanceof ScrollView);
    check(scroll != null, "settings scroll");
    for (int i = 0; i < 8; i++) {
      onMain(() -> scroll.smoothScrollTo(0, scroll.getChildAt(0).getHeight()));
      SystemClock.sleep(500);
      onMain(() -> scroll.smoothScrollTo(0, 0));
      SystemClock.sleep(500);
    }
    onMain(() -> screen.getWindow().removeOnFrameMetricsAvailableListener(listener));
    thread.quitSafely();
    List<Double> sorted;
    synchronized (frames) {
      sorted = new ArrayList<>(frames);
    }
    Collections.sort(sorted);
    check(!sorted.isEmpty(), "frame samples");
    JSONObject data = new JSONObject();
    data.put("frame_samples", sorted.size());
    data.put("median_ms", sorted.get(sorted.size() / 2));
    data.put("p95_ms", sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * .95))));
    data.put("over_16_67_ms", sorted.stream().filter(n -> n > 16.67).count());
    data.put("app_pss_kb", Debug.getPss());
    data.put(
        "configuration",
        "API35 software GPU, 720x1280, density320; settings 8 scroll round trips. PSS excludes"
            + " separate WebView renderer.");
    return data;
  }

  private void regression() throws Exception {
    CaptureCountdown countdown = new CaptureCountdown();
    int old = countdown.begin();
    countdown.cancel();
    int fresh = countdown.begin();
    check(
        !countdown.accepts(old) && countdown.accepts(fresh),
        "cancelled countdown cannot affect fresh capture");
    check(ZoomMath.clamp(Float.NaN, .5f, 20) == 1, "invalid zoom reset");
    check(
        ZoomMath.clamp(25, .5f, 20) == 20 && ZoomMath.clamp(.1f, .5f, 20) == .5f,
        "zoom boundaries");
    check(
        ZoomMath.stops(1, 4).equals(Arrays.asList(1f, 2f, 3f, 4f)),
        "only hardware supported stops");
    check(
        !ZoomMath.stops(1, 4).contains(.5f) && !ZoomMath.stops(1, 4).contains(20f),
        "no invented zoom");
    await(
        () -> field("camera") != null && ((CameraController) field("camera")).isReady(),
        "camera ready");
    onMain(
        () -> {
          check(
              find(screen.getWindow().getDecorView(), v -> v instanceof WebView) == null,
              "no overlay WebView on camera");
          CameraController camera = (CameraController) field("camera");
          camera.setZoom(999);
          check(camera.zoom() == camera.maxZoom(), "camera max zoom clamp");
          camera.setZoom(-1);
          check(camera.zoom() == camera.minZoom(), "camera min zoom clamp");
          camera.setZoom(1);
          ShutterButton shutter = (ShutterButton) field("shutterButton");
          check(shutter.getWidth() >= 144 && shutter.getHeight() >= 144, "large native shutter");
        });
    PhotoStore photos = new PhotoStore(getTargetContext());
    onMain(
        () -> {
          View texture = (View) field("texture");
          long down = SystemClock.uptimeMillis();
          MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
          MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
          for (int i = 0; i < 2; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].pressure = 1;
            coords[i].size = 1;
            coords[i].y = texture.getHeight() / 2f;
          }
          coords[0].x = texture.getWidth() / 2f - 120;
          coords[1].x = texture.getWidth() / 2f + 120;
          dispatchTouch(texture, down, down, MotionEvent.ACTION_DOWN, 1, properties, coords);
          dispatchTouch(
              texture,
              down,
              down + 20,
              MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
              2,
              properties,
              coords);
          for (int i = 1; i <= 10; i++) {
            coords[0].x -= 12;
            coords[1].x += 12;
            dispatchTouch(
                texture, down, down + 20 + i * 20, MotionEvent.ACTION_MOVE, 2, properties, coords);
          }
          dispatchTouch(
              texture,
              down,
              down + 250,
              MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
              2,
              properties,
              coords);
          dispatchTouch(texture, down, down + 270, MotionEvent.ACTION_UP, 1, properties, coords);
          CameraController camera = (CameraController) field("camera");
          check(
              camera.maxZoom() <= 1 || camera.zoom() > 1,
              "two-finger gesture reaches real camera zoom");
          camera.setZoom(1);
          camera.setExposure(1000);
          check(camera.exposure() == camera.maxExposure(), "exposure respects hardware range");
          camera.setExposure(0);
        });
    int before = photos.list().size();
    onMain(() -> invoke("beginCapture"));
    await(() -> !((Boolean) field("busy")), "photo saved");
    check(photos.list().size() == before + 1, "camera capture creates photo");
    PhotoStore.Photo captured = photos.list().get(0);
    onMain(
        () -> {
          View flip =
              find(
                  screen.getWindow().getDecorView(),
                  v ->
                      "Đổi camera"
                          .contentEquals(
                              v.getContentDescription() == null ? "" : v.getContentDescription()));
          check(flip != null, "front camera control available");
          flip.performClick();
        });
    await(() -> ((CameraController) field("camera")).isReady(), "front camera ready");
    onMain(() -> invoke("beginCapture"));
    await(() -> !((Boolean) field("busy")), "front photo saves");
    PhotoStore.Photo front = photos.list().get(0);
    check(front.mirrored, "front camera capture keeps mirror metadata");
    onMain(
        () -> {
          set("tab", 3);
          invoke("render");
        });
    waitForIdleSync();
    onMain(
        () -> {
          check(
              find(screen.getWindow().getDecorView(), v -> v instanceof WebView) == null,
              "settings has no text-replica overlay");
          check(
              find(screen.getWindow().getDecorView(), v -> v instanceof Switch) != null,
              "native settings switches");
        });
    onMain(
        () -> {
          set("tab", 0);
          set("videoMode", true);
          for (Presets.Style style : Presets.ALL)
            if (style.name.equals("Mono")) set("styleId", style.id);
          set("strength", 1f);
          set("videoAudio", true);
          invoke("render");
        });
    await(() -> ((CameraController) field("camera")).isReady(), "video preview");
    VideoStore videos = new VideoStore(getTargetContext());
    List<File> existing = videos.list();
    onMain(() -> invoke("beginRecording"));
    await(() -> (Boolean) field("recordingVideo"), "recording begins");
    SystemClock.sleep(1800);
    onMain(() -> invoke("toggleVideoPause"));
    check((Boolean) field("videoPaused"), "pause video");
    SystemClock.sleep(350);
    onMain(() -> invoke("toggleVideoPause"));
    SystemClock.sleep(1600);
    onMain(() -> invoke("stopRecording"));
    await(() -> !((Boolean) field("busy")), "video finalizes");
    List<File> added = videos.list();
    added.removeAll(existing);
    check(added.size() == 1, "one finalized video");
    File clip = added.get(0);
    try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
      retriever.setDataSource(clip.getPath());
      check(
          "yes".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)),
          "MP4 video track");
      check(
          "yes".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)),
          "MP4 audio track");
      check(
          Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
              > 2000,
          "real recording duration");
      Bitmap frame = retriever.getFrameAtTime();
      check(frame != null, "video decodes actual frame");
      if (frame != null) {
        int brightest = 0, darkest = 255, chroma = 0, samples = 0;
        for (int y = 0; y < frame.getHeight(); y += 16)
          for (int x = 0; x < frame.getWidth(); x += 16) {
            int c = frame.getPixel(x, y);
            int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
            darkest = Math.min(darkest, r);
            brightest = Math.max(brightest, r);
            chroma += Math.abs(r - g) + Math.abs(g - b);
            samples++;
          }
        check(
            brightest - darkest > 20 && chroma / (float) samples < 10,
            "encoded Mono frame has detail and neutral color");
        frame.recycle();
      }
    }
    android.net.Uri exported = videos.export(clip);
    check(exported != null, "MediaStore video export");
    getTargetContext().getContentResolver().delete(exported, null, null);
    // Going into the background must finish the recording rather than leave a corrupt pending file.
    await(() -> ((CameraController) field("camera")).isReady(), "restored camera");
    onMain(() -> invoke("beginRecording"));
    await(() -> (Boolean) field("recordingVideo"), "second recording");
    SystemClock.sleep(1500);
    onMain(() -> ((CameraController) field("camera")).stop());
    await(() -> !((Boolean) field("busy")), "background recording saves");
    check(videos.list().size() == existing.size() + 2, "background video finalized");
    onMain(
        () -> {
          ((CameraController) field("camera")).start();
          set("videoAudio", false);
        });
    await(() -> ((CameraController) field("camera")).isReady(), "restart after background");
    onMain(() -> invoke("beginRecording"));
    await(() -> (Boolean) field("recordingVideo"), "silent video begins");
    SystemClock.sleep(1600);
    onMain(() -> invoke("stopRecording"));
    await(() -> !((Boolean) field("busy")), "silent video finalizes");
    File silent = videos.list().get(0);
    try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
      retriever.setDataSource(silent.getPath());
      check(
          !"yes".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)),
          "silent recording has no invented audio track");
    }
    await(() -> ((CameraController) field("camera")).isReady(), "camera after silent recording");
    onMain(
        () -> {
          invoke("beginRecording");
          invoke("stopRecording");
        });
    await(() -> !((Boolean) field("busy")), "cancel while preparing");
    File[] pending =
        new File(getTargetContext().getFilesDir(), "videos")
            .listFiles((dir, name) -> name.endsWith(".pending"));
    check(pending != null && pending.length == 0, "cancelled recorder leaves no partial file");
    for (File file : videos.list())
      if (!existing.contains(file)) java.nio.file.Files.deleteIfExists(file.toPath());
    photos.delete(captured);
    photos.delete(front);
  }

  private void dispatchTouch(
      View target,
      long down,
      long time,
      int action,
      int count,
      MotionEvent.PointerProperties[] properties,
      MotionEvent.PointerCoords[] coords) {
    MotionEvent event =
        MotionEvent.obtain(
            down,
            time,
            action,
            count,
            properties,
            coords,
            0,
            0,
            1,
            1,
            0,
            0,
            android.view.InputDevice.SOURCE_TOUCHSCREEN,
            0);
    target.dispatchTouchEvent(event);
    event.recycle();
  }
}
