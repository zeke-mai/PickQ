package com.pickq;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

final class PracticeResults {
  private PracticeResults() {}

  static boolean complete(MainActivity activity) {
    if (activity.exam || activity.questions.isEmpty()) return false;
    for (JSONObject q : activity.questions) if (activity.answer(q).isEmpty()) return false;
    return true;
  }

  static void show(MainActivity activity) {
    int right = 0, graded = 0;
    int total = activity.questions.size();
    for (JSONObject q : activity.questions) {
      if (!q.has(activity.rightKey())) continue;
      graded++;
      if (q.optBoolean(activity.rightKey())) right++;
    }
    LinearLayout box = activity.settingsContent();
    View accuracy = new AccuracyRing(activity, right, graded, true);
    LinearLayout.LayoutParams ringLayout = new LinearLayout.LayoutParams(activity.dp(132), activity.dp(132));
    ringLayout.gravity = Gravity.CENTER_HORIZONTAL;
    box.addView(accuracy, ringLayout);
    TextView counts = activity.label("共 " + total + " 题  ·  答对 " + right + " 题  ·  答错 "
        + (graded - right) + " 题" + (graded == total ? "" : "\n未判分 " + (total - graded) + " 题"), 15);
    counts.setGravity(Gravity.CENTER);
    box.addView(counts);
    activity.settingsDialog("练习成绩", box, "查看题目", null).show();
  }

  static View overviewAccuracy(MainActivity activity) {
    int graded = 0, right = 0;
    for (JSONObject q : activity.questions) {
      if (activity.answer(q).isEmpty()) continue;
      if (!activity.exam && !q.has(activity.rightKey())) continue;
      graded++;
      if (q.optBoolean(activity.exam ? "examRight" : activity.rightKey())) right++;
    }
    return new AccuracyRing(activity, right, graded, false);
  }
}
