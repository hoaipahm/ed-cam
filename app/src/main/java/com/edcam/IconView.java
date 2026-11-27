package com.edcam;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A single 24-unit stroke vocabulary, with native accessibility labels on each action. */
public final class IconView extends View {
  private final String icon;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();

  public IconView(Context context) {
    this(context, "camera", android.graphics.Color.BLACK);
  }

  public IconView(Context context, String icon, int color) {
    super(context);
    this.icon = icon;
    paint.setColor(color);
    paint.setStrokeWidth(1.65f);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  private void line(Canvas c, float... points) {
    Path p = path;
    p.reset();
    p.moveTo(points[0], points[1]);
    for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
    c.drawPath(p, paint);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save();
    float size = Math.min(getWidth(), getHeight());
    c.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
    c.scale(size / 24, size / 24);
    switch (icon) {
      case "camera":
        c.drawRoundRect(3, 7, 21, 20, 3, 3, paint);
        c.drawCircle(12, 13, 4, paint);
        line(c, 7, 7, 9, 4, 15, 4, 17, 7);
        break;
      case "album":
        c.drawRoundRect(3, 3, 21, 21, 3, 3, paint);
        c.drawCircle(8, 8, 1.4f, paint);
        line(c, 4, 18, 10, 12, 14, 16, 17, 12, 21, 17);
        break;
      case "ratio":
        c.drawRoundRect(5, 3, 19, 21, 2, 2, paint);
        line(c, 9, 3, 9, 7);
        line(c, 15, 17, 15, 21);
        break;
      case "styles":
        c.drawCircle(9, 9, 5, paint);
        c.drawCircle(15, 9, 5, paint);
        c.drawCircle(12, 15, 5, paint);
        break;
      case "settings":
        c.drawCircle(12, 12, 4, paint);
        for (int i = 0; i < 8; i++) {
          c.save();
          c.rotate(i * 45, 12, 12);
          line(c, 12, 2, 12, 5);
          c.restore();
        }
        break;
      case "theme":
        c.drawCircle(12, 12, 9, paint);
        c.drawArc(6, 6, 18, 18, -90, 180, false, paint);
        line(c, 12, 3, 12, 21);
        break;
      case "flip":
        c.drawArc(4, 4, 20, 20, 205, 135, false, paint);
        c.drawArc(4, 4, 20, 20, 25, 135, false, paint);
        line(c, 17, 3, 20, 8, 15, 8);
        line(c, 7, 21, 4, 16, 9, 16);
        break;
      case "flash":
        line(c, 14, 2, 5, 14, 11, 14, 10, 22, 19, 10, 13, 10, 14, 2);
        break;
      case "timer":
        c.drawCircle(12, 14, 8, paint);
        line(c, 9, 2, 15, 2);
        line(c, 12, 6, 12, 2);
        line(c, 12, 9, 12, 14, 15, 16);
        break;
      case "grid":
        c.drawRoundRect(3, 3, 21, 21, 2, 2, paint);
        line(c, 9, 3, 9, 21);
        line(c, 15, 3, 15, 21);
        line(c, 3, 9, 21, 9);
        line(c, 3, 15, 21, 15);
        break;
      case "pin":
        c.drawOval(5, 2, 19, 16, paint);
        line(c, 6, 13, 12, 22, 18, 13);
        c.drawCircle(12, 9, 2.3f, paint);
        break;
      case "back":
        line(c, 14, 5, 7, 12, 14, 19);
        break;
      case "plus":
        line(c, 12, 5, 12, 19);
        line(c, 5, 12, 19, 12);
        break;
      case "check":
        line(c, 5, 12, 10, 17, 20, 6);
        break;
      case "close":
        line(c, 6, 6, 18, 18);
        line(c, 18, 6, 6, 18);
        break;
      case "download":
        line(c, 12, 3, 12, 15);
        line(c, 7, 10, 12, 15, 17, 10);
        line(c, 4, 16, 4, 21, 20, 21, 20, 16);
        break;
      case "trash":
        line(c, 4, 6, 20, 6);
        line(c, 9, 6, 9, 3, 15, 3, 15, 6);
        line(c, 6, 6, 7, 21, 17, 21, 18, 6);
        line(c, 10, 10, 10, 17);
        line(c, 14, 10, 14, 17);
        break;
      case "heart":
        Path h = path;
        h.reset();
        h.moveTo(12, 21);
        h.cubicTo(-5, 10, 5, -2, 12, 7);
        h.cubicTo(19, -2, 29, 10, 12, 21);
        c.drawPath(h, paint);
        break;
      case "edit":
        line(c, 4, 17, 16, 5, 20, 9, 8, 21, 4, 21, 4, 17);
        line(c, 14, 7, 18, 11);
        break;
      default:
        c.drawCircle(12, 12, 8, paint);
        line(c, 12, 8, 12, 13);
        c.drawCircle(12, 17, .4f, paint);
    }
    c.restore();
  }
}
