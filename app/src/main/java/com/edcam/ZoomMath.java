package com.edcam;

import java.util.ArrayList;
import java.util.List;

/** Hardware bounded zoom values shared by the camera and its controls. */
public final class ZoomMath {
  private ZoomMath() {}

  public static float clamp(float value, float min, float max) {
    if (!Float.isFinite(value)) return Math.max(min, Math.min(max, 1));
    return Math.max(min, Math.min(max, value));
  }

  public static List<Float> stops(float min, float max) {
    List<Float> values = new ArrayList<>();
    values.add(min);
    for (float value : new float[] {.3f, .5f, 1, 2, 3, 4, 5, 6, 10, 15, 20, 30, 50, 100})
      if (value > min + .05f && value < max - .05f) values.add(value);
    if (max > min + .05f) values.add(max);
    return values;
  }
}
