package com.pickq;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.math.BigDecimal;
import java.util.*;
import org.json.*;

final class ExamFlow {
  private final MainActivity activity;
  private Map<String, BigDecimal> points = new HashMap<>();
  private boolean grading;

  ExamFlow(MainActivity activity) {
    this.activity = activity;
  }

  void reset() {
    points.clear();
  }

  void setup() {
    LinearLayout box = activity.settingsContent();
    box.setFocusableInTouchMode(true);
    Map<String, ArrayList<JSONObject>> groups = new LinkedHashMap<>();
    JSONArray qs = activity.bank.optJSONArray("questions");
    for (int i = 0; i < qs.length(); i++) {
      JSONObject q = qs.optJSONObject(i);
      if (CutQuestions.contains(q)) continue;
      groups.computeIfAbsent(q.optString("type"), k -> new ArrayList<>()).add(q);
    }
    if (groups.isEmpty()) {
      activity.toast("暂无可抽取的题目，可去斩题库恢复");
      return;
    }
    Map<String, EditText[]> inputs = new LinkedHashMap<>();
    int numeric = android.text.InputType.TYPE_CLASS_NUMBER;
    int decimal = numeric | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
    CheckBox autoScore = new CheckBox(activity);
    autoScore.setText("按总分自动分配");
    activity.settingsRow(box, autoScore, 0xff263650, 0xfff3f5f9);
    LinearLayout totalRow = new LinearLayout(activity);
    EditText examTotal = examField(totalRow, "试卷总分（最多两位小数）", "100", decimal);
    TextView allocationHint = activity.label("每题尽量同分，余下的分值按题型顺序以 0.01 分补齐。", 13);
    totalRow.setVisibility(View.GONE);
    allocationHint.setVisibility(View.GONE);
    box.addView(totalRow);
    box.addView(allocationHint);
    activity.toggle(box, "选项乱序", "examShuffle");
    activity.toggle(box, "判断题选项乱序", "examJudgmentShuffle");
    for (Map.Entry<String, ArrayList<JSONObject>> group : groups.entrySet()) {
      String type = group.getKey();
      int available = group.getValue().size();
      LinearLayout card = new LinearLayout(activity);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(12));
      card.setBackground(activity.bg(0xfff3f5f9));
      LinearLayout.LayoutParams cardLayout = new LinearLayout.LayoutParams(-1, -2);
      cardLayout.topMargin = activity.dp(8);
      box.addView(card, cardLayout);

      LinearLayout heading = new LinearLayout(activity);
      heading.setGravity(Gravity.CENTER_VERTICAL);
      TextView title = activity.label(type, 16);
      title.setTypeface(null, android.graphics.Typeface.BOLD);
      title.setPadding(0, 0, activity.dp(8), 0);
      heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
      TextView badge = activity.label("共 " + available + " 题", 12);
      badge.setTextColor(0xff315cba);
      badge.setBackground(activity.bg(0xffe4ecff));
      badge.setPadding(activity.dp(8), activity.dp(4), activity.dp(8), activity.dp(4));
      heading.addView(badge);
      card.addView(heading);

      LinearLayout row = new LinearLayout(activity);
      row.setBaselineAligned(false);
      EditText count = examField(row, "抽取题数", String.valueOf(available), numeric);
      row.addView(new Space(activity), new LinearLayout.LayoutParams(activity.dp(10), 1));
      EditText score =
          examField(row, "每题分值", "1", numeric | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
      card.addView(row);
      inputs.put(type, new EditText[] {count, score});
    }
    Runnable refreshAllocation = () -> {
      if (!autoScore.isChecked()) return;
      try {
        Map<String, Integer> counts = examCounts(groups, inputs);
        ExamAllocation allocation = new ExamAllocation(examTotal.getText().toString(), counts);
        examTotal.setError(null);
        for (String type : counts.keySet()) {
          int count = counts.get(type);
          int extra = allocation.takeExtra(count);
          String perQuestion = count == 0 ? "—" : scoreText(allocation.base);
          if (extra > 0) {
            String higher = scoreText(allocation.base.add(ExamAllocation.CENT));
            perQuestion = extra == count ? higher : perQuestion + " / " + higher;
          }
          inputs.get(type)[1].setText(perQuestion);
        }
      } catch (IllegalArgumentException | ArithmeticException e) {
        for (EditText[] input : inputs.values()) input[1].setText("—");
      }
    };
    autoScore.setOnCheckedChangeListener((button, enabled) -> {
      totalRow.setVisibility(enabled ? View.VISIBLE : View.GONE);
      allocationHint.setVisibility(enabled ? View.VISIBLE : View.GONE);
      for (EditText[] input : inputs.values()) {
        EditText field = input[1];
        if (enabled) {
          field.setTag(field.getText().toString());
          field.setKeyListener(null);
        } else {
          field.setInputType(decimal);
          field.setSingleLine(true);
          field.setText((String) field.getTag());
        }
        field.setEnabled(!enabled);
      }
      refreshAllocation.run();
    });
    android.text.TextWatcher allocationWatcher = new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
      public void onTextChanged(CharSequence s, int start, int before, int count) {}
      public void afterTextChanged(android.text.Editable s) { refreshAllocation.run(); }
    };
    examTotal.addTextChangedListener(allocationWatcher);
    for (EditText[] input : inputs.values()) input[0].addTextChangedListener(allocationWatcher);
    ScrollView sc = new ScrollView(activity);
    sc.addView(box);
    AlertDialog d =
        new AlertDialog.Builder(activity)
            .setTitle("模拟考试组卷")
            .setView(sc)
            .setPositiveButton("开始考试", null)
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      ArrayList<JSONObject> chosen = new ArrayList<>();
                      Map<String, BigDecimal> scores = new HashMap<>();
                      try {
                        Map<String, Integer> counts = examCounts(groups, inputs);
                        ExamAllocation allocation = autoScore.isChecked()
                            ? new ExamAllocation(examTotal.getText().toString(), counts) : null;
                        for (String t : groups.keySet()) {
                          int n = counts.get(t);
                          if (n == 0) continue;
                          BigDecimal p = allocation == null
                              ? ExamAllocation.readExamScore(inputs.get(t)[1].getText().toString()) : allocation.base;
                          int extra = allocation == null ? 0 : allocation.takeExtra(n);
                          ArrayList<JSONObject> pool = new ArrayList<>(groups.get(t));
                          Collections.shuffle(pool);
                          for (int i = 0; i < n; i++) {
                            chosen.add(pool.get(i));
                            scores.put(activity.id(pool.get(i)), i < extra ? p.add(ExamAllocation.CENT) : p);
                          }
                        }
                      } catch (IllegalArgumentException | ArithmeticException e) {
                        activity.toast(e.getMessage());
                        return;
                      }
                      activity.questions = chosen;
                      points = scores;
                      Collections.shuffle(activity.questions);
                      activity.examAnswers.clear();
                      activity.pendingAnswers.clear();
                      activity.orders.clear();
                      activity.exam = true;
                      activity.review = false;
                      activity.mistakePractice = false;
                      grading = false;
                      activity.index = 0;
                      d.dismiss();
                      activity.practice();
                    }));
    d.show();
  }

  Map<String, Integer> examCounts(
      Map<String, ArrayList<JSONObject>> groups, Map<String, EditText[]> inputs) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String type : groups.keySet()) {
      int limit = groups.get(type).size();
      try {
        int count = Integer.parseInt(inputs.get(type)[0].getText().toString().trim());
        if (count < 0 || count > limit) throw new NumberFormatException();
        counts.put(type, count);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(type + "题数须为 0–" + limit + " 的整数");
      }
    }
    if (counts.values().stream().noneMatch(count -> count > 0))
      throw new IllegalArgumentException("至少抽取一题");
    return counts;
  }

  String scoreText(BigDecimal score) {
    return score.stripTrailingZeros().toPlainString();
  }

  EditText examField(LinearLayout row, String caption, String value, int inputType) {
    LinearLayout field = new LinearLayout(activity);
    field.setOrientation(LinearLayout.VERTICAL);
    TextView title = activity.label(caption, 13);
    title.setPadding(0, activity.dp(8), 0, activity.dp(4));
    field.addView(title);
    EditText input = new EditText(activity);
    input.setId(View.generateViewId());
    title.setLabelFor(input.getId());
    input.setInputType(inputType);
    input.setSingleLine(true);
    input.setText(value);
    input.setTextSize(16);
    input.setTextColor(0xff263650);
    input.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(8));
    input.setMinimumHeight(activity.dp(48));
    input.setBackground(activity.bg(Color.WHITE));
    field.addView(input, new LinearLayout.LayoutParams(-1, -2));
    row.addView(field, new LinearLayout.LayoutParams(0, -2, 1));
    return input;
  }

  void finish() {
    if (!activity.exam || activity.review || grading) return;
    grading = true;
    gradeNext(0);
  }

  void gradeNext(int start) {
    for (int i = start; i < activity.questions.size(); i++) {
      JSONObject q = activity.questions.get(i);
      String answer = activity.examAnswers.getOrDefault(activity.id(q), "");
      if (!answer.isEmpty() && activity.manual(q)) {
        final int next = i + 1;
        new AlertDialog.Builder(activity)
            .setCancelable(false)
            .setTitle("主观题人工判分")
            .setMessage(q.optString("title") + "\n你的答案：" + activity.displayedChoice(q, answer)
                + "\n参考：" + q.optString("answer"))
            .setPositiveButton("答对", (d, w) -> {
              gradeOne(q, true);
              gradeNext(next);
            })
            .setNegativeButton("答错", (d, w) -> {
              gradeOne(q, false);
              gradeNext(next);
            })
            .show();
        return;
      }
      gradeOne(q, !answer.isEmpty() && activity.correct(q, answer));
    }
    BigDecimal score = BigDecimal.ZERO, total = BigDecimal.ZERO;
    int right = 0, answered = 0;
    for (JSONObject q : activity.questions) {
      BigDecimal point = points.get(activity.id(q));
      total = total.add(point);
      if (!activity.answer(q).trim().isEmpty()) answered++;
      if (q.optBoolean("examRight")) {
        score = score.add(point);
        right++;
      }
    }
    ExamHistory.add(activity, scoreText(score), scoreText(total), activity.questions.size(), answered, right);
    activity.review = true;
    grading = false;
    activity.index = 0;
    activity.save();
    activity.practice();
    LinearLayout box = activity.settingsContent();
    TextView result = activity.label(scoreText(score) + " / " + scoreText(total) + " 分", 26);
    result.setGravity(Gravity.CENTER);
    result.setTextColor(0xff315cba);
    result.setBackground(activity.bg(0xffeaf0ff));
    box.addView(result, new LinearLayout.LayoutParams(-1, -2));
    activity.settingsDialog("考试成绩", box, "查看试卷", null).show();
  }

  void showHistory() {
    ExamHistory.show(activity);
  }

  void gradeOne(JSONObject q, boolean ok) {
    activity.put(q, "examRight", ok);
    if (!ok) activity.put(q, "wrong", q.optInt("wrong") + 1);
  }

}
