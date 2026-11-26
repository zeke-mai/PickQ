package com.pickq;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

final class CutQuestions {
  private static final String KEY = "cut";
  private final MainActivity activity;

  CutQuestions(MainActivity activity) {
    this.activity = activity;
  }

  static boolean contains(JSONObject question) {
    return question.optBoolean(KEY);
  }

  int count(JSONArray questions) {
    int count = 0;
    for (int i = 0; i < questions.length(); i++)
      if (contains(questions.optJSONObject(i))) count++;
    return count;
  }

  boolean setCut(JSONObject question, boolean cut) {
    Object previous = question.opt(KEY);
    activity.put(question, KEY, cut ? Boolean.TRUE : null);
    if (activity.save()) return true;
    activity.put(question, KEY, previous);
    return false;
  }

  void show() {
    activity.screen = "cut";
    activity.shell("斩题库");
    Button back = activity.button("‹", activity::bankPage);
    back.setContentDescription("返回题库");
    activity.bar.addView(back, 0);
    JSONArray questions = activity.bank.optJSONArray("questions");
    boolean empty = true;
    for (int i = 0; i < questions.length(); i++) {
      JSONObject question = questions.optJSONObject(i);
      if (!contains(question)) continue;
      empty = false;
      LinearLayout row = new LinearLayout(activity);
      row.setGravity(android.view.Gravity.CENTER_VERTICAL);
      row.setBackground(activity.bg(android.graphics.Color.WHITE));
      TextView title = activity.label(question.optString("title"), 15);
      title.setMaxLines(2);
      title.setEllipsize(android.text.TextUtils.TruncateAt.END);
      title.setOnClickListener(view -> activity.showMistake(question, "斩题详情"));
      row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(activity.button("恢复", () -> {
        if (!setCut(question, false)) return;
        show();
        activity.toast("已恢复到练习和模拟考试");
      }));
      LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
      layout.bottomMargin = activity.dp(6);
      activity.body.addView(row, layout);
    }
    if (empty) activity.body.addView(activity.label("暂无斩题", 15));
  }
}
