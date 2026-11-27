package com.edcam;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Repeatable emulator comparison. These results do not establish physical-device performance. */
public final class PerformanceInstrumentation extends Instrumentation {
  private String label;
  private Activity screen;
  private final List<Double> frames = Collections.synchronizedList(new ArrayList<>());
  private int missed, received, dropped;
  private HandlerThread collector;

  @Override
  public void onCreate(Bundle args) {
    label = args == null ? "run" : args.getString("label", "run");
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      JSONObject report = new JSONObject();
      report.put("label", label);
      report.put("model", Build.MODEL);
      report.put("sdk", Build.VERSION.SDK_INT);
      report.put(
          "version",
          getTargetContext().getPackageManager().getPackageInfo("com.edcam", 0).versionName);
      screen =
          startActivitySync(
              new Intent(getTargetContext(), MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      waitForIdleSync();
      ready();
      collector = new HandlerThread("FrameBenchmark");
      collector.start();
      Window.OnFrameMetricsAvailableListener listener =
          (window, metrics, lost) -> {
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
            long total = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
            long deadline =
                Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.DEADLINE) : 16_666_667;
            synchronized (frames) {
              if (total >= 0) {
                frames.add(total / 1_000_000d);
                received++;
                if (deadline > 0 && total > deadline) missed++;
              }
              dropped += lost;
            }
          };
      runOnMainSync(
          () ->
              screen
                  .getWindow()
                  .addOnFrameMetricsAvailableListener(
                      listener, new Handler(collector.getLooper())));
      report.put("idle_pss_kb", Debug.getPss());
      List<Double> toggles = new ArrayList<>(),
          availability = new ArrayList<>(),
          styleOpen = new ArrayList<>(),
          search = new ArrayList<>();
      int replacements = 0;
      for (int round = 0; round < 3; round++) {
        for (String action : new String[] {"Hẹn giờ", "Lưới"}) {
          Object before = controller();
          long start = SystemClock.elapsedRealtimeNanos();
          clickPrefix(action);
          toggles.add(ms(start));
          ready();
          availability.add(ms(start));
          if (before != controller()) replacements++;
        }
        long start = SystemClock.elapsedRealtimeNanos();
        click("Mở thư viện style");
        waitForIdleSync();
        styleOpen.add(ms(start));
        EditText input =
            (EditText) find(screen.getWindow().getDecorView(), v -> v instanceof EditText);
        for (String query : new String[] {"f", "fi", "film", ""}) {
          long typed = SystemClock.elapsedRealtimeNanos();
          runOnMainSync(() -> input.setText(query));
          search.add(ms(typed));
          SystemClock.sleep(180);
        }
        ScrollView scroll =
            (ScrollView) find(screen.getWindow().getDecorView(), v -> v instanceof ScrollView);
        if (scroll != null) {
          runOnMainSync(() -> scroll.smoothScrollBy(0, 1600));
          SystemClock.sleep(450);
          runOnMainSync(() -> scroll.smoothScrollTo(0, 0));
          SystemClock.sleep(450);
        } else {
          ListView list =
              (ListView) find(screen.getWindow().getDecorView(), v -> v instanceof ListView);
          if (list != null) {
            runOnMainSync(() -> list.smoothScrollBy(1600, 400));
            SystemClock.sleep(450);
            runOnMainSync(() -> list.smoothScrollToPosition(0));
            SystemClock.sleep(450);
          }
        }
        runOnMainSync(() -> screen.onBackPressed());
        waitForIdleSync();
        ready();
      }
      report.put("camera_control_dispatch_ms", summary(toggles));
      report.put("camera_ready_after_control_ms", summary(availability));
      report.put("camera_controller_replacements", replacements);
      report.put("style_open_ui_ms", summary(styleOpen));
      report.put("search_dispatch_ui_ms", summary(search));
      report.put("post_navigation_pss_kb", Debug.getPss());
      Bitmap input = Bitmap.createBitmap(1920, 2560, Bitmap.Config.ARGB_8888);
      input.eraseColor(Color.rgb(125, 157, 186));
      List<Double> renders = new ArrayList<>();
      for (int i = 0; i < 7; i++) {
        long started = SystemClock.elapsedRealtimeNanos();
        Bitmap output = PhotoProcessor.render(input, Presets.get(39), 1, .75f, false, "");
        double elapsed = ms(started);
        output.recycle();
        if (i >= 2) renders.add(elapsed);
      }
      input.recycle();
      report.put("render_1920x2560_grain_ms", summary(renders));
      runOnMainSync(() -> screen.getWindow().removeOnFrameMetricsAvailableListener(listener));
      collector.quitSafely();
      synchronized (frames) {
        report.put("ui_frame_duration_ms", summary(frames));
        report.put("ui_frames_received", received);
        report.put("ui_frames_over_deadline", missed);
        report.put("frame_callback_drops", dropped);
      }
      report.put(
          "notes",
          "Android emulator with software GPU; UI frame metrics are not camera sensor FPS."
              + " Synthetic 5 MP render input. Navigation includes repeated search/scroll. Same"
              + " device profile required for before/after.");
      result.putString("stream", report.toString(2) + "\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (screen != null) runOnMainSync(screen::finish);
      if (collector != null) collector.quitSafely();
    }
  }

  private Object controller() throws Exception {
    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("camera");
    field.setAccessible(true);
    return field.get(screen);
  }

  private void ready() throws Exception {
    long until = SystemClock.elapsedRealtime() + 6000;
    while (SystemClock.elapsedRealtime() < until) {
      final boolean[] state = {false};
      runOnMainSync(
          () -> {
            try {
              Object c = controller();
              state[0] = c != null && ((CameraController) c).isReady();
            } catch (Exception e) {
              throw new IllegalStateException(e);
            }
          });
      if (state[0]) return;
      SystemClock.sleep(20);
    }
    throw new IllegalStateException("Camera not ready within 6 seconds");
  }

  private void click(String description) {
    runOnMainSync(
        () -> {
          View target =
              find(
                  screen.getWindow().getDecorView(),
                  v ->
                      v.getContentDescription() != null
                          && description.contentEquals(v.getContentDescription()));
          if (target == null) throw new IllegalStateException("Missing action " + description);
          target.performClick();
        });
  }

  private void clickPrefix(String prefix) {
    runOnMainSync(
        () -> {
          View target =
              find(
                  screen.getWindow().getDecorView(),
                  v ->
                      v.getContentDescription() != null
                          && (v.getContentDescription().toString().startsWith(prefix)
                              || (prefix.equals("Hẹn giờ")
                                  && v.getContentDescription().toString().endsWith(" giây")))
                          && v.isClickable());
          if (target == null) throw new IllegalStateException("Missing action " + prefix);
          target.performClick();
        });
  }

  private interface Match {
    boolean matches(View view);
  }

  private static View find(View view, Match match) {
    if (match.matches(view)) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = find(group.getChildAt(i), match);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static double ms(long started) {
    return (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000d;
  }

  private static JSONObject summary(List<Double> values) throws JSONException {
    List<Double> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    JSONObject result = new JSONObject();
    result.put("samples", sorted.size());
    if (!sorted.isEmpty()) {
      result.put("median", sorted.get(sorted.size() / 2));
      result.put(
          "p95", sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * .95) - 1)));
      result.put("max", sorted.get(sorted.size() - 1));
    }
    return result;
  }
}
