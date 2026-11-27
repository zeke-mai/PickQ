package com.pickq;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import org.json.JSONArray;
import org.json.JSONObject;

final class ExamHistory {
  private static final String KEY = "examHistory";

  private ExamHistory() {}

  static void add(MainActivity activity, String score, String total, int count, int answered, int right) {
    JSONObject record = new JSONObject();
    activity.put(record, "time", System.currentTimeMillis());
    activity.put(record, "score", score);
    activity.put(record, "total", total);
    activity.put(record, "count", count);
    activity.put(record, "answered", answered);
    activity.put(record, "right", right);
    JSONArray history = activity.bank.optJSONArray(KEY);
    if (history == null) {
      history = new JSONArray();
      activity.put(activity.bank, KEY, history);
    }
    history.put(record);
  }

  static void show(MainActivity activity) {
    activity.screen = "examHistory";
    activity.shell("考试记录");
    Button back = activity.button("‹", activity::bankPage);
    back.setContentDescription("返回题库");
    activity.bar.addView(back, 0);
    JSONArray history = activity.bank.optJSONArray(KEY);
    if (history == null || history.length() == 0) {
      activity.body.addView(activity.label("暂无考试记录", 15));
      return;
    }
    DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    for (int i = history.length() - 1; i >= 0; i--) {
      JSONObject record = history.optJSONObject(i);
      LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));
      card.setBackground(activity.bg(android.graphics.Color.WHITE));
      TextView score = activity.label(record.optString("score") + " / " + record.optString("total") + " 分", 22);
      score.setTextColor(0xff315cba);
      score.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), 0);
      card.addView(score);
      card.addView(activity.label(format.format(new Date(record.optLong("time"))) + "\n已答 "
          + record.optInt("answered") + "/" + record.optInt("count")
          + " 题 · 答对 " + record.optInt("right") + " 题", 14));
      activity.body.addView(card, PracticeAnswerViews.cardLayout(activity));
    }
  }
}
