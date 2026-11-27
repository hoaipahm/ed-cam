package com.edcam;

import android.content.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Videos stay private until explicitly exported, like photographs. */
public final class VideoStore {
  private final Context context;
  private final File root;

  public VideoStore(Context context) {
    this.context = context.getApplicationContext();
    root = new File(context.getFilesDir(), "videos");
    if (!root.isDirectory() && !root.mkdirs())
      throw new IllegalStateException("Không tạo được thư mục video.");
  }

  public File pending() {
    return new File(root, UUID.randomUUID() + ".pending");
  }

  public File finish(File pending) throws IOException {
    try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
      retriever.setDataSource(pending.getPath());
      String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (duration == null || Long.parseLong(duration) <= 0)
        throw new IOException("Video chưa có dữ liệu.");
    } catch (RuntimeException e) {
      throw new IOException("Video không hợp lệ.", e);
    }
    File saved = new File(root, pending.getName().replace(".pending", ".mp4"));
    Files.move(pending.toPath(), saved.toPath());
    return saved;
  }

  public List<File> list() throws IOException {
    File[] files = root.listFiles((dir, name) -> name.endsWith(".mp4"));
    if (files == null) throw new IOException("Không đọc được video.");
    List<File> videos = new ArrayList<>(Arrays.asList(files));
    videos.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
    return videos;
  }

  public Uri export(File video) throws IOException {
    ContentValues values = new ContentValues();
    values.put(MediaStore.Video.Media.DISPLAY_NAME, "EdCam_" + video.getName());
    values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Ed-Cam");
    values.put(MediaStore.Video.Media.IS_PENDING, 1);
    ContentResolver resolver = context.getContentResolver();
    Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    if (uri == null) throw new IOException("Không tạo được video trong thư viện máy.");
    try {
      try (InputStream in = new FileInputStream(video);
          OutputStream out = resolver.openOutputStream(uri)) {
        if (out == null) throw new IOException("Không mở được thư viện máy.");
        byte[] buffer = new byte[65536];
        int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
      }
      values.clear();
      values.put(MediaStore.Video.Media.IS_PENDING, 0);
      resolver.update(uri, values, null, null);
      return uri;
    } catch (IOException | RuntimeException e) {
      resolver.delete(uri, null, null);
      throw e;
    }
  }
}
