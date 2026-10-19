package com.pickq;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;

/** Exposes only the cached update APK, read-only, with temporary URI grants. */
public final class UpdateProvider extends ContentProvider {
  public boolean onCreate() {
    return true;
  }

  private File file(Uri uri) throws FileNotFoundException {
    if (!uri.getAuthority().equals(getContext().getPackageName() + ".updates")
        || !"/update.apk".equals(uri.getPath())) throw new FileNotFoundException();
    return new File(getContext().getCacheDir(), "update.apk");
  }

  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
    return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  public String getType(Uri uri) {
    return "application/vnd.android.package-archive";
  }

  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
    try {
      File f = file(uri);
      String[] cols = projection != null ? projection : new String[] {"_display_name", "_size"};
      MatrixCursor c = new MatrixCursor(cols);
      Object[] row = new Object[cols.length];
      for (int i = 0; i < cols.length; i++)
        row[i] =
            cols[i].equals("_display_name")
                ? "PickQ-update.apk"
                : cols[i].equals("_size") ? f.length() : null;
      c.addRow(row);
      return c;
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  public int delete(Uri uri, String where, String[] args) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri uri, ContentValues values, String where, String[] args) {
    throw new UnsupportedOperationException();
  }
}
