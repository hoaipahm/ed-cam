package com.edcam;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Keeps the current magnification on the shutter axis, including at either endpoint. */
final class ZoomTabs extends LinearLayout {
  interface Listener {
    void zoom(float value);

    void details();
  }

  private final java.text.DecimalFormat format = new java.text.DecimalFormat("0.#");
  private final Listener listener;
  private final TextView[] tabs = new TextView[3];
  private final float[] targets = new float[3];
  private float value = 1, min = 1, max = 1, down, start;
  private boolean fine, dragged;
  private final Runnable hold =
      () -> {
        fine = true;
        invalidate();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
      };

  ZoomTabs(Context context, Listener listener) {
    super(context);
    this.listener = listener;
    setGravity(Gravity.CENTER);
    for (int i = 0; i < 3; i++) {
      final int index = i;
      TextView t = tabs[i] = new TextView(context);
      t.setGravity(Gravity.CENTER);
      t.setTextSize(i == 1 ? 17 : 14);
      t.setTextColor(i == 1 ? 0xffffd45c : Color.WHITE);
      t.setOnClickListener(
          v -> {
            if (index == 1) listener.details();
            else listener.zoom(targets[index]);
          });
      addView(t, new LayoutParams((int) (64 * getResources().getDisplayMetrics().density), -1));
    }
    update(1, 1, 1);
  }

  void update(float current, float low, float high) {
    value = current;
    min = low;
    max = high;
    invalidate();
    List<Float> stops = ZoomMath.stops(min, max);
    targets[0] = targets[2] = Float.NaN;
    targets[1] = value;
    for (float stop : stops) {
      if (stop < value - .025f) targets[0] = stop;
      if (stop > value + .025f) {
        targets[2] = stop;
        break;
      }
    }
    for (int i = 0; i < 3; i++) {
      float n = targets[i];
      TextView t = tabs[i];
      t.setVisibility(Float.isNaN(n) ? INVISIBLE : VISIBLE);
      if (Float.isNaN(n)) continue;
      String label = format.format(n) + "×";
      t.setText(label);
      t.setContentDescription(i == 1 ? "Chỉnh zoom liên tục" : "Zoom " + label);
      t.setSelected(i == 1);
    }
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent e) {
    float x = e.getX();
    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        down = x;
        start = value;
        fine = dragged = false;
        postDelayed(hold, ViewConfiguration.getLongPressTimeout());
        break;
      case MotionEvent.ACTION_MOVE:
        if (Math.abs(x - down) > slop) {
          dragged = true;
          removeCallbacks(hold);
        }
        if (fine && dragged)
          listener.zoom(
              ZoomMath.clamp(
                  (float)
                      (start
                          * Math.exp(
                              (x - down) * Math.log(max / min) / Math.max(1, getWidth() * .7))),
                  min,
                  max));
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        removeCallbacks(hold);
        if (fine || dragged) {
          MotionEvent cancel = MotionEvent.obtain(e);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
          if (e.getActionMasked() == MotionEvent.ACTION_UP && !fine && dragged) {
            float n = targets[x < down ? 2 : 0];
            if (Float.isFinite(n)) listener.zoom(n);
          }
          fine = dragged = false;
          invalidate();
          return true;
        }
        break;
    }
    super.dispatchTouchEvent(e);
    return true;
  }

  private final android.graphics.Paint tickPaint =
      new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

  @Override
  protected void dispatchDraw(android.graphics.Canvas canvas) {
    super.dispatchDraw(canvas);
    if (!fine || max <= min) return;
    float spacing = 12 * getResources().getDisplayMetrics().density;
    float offset = (float) (Math.log(value / min) / Math.log(max / min) * 24 * spacing);
    tickPaint.setColor(0xffdddddd);
    tickPaint.setStrokeWidth(2);
    for (int i = 0; i <= 24; i++) {
      float x = getWidth() / 2f + i * spacing - offset;
      canvas.drawLine(x, getHeight(), x, getHeight() - (i % 4 == 0 ? 14 : 7), tickPaint);
    }
    tickPaint.setColor(0xffffd45c);
    canvas.drawLine(getWidth() / 2f, getHeight(), getWidth() / 2f, getHeight() - 18, tickPaint);
  }

  @Override
  protected void onDetachedFromWindow() {
    removeCallbacks(hold);
    super.onDetachedFromWindow();
  }
}
