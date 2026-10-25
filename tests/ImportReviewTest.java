package com.pickq;

import java.util.*;

public final class ImportReviewTest {
  static Map<String, String> row(String title, String type, String answer) {
    Map<String, String> row = new HashMap<>();
    row.put("题干", title);
    row.put("题型", type);
    row.put("正确答案", answer);
    row.put("选项A", "甲");
    row.put("选项B", "乙");
    return row;
  }

  static void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    Map<String, String> base = row("测试题", "单选题", "A");
    Map<String, String> swapped = row(" 测试 题 ", "单选题", "B");
    swapped.put("选项A", "乙");
    swapped.put("选项B", "甲");
    Map<String, String> invalid = row("答案坏了", "单选题", "C");
    Map<String, String> empty = row("", "单选题", "A");
    Map<String, String> unsupported = row("其他题型", "未知题型", "A");
    Map<String, String> conflict = row("测试题", "单选题", "B");
    Map<String, String> noAnswer = row("无答案", "单选题", "");
    ImportReview.Report report =
        ImportReview.analyze(
            Arrays.asList(base, swapped, invalid, empty, unsupported, conflict, noAnswer));
    check(report.entries.size() == 7, "All questions preserved in review");
    check(report.errors == 3, "Format errors");
    check(report.duplicates == 1, "Reordered options and whitespace are duplicates");
    check(report.selected(true).size() == 3, "Default skip invalid and repeats");
    check(report.selected(false).size() == 4, "Can retain duplicates but never invalid questions");
    check(
        report.entries.get(5).warnings.size() == 1,
        "Same stem with conflicting answer requires review");
    check(
        report.entries.get(6).valid() && !report.entries.get(6).warnings.isEmpty(),
        "Missing answer warns and allows self assessment");
    check(
        ImportReview.analyze(Collections.singletonList(swapped)).duplicates == 0,
        "A single document question is not a duplicate");
    Map<String, String> bool = row("反序判断", "判断题", "正确");
    bool.put("选项A", "错");
    bool.put("选项B", "对");
    check(
        ImportReview.check(bool).row.get("正确答案").equals("B"),
        "Boolean correct label by option text");
    Map<String, String> multi = row("多选", "多选题", "Ａ，Ｂ");
    check(
        ImportReview.check(multi).row.get("正确答案").equals("AB"), "Full width labels and separators");
    check(ImportReview.check(multi).valid(), "Valid multiple choice");
    check(!ImportReview.check(row("单个答案", "多选题", "A")).valid(), "Multi type needs two answers");
    Map<String, String> dupOptions = row("重复选项", "单选题", "A");
    dupOptions.put("选项B", "甲");
    check(!ImportReview.check(dupOptions).valid(), "Duplicate option content");
    Map<String, String> sorted = row("排序", "排序题", "AA");
    check(!ImportReview.check(sorted).valid(), "Invalid sorting answers");
    Map<String, String> orphan = row("(1)", "单选题", "A");
    orphan.put("__error", "材料小题缺少共用题干");
    check(!ImportReview.check(orphan).valid(), "Parser errors are visible");
    System.out.println(
        "PASS: format review, duplicate choices/reordering, document-only comparison, conflicts,"
            + " missing answers, judgment, invalid options and source errors");
  }
}
