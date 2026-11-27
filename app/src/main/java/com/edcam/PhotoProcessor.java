package com.edcam;

import android.graphics.*;
import java.io.IOException;
import java.util.Random;

public final class PhotoProcessor {
  public static final int MAX_EDGE = 2560;

  public static Bitmap decode(byte[] bytes) throws IOException {
    ImageDecoder.Source source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes));
    return ImageDecoder.decodeBitmap(
        source,
        (decoder, info, input) -> {
          int width = info.getSize().getWidth(), height = info.getSize().getHeight();
          if (width < 1 || height < 1)
            throw new IllegalArgumentException("Kích thước ảnh không hợp lệ.");
          float scale = Math.min(1, MAX_EDGE / (float) Math.max(width, height));
          decoder.setTargetSize(
              Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
          decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
          decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
  }

  public static Bitmap render(
      Bitmap source,
      Presets.Style style,
      float strength,
      float ratio,
      boolean mirror,
      String stamp) {
    int width = source.getWidth(), height = source.getHeight();
    int cropW = width, cropH = height;
    if (ratio > 0) {
      if (width / (float) height > ratio) cropW = Math.round(height * ratio);
      else cropH = Math.round(width / ratio);
    }
    Bitmap output = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(output);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(Presets.matrix(style, strength))));
    canvas.save();
    if (mirror) {
      canvas.translate(cropW, 0);
      canvas.scale(-1, 1);
    }
    canvas.drawBitmap(
        source,
        new Rect(
            (width - cropW) / 2, (height - cropH) / 2, (width + cropW) / 2, (height + cropH) / 2),
        new Rect(0, 0, cropW, cropH),
        p);
    canvas.restore();
    p.setColorFilter(null);
    float grain = style.grain * strength;
    if (grain > .1f) {
      int noiseW = Math.max(1, cropW / 3), noiseH = Math.max(1, cropH / 3);
      int[] noise = new int[noiseW * noiseH];
      Random random = new Random(104729);
      for (int i = 0; i < noise.length; i++) {
        int n = random.nextBoolean() ? 255 : 0;
        int alpha = random.nextInt(Math.max(1, (int) grain * 2));
        noise[i] = Color.argb(alpha, n, n, n);
      }
      Bitmap texture = Bitmap.createBitmap(noise, noiseW, noiseH, Bitmap.Config.ARGB_8888);
      p.setFilterBitmap(false);
      canvas.drawBitmap(texture, null, new Rect(0, 0, cropW, cropH), p);
      texture.recycle();
    }
    if (style.vignette * strength > 0) {
      p.setShader(
          new RadialGradient(
              cropW / 2f,
              cropH / 2f,
              Math.max(cropW, cropH) * .72f,
              new int[] {
                Color.TRANSPARENT, Color.argb((int) (style.vignette * strength * 255), 0, 0, 0)
              },
              new float[] {.3f, 1},
              Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, cropW, cropH, p);
      p.setShader(null);
    }
    if (stamp != null && !stamp.isEmpty()) {
      float font = Math.max(18, cropW * .025f);
      p.setTypeface(Typeface.create("monospace", Typeface.BOLD));
      p.setTextSize(font);
      p.setColor(Color.rgb(255, 227, 177));
      p.setShadowLayer(font * .15f, 0, 2, Color.BLACK);
      String[] lines = stamp.split("\n");
      float y = cropH - font * 1.15f - (lines.length - 1) * font * 1.4f;
      for (String line : lines) {
        canvas.drawText(line, font * 1.1f, y, p);
        y += font * 1.4f;
      }
      p.clearShadowLayer();
    }
    return output;
  }

  private PhotoProcessor() {}
}
