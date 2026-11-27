package com.edcam;

import android.content.Context;
import android.view.MotionEvent;
import android.view.TextureView;

/** Focus works with touch and accessibility click actions. */
public final class FocusTextureView extends TextureView {
  public interface FocusListener {
    void focus(float x, float y);
  }

  private FocusListener listener;
  private float x = .5f, y = .5f;

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
    if (event.getAction() == MotionEvent.ACTION_UP) {
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
