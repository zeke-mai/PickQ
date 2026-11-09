package com.pickq;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InteractionTest {
  ActivityController<MainActivity> controller;
  MainActivity activity;

  @Before
  public void setup() throws Exception {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("settings", 0)
        .edit()
        .clear()
        .putBoolean("updates", false)
        .commit();
    controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
    activity = controller.get();
    JSONArray qs = new JSONArray();
    for (int i = 0; i < 4; i++)
      qs.put(
          new JSONObject()
              .put("id", "q" + i)
              .put("title", "Question " + i)
              .put("type", "多选题")
              .put("answer", "AB")
              .put("options", new JSONObject().put("A", "One").put("B", "Two")));
    activity.bank =
        new JSONObject()
            .put("id", "fixture")
            .put("name", "Example")
            .put("questions", qs)
            .put("folder", "Review");
    activity.banks = new JSONArray().put(activity.bank);
    activity
        .prefs
        .edit()
        .putString("folders", new JSONArray().put("Review").put("Other").toString())
        .commit();
  }

  @After
  public void cleanup() {
    controller.pause().stop().destroy();
  }

  void layout() {
    activity.root.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
    activity.root.layout(0, 0, 1080, 1920);
    shadowOf(Looper.getMainLooper()).idle();
    activity.root.measure(
        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
    activity.root.layout(0, 0, 1080, 1920);
  }

  void settle() {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
    layout();
  }

  View withText(View view, String text) {
    if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) return view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        View match = withText(group.getChildAt(i), text);
        if (match != null) return match;
      }
    }
    return null;
  }

  @Test
  public void shuffledLabelsKeepGradingAndFeedbackTiedToOptionContent() throws Exception {
    JSONObject question = activity.bank.getJSONArray("questions").getJSONObject(0);
    question.getJSONObject("options").put("C", "Three");
    question.put("chapter", "不显示的章节");
    activity.start(false);
    activity.orders.put("q0", Arrays.asList("C", "A", "B"));
    activity.practice();
    View page = activity.practicePages.get(0).view;
    Button wrong = page.findViewWithTag("C"), correct = page.findViewWithTag("A");
    assertEquals("A  Three", wrong.getText().toString());
    assertEquals("B  One", correct.getText().toString());
    assertEquals(Gravity.START, wrong.getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);
    assertEquals("", ((TextView) activity.bar.getChildAt(0)).getText().toString());

    CheckBox all = (CheckBox) withText(page, "全选");
    View submit = withText(page, "提交答案");
    assertSame(all.getParent(), submit.getParent());
    assertNotSame(all.getParent(), wrong.getParent());
    assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, submit.getLayoutParams().width);
    TextView badge = (TextView) withText(page, "多选题");
    TextView title = (TextView) withText(page, "1. Question 0");
    assertNotNull(badge.getBackground());
    assertSame(badge.getParent(), title.getParent());
    android.widget.LinearLayout questionCard = (android.widget.LinearLayout) badge.getParent();
    assertEquals(android.widget.LinearLayout.VERTICAL, questionCard.getOrientation());
    assertEquals(0, questionCard.indexOfChild(badge));
    assertEquals(1, questionCard.indexOfChild(title));
    assertNull(withText(page, "多选题 · 不显示的章节"));
    assertEquals(
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 17, title.getResources().getDisplayMetrics()),
        title.getTextSize(), 0.01f);
    all.performClick();
    assertEquals("ABC", activity.draft);
    assertEquals("A  Three", wrong.getText().toString());
    assertEquals("B  One", correct.getText().toString());
    assertTrue(wrong.isSelected());
    wrong.performClick();
    assertFalse(all.isChecked());
    assertEquals("B  One", correct.getText().toString());
    assertTrue(correct.isSelected());
    all.performClick();
    all.performClick();
    assertEquals("", activity.draft);
    wrong.performClick();
    correct.performClick();
    submit.performClick();

    page = activity.practicePages.get(0).view;
    assertNotNull(withText(page, "A  Three  ·  错选"));
    assertNotNull(withText(page, "B  One  ·  选对"));
    assertNotNull(withText(page, "C  Two  ·  漏选"));
    assertNotNull(withText(page, "参考答案：BC\n解析："));
    assertEquals(0xffc62828, ((Button) page.findViewWithTag("C")).getCurrentTextColor());
    assertEquals("CA", question.getString("response"));
    assertFalse(question.getBoolean("right"));
  }

  @Test
  public void singleChoiceUsesGreenForCorrectAndRedForWrongWithoutOptionMarkers() throws Exception {
    JSONObject question = activity.bank.getJSONArray("questions").getJSONObject(0);
    question.put("type", "单选题").put("answer", "A");
    activity.start(false);
    View page = activity.practicePages.get(0).view;
    page.findViewWithTag("B").performClick();
    page = activity.practicePages.get(0).view;
    Button correct = page.findViewWithTag("A"), wrong = page.findViewWithTag("B");
    assertEquals("A  One", correct.getText().toString());
    assertEquals("B  Two", wrong.getText().toString());
    assertEquals(0xffc9f0db, ((android.graphics.drawable.GradientDrawable) correct.getBackground()).getColor().getDefaultColor());
    assertEquals(0xffffd7d7, ((android.graphics.drawable.GradientDrawable) wrong.getBackground()).getColor().getDefaultColor());

    question.remove("response");
    activity.practice();
    activity.practicePages.get(0).view.findViewWithTag("A").performClick();
    correct = activity.practicePages.get(0).view.findViewWithTag("A");
    assertEquals("A  One", correct.getText().toString());
    assertEquals(0xffc9f0db, ((android.graphics.drawable.GradientDrawable) correct.getBackground()).getColor().getDefaultColor());
  }

  @Test
  public void judgmentUsesColorsWithoutMissedOptionText() throws Exception {
    JSONObject question = activity.bank.getJSONArray("questions").getJSONObject(0);
    question.put("type", "判断题").put("answer", "A");
    activity.start(false);
    activity.practicePages.get(0).view.findViewWithTag("B").performClick();
    View page = activity.practicePages.get(0).view;
    Button correct = page.findViewWithTag("A"), wrong = page.findViewWithTag("B");
    assertEquals("A  One", correct.getText().toString());
    assertEquals("B  Two", wrong.getText().toString());
    assertEquals(0xffc9f0db, ((GradientDrawable) correct.getBackground()).getColor().getDefaultColor());
    assertEquals(0xffffd7d7, ((GradientDrawable) wrong.getBackground()).getColor().getDefaultColor());
  }

  @Test
  public void submitLabelFitsAndHasSpaceFromSelectAllOnNarrowScreen() {
    activity.start(false);
    View page = activity.practicePages.get(0).view;
    page.measure(
        View.MeasureSpec.makeMeasureSpec(activity.dp(300), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(activity.dp(500), View.MeasureSpec.EXACTLY));
    page.layout(0, 0, page.getMeasuredWidth(), page.getMeasuredHeight());
    View all = withText(page, "全选");
    Button submit = (Button) withText(page, "提交答案");
    View row = (View) submit.getParent();
    assertEquals(row.getWidth(), submit.getLeft() + submit.getRight(), 1);
    assertTrue(all.getRight() <= submit.getLeft());
    assertTrue(submit.getTop() >= activity.dp(8));
    assertTrue(row.getHeight() - submit.getBottom() >= activity.dp(8));
    assertEquals(1, submit.getLineCount());
    assertEquals(0, submit.getLayout().getEllipsisCount(0));
    assertTrue(submit.getLayout().getLineWidth(0) <= submit.getLayout().getWidth());
  }

  GridView overviewNumbers() {
    View close = withText(activity.questionOverview.getWindow().getDecorView(), "关闭");
    return (GridView) ((ViewGroup) close.getParent().getParent()).getChildAt(2);
  }

  @Test
  public void overviewShowsHalfScreenAnswerStatesAndJumpsDirectly() throws Exception {
    JSONArray questions = activity.bank.getJSONArray("questions");
    questions.getJSONObject(0).put("response", "AB").put("right", true);
    questions.getJSONObject(1).put("response", "A").put("right", false);
    activity.start(false);
    layout();
    withText(activity.resultBar, "题号总览").performClick();
    assertTrue(activity.questionOverview.isShowing());
    assertEquals(
        activity.dp(activity.getResources().getConfiguration().screenHeightDp) / 2,
        activity.questionOverview.getWindow().getAttributes().height);
    GridView numbers = overviewNumbers();
    assertEquals(4, numbers.getCount());
    View correct = numbers.getAdapter().getView(0, null, numbers);
    View wrong = numbers.getAdapter().getView(1, null, numbers);
    View unanswered = numbers.getAdapter().getView(2, null, numbers);
    assertEquals("第 1 题，答对，当前题", correct.getContentDescription());
    assertEquals("第 2 题，答错", wrong.getContentDescription());
    assertEquals("第 3 题，未答", unanswered.getContentDescription());
    assertEquals(0xffc9f0db, ((GradientDrawable) correct.getBackground()).getColor().getDefaultColor());
    assertEquals(0xffffd7d7, ((GradientDrawable) wrong.getBackground()).getColor().getDefaultColor());
    assertEquals(0xffedf0f5, ((GradientDrawable) unanswered.getBackground()).getColor().getDefaultColor());
    numbers.performItemClick(null, 3, 3);
    settle();
    assertNull(activity.questionOverview);
    assertEquals(3, activity.index);
    assertEquals(3, activity.prefs.getInt("position:fixture", -1));
    assertNotNull(withText(activity.practicePages.get(3).view, "4. Question 3"));
    assertTrue(activity.practicePages.size() <= 3);
  }

  @Test
  public void footerSwipeOpensOverviewWithoutRevealingExamResults() {
    activity.start(false);
    activity.exam = true;
    activity.examAnswers.put("q0", "A");
    activity.practice();
    layout();
    long time = android.os.SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 10, activity.dp(42), 0);
    MotionEvent move = MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_MOVE, 10, activity.dp(16), 0);
    MotionEvent up = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, 10, activity.dp(4), 0);
    activity.resultBar.dispatchTouchEvent(down);
    activity.resultBar.dispatchTouchEvent(move);
    View panel = (View) overviewNumbers().getParent();
    assertEquals(activity.overviewHeight() - activity.dp(26), panel.getTranslationY(), 0.01f);
    activity.resultBar.dispatchTouchEvent(up);
    down.recycle();
    move.recycle();
    up.recycle();
    assertTrue(activity.questionOverview.isShowing());
    GridView numbers = overviewNumbers();
    View answered = numbers.getAdapter().getView(0, null, numbers);
    assertEquals("第 1 题，已作答，当前题", answered.getContentDescription());
    assertEquals(0xffdbe5ff, ((GradientDrawable) answered.getBackground()).getColor().getDefaultColor());
    activity.questionOverview.dismiss();
  }

  @Test
  public void randomPracticeKeepsItsOwnAnswersOrderAndPosition() throws Exception {
    activity.prefs.edit().putInt("position:fixture", 2).commit();
    JSONObject existing = activity.bank.getJSONArray("questions").getJSONObject(2);
    existing.put("response", "AB").put("right", true);
    activity.bankPage();
    View random = withText(activity.root, "随机练习");
    assertNotNull(random);
    assertTrue(((View) random.getParent().getParent()).performClick());
    assertTrue(activity.randomPractice);
    assertEquals(0, activity.index);
    assertEquals("", activity.answer(existing));
    assertEquals("已做 0/4", activity.progressLabel.getText().toString());
    HashSet<String> ids = new HashSet<>();
    for (JSONObject q : activity.questions) ids.add(activity.id(q));
    assertEquals(new HashSet<>(Arrays.asList("q0", "q1", "q2", "q3")), ids);
    java.util.ArrayList<JSONObject> randomOrder = new java.util.ArrayList<>(activity.questions);
    layout();
    JSONObject answeredRandomly = activity.questions.get(0);
    activity.draft = "A";
    activity.submit(answeredRandomly);
    assertEquals("A", activity.answer(answeredRandomly));
    assertEquals("已做 1/4", activity.progressLabel.getText().toString());
    activity.move(1);
    settle();
    assertEquals(1, activity.index);
    activity.start(false);
    assertEquals(2, activity.index);
    assertEquals("AB", activity.answer(existing));
    assertEquals("已做 1/4", activity.progressLabel.getText().toString());
    activity.start(false, true);
    assertEquals(randomOrder, activity.questions);
    assertEquals(1, activity.index);
    assertEquals("A", activity.answer(answeredRandomly));
  }

  @Test
  public void correctAnswerStartsAutomaticTurnWithin120Milliseconds() {
    activity.prefs.edit().putBoolean("auto", true).commit();
    activity.start(false);
    layout();
    activity.draft = "AB";
    activity.submit(activity.questions.get(0));
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120));
    assertTrue(activity.turning || activity.index == 1);
    settle();
    assertEquals(1, activity.index);
  }

  @Test
  public void folderDeletionKeepsBanksAnswersAndDiskData() throws Exception {
    JSONObject q = activity.bank.getJSONArray("questions").getJSONObject(0);
    q.put("response", "A").put("wrong", 3);
    activity.folderPage("Review");
    activity.deleteFolder("Review");
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals(1, activity.banks.length());
    assertEquals("", activity.bank.getString("folder"));
    assertEquals("A", q.getString("response"));
    assertEquals(3, q.getInt("wrong"));
    assertFalse(activity.folderNames().contains("Review"));
    assertTrue(activity.folderNames().contains("Other"));
    JSONArray stored =
        new JSONArray(
            new String(
                java.nio.file.Files.readAllBytes(
                    new java.io.File(activity.getFilesDir(), "banks.json").toPath()),
                StandardCharsets.UTF_8));
    assertEquals(
        3, stored.getJSONObject(0).getJSONArray("questions").getJSONObject(0).getInt("wrong"));
  }

  @Test
  public void cancellingFolderDeletionDoesNotMoveAnything() {
    activity.folderPage("Review");
    activity.deleteFolder("Review");
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertEquals("Review", activity.bank.optString("folder"));
    assertTrue(activity.folderNames().contains("Review"));
  }

  @Test
  public void repeatedTurnsReusePreparedPageAndKeepMenus() throws Exception {
    activity.start(false);
    layout();
    FrameLayout viewport = activity.practiceViewport;
    View top = activity.bar, bottom = activity.resultBar;
    MainActivity.PracticePage prepared = activity.practicePages.get(1);
    activity.draft = "A";
    activity.pendingAnswers.put("q0", "A");
    long storageVersion = activity.storageVersion.get();
    activity.move(1);
    settle();
    assertEquals(storageVersion, activity.storageVersion.get());
    assertEquals(1, activity.index);
    assertSame(viewport, activity.practiceViewport);
    assertSame(top, activity.bar);
    assertSame(bottom, activity.resultBar);
    assertSame(prepared, activity.practicePages.get(1));
    assertTrue(activity.practicePages.size() <= 3);
    assertEquals(1, activity.prefs.getInt("position:fixture", -1));
    activity.move(-1);
    settle();
    assertEquals(0, activity.index);
    assertEquals("A", activity.draft);
  }

  @Test
  public void boundaryBounceDoesNotTurnOrExposeOtherPages() {
    activity.start(false);
    layout();
    activity.dragPages(200);
    assertEquals(View.INVISIBLE, activity.practicePages.get(1).view.getVisibility());
    activity.settlePages(0);
    settle();
    assertEquals(0, activity.index);
    assertEquals(1f, activity.practicePages.get(0).view.getScaleX(), 0.001f);
    assertEquals(0f, activity.practicePages.get(0).view.getTranslationX(), 0.001f);
  }

  @Test
  public void pauseFlushesDeferredAnswerSave() throws Exception {
    activity.start(false);
    layout();
    activity.draft = "A";
    activity.record(activity.questions.get(0), false);
    controller.pause();
    JSONArray stored =
        new JSONArray(
            new String(
                java.nio.file.Files.readAllBytes(
                    new java.io.File(activity.getFilesDir(), "banks.json").toPath()),
                StandardCharsets.UTF_8));
    assertEquals(
        "A",
        stored.getJSONObject(0).getJSONArray("questions").getJSONObject(0).getString("response"));
  }

  @Test
  public void repeatedSwipeRequestsCannotSkipQuestions() {
    activity.start(false);
    layout();
    activity.move(1);
    activity.move(1);
    settle();
    assertEquals(1, activity.index);
  }

  @Test
  public void cancelledTransitionCannotChangeResetPage() {
    activity.start(false);
    layout();
    activity.move(1);
    activity.practice();
    layout();
    settle();
    assertEquals(0, activity.index);
  }

  @Test
  public void gestureFollowsFingerAndDoesNotSelectOption() {
    activity.start(false);
    layout();
    long time = android.os.SystemClock.uptimeMillis();
    touch(time, time, android.view.MotionEvent.ACTION_DOWN, 700, 180);
    touch(time, time + 16, android.view.MotionEvent.ACTION_MOVE, 500, 180);
    assertEquals(-200f, activity.practicePages.get(0).view.getTranslationX(), 0.1f);
    touch(time, time + 32, android.view.MotionEvent.ACTION_MOVE, 350, 180);
    assertEquals(-350f, activity.practicePages.get(0).view.getTranslationX(), 0.1f);
    touch(time, time + 48, android.view.MotionEvent.ACTION_UP, 200, 180);
    settle();
    assertEquals(1, activity.index);
    assertEquals("", activity.pendingAnswers.getOrDefault("q0", ""));
  }

  @Test
  public void shortFastFlickUsesScreenVelocity() {
    activity.start(false);
    layout();
    long time = android.os.SystemClock.uptimeMillis();
    touch(time, time, android.view.MotionEvent.ACTION_DOWN, 700, 180);
    touch(time, time + 10, android.view.MotionEvent.ACTION_MOVE, 660, 180);
    touch(time, time + 20, android.view.MotionEvent.ACTION_UP, 630, 180);
    settle();
    assertEquals(1, activity.index);
  }

  void touch(long down, long time, int action, float x, float y) {
    android.view.MotionEvent event = android.view.MotionEvent.obtain(down, time, action, x, y, 0);
    activity.practiceViewport.dispatchTouchEvent(event);
    event.recycle();
  }

  @Test
  public void staleBackgroundSnapshotCannotOverwriteNewData() throws Exception {
    String old = activity.banks.toString();
    activity.bank.put("name", "Newest");
    activity.storageVersion.set(2);
    assertTrue(activity.writeSnapshot(activity.banks.toString(), 2));
    assertTrue(activity.writeSnapshot(old, 1));
    JSONArray stored =
        new JSONArray(
            new String(
                java.nio.file.Files.readAllBytes(
                    new java.io.File(activity.getFilesDir(), "banks.json").toPath()),
                StandardCharsets.UTF_8));
    assertEquals("Newest", stored.getJSONObject(0).getString("name"));
  }

  @Test
  public void examReviewReplacesSubmissionFooter() {
    activity.start(false);
    layout();
    activity.exam = true;
    activity.practice();
    layout();
    assertEquals(5, activity.resultBar.getChildCount());
    activity.review = true;
    activity.practice();
    layout();
    assertEquals(4, activity.resultBar.getChildCount());
  }
}
