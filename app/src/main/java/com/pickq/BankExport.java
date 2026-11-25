package com.pickq;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

final class BankExport {
  static final int REQUEST = 4;
  private static final String STATE_KEY = "exportBankId";
  private final MainActivity activity;
  private String bankId;

  BankExport(MainActivity activity) {
    this.activity = activity;
  }

  void saveState(Bundle state) {
    if (bankId != null) state.putString(STATE_KEY, bankId);
  }

  void restore(Bundle state) {
    if (state != null) bankId = state.getString(STATE_KEY);
  }

  void showOptions(JSONObject bank) {
    new AlertDialog.Builder(activity)
        .setTitle("导出题库")
        .setItems(new String[] {"保存 Excel 到本机", "分享 Excel"}, (dialog, choice) -> {
          if (choice == 0) chooseDestination(bank);
          else share(bank);
        })
        .setNegativeButton("取消", null)
        .show();
  }

  private String fileName(JSONObject bank) {
    String name = bank.optString("name", "题库").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
    if (name.isEmpty()) name = "题库";
    // Stay within common filesystem filename limits, including for Unicode names.
    int length = Math.min(60, name.codePointCount(0, name.length()));
    return name.substring(0, name.offsetByCodePoints(0, length)) + ".xlsx";
  }

  void chooseDestination(JSONObject bank) {
    bankId = bank.optString("id");
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(SharedWorkbookProvider.MIME)
        .putExtra(Intent.EXTRA_TITLE, fileName(bank));
    try {
      activity.startActivityForResult(intent, REQUEST);
    } catch (ActivityNotFoundException e) {
      bankId = null;
      activity.toast("找不到可保存文件的应用");
    }
  }

  private void share(JSONObject bank) {
    String snapshot = bank.optJSONArray("questions").toString();
    String fileName = fileName(bank);
    String token = UUID.randomUUID().toString();
    new Thread(() -> {
      File root = new File(activity.getCacheDir(), SharedWorkbookProvider.CACHE_DIR);
      File folder = new File(root, token);
      File workbook = new File(folder, fileName);
      try {
        pruneOldShares(root);
        if (!folder.mkdirs()) throw new IOException("无法创建分享文件");
        JSONArray questions = new JSONArray(snapshot);
        BankWorkbook.write(new FileOutputStream(workbook), questions);
      } catch (Exception e) {
        workbook.delete();
        folder.delete();
        activity.runOnUiThread(() -> activity.toast("导出失败：" + e.getMessage()));
        return;
      }
      Uri uri = new Uri.Builder().scheme("content")
          .authority(activity.getPackageName() + ".workbooks")
          .appendPath(token).appendPath(fileName).build();
      activity.runOnUiThread(() -> {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
          activity.startActivity(shareChooser(uri));
        } catch (ActivityNotFoundException e) {
          activity.toast("找不到可分享文件的应用");
        }
      });
    }, "pickq-share").start();
  }

  static Intent shareChooser(Uri uri) {
    Intent send = new Intent(Intent.ACTION_SEND)
        .setType(SharedWorkbookProvider.MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    send.setClipData(ClipData.newRawUri("题库 Excel", uri));
    return Intent.createChooser(send, "分享题库");
  }

  private static void pruneOldShares(File root) {
    File[] folders = root.listFiles();
    if (folders == null) return;
    long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
    for (File folder : folders) {
      if (!SharedWorkbookProvider.isToken(folder.getName()) || folder.lastModified() >= cutoff) continue;
      File[] files = folder.listFiles();
      if (files == null) continue;
      for (File file : files) if (file.isFile()) file.delete();
      folder.delete();
    }
  }

  void complete(Uri destination) {
    String selected = bankId;
    bankId = null;
    if (destination == null || selected == null) return;
    for (int i = 0; i < activity.banks.length(); i++) {
      JSONObject bank = activity.banks.optJSONObject(i);
      if (!selected.equals(bank.optString("id"))) continue;
      String snapshot = bank.optJSONArray("questions").toString();
      new Thread(() -> write(destination, snapshot), "pickq-export").start();
      return;
    }
    activity.toast("找不到要导出的题库");
  }

  private void write(Uri destination, String snapshot) {
    String message;
    try (OutputStream stream = activity.getContentResolver().openOutputStream(destination, "wt")) {
      if (stream == null) throw new IOException("无法打开保存位置");
      BankWorkbook.write(stream, new JSONArray(snapshot));
      message = "题库已导出";
    } catch (Exception e) {
      message = "导出失败：" + e.getMessage();
    }
    String result = message;
    activity.runOnUiThread(() -> activity.toast(result));
  }
}
