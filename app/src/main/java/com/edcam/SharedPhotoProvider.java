package com.edcam;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only edited JPEGs kept in the private album are eligible for a temporary URI grant. */
public final class SharedPhotoProvider extends ContentProvider {
  static Uri uri(File file) {
    return new Uri.Builder()
        .scheme("content")
        .authority("com.edcam.shared")
        .appendPath(file.getName())
        .build();
  }

  private File file(Uri uri) {
    String name = uri.getLastPathSegment();
    if (uri.getPathSegments().size() != 1
        || name == null
        || !name.matches("[a-fA-F0-9-]{36}\\.jpg"))
      throw new IllegalArgumentException("Invalid photo");
    return new File(new File(getContext().getFilesDir(), "photos"), name);
  }

  public boolean onCreate() {
    return true;
  }

  public String getType(Uri uri) {
    file(uri);
    return "image/jpeg";
  }

  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
    return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  public Cursor query(Uri uri, String[] cols, String selection, String[] args, String order) {
    File photo = file(uri);
    if (cols == null) cols = new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
    MatrixCursor c = new MatrixCursor(cols);
    MatrixCursor.RowBuilder row = c.newRow();
    for (String col : cols)
      row.add(
          OpenableColumns.SIZE.equals(col)
              ? photo.length()
              : OpenableColumns.DISPLAY_NAME.equals(col) ? "Ed-Cam.jpg" : null);
    return c;
  }

  public Uri insert(Uri uri, ContentValues v) {
    throw new UnsupportedOperationException();
  }

  public int delete(Uri uri, String s, String[] args) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri uri, ContentValues v, String s, String[] a) {
    throw new UnsupportedOperationException();
  }
}
