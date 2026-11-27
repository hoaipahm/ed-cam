import com.edcam.CaptureCountdown;
import com.edcam.ZoomMath;
import java.util.*;

public final class CameraCoreTest {
  private static int checks;

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    CaptureCountdown countdown = new CaptureCountdown();
    int first = countdown.begin();
    check(countdown.accepts(first), "current capture callback accepted");
    countdown.cancel();
    check(!countdown.accepts(first), "background cancels countdown");
    int second = countdown.begin();
    check(
        countdown.accepts(second) && !countdown.accepts(first),
        "old callback cannot enter new capture");
    for (float[] range : new float[][] {{1, 1}, {1, 4}, {.5f, 20}, {.3f, 30}, {1, 6.25f}}) {
      List<Float> values = ZoomMath.stops(range[0], range[1]);
      check(!values.isEmpty(), "always includes a valid lens stop");
      check(values.get(0) == range[0], "wide end exposed");
      check(values.get(values.size() - 1) == range[1], "tele end exposed");
      float previous = 0;
      for (float value : values) {
        check(value >= range[0] && value <= range[1], "no unsupported zoom stops");
        check(value > previous, "zoom stops unique and ascending");
        previous = value;
      }
      check(
          ZoomMath.clamp(Float.NaN, range[0], range[1])
              == Math.max(range[0], Math.min(range[1], 1)),
          "NaN resets to normal lens");
      check(
          ZoomMath.clamp(Float.POSITIVE_INFINITY, range[0], range[1])
              == Math.max(range[0], Math.min(range[1], 1)),
          "infinite gesture rejected");
      check(ZoomMath.clamp(-100, range[0], range[1]) == range[0], "wide clamp");
      check(ZoomMath.clamp(1000, range[0], range[1]) == range[1], "tele clamp");
    }
    check(!ZoomMath.stops(1, 4).contains(.5f), "no fake ultrawide");
    check(!ZoomMath.stops(1, 4).contains(20f), "no fake 20x");
    System.out.println("PASS: " + checks + " camera core checks.");
  }
}
