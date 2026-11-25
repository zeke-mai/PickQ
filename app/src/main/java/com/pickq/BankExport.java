package com.pickq;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.IOException;
import java.io.OutputStream;
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

  void chooseDestination(JSONObject bank) {
    bankId = bank.optString("id");
    String name = bank.optString("name", "题库").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .putExtra(Intent.EXTRA_TITLE, name + ".xlsx");
    try {
      activity.startActivityForResult(intent, REQUEST);
    } catch (ActivityNotFoundException e) {
      bankId = null;
      activity.toast("找不到可保存文件的应用");
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
      BankWorkbook.write(stream, new org.json.JSONArray(snapshot));
      message = "题库已导出";
    } catch (Exception e) {
      message = "导出失败：" + e.getMessage();
    }
    String result = message;
    activity.runOnUiThread(() -> activity.toast(result));
  }
}
