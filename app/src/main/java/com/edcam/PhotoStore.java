package com.edcam;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public final class PhotoStore {
  public static final class Photo {
    public String id, style, place, source, exportedUri = "";
    public long time;
    public double lat = Double.NaN, lon = Double.NaN;
    public float accuracy;
    public int width, height;
    public boolean hasOriginal;
    public boolean mirrored;
    public float cropRatio, strength = 1;
    public int styleId;
    public File image, original;

    JSONObject json() throws JSONException {
      JSONObject j = new JSONObject();
      j.put("id", id);
      j.put("style", style);
      j.put("place", place);
      j.put("source", source);
      j.put("time", time);
      j.put("width", width);
      j.put("height", height);
      j.put("hasOriginal", hasOriginal);
      j.put("exportedUri", exportedUri);
      j.put("mirrored", mirrored);
      j.put("cropRatio", cropRatio);
      j.put("strength", strength);
      j.put("styleId", styleId);
      if (!Double.isNaN(lat)) {
        j.put("lat", lat);
        j.put("lon", lon);
        j.put("accuracy", accuracy);
      }
      return j;
    }
  }

  private final Context context;
  private final File root;

  public PhotoStore(Context context) {
    this.context = context.getApplicationContext();
    root = new File(context.getFilesDir(), "photos");
    if (!root.exists() && !root.mkdirs())
      throw new IllegalStateException("Không tạo được thư mục ảnh.");
  }

  public Photo save(
      Bitmap bitmap,
      byte[] original,
      Presets.Style style,
      long time,
      String place,
      String source,
      double lat,
      double lon,
      float accuracy,
      boolean mirrored,
      float cropRatio,
      float strength)
      throws IOException, JSONException {
    Photo photo = new Photo();
    photo.id = UUID.randomUUID().toString();
    photo.time = time;
    photo.place = place;
    photo.style = style.name;
    photo.source = source;
    photo.lat = lat;
    photo.lon = lon;
    photo.accuracy = accuracy;
    photo.mirrored = mirrored;
    photo.cropRatio = cropRatio;
    photo.strength = strength;
    photo.styleId = style.id;
    photo.width = bitmap.getWidth();
    photo.height = bitmap.getHeight();
    photo.image = new File(root, photo.id + ".jpg");
    photo.original = new File(root, photo.id + ".original");
    try {
      try (FileOutputStream out = new FileOutputStream(photo.image)) {
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out))
          throw new IOException("Không mã hóa được ảnh.");
      }
      if (original != null) {
        try (FileOutputStream out = new FileOutputStream(photo.original)) {
          out.write(original);
        }
        photo.hasOriginal = true;
      }
      write(photo);
      return photo;
    } catch (IOException | JSONException e) {
      Files.deleteIfExists(photo.image.toPath());
      Files.deleteIfExists(photo.original.toPath());
      throw e;
    }
  }

  private void write(Photo p) throws IOException, JSONException {
    File target = new File(root, p.id + ".json"), tmp = new File(root, p.id + ".tmp");
    try (Writer out =
        new OutputStreamWriter(
            new FileOutputStream(tmp), java.nio.charset.StandardCharsets.UTF_8)) {
      out.write(p.json().toString());
    }
    Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }

  public List<Photo> list() throws IOException {
    File[] files = root.listFiles((dir, name) -> name.endsWith(".json"));
    if (files == null) throw new IOException("Không đọc được album.");
    List<Photo> photos = new ArrayList<>();
    for (File file : files) {
      try {
        JSONObject j =
            new JSONObject(
                new String(
                    Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
        Photo p = new Photo();
        p.id = j.getString("id");
        if (!p.id.matches("[a-f0-9-]{36}")) continue;
        p.time = j.getLong("time");
        p.style = j.optString("style");
        p.place = j.optString("place");
        p.source = j.optString("source", "camera");
        p.width = j.optInt("width");
        p.height = j.optInt("height");
        p.hasOriginal = j.optBoolean("hasOriginal");
        p.mirrored = j.optBoolean("mirrored", false);
        p.cropRatio = (float) j.optDouble("cropRatio", 0);
        p.strength = (float) j.optDouble("strength", 1);
        p.styleId = j.optInt("styleId", 0);
        p.exportedUri = j.optString("exportedUri");
        p.lat = j.optDouble("lat", Double.NaN);
        p.lon = j.optDouble("lon", Double.NaN);
        p.accuracy = (float) j.optDouble("accuracy", 0);
        p.image = new File(root, p.id + ".jpg");
        p.original = new File(root, p.id + ".original");
        if (p.image.isFile()) photos.add(p);
      } catch (JSONException ignored) {
        /* Skip damaged entries; never synthesize metadata. */
      }
    }
    photos.sort((a, b) -> Long.compare(b.time, a.time));
    return photos;
  }

  public void delete(Photo photo) throws IOException {
    Files.deleteIfExists(photo.original.toPath());
    Files.deleteIfExists(photo.image.toPath());
    Files.deleteIfExists(new File(root, photo.id + ".json").toPath());
  }

  public Uri export(Photo photo, boolean original) throws IOException, JSONException {
    File file = original ? photo.original : photo.image;
    if (!file.isFile()) throw new IOException("Không tìm thấy ảnh.");
    String name =
        "EdCam_"
            + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(photo.time))
            + "_"
            + photo.id.substring(0, 6)
            + (original ? "_original" : "")
            + ".jpg";
    String mimeType = original ? mime(file) : "image/jpeg";
    String extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
    if (extension != null) name = name.substring(0, name.length() - 4) + "." + extension;
    ContentValues values = new ContentValues();
    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
    values.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Ed-Cam");
    values.put(MediaStore.Images.Media.DATE_TAKEN, photo.time);
    values.put(MediaStore.Images.Media.IS_PENDING, 1);
    ContentResolver resolver = context.getContentResolver();
    Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    if (uri == null) throw new IOException("Không tạo được ảnh trong thư viện máy.");
    try {
      try (OutputStream out = resolver.openOutputStream(uri);
          InputStream in = new FileInputStream(file)) {
        if (out == null) throw new IOException("Không mở được thư viện.");
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      }
      ContentValues ready = new ContentValues();
      ready.put(MediaStore.Images.Media.IS_PENDING, 0);
      resolver.update(uri, ready, null, null);
      if (!original) {
        photo.exportedUri = uri.toString();
        write(photo);
      }
      return uri;
    } catch (IOException | RuntimeException | JSONException e) {
      resolver.delete(uri, null, null);
      throw e;
    }
  }

  private String mime(File file) throws IOException {
    try (InputStream in = new FileInputStream(file)) {
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(in, null, o);
      return o.outMimeType == null ? "image/jpeg" : o.outMimeType;
    }
  }

  public static Bitmap thumbnail(File file, int edge) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getPath(), bounds);
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inSampleSize = 1;
    while (Math.max(bounds.outWidth, bounds.outHeight) / o.inSampleSize > edge * 2)
      o.inSampleSize *= 2;
    return BitmapFactory.decodeFile(file.getPath(), o);
  }
}
