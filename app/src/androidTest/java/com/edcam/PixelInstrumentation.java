package com.edcam;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.*;
import android.os.Bundle;
import java.io.*;
import java.util.*;

/** Device-side integration checks use only the isolated target application's storage. */
public final class PixelInstrumentation extends Instrumentation {
  private int checks;

  private void check(boolean value, String name) {
    checks++;
    if (!value) throw new AssertionError(name);
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      Bitmap input = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888);
      input.eraseColor(Color.rgb(70, 130, 190));
      Bitmap identity = PhotoProcessor.render(input, Presets.get(0), 1, .75f, false, "");
      check(identity.getWidth() == 120 && identity.getHeight() == 160, "portrait dimensions");
      check(identity.getPixel(60, 80) == input.getPixel(60, 80), "original pixel identity");
      Bitmap zero = PhotoProcessor.render(input, Presets.get(10), 0, .75f, false, "");
      check(
          zero.getPixel(60, 80) == input.getPixel(60, 80),
          "zero intensity disables grain and vignette");
      Bitmap square = PhotoProcessor.render(input, Presets.get(36), 1, 1, false, "");
      check(square.getWidth() == 120 && square.getHeight() == 120, "square crop");
      int pixel = square.getPixel(60, 60);
      check(
          Math.abs(Color.red(pixel) - Color.green(pixel)) <= 1
              && Math.abs(Color.green(pixel) - Color.blue(pixel)) <= 1,
          "monochrome output");
      Bitmap film = PhotoProcessor.render(input, Presets.get(7), 1, .75f, false, "26.11.2026");
      check(film.getPixel(60, 80) != identity.getPixel(60, 80), "film alters actual pixels");
      ByteArrayOutputStream encoded = new ByteArrayOutputStream();
      input.compress(Bitmap.CompressFormat.JPEG, 95, encoded);
      byte[] raw = encoded.toByteArray();
      Bitmap decoded = PhotoProcessor.decode(raw);
      check(decoded.getWidth() == 120, "decode JPEG");
      boolean invalid = false;
      try {
        PhotoProcessor.decode(new byte[] {1, 2, 3});
      } catch (IOException e) {
        invalid = true;
      }
      check(invalid, "reject invalid image");
      File exifFile = File.createTempFile("orientation-", ".jpg", getTargetContext().getCacheDir());
      try {
        try (FileOutputStream out = new FileOutputStream(exifFile)) {
          out.write(raw);
        }
        android.media.ExifInterface exif = new android.media.ExifInterface(exifFile);
        exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6");
        exif.saveAttributes();
        Bitmap rotated = PhotoProcessor.decode(java.nio.file.Files.readAllBytes(exifFile.toPath()));
        check(
            rotated.getWidth() == 160 && rotated.getHeight() == 120, "EXIF 90-degree orientation");
        rotated.recycle();
      } finally {
        java.nio.file.Files.deleteIfExists(exifFile.toPath());
      }
      Bitmap landscape = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
      landscape.eraseColor(Color.BLUE);
      Bitmap uncropped = PhotoProcessor.render(landscape, Presets.get(0), 1, 0, false, "");
      check(
          uncropped.getWidth() == 160 && uncropped.getHeight() == 90,
          "import preserves original aspect ratio");
      landscape.recycle();
      uncropped.recycle();
      PhotoStore store = new PhotoStore(getTargetContext());
      int before = store.list().size();
      PhotoStore.Photo saved =
          store.save(
              film,
              raw,
              Presets.get(7),
              System.currentTimeMillis(),
              "QA synthetic",
              "import",
              Double.NaN,
              Double.NaN,
              0,
              true,
              .75f,
              .7f);
      PhotoStore.Photo persisted =
          store.list().stream().filter(p -> p.id.equals(saved.id)).findFirst().get();
      check(
          persisted.mirrored
              && persisted.cropRatio == .75f
              && persisted.strength == .7f
              && persisted.styleId == 7,
          "preserve capture transform for re-edit");
      check(store.list().size() == before + 1, "persist photo metadata");
      check(saved.original.isFile(), "preserve original bytes");
      check(
          Arrays.equals(raw, java.nio.file.Files.readAllBytes(saved.original.toPath())),
          "original unchanged");
      check(Double.isNaN(saved.lat), "no invented coordinates");
      android.net.Uri uri = store.export(saved, false);
      check(uri != null, "MediaStore export");
      try (InputStream exported = getTargetContext().getContentResolver().openInputStream(uri)) {
        check(exported != null && exported.read() != -1, "export contains actual bytes");
      }
      getTargetContext().getContentResolver().delete(uri, null, null);
      store.delete(saved);
      check(store.list().size() == before, "delete album record");
      check(!saved.original.exists() && !saved.image.exists(), "delete image pair");
      for (Bitmap b : new Bitmap[] {input, identity, zero, square, film, decoded}) b.recycle();
      android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences("edcam", 0);
      int previousRatio = prefs.getInt("ratio", 0);
      prefs.edit().putInt("ratio", 0).commit();
      Activity screen =
          startActivitySync(
              new android.content.Intent(getTargetContext(), MainActivity.class)
                  .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
      try {
        waitForIdleSync();
        final int[] geometry = new int[4];
        runOnMainSync(
            () -> {
              android.view.View decor = screen.getWindow().getDecorView();
              android.view.TextureView preview = findPreview(decor);
              if (preview != null) {
                geometry[0] = preview.getWidth();
                geometry[1] = preview.getHeight();
              }
              geometry[2] = decor.getWidth();
              geometry[3] = decor.getHeight();
            });
        check(
            geometry[0] >= geometry[2] * .9f,
            "camera preview occupies at least 90 percent of screen width at 3:4");
        check(
            geometry[1] >= geometry[3] * .55f,
            "camera preview occupies at least 55 percent of screen height");
      } finally {
        runOnMainSync(screen::finish);
        prefs.edit().putInt("ratio", previousRatio).commit();
      }
      result.putString(
          "stream",
          "PASS: "
              + checks
              + " pixel, persistence, export and full-size camera geometry checks.\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putString(
          "stream",
          "FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(error));
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private static android.view.TextureView findPreview(android.view.View view) {
    if (view instanceof android.view.TextureView) return (android.view.TextureView) view;
    if (view instanceof android.view.ViewGroup) {
      android.view.ViewGroup group = (android.view.ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        android.view.TextureView found = findPreview(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }
}
