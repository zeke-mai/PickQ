package com.pickq;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    assertEquals(4, activity.resultBar.getChildCount());
    activity.review = true;
    activity.practice();
    layout();
    assertEquals(3, activity.resultBar.getChildCount());
  }
}
