package com.pickq;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;
import org.json.JSONObject;

final class ExplanationImport {
  static final int REQUEST = 5;
  private static final String STATE_KEY = "explanationBankId";
  private final MainActivity activity;
  private String bankId;

  ExplanationImport(MainActivity activity) {
    this.activity = activity;
  }

  void saveState(Bundle state) {
    if (bankId != null) state.putString(STATE_KEY, bankId);
  }

  void restore(Bundle state) {
    if (state != null) bankId = state.getString(STATE_KEY);
  }

  void chooseFile(JSONObject bank) {
    bankId = bank.optString("id");
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
        .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
          SharedWorkbookProvider.MIME, "application/vnd.ms-excel", "application/octet-stream"
        });
    try {
      activity.startActivityForResult(intent, REQUEST);
    } catch (ActivityNotFoundException e) {
      bankId = null;
      activity.toast("找不到可打开 Excel 的应用");
    }
  }

  void complete(Uri uri) {
    String id = bankId;
    bankId = null;
    if (uri == null || id == null) return;
    new Thread(() -> {
      try {
        List<Map<String, String>> rows =
            ExcelImporter.readForPreview(activity.getContentResolver().openInputStream(uri));
        activity.runOnUiThread(() -> {
          if (activity.isFinishing() || activity.isDestroyed()) return;
          JSONObject current = findBank(id);
          if (current == null) {
            activity.toast("找不到要更新的题库");
            return;
          }
          preview(current, ExplanationMatches.find(current.optJSONArray("questions"), rows));
        });
      } catch (Exception e) {
        activity.runOnUiThread(() -> activity.toast("无法读取解析文件：" + e.getMessage()));
      }
    }, "pickq-explanation-import").start();
  }

  private JSONObject findBank(String id) {
    for (int i = 0; i < activity.banks.length(); i++) {
      JSONObject bank = activity.banks.optJSONObject(i);
      if (id.equals(bank.optString("id"))) return bank;
    }
    return null;
  }

  private void preview(JSONObject bank, ExplanationMatches matches) {
    LinearLayout content = activity.settingsContent();
    content.addView(activity.label(bank.optString("name") + "\n可新增 " + matches.additions
        + " · 可覆盖 " + matches.replacements + " · 无变化 " + matches.unchanged
        + "\n空白解析 " + matches.blank + " · 未匹配或冲突 " + matches.issues.size(), 15));
    CheckBox overwrite = new CheckBox(activity);
    overwrite.setText("覆盖已有解析");
    if (matches.replacements > 0) content.addView(overwrite);
    if (!matches.changes.isEmpty()) {
      content.addView(activity.label("解析预览（最多显示 8 条）", 15));
      for (int i = 0; i < Math.min(8, matches.changes.size()); i++) {
        ExplanationMatches.Change change = matches.changes.get(i);
        TextView example = activity.label((change.replacesExisting() ? "可覆盖 · " : "新增 · ")
            + change.question.optString("title") + "\n" + change.text, 14);
        example.setMaxLines(4);
        example.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(example);
      }
    }
    for (int i = 0; i < Math.min(8, matches.issues.size()); i++)
      content.addView(activity.label(matches.issues.get(i), 13));
    AlertDialog dialog = activity.settingsDialog("导入解析预览", content, "应用", null);
    dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (android.content.DialogInterface.OnClickListener) null);
    dialog.setOnShowListener(ignored -> {
      android.widget.Button apply = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
      apply.setEnabled(matches.additions > 0);
      overwrite.setOnCheckedChangeListener((button, checked) ->
          apply.setEnabled(matches.additions > 0 || (checked && matches.replacements > 0)));
      apply.setOnClickListener(view -> {
        if (findBank(bank.optString("id")) != bank) {
          activity.toast("题库已变更，请重新选择文件");
          dialog.dismiss();
          return;
        }
        int count = apply(matches, overwrite.isChecked());
        if (count < 0) return;
        dialog.dismiss();
        activity.bank = bank;
        activity.bankPage();
        activity.toast("已更新 " + count + " 道题的解析");
      });
    });
    dialog.show();
  }

  int apply(ExplanationMatches matches, boolean overwrite) {
    Map<JSONObject, Object> previous = new IdentityHashMap<>();
    for (ExplanationMatches.Change change : matches.changes) {
      if (!overwrite && change.replacesExisting()) continue;
      previous.put(change.question, change.question.opt("explanation"));
      activity.put(change.question, "explanation", change.text);
    }
    if (previous.isEmpty() || activity.save()) return previous.size();
    previous.forEach((question, value) -> activity.put(question, "explanation", value));
    return -1;
  }
}
