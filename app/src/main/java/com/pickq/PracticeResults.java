package com.pickq;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONObject;

final class PracticeResults {
  private PracticeResults() {}

  static boolean complete(MainActivity activity) {
    if (activity.exam || activity.questions.isEmpty()) return false;
    for (JSONObject q : activity.questions) if (activity.answer(q).isEmpty()) return false;
    return true;
  }

  static void show(MainActivity activity) {
    int right = 0;
    int total = activity.questions.size();
    for (JSONObject q : activity.questions) if (q.optBoolean(activity.rightKey())) right++;
    LinearLayout box = activity.settingsContent();
    TextView accuracy = activity.label(String.format(Locale.CHINA, "正确率 %.1f%%", right * 100.0 / total), 26);
    accuracy.setGravity(Gravity.CENTER);
    accuracy.setTextColor(0xff315cba);
    accuracy.setBackground(activity.bg(0xffeaf0ff));
    box.addView(accuracy);
    TextView counts = activity.label("共 " + total + " 题  ·  答对 " + right + " 题  ·  答错 " + (total - right) + " 题", 15);
    counts.setGravity(Gravity.CENTER);
    box.addView(counts);
    activity.settingsDialog("练习成绩", box, "查看题目", null).show();
  }

}
