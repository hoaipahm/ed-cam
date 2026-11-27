package com.edcam;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;

/** Focus works with touch and accessibility click actions. */
public final class FocusTextureView extends TextureView {
  public interface FocusListener {
    void focus(float x, float y);
  }

  private FocusListener listener;
  private float x = .5f, y = .5f;
  private ScaleGestureDetector scale;
  private boolean pinching;

  public interface ZoomListener {
    void zoom(float factor);
  }

  public void setZoomListener(ZoomListener listener) {
    scale =
        new ScaleGestureDetector(
            getContext(),
            new ScaleGestureDetector.SimpleOnScaleGestureListener() {
              @Override
              public boolean onScale(ScaleGestureDetector detector) {
                pinching = true;
                listener.zoom(detector.getScaleFactor());
                return true;
              }
            });
  }

  public FocusTextureView(Context context) {
    super(context);
    setClickable(true);
    setFocusable(true);
  }

  public void setFocusListener(FocusListener listener) {
    this.listener = listener;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) pinching = false;
    if (scale != null) scale.onTouchEvent(event);
    if (event.getPointerCount() > 1) pinching = true;
    if (event.getAction() == MotionEvent.ACTION_UP && !pinching) {
      x = Math.max(0, Math.min(1, event.getX() / Math.max(1, getWidth())));
      y = Math.max(0, Math.min(1, event.getY() / Math.max(1, getHeight())));
      performClick();
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    if (listener != null) listener.focus(x, y);
    return true;
  }
}
