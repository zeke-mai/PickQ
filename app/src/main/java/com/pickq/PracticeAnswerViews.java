package com.pickq;

import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class PracticeAnswerViews {
  private PracticeAnswerViews() {}

  static boolean longAnswer(String type) {
    return type.equals("简答题") || type.equals("论述题");
  }

  static void styleLongAnswerInput(MainActivity activity, EditText field) {
    field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    field.setGravity(Gravity.TOP | Gravity.START);
    field.setMinHeight(activity.dp(168));
    field.setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14));
    field.setTextSize(16);
    field.setTextColor(0xff263650);
    field.setBackground(activity.bg(Color.WHITE));
    field.setHint("在此填写答案");
  }

  static void addExplanation(MainActivity activity, LinearLayout body, String explanation) {
    if (explanation.trim().isEmpty()) return;
    addCard(activity, body, "解析", explanation, 0xff263650);
  }

  static void addComparison(MainActivity activity, LinearLayout body, String submitted, String reference) {
    CharSequence[] compared = AnswerDifferences.highlight(submitted, reference);
    addCard(activity, body, "你的答案", submitted.trim().isEmpty() ? "未作答" : compared[0], 0xff315cba);
    addCard(activity, body, "参考答案", reference.trim().isEmpty() ? "未提供" : compared[1], 0xff14683c);
  }

  private static void addCard(
      MainActivity activity, LinearLayout body, String title, CharSequence text, int accent) {
    LinearLayout card = new LinearLayout(activity);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackground(activity.bg(Color.WHITE));
    card.setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(16));
    TextView heading = activity.label(title, 15);
    heading.setTextColor(accent);
    heading.setPadding(0, 0, 0, activity.dp(8));
    heading.setTypeface(null, android.graphics.Typeface.BOLD);
    card.addView(heading);
    TextView content = activity.label("", 16);
    content.setText(text);
    content.setPadding(0, 0, 0, 0);
    card.addView(content);
    body.addView(card, cardLayout(activity));
  }

  static LinearLayout.LayoutParams cardLayout(MainActivity activity) {
    LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
    layout.topMargin = activity.dp(12);
    return layout;
  }
}
