package com.edcam;

/** Each capture owns a token, so callbacks from a cancelled timer cannot trigger a new capture. */
public final class CaptureCountdown {
  private int generation;

  public int begin() {
    return ++generation;
  }

  public void cancel() {
    generation++;
  }

  public boolean accepts(int token) {
    return token == generation;
  }
}
