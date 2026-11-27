package com.edcam;

import android.hardware.camera2.*;
import android.os.Build;
import android.util.Range;
import android.util.SizeF;
import java.util.*;

/** Only exposes lenses and zoom ranges actually advertised by Camera2. */
final class CameraCatalog {
  static final class Lens {
    final String id;
    final CameraCharacteristics info;
    float base = 1, min = 1, max = 1;
    boolean ratioControl, logical;

    Lens(String id, CameraCharacteristics info) {
      this.id = id;
      this.info = info;
    }

    float minZoom() {
      return base * min;
    }

    float maxZoom() {
      return base * max;
    }
  }

  final List<Lens> lenses = new ArrayList<>();
  final float min, max;

  CameraCatalog(CameraManager manager, boolean front) throws CameraAccessException {
    for (String id : manager.getCameraIdList()) {
      CameraCharacteristics c = manager.getCameraCharacteristics(id);
      Integer facing = c.get(CameraCharacteristics.LENS_FACING);
      if (facing == null
          || facing
              != (front
                  ? CameraCharacteristics.LENS_FACING_FRONT
                  : CameraCharacteristics.LENS_FACING_BACK)) continue;
      Lens lens = new Lens(id, c);
      Float digital = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
      lens.max = digital == null ? 1 : Math.max(1, digital);
      if (Build.VERSION.SDK_INT >= 30) {
        Range<Float> range = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        if (range != null) {
          lens.min = range.getLower();
          lens.max = range.getUpper();
          lens.ratioControl = true;
        }
      }
      int[] capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
      if (capabilities != null)
        for (int capability : capabilities)
          if (capability
              == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
            lens.logical = true;
      lenses.add(lens);
    }
    // Prefer the logical main camera. For discrete lenses estimate the main wide
    // camera by proximity to a 26 mm full-frame equivalent horizontal angle.
    Lens primary = null;
    double bestScore = Double.POSITIVE_INFINITY;
    for (Lens candidate : lenses) {
      double score = Math.abs(Math.log(fieldOfViewScale(candidate.info) * 36 / 26.0));
      if (candidate.logical) score -= 10;
      if (candidate.logical && candidate.min < 1) score -= 10;
      if (score < bestScore) {
        primary = candidate;
        bestScore = score;
      }
    }
    float reference = primary == null ? 1 : fieldOfViewScale(primary.info);
    float low = 1, high = 1;
    for (Lens lens : lenses) {
      lens.base = fieldOfViewScale(lens.info) / reference;
      low = Math.min(low, lens.minZoom());
      high = Math.max(high, lens.maxZoom());
    }
    min = low;
    max = high;
  }

  Lens select(float zoom) {
    Lens logical = null;
    for (Lens lens : lenses)
      if (lens.logical
          && zoom >= lens.minZoom()
          && zoom <= lens.maxZoom()
          && (logical == null || Math.abs(lens.base - 1) < Math.abs(logical.base - 1)))
        logical = lens;
    if (logical != null) return logical;
    Lens best = null;
    for (Lens lens : lenses) {
      if (zoom < lens.minZoom() - .001f || zoom > lens.maxZoom() + .001f) continue;
      if (best == null || lens.base > best.base) best = lens;
    }
    return best == null && !lenses.isEmpty() ? lenses.get(0) : best;
  }

  private static float fieldOfViewScale(CameraCharacteristics c) {
    float[] focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
    SizeF sensor = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
    return focal == null || focal.length == 0 || sensor == null || sensor.getWidth() <= 0
        ? 1
        : focal[0] / sensor.getWidth();
  }
}
