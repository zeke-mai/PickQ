package com.pickq;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.regex.Pattern;

/** Exposes only the temporary workbook addressed by a granted share URI. */
public final class SharedWorkbookProvider extends ContentProvider {
  static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  static final String CACHE_DIR = "shared-workbooks";
  private static final Pattern TOKEN = Pattern.compile("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");

  static boolean isToken(String value) {
    return TOKEN.matcher(value).matches();
  }

  @Override public boolean onCreate() {
    return true;
  }

  private File workbook(Uri uri) {
    List<String> parts = uri.getPathSegments();
    if (parts.size() != 2 || !isToken(parts.get(0)))
      throw new IllegalArgumentException("Invalid workbook URI");
    String name = parts.get(1);
    if (!name.endsWith(".xlsx") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
        || name.indexOf('\0') >= 0)
      throw new IllegalArgumentException("Invalid workbook name");
    File root = new File(getContext().getCacheDir(), CACHE_DIR);
    return new File(new File(root, parts.get(0)), name);
  }

  @Override public String getType(Uri uri) {
    workbook(uri);
    return MIME;
  }

  @Override public Cursor query(
      Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
    File file = workbook(uri);
    String[] columns = projection == null
        ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
    MatrixCursor cursor = new MatrixCursor(columns, 1);
    if (file.isFile()) {
      Object[] row = new Object[columns.length];
      for (int i = 0; i < columns.length; i++) {
        if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
        if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
      }
      cursor.addRow(row);
    }
    return cursor;
  }

  @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Workbook is read only");
    return ParcelFileDescriptor.open(workbook(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  @Override public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }

  @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
    throw new UnsupportedOperationException();
  }
}
