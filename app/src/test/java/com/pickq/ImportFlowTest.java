package com.pickq;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ImportFlowTest {
  ActivityController<MainActivity> controller;
  MainActivity activity;

  @Before
  public void setup() {
    RuntimeEnvironment.getApplication()
        .getSharedPreferences("settings", 0)
        .edit()
        .clear()
        .putBoolean("updates", false)
        .commit();
    controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
    activity = controller.get();
    activity.banks = new JSONArray();
  }

  @After
  public void cleanup() {
    controller.pause().stop().destroy();
  }

  Map<String, String> row(String title, String answer) {
    Map<String, String> row = new HashMap<>();
    row.put("题干", title);
    row.put("题型", "单选题");
    row.put("正确答案", answer);
    row.put("选项A", "甲");
    row.put("选项B", "乙");
    return row;
  }

  ImportReview.Report report() {
    return ImportReview.analyze(
        Arrays.asList(row("正常题", "A"), row("正常题", "A"), row("错误题", "Z")), Collections.emptyList());
  }

  @Test
  public void previewWritesNothingUntilExplicitConfirmation() throws Exception {
    activity.showImportPreview(report(), "新题库");
    assertEquals("import", activity.screen);
    assertEquals(0, activity.banks.length());
    assertEquals(3, activity.importList.getAdapter().getCount());
    activity.importConfirm.performClick();
    assertEquals(1, activity.banks.length());
    JSONArray qs = activity.banks.getJSONObject(0).getJSONArray("questions");
    assertEquals(1, qs.length());
    assertEquals("正常题", qs.getJSONObject(0).getString("title"));
  }

  @Test
  public void cancelLeavesLibrariesUntouched() {
    activity.showImportPreview(report(), "新题库");
    activity.onBackPressed();
    assertEquals(0, activity.banks.length());
    assertNull(activity.previewReport);
    assertEquals("home", activity.screen);
  }

  @Test
  public void duplicatesCanBeKeptButInvalidQuestionsCannot() throws Exception {
    activity.showImportPreview(report(), "新题库");
    activity.skipImportDuplicates.setChecked(false);
    activity.importConfirm.performClick();
    assertEquals(2, activity.banks.getJSONObject(0).getJSONArray("questions").length());
  }

  @Test
  public void problemFilterAndDetailsExposeErrors() {
    activity.showImportPreview(report(), "新题库");
    activity.onlyImportIssues.setChecked(true);
    assertEquals(2, activity.importList.getAdapter().getCount());
    activity.importList.performItemClick(null, 1, 1);
    assertEquals("题目检查详情", shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
  }

  @Test
  public void allInvalidOrAllSkippedDisablesConfirmation() {
    activity.showImportPreview(
        ImportReview.analyze(Collections.singletonList(row("错误题", "Z")), Collections.emptyList()),
        "错误");
    assertFalse(activity.importConfirm.isEnabled());
    activity.showImportPreview(
        ImportReview.analyze(
            Collections.singletonList(row("重复", "A")), Collections.singletonList(row("重复", "A"))),
        "重复");
    assertFalse(activity.importConfirm.isEnabled());
    activity.skipImportDuplicates.setChecked(false);
    assertTrue(activity.importConfirm.isEnabled());
  }

  @Test
  public void manifestAcceptsOpenAndShareExcelButNotWebLinks() {
    Intent open =
        new Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                Uri.parse("content://documents/questions.xls"), "application/vnd.ms-excel")
            .setPackage(activity.getPackageName());
    assertFalse(
        activity
            .getPackageManager()
            .queryIntentActivities(open, PackageManager.MATCH_DEFAULT_ONLY)
            .isEmpty());
    Intent share =
        new Intent(Intent.ACTION_SEND)
            .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .setPackage(activity.getPackageName());
    assertFalse(
        activity
            .getPackageManager()
            .queryIntentActivities(share, PackageManager.MATCH_DEFAULT_ONLY)
            .isEmpty());
    open.setDataAndType(Uri.parse("https://example.com/questions.xls"), "application/vnd.ms-excel");
    assertTrue(
        activity
            .getPackageManager()
            .queryIntentActivities(open, PackageManager.MATCH_DEFAULT_ONLY)
            .isEmpty());
  }

  byte[] fixture() throws Exception {
    String xml =
        "<worksheet><sheetData><row r='1'><c r='A1' t='inlineStr'><is><t>题干</t></is></c><c r='B1'"
            + " t='inlineStr'><is><t>题型</t></is></c><c r='C1'"
            + " t='inlineStr'><is><t>正确答案</t></is></c><c r='D1'"
            + " t='inlineStr'><is><t>选项A</t></is></c><c r='E1'"
            + " t='inlineStr'><is><t>选项B</t></is></c></row><row r='2'><c r='A2'"
            + " t='inlineStr'><is><t>来自其他应用</t></is></c><c r='B2'"
            + " t='inlineStr'><is><t>单选题</t></is></c><c r='C2'"
            + " t='inlineStr'><is><t>A</t></is></c><c r='D2' t='inlineStr'><is><t>甲</t></is></c><c"
            + " r='E2' t='inlineStr'><is><t>乙</t></is></c></row></sheetData></worksheet>";
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      zip.write(xml.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }

  void register(Uri uri) throws Exception {
    shadowOf(activity.getContentResolver())
        .registerInputStream(uri, new ByteArrayInputStream(fixture()));
  }

  void awaitPreview() throws Exception {
    long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (activity.previewReport == null && System.nanoTime() < end) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(10);
    }
    shadowOf(Looper.getMainLooper()).idle();
    assertNotNull(activity.previewReport);
    assertEquals(0, activity.banks.length());
  }

  @Test
  public void warmOpenIntentShowsPreviewWithoutSaving() throws Exception {
    Uri uri = Uri.parse("content://documents/warm.xlsx");
    register(uri);
    activity.onNewIntent(
        new Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    awaitPreview();
    assertEquals("import", activity.screen);
  }

  @Test
  public void warmShareIntentUsesSamePreview() throws Exception {
    Uri uri = Uri.parse("content://documents/share.xlsx");
    register(uri);
    activity.onNewIntent(
        new Intent(Intent.ACTION_SEND)
            .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .putExtra(Intent.EXTRA_STREAM, uri));
    awaitPreview();
  }

  @Test
  public void coldOpenIntentAlsoShowsPreview() throws Exception {
    Uri uri = Uri.parse("content://documents/cold.xlsx");
    register(uri);
    controller.pause().stop().destroy();
    controller =
        Robolectric.buildActivity(
                MainActivity.class,
                new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(
                        uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .setup()
            .visible();
    activity = controller.get();
    awaitPreview();
  }

  @Test
  public void clipDataFallbackAndUnsupportedSchemes() {
    Uri uri = Uri.parse("content://documents/clip.xlsx");
    Intent share = new Intent(Intent.ACTION_SEND);
    share.setClipData(ClipData.newRawUri("Excel", uri));
    assertEquals(uri, MainActivity.incomingUri(share));
    assertNull(
        MainActivity.incomingUri(
            new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/a.xls"))));
    assertNull(MainActivity.incomingUri(new Intent(Intent.ACTION_SEND)));
  }

  @Test
  public void incomingFileDoesNotDiscardActiveExamWithoutConsent() throws Exception {
    JSONArray qs =
        new JSONArray().put(activity.importedQuestion(ImportReview.check(row("考试题", "A")).row));
    activity.bank = new JSONObject().put("id", "exam").put("questions", qs).put("name", "考试");
    activity.start(false);
    activity.exam = true;
    activity.practice();
    activity.onNewIntent(
        new Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                Uri.parse("content://documents/new.xlsx"),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    assertEquals("正在考试", shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
    ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
    shadowOf(Looper.getMainLooper()).idle();
    assertTrue(activity.exam);
    assertEquals("practice", activity.screen);
    assertNull(activity.previewReport);
  }
}
