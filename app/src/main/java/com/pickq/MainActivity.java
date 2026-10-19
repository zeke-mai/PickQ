package com.pickq;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
  JSONArray banks = new JSONArray();
  JSONObject bank;
  ArrayList<JSONObject> questions = new ArrayList<>();
  Map<String, String> examAnswers = new HashMap<>();
  Map<String, Double> points = new HashMap<>();
  Map<String, List<String>> orders = new HashMap<>();
  int index = 0;
  boolean exam = false, review = false;
  String screen = "home";
  LinearLayout root, body, bar;
  FrameLayout stage;
  boolean downloading;
  View neighborPreview;
  android.content.SharedPreferences prefs;
  String draft = "";
  LinearLayout questionCard;
  TextView progressLabel;
  LinearLayout resultBar;

  void put(JSONObject o, String k, Object v) {
    try {
      o.put(k, v);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  String id(JSONObject q) {
    return q.optString("id");
  }

  String answer(JSONObject q) {
    return exam ? examAnswers.getOrDefault(id(q), "") : q.optString("response");
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("settings", 0);
    try {
      File f = new File(getFilesDir(), "banks.json");
      if (f.exists())
        banks =
            new JSONArray(
                new String(
                    java.nio.file.Files.readAllBytes(f.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception e) {
      toast("题库读取失败：" + e.getMessage());
    }
    home();
    checkUpdate(false);
  }

  void save() {
    try {
      File f = new File(getFilesDir(), "banks.tmp");
      try (FileOutputStream out = new FileOutputStream(f)) {
        out.write(banks.toString().getBytes("UTF-8"));
        out.getFD().sync();
      }
      if (!f.renameTo(new File(getFilesDir(), "banks.json"))) throw new IOException("替换存储失败");
    } catch (Exception e) {
      toast("保存失败：" + e.getMessage());
    }
  }

  int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density);
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  TextView label(String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setLineSpacing(dp(3), 1.08f);
    if (size >= 18)
      t.setTypeface(
          android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    t.setTextColor(Color.rgb(32, 44, 67));
    t.setPadding(dp(12), dp(12), dp(12), dp(12));
    return t;
  }

  GradientDrawable bg(int c) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(c);
    d.setCornerRadius(dp(16));
    return d;
  }

  Button button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setTextColor(0xff315cdb);
    b.setMinWidth(dp(48));
    b.setMinimumWidth(dp(48));
    b.setMinHeight(dp(48));
    b.setMinimumHeight(dp(48));
    b.setPadding(dp(16), dp(10), dp(16), dp(10));
    b.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x22315cdb), bg(0xffeaf0ff), null));
    LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
    layout.setMargins(dp(3), dp(5), dp(3), dp(5));
    b.setLayoutParams(layout);
    b.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
    b.setOnClickListener(v -> r.run());
    return b;
  }

  void shell(String title) {
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(dp(12), dp(8), dp(12), dp(8));
    root.setBackgroundColor(0xfff5f7fc);
    setContentView(root);
    bar = new LinearLayout(this);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(0, dp(6), 0, dp(12));
    TextView t = label(title, 22);
    bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
    root.addView(bar);
    ScrollView scroll = new SwipeScroll();
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    scroll.setFillViewport(true);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
    scroll.addView(body);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
  }

  void card(String title, String sub, Runnable r) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable surface = bg(Color.WHITE);
    surface.setStroke(dp(1), 0xffe4e9f3);
    c.setBackground(
        new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x15315cdb), surface, null));
    c.setPadding(dp(8), dp(10), dp(8), dp(10));
    c.setElevation(dp(1));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(6), 0, dp(6));
    c.setLayoutParams(lp);
    LinearLayout heading = new LinearLayout(this);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    heading.addView(label(title, 18), new LinearLayout.LayoutParams(0, -2, 1));
    TextView arrow = label("›", 24);
    arrow.setTextColor(0xff8592b0);
    heading.addView(arrow);
    c.addView(heading);
    TextView subtitle = label(sub, 13);
    subtitle.setTextColor(0xff77849e);
    c.addView(subtitle);
    c.setOnClickListener(v -> r.run());
    body.addView(c);
  }

  void home() {
    screen = "home";
    shell("我的题库");
    bar.addView(
        button(
            "＋",
            () -> {
              Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
              i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
              i.addCategory(Intent.CATEGORY_OPENABLE);
              startActivityForResult(i, 1);
            }));
    bar.addView(button("⋮", () -> settings()));
    LinearLayout hero = new LinearLayout(this);
    hero.setOrientation(LinearLayout.VERTICAL);
    hero.setPadding(dp(12), dp(12), dp(12), dp(16));
    GradientDrawable gradient =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {0xff315cdb, 0xff7188ed});
    gradient.setCornerRadius(dp(24));
    hero.setBackground(gradient);
    TextView eyebrow = label("P I C K Q   /   专注每一次进步", 12);
    eyebrow.setTextColor(0xffdce6ff);
    hero.addView(eyebrow);
    TextView greeting = label("把每一道题，变成积累。", 23);
    greeting.setTextColor(Color.WHITE);
    hero.addView(greeting);
    TextView summary = label("已导入 " + banks.length() + " 个题库  ·  从今天开始练习", 13);
    summary.setTextColor(0xffe1e9ff);
    hero.addView(summary);
    body.addView(hero);
    body.addView(label("本地题库", 16));
    ArrayList<JSONObject> list = new ArrayList<>();
    for (int i = 0; i < banks.length(); i++) list.add(banks.optJSONObject(i));
    list.sort((a, b) -> Long.compare(b.optLong("visited"), a.optLong("visited")));
    for (JSONObject b : list)
      card(
          b.optString("name"),
          b.optJSONArray("questions").length()
              + " 题 · 导入于 "
              + android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", b.optLong("imported")),
          () -> {
            bank = b;
            put(bank, "visited", System.currentTimeMillis());
            save();
            bankPage();
          });
    if (list.isEmpty())
      card("导入你的第一份题库", "点击右上角 ＋ 选择 Excel (.xlsx)", () -> toast("支持模板列名，非必要列可以省略"));
  }

  protected void onActivityResult(int r, int c, Intent data) {
    super.onActivityResult(r, c, data);
    if (r == 2) {
      if (getPackageManager().canRequestPackageInstalls()) installUpdate();
      return;
    }
    if (r == 3 && c == RESULT_OK && data != null) {
      importUpdate(data.getData());
      return;
    }
    if (r != 1 || c != RESULT_OK || data == null) return;
    Uri uri = data.getData();
    toast("正在读取题库…");
    new Thread(
            () -> {
              try {
                List<Map<String, String>> rows =
                    Xlsx.read(getContentResolver().openInputStream(uri));
                JSONArray qs = new JSONArray();
                for (Map<String, String> row : rows) {
                  JSONObject q = new JSONObject();
                  put(q, "id", UUID.randomUUID().toString());
                  put(q, "title", row.get("题干"));
                  put(q, "type", row.getOrDefault("题型", "单选题"));
                  JSONObject opts = new JSONObject();
                  for (char a = 'A'; a <= 'H'; a++) {
                    String v = row.getOrDefault("选项" + a, "");
                    if (!v.isEmpty()) put(opts, "" + a, v);
                  }
                  put(q, "options", opts);
                  String ans = row.getOrDefault("正确答案", "");
                  if (q.optString("type").equals("填空题") && ans.isEmpty()) {
                    ArrayList<String> values = new ArrayList<>();
                    for (char a = 'A'; a <= 'H'; a++)
                      if (opts.has("" + a)) values.add(opts.optString("" + a));
                    ans = String.join("|", values);
                  }
                  if (q.optString("type").equals("判断题")) {
                    if (opts.length() == 0) {
                      put(opts, "A", "正确");
                      put(opts, "B", "错误");
                    }
                    if (Arrays.asList("正确", "对", "√", "是").contains(ans)) ans = "A";
                    if (Arrays.asList("错误", "错", "×", "否").contains(ans)) ans = "B";
                  }
                  put(q, "answer", ans);
                  put(q, "explanation", row.getOrDefault("解析", ""));
                  put(q, "chapter", row.getOrDefault("章节", ""));
                  put(q, "difficulty", row.getOrDefault("难度", ""));
                  qs.put(q);
                }
                JSONObject b = new JSONObject();
                put(b, "name", "新题库");
                try (android.database.Cursor cur =
                    getContentResolver().query(uri, null, null, null, null)) {
                  if (cur != null && cur.moveToFirst()) {
                    int col = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (col >= 0)
                      put(b, "name", cur.getString(col).replaceFirst("(?i)\\.xlsx$", ""));
                  }
                }
                put(b, "questions", qs);
                put(b, "imported", System.currentTimeMillis());
                put(b, "visited", System.currentTimeMillis());
                runOnUiThread(
                    () -> {
                      banks.put(b);
                      save();
                      home();
                      toast("已导入 " + qs.length() + " 题");
                    });
              } catch (Exception e) {
                runOnUiThread(() -> toast("导入失败：" + e.getMessage()));
              }
            })
        .start();
  }

  void bankPage() {
    screen = "bank";
    shell(bank.optString("name"));
    bar.addView(button("‹", () -> home()), 0);
    bar.getChildAt(0).setContentDescription("返回主界面");
    bar.addView(button("⋮", () -> settings()));
    card("顺序练习", "继续上次进度 · 即时解析", () -> start(false));
    card("模拟考试", "按题型抽题 · 交卷后查看答案", () -> examSetup());
    card("错题集", "查看累计错题与错误次数", () -> wrongPage());
    card(
        "云端题库 · 即将开放",
        "刷新同步与导入入口预留",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("云端题库")
                .setMessage("此功能为预留，当前版本尚未接入云端服务。")
                .setPositiveButton("刷新（暂不可用）", (d, w) -> toast("云端题库尚未开放"))
                .show());
  }

  void start(boolean wrong) {
    questions.clear();
    JSONArray qs = bank.optJSONArray("questions");
    for (int i = 0; i < qs.length(); i++) {
      JSONObject q = qs.optJSONObject(i);
      if (!wrong || q.optInt("wrong") > 0) questions.add(q);
    }
    if (questions.isEmpty()) {
      toast("暂无错题");
      return;
    }
    exam = false;
    review = false;
    orders.clear();
    index = wrong ? 0 : Math.min(bank.optInt("position"), questions.size() - 1);
    practice();
  }

  void wrongPage() {
    screen = "wrong";
    shell("错题集");
    bar.addView(button("返回", () -> bankPage()));
    JSONArray qs = bank.optJSONArray("questions");
    for (int i = 0; i < qs.length(); i++) {
      JSONObject q = qs.optJSONObject(i);
      if (q.optInt("wrong") > 0)
        card(
            q.optString("title"),
            "累计错误 " + q.optInt("wrong") + " 次",
            () -> {
              start(true);
              index = questions.indexOf(q);
              practice();
            });
    }
  }

  boolean choice(JSONObject q) {
    return q.optString("type").contains("选") || q.optString("type").equals("判断题");
  }

  boolean manual(JSONObject q) {
    return Arrays.asList("简答题", "计算题", "论述题").contains(q.optString("type"))
        || q.optString("answer").isEmpty();
  }

  String norm(String s) {
    return s.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  boolean correct(JSONObject q, String a) {
    if (q.optString("type").contains("多选") || q.optString("type").contains("不定项")) {
      char[] x = norm(a).toCharArray(), y = norm(q.optString("answer")).toCharArray();
      Arrays.sort(x);
      Arrays.sort(y);
      return Arrays.equals(x, y);
    }
    return norm(a).equals(norm(q.optString("answer")));
  }

  void practice() {
    screen = "practice";
    JSONObject q = questions.get(index);
    draft = answer(q);
    shell((exam ? "模拟考试" : "顺序练习") + "  " + (index + 1) + "/" + questions.size());
    bar.addView(button("⋮", () -> practiceMenu()));
    body.addView(label(q.optString("type") + " · " + q.optString("chapter"), 13));
    stage = new FrameLayout(this);
    stage.setMinimumHeight(dp(320));
    TextView behind =
        label(
            (index > 0 ? "← " + questions.get(index - 1).optString("title") : "已到第一题")
                + "\n\n"
                + (index + 1 < questions.size()
                    ? questions.get(index + 1).optString("title") + " →"
                    : "已到最后一题"),
            16);
    stage.addView(behind);
    neighborPreview = behind;
    behind.setVisibility(View.INVISIBLE);
    stage.setClipChildren(true);
    LinearLayout front = new LinearLayout(this);
    front.setOrientation(LinearLayout.VERTICAL);
    front.setBackground(bg(Color.WHITE));
    front.setElevation(dp(1));
    front.setPadding(dp(8), dp(8), dp(8), dp(8));
    front.addView(label(q.optString("title"), 21));
    stage.addView(front, new FrameLayout.LayoutParams(-1, -2));
    body.addView(stage);
    questionCard = front;
    if (choice(q)) {
      JSONObject opts = q.optJSONObject("options");
      List<String> keys = orders.get(id(q));
      if (keys == null) {
        keys = new ArrayList<>();
        for (char a = 'A'; a <= 'H'; a++) if (opts.has("" + a)) keys.add("" + a);
        if (prefs.getBoolean(exam ? "examShuffle" : "shuffle", false)) Collections.shuffle(keys);
        orders.put(id(q), keys);
      }
      for (String k : keys) {
        Button b =
            button(
                k + "  " + opts.optString(k),
                () -> {
                  if (review || (!exam && !answer(q).isEmpty())) return;
                  if (multiple(q)) {
                    draft = draft.contains(k) ? draft.replace(k, "") : draft + k;
                  } else draft = k;
                  if (exam) {
                    examAnswers.put(id(q), draft);
                    updateProgress();
                  }
                  if (!multiple(q) && !exam) {
                    refreshOptions(front, q);
                    submit(q);
                  } else {
                    refreshOptions(front, q);
                    updateProgress();
                  }
                });
        b.setTag(k);
        LinearLayout.LayoutParams optionLayout = new LinearLayout.LayoutParams(-1, -2);
        optionLayout.setMargins(0, dp(5), 0, dp(5));
        front.addView(b, optionLayout);
      }
      refreshOptions(front, q);
      if (multiple(q) && !review) {
        front.addView(
            button(
                "全选",
                () -> {
                  if (!exam && !answer(q).isEmpty()) return;
                  StringBuilder all = new StringBuilder();
                  for (char k = 'A'; k <= 'H'; k++) if (opts.has("" + k)) all.append(k);
                  draft = all.toString();
                  if (exam) examAnswers.put(id(q), draft);
                  refreshOptions(front, q);
                  updateProgress();
                }));
      }
    } else {
      EditText e = new EditText(this);
      e.setHint(q.optString("type").equals("填空题") ? "多个空用 | 分隔" : "输入答案；排序题填写原选项字母顺序");
      e.setText(draft);
      e.setEnabled(!review);
      if (q.optString("type").equals("排序题")) {
        JSONObject opts = q.optJSONObject("options");
        for (char a = 'A'; a <= 'H'; a++)
          if (opts.has("" + a)) front.addView(label(a + "  " + opts.optString("" + a), 16));
      }
      e.addTextChangedListener(
          new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

            public void onTextChanged(CharSequence s, int a, int before, int count) {
              draft = s.toString();
              if (exam) {
                examAnswers.put(id(q), draft);
                updateProgress();
              }
            }

            public void afterTextChanged(android.text.Editable s) {}
          });
      front.addView(e);
    }
    if (!review && (!choice(q) || multiple(q)))
      body.addView(button(exam ? "保存答案" : "提交答案", () -> submit(q)));
    if ((!exam && !answer(q).isEmpty()) || review)
      body.addView(
          label("参考答案：" + q.optString("answer") + "\n解析：" + q.optString("explanation"), 16));
    View divider = new View(this);
    divider.setBackgroundColor(0xffcbd5e1);
    root.addView(divider, new LinearLayout.LayoutParams(-1, dp(2)));
    resultBar = new LinearLayout(this);
    resultBar.setGravity(Gravity.CENTER_VERTICAL);
    resultBar.setBackground(bg(Color.WHITE));
    resultBar.setPadding(dp(8), dp(10), dp(8), dp(10));
    root.addView(resultBar);
    updateProgress();
    if (exam && !review)
      resultBar.addView(
          button(
              "交卷",
              () ->
                  new AlertDialog.Builder(this)
                      .setMessage("确认交卷？未答题按零分计。")
                      .setPositiveButton("交卷", (d, w) -> finishExam())
                      .setNegativeButton("继续答题", null)
                      .show()));
  }

  boolean multiple(JSONObject q) {
    return q.optString("type").contains("多选") || q.optString("type").contains("不定项");
  }

  void submit(JSONObject q) {
    if (draft.trim().isEmpty()) {
      toast("请先作答");
      return;
    }
    if (exam) {
      examAnswers.put(id(q), draft);
      updateProgress();
    } else if (!answer(q).isEmpty()) return;
    else if (manual(q)) {
      new AlertDialog.Builder(this)
          .setTitle("参考答案")
          .setMessage(q.optString("answer") + "\n" + q.optString("explanation"))
          .setPositiveButton("我答对了", (d, w) -> record(q, true))
          .setNegativeButton("我答错了", (d, w) -> record(q, false))
          .show();
    } else record(q, correct(q, draft));
  }

  void updateProgress() {
    if (resultBar == null || !screen.equals("practice")) return;
    // Keep the exam submit button while updating the counters in place.
    if (resultBar.getChildCount() >= 3) {
      resultBar.removeViews(0, 3);
    }
    int done = 0, right = 0;
    for (JSONObject x : questions) {
      if (!answer(x).isEmpty()) done++;
      if (exam ? review && x.optBoolean("examRight") : x.optBoolean("right")) right++;
    }
    boolean hidden = exam && !review;
    TextView yes = label("  " + (hidden ? "—" : right), 14);
    yes.setCompoundDrawablesWithIntrinsicBounds(new ResultIcon(true), null, null, null);
    yes.setTextColor(0xff16834a);
    yes.setContentDescription(hidden ? "交卷后显示答对数量" : "答对 " + right + " 题");
    TextView no = label("  " + (hidden ? "—" : (done - right)), 14);
    no.setCompoundDrawablesWithIntrinsicBounds(new ResultIcon(false), null, null, null);
    no.setTextColor(0xffd33443);
    no.setContentDescription(hidden ? "交卷后显示答错数量" : "答错 " + (done - right) + " 题");
    resultBar.addView(yes, 0);
    resultBar.addView(no, 1);
    progressLabel = label("已做 " + done + "/" + questions.size(), 14);
    resultBar.addView(progressLabel, 2, new LinearLayout.LayoutParams(0, -2, 1));
  }

  final class ResultIcon extends android.graphics.drawable.Drawable {
    final boolean ok;

    ResultIcon(boolean value) {
      ok = value;
      setBounds(0, 0, dp(22), dp(22));
    }

    public int getIntrinsicWidth() {
      return dp(22);
    }

    public int getIntrinsicHeight() {
      return dp(22);
    }

    public void draw(android.graphics.Canvas canvas) {
      android.graphics.Paint paint =
          new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
      paint.setColor(ok ? 0xff16834a : 0xffd33443);
      android.graphics.Rect bounds = getBounds();
      canvas.save();
      canvas.translate(bounds.left, bounds.top);
      float unit = bounds.width() / 24f;
      canvas.scale(unit, unit);
      paint.setStyle(android.graphics.Paint.Style.STROKE);
      paint.setStrokeWidth(2);
      paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
      canvas.drawCircle(12, 12, 10, paint);
      if (ok) {
        canvas.drawLine(7, 12, 10.5f, 15.5f, paint);
        canvas.drawLine(10.5f, 15.5f, 17, 8.5f, paint);
      } else {
        canvas.drawLine(8, 8, 16, 16, paint);
        canvas.drawLine(16, 8, 8, 16, paint);
      }
      canvas.restore();
    }

    public void setAlpha(int alpha) {}

    public void setColorFilter(android.graphics.ColorFilter filter) {}

    public int getOpacity() {
      return android.graphics.PixelFormat.TRANSLUCENT;
    }
  }

  final class SwipeScroll extends ScrollView {
    float startX, startY;
    boolean swiping, vertical;

    SwipeScroll() {
      super(MainActivity.this);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
      if (!screen.equals("practice")) return super.dispatchTouchEvent(event);
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        startX = event.getX();
        startY = event.getY();
        swiping = false;
        vertical = false;
      }
      float dx = event.getX() - startX, dy = event.getY() - startY;
      int slop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
      if (action == MotionEvent.ACTION_MOVE && !swiping && !vertical) {
        if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) vertical = true;
        if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
          swiping = true;
          MotionEvent cancel = MotionEvent.obtain(event);
          cancel.setAction(MotionEvent.ACTION_CANCEL);
          super.dispatchTouchEvent(cancel);
          cancel.recycle();
        }
      }
      if (swiping) {
        boolean edge = (dx > 0 && index == 0) || (dx < 0 && index == questions.size() - 1);
        if (neighborPreview != null)
          neighborPreview.setVisibility(edge ? View.INVISIBLE : View.VISIBLE);
        if (questionCard != null) {
          questionCard.animate().cancel();
          if (edge) {
            questionCard.setTranslationX(0);
            questionCard.setPivotX(dx > 0 ? 0 : questionCard.getWidth());
            questionCard.setScaleX(
                1f + Math.min(Math.abs(dx) / Math.max(1, getWidth()), 1f) * 0.045f);
          } else {
            questionCard.setScaleX(1f);
            questionCard.setTranslationX(dx);
          }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
          swiping = false;
          if (questionCard != null)
            questionCard
                .animate()
                .translationX(0)
                .scaleX(1f)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(220);
          if (edge) return true;
          if (action == MotionEvent.ACTION_UP && Math.abs(dx) > dp(70)) move(dx < 0 ? 1 : -1);
        }
        return true;
      }
      return super.dispatchTouchEvent(event);
    }
  }

  void refreshOptions(LinearLayout front, JSONObject q) {
    for (int i = 0; i < front.getChildCount(); i++) {
      View v = front.getChildAt(i);
      if (v.getTag() instanceof String) {
        String key = (String) v.getTag();
        boolean selected = draft.contains(key);
        int c = selected ? 0xffdbe5ff : 0xfff3f5f9;
        if ((!exam && !answer(q).isEmpty()) || review) {
          if (q.optString("answer").contains(key)) c = 0xffc9f0db;
          else if (selected) c = 0xffffd7d7;
        }
        v.setBackground(bg(c));
      }
    }
  }

  void record(JSONObject q, boolean ok) {
    put(q, "response", draft);
    put(q, "right", ok);
    if (!ok) put(q, "wrong", q.optInt("wrong") + 1);
    save();
    practice();
    if (ok && prefs.getBoolean("auto", false) && index + 1 < questions.size())
      new Handler()
          .postDelayed(
              () -> {
                if (screen.equals("practice") && !exam && questions.get(index) == q) move(1);
              },
              400);
  }

  void move(int delta) {
    if (index + delta < 0 || index + delta >= questions.size()) return;
    if (exam && !review && !draft.isEmpty()) examAnswers.put(id(questions.get(index)), draft);
    index += delta;
    if (!exam) {
      put(bank, "position", index);
      save();
    }
    practice();
    body.setTranslationX(dp(delta > 0 ? 200 : -200));
    body.animate().translationX(0).setDuration(240);
  }

  void toggle(LinearLayout box, String title, String key) {
    Switch s = new Switch(this);
    s.setText(title);
    s.setChecked(prefs.getBoolean(key, key.equals("updates")));
    s.setOnCheckedChangeListener(
        (b, v) -> {
          prefs.edit().putBoolean(key, v).apply();
          orders.clear();
        });
    box.addView(s);
  }

  void practiceMenu() {
    LinearLayout box = new LinearLayout(this);
    box.setPadding(dp(20), dp(10), dp(20), dp(10));
    box.setOrientation(LinearLayout.VERTICAL);
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("练习设置")
            .setView(box)
            .setPositiveButton("关闭", (x, w) -> practice())
            .create();
    if (!exam) {
      box.addView(
          button(
              "试题编辑",
              () -> {
                d.dismiss();
                edit();
              }));
      box.addView(
          button(
              "重练本题",
              () -> {
                JSONObject q = questions.get(index);
                q.remove("response");
                q.remove("right");
                save();
                d.dismiss();
                practice();
              }));
      toggle(box, "答对自动跳转", "auto");
    }
    toggle(box, "选项乱序", exam ? "examShuffle" : "shuffle");
    box.addView(
        button(
            "返回题库",
            () -> {
              d.dismiss();
              onBackPressed();
            }));
    d.show();
  }

  void edit() {
    JSONObject q = questions.get(index);
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    ScrollView sc = new ScrollView(this);
    sc.addView(box);
    Map<String, EditText> edits = new LinkedHashMap<>();
    for (String key :
        new String[] {"type", "title", "answer", "explanation", "chapter", "difficulty"}) {
      EditText e = new EditText(this);
      e.setHint(key);
      e.setText(q.optString(key));
      box.addView(e);
      edits.put(key, e);
    }
    for (char k = 'A'; k <= 'H'; k++) {
      EditText e = new EditText(this);
      e.setHint("选项" + k);
      e.setText(q.optJSONObject("options").optString("" + k));
      box.addView(e);
      edits.put("" + k, e);
    }
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("编辑题型 / 题目 / 答案 / 解析")
            .setView(sc)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(-1)
                .setOnClickListener(
                    w -> {
                      String type = edits.get("type").getText().toString().trim();
                      if (edits.get("title").getText().toString().trim().isEmpty()
                          || !Arrays.asList(
                                  "单选题", "多选题", "不定项选择题", "判断题", "填空题", "简答题", "排序题", "计算题", "论述题")
                              .contains(type)) {
                        toast("请填写题目及模板支持的题型");
                        return;
                      }
                      JSONObject opts = new JSONObject();
                      for (Map.Entry<String, EditText> e : edits.entrySet()) {
                        String val = e.getValue().getText().toString();
                        if (e.getKey().length() == 1) {
                          if (!val.trim().isEmpty()) put(opts, e.getKey(), val);
                        } else put(q, e.getKey(), val);
                      }
                      put(q, "options", opts);
                      q.remove("response");
                      q.remove("right");
                      orders.clear();
                      save();
                      d.dismiss();
                      practice();
                    }));
    d.show();
  }

  void examSetup() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    Map<String, ArrayList<JSONObject>> groups = new LinkedHashMap<>();
    JSONArray qs = bank.optJSONArray("questions");
    for (int i = 0; i < qs.length(); i++) {
      JSONObject q = qs.optJSONObject(i);
      groups.computeIfAbsent(q.optString("type"), k -> new ArrayList<>()).add(q);
    }
    Map<String, EditText[]> inputs = new LinkedHashMap<>();
    for (String t : groups.keySet()) {
      box.addView(label(t + " · 共 " + groups.get(t).size() + " 题", 16));
      LinearLayout row = new LinearLayout(this);
      EditText count = new EditText(this), score = new EditText(this);
      count.setHint("题数");
      count.setText("" + groups.get(t).size());
      count.setInputType(2);
      score.setHint("每题分值");
      score.setText("1");
      score.setInputType(8194);
      row.addView(label("题数", 14));
      row.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
      row.addView(label("分值", 14));
      row.addView(score, new LinearLayout.LayoutParams(0, -2, 1));
      box.addView(row);
      inputs.put(t, new EditText[] {count, score});
    }
    toggle(box, "选项乱序", "examShuffle");
    ScrollView sc = new ScrollView(this);
    sc.addView(box);
    AlertDialog d =
        new AlertDialog.Builder(this)
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
                      Map<String, Double> scores = new HashMap<>();
                      try {
                        for (String t : groups.keySet()) {
                          int n = Integer.parseInt(inputs.get(t)[0].getText().toString());
                          double p = Double.parseDouble(inputs.get(t)[1].getText().toString());
                          if (n < 0 || n > groups.get(t).size() || p < 0 || !Double.isFinite(p))
                            throw new Exception();
                          ArrayList<JSONObject> pool = new ArrayList<>(groups.get(t));
                          Collections.shuffle(pool);
                          for (int i = 0; i < n; i++) {
                            chosen.add(pool.get(i));
                            scores.put(id(pool.get(i)), p);
                          }
                        }
                        if (chosen.isEmpty()) throw new Exception();
                      } catch (Exception e) {
                        toast("请填写有效题数和非负分值，至少抽取一题");
                        return;
                      }
                      questions = chosen;
                      points = scores;
                      Collections.shuffle(questions);
                      examAnswers.clear();
                      orders.clear();
                      exam = true;
                      review = false;
                      index = 0;
                      d.dismiss();
                      practice();
                    }));
    d.show();
  }

  void finishExam() {
    gradeNext(0, 0, 0);
  }

  void gradeNext(int pos, double score, double total) {
    if (pos == questions.size()) {
      review = true;
      index = 0;
      save();
      practice();
      new AlertDialog.Builder(this)
          .setTitle("考试成绩 " + score + " / " + total)
          .setMessage("已交卷，可翻题查看答案和解析。")
          .setPositiveButton("查看试卷", null)
          .show();
      return;
    }
    JSONObject q = questions.get(pos);
    String a = examAnswers.getOrDefault(id(q), "");
    double p = points.get(id(q));
    if (!a.isEmpty() && manual(q)) {
      new AlertDialog.Builder(this)
          .setCancelable(false)
          .setTitle("主观题人工判分")
          .setMessage(q.optString("title") + "\n你的答案：" + a + "\n参考：" + q.optString("answer"))
          .setPositiveButton("答对", (d, w) -> gradeOne(pos, true, score, total, p))
          .setNegativeButton("答错", (d, w) -> gradeOne(pos, false, score, total, p))
          .show();
    } else gradeOne(pos, !a.isEmpty() && correct(q, a), score, total, p);
  }

  void gradeOne(int pos, boolean ok, double s, double total, double p) {
    JSONObject q = questions.get(pos);
    put(q, "examRight", ok);
    if (!ok) put(q, "wrong", q.optInt("wrong") + 1);
    gradeNext(pos + 1, s + (ok ? p : 0), total + p);
  }

  void settings() {
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    toggle(box, "启动时检查更新", "updates");
    EditText repo = new EditText(this);
    repo.setHint("GitHub 账号/仓库（公开发布仓库）");
    repo.setText(prefs.getString("repo", BuildConfig.UPDATE_REPO));
    box.addView(repo);
    box.addView(
        button(
            "检查更新",
            () -> {
              prefs.edit().putString("repo", repo.getText().toString().trim()).apply();
              checkUpdate(true);
            }));
    box.addView(
        button(
            "从本地 APK 更新",
            () -> {
              Intent choose =
                  new Intent(Intent.ACTION_OPEN_DOCUMENT)
                      .setType("application/vnd.android.package-archive");
              choose.addCategory(Intent.CATEGORY_OPENABLE);
              startActivityForResult(choose, 3);
            }));
    new AlertDialog.Builder(this)
        .setTitle("总设置 · v" + BuildConfig.VERSION_NAME)
        .setView(box)
        .setPositiveButton(
            "保存",
            (d, w) -> prefs.edit().putString("repo", repo.getText().toString().trim()).apply())
        .show();
  }

  void checkUpdate(boolean user) {
    if (!user && !prefs.getBoolean("updates", true)) return;
    String repo = prefs.getString("repo", BuildConfig.UPDATE_REPO);
    if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
      if (user) toast("请填写 GitHub 账号/仓库");
      return;
    }
    new Thread(
            () -> {
              java.net.HttpURLConnection c = null;
              try {
                c =
                    (java.net.HttpURLConnection)
                        new java.net.URL(
                                "https://api.github.com/repos/" + repo + "/releases/latest")
                            .openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "PickQ");
                if (c.getResponseCode() != 200) throw new IOException("尚无公开版本或网络不可用");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                  byte[] b = new byte[4096];
                  int n;
                  while ((n = in.read(b)) != -1) {
                    if (out.size() > 2 * 1024 * 1024) throw new IOException("响应过大");
                    out.write(b, 0, n);
                  }
                }
                JSONObject release = new JSONObject(out.toString("UTF-8"));
                String tag = release.getString("tag_name").replaceFirst("^v", "");
                if (newer(tag, BuildConfig.VERSION_NAME)) {
                  JSONArray assets = release.optJSONArray("assets");
                  String url = "";
                  if (assets != null)
                    for (int i = 0; i < assets.length(); i++) {
                      JSONObject asset = assets.getJSONObject(i);
                      if (asset.optString("name").endsWith(".apk")) {
                        String candidate = asset.optString("browser_download_url");
                        if (candidate.startsWith(
                            "https://github.com/" + repo + "/releases/download/")) {
                          url = candidate;
                          break;
                        }
                      }
                    }
                  if (url.isEmpty()) throw new IOException("此版本暂无安装包");
                  String download = url;
                  runOnUiThread(
                      () ->
                          new AlertDialog.Builder(this)
                              .setTitle("发现新版本 " + tag)
                              .setMessage("在应用内下载更新，安装后保留题库和练习记录。")
                              .setPositiveButton("下载并安装", (d, w) -> downloadUpdate(download))
                              .setNegativeButton("稍后", null)
                              .show());
                } else if (user) runOnUiThread(() -> toast("当前已是最新版本"));
              } catch (Exception e) {
                if (user) runOnUiThread(() -> toast("检查失败：" + e.getMessage()));
              } finally {
                if (c != null) c.disconnect();
              }
            })
        .start();
  }

  void importUpdate(Uri uri) {
    if (downloading) {
      toast("更新正在处理中");
      return;
    }
    downloading = true;
    toast("正在校验本地安装包…");
    new Thread(
            () -> {
              File part = new File(getCacheDir(), "update.part");
              try {
                try (InputStream in = getContentResolver().openInputStream(uri);
                    FileOutputStream out = new FileOutputStream(part)) {
                  byte[] buffer = new byte[16384];
                  long total = 0;
                  int n;
                  while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > 128 * 1024 * 1024) throw new IOException("安装包过大");
                    out.write(buffer, 0, n);
                  }
                }
                verifyUpdate(part);
                if (!part.renameTo(new File(getCacheDir(), "update.apk")))
                  throw new IOException("无法保存安装包");
                runOnUiThread(
                    () -> {
                      downloading = false;
                      installUpdate();
                    });
              } catch (Exception e) {
                part.delete();
                runOnUiThread(
                    () -> {
                      downloading = false;
                      toast("无法更新：" + e.getMessage());
                    });
              }
            })
        .start();
  }

  void downloadUpdate(String url) {
    if (downloading) {
      toast("更新正在下载中");
      return;
    }
    downloading = true;
    ProgressDialog progress = new ProgressDialog(this);
    progress.setTitle("正在下载更新");
    progress.setMessage("正在连接…");
    progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    progress.setMax(100);
    progress.setCancelable(false);
    progress.show();
    new Thread(
            () -> {
              java.net.HttpURLConnection connection = null;
              File part = new File(getCacheDir(), "update.part"),
                  apk = new File(getCacheDir(), "update.apk");
              try {
                connection = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("User-Agent", "PickQ");
                if (connection.getResponseCode() != 200
                    || !connection.getURL().getProtocol().equals("https"))
                  throw new IOException("下载服务暂不可用");
                long length = connection.getContentLengthLong();
                if (length > 128 * 1024 * 1024) throw new IOException("安装包过大");
                try (InputStream in = connection.getInputStream();
                    FileOutputStream out = new FileOutputStream(part)) {
                  byte[] bytes = new byte[16384];
                  long total = 0;
                  int count, last = -1;
                  while ((count = in.read(bytes)) != -1) {
                    total += count;
                    if (total > 128 * 1024 * 1024) throw new IOException("安装包过大");
                    out.write(bytes, 0, count);
                    int percent = length > 0 ? (int) (100 * total / length) : 0;
                    if (percent != last) {
                      last = percent;
                      runOnUiThread(
                          () -> {
                            progress.setMessage("下载后将校验安装包");
                            progress.setProgress(percent);
                          });
                    }
                  }
                  if (length > 0 && total != length) throw new IOException("下载不完整，请重试");
                }
                verifyUpdate(part);
                if (!part.renameTo(apk)) throw new IOException("无法保存安装包");
                runOnUiThread(
                    () -> {
                      progress.dismiss();
                      downloading = false;
                      installUpdate();
                    });
              } catch (Exception e) {
                part.delete();
                runOnUiThread(
                    () -> {
                      progress.dismiss();
                      downloading = false;
                      toast("更新失败：" + e.getMessage());
                    });
              } finally {
                if (connection != null) connection.disconnect();
              }
            })
        .start();
  }

  void verifyUpdate(File apk) throws Exception {
    android.content.pm.PackageManager pm = getPackageManager();
    android.content.pm.PackageInfo incoming =
        pm.getPackageArchiveInfo(apk.getPath(), android.content.pm.PackageManager.GET_SIGNATURES);
    android.content.pm.PackageInfo current =
        pm.getPackageInfo(getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES);
    if (incoming == null
        || !getPackageName().equals(incoming.packageName)
        || incoming.versionCode <= current.versionCode) throw new IOException("安装包版本或应用标识不匹配");
    if (incoming.signatures == null
        || current.signatures == null
        || !new HashSet<>(Arrays.asList(incoming.signatures))
            .equals(new HashSet<>(Arrays.asList(current.signatures))))
      throw new IOException("安装包签名校验失败");
  }

  void installUpdate() {
    try {
      File apk = new File(getCacheDir(), "update.apk");
      verifyUpdate(apk);
      if (!getPackageManager().canRequestPackageInstalls()) {
        new AlertDialog.Builder(this)
            .setTitle("允许安装更新")
            .setMessage("请在系统设置中允许 PickQ 安装应用，返回后继续安装。")
            .setPositiveButton(
                "前往设置",
                (d, w) ->
                    startActivityForResult(
                        new Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())),
                        2))
            .setNegativeButton("稍后", null)
            .show();
        return;
      }
      Uri uri = Uri.parse("content://" + getPackageName() + ".updates/update.apk");
      Intent install =
          new Intent(Intent.ACTION_INSTALL_PACKAGE)
              .setDataAndType(uri, "application/vnd.android.package-archive");
      install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      install.setClipData(ClipData.newRawUri("PickQ 更新", uri));
      startActivity(install);
    } catch (Exception e) {
      toast("无法安装：" + e.getMessage());
    }
  }

  static boolean newer(String a, String b) {
    try {
      String[] x = a.split("\\."), y = b.split("\\.");
      for (int i = 0; i < Math.max(x.length, y.length); i++) {
        int p = i < x.length ? Integer.parseInt(x[i]) : 0,
            q = i < y.length ? Integer.parseInt(y[i]) : 0;
        if (p != q) return p > q;
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  @Override
  public void onBackPressed() {
    if (screen.equals("home")) super.onBackPressed();
    else if (screen.equals("bank")) home();
    else if (exam && !review && screen.equals("practice"))
      new AlertDialog.Builder(this)
          .setMessage("退出会放弃当前考试，确认退出？")
          .setPositiveButton("退出", (d, w) -> bankPage())
          .setNegativeButton("继续考试", null)
          .show();
    else bankPage();
  }
}
