package com.edcam;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Native press feedback without a second rendering layer. */
public final class ShutterButton extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean video, recording;

  public ShutterButton(Context context) {
    super(context);
    setClickable(true);
    setFocusable(true);
  }

  public void state(boolean video, boolean recording) {
    this.video = video;
    this.recording = recording;
    setContentDescription(
        recording ? "Dừng quay video" : video ? "Bắt đầu quay video" : "Chụp ảnh ngay");
    invalidate();
  }

  @Override
  protected void drawableStateChanged() {
    super.drawableStateChanged();
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    float radius =
        Math.min(getWidth(), getHeight()) / 2f - 5 * getResources().getDisplayMetrics().density;
    float cx = getWidth() / 2f, cy = getHeight() / 2f;
    paint.setColor(Color.WHITE);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
    canvas.drawCircle(cx, cy, radius, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(video ? Color.rgb(242, 74, 86) : Color.WHITE);
    float core = radius * (isPressed() ? .69f : .8f);
    if (recording) {
      float half = core * .52f;
      canvas.drawRoundRect(cx - half, cy - half, cx + half, cy + half, 5, 5, paint);
    } else canvas.drawCircle(cx, cy, core, paint);
  }
}
