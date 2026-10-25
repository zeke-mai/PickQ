package com.pickq;

import java.io.*;
import java.util.*;
import jxl.*;
import jxl.write.*;

public final class LegacyImportTest {
  static void check(boolean b, String message) {
    if (!b) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    WritableWorkbook workbook = Workbook.createWorkbook(output);
    WritableSheet sheet = workbook.createSheet("题库", 0);
    String[][] data = {
      {"序号", "教材名称", "试题内容", "试题类型", "正确选项", "A", "B", "C", "D"},
      {"1", "第一章", "普通单选", "1", "A", "甲", "乙", "丙", "丁"},
      {"2", "第一章", "普通多选", "2", "AC", "甲", "乙", "丙", "丁"},
      {"3", "第一章", "判断题", "3", "B", "对", "错", "", ""},
      {"序号", "教材名称", "试题内容", "试题类型", "正确选项", "A", "B", "C", "D"},
      {"4", "第二章", "材料公共题干", "4", "", "", "", "", ""},
      {"5", "第二章", "(1)", "5", "B", "一", "二", "三", "四"},
      {"6", "第二章", "(2)", "5", "AC", "一", "二", "三", "四"}
    };
    for (int r = 0; r < data.length; r++)
      for (int c = 0; c < data[r].length; c++) sheet.addCell(new Label(c, r, data[r][c]));
    workbook.write();
    workbook.close();
    List<Map<String, String>> rows =
        ExcelImporter.read(new ByteArrayInputStream(output.toByteArray()));
    check(rows.size() == 5, "Skip repeated headers and scenario stems");
    check(rows.get(0).get("选项A").equals("甲"), "A-D aliases");
    check(rows.get(1).get("题型").equals("多选题"), "Numeric type");
    check(rows.get(2).get("题型").equals("判断题"), "Boolean type");
    check(rows.get(3).get("题干").equals("材料公共题干\n\n(1)"), "Scenario attached");
    check(rows.get(4).get("题型").equals("多选题"), "Scenario multiple choice");
    check(rows.get(0).get("章节").equals("第一章"), "Chapter metadata");
    try {
      ExcelImporter.read(new ByteArrayInputStream("not an Excel file".getBytes()));
      throw new AssertionError("Invalid format accepted");
    } catch (IOException expected) {
    }
    if (args.length > 0) {
      rows = ExcelImporter.read(new FileInputStream(args[0]));
      check(rows.size() == 1669, "Real workbook count: " + rows.size());
      Map<String, Integer> counts = new TreeMap<>();
      for (Map<String, String> row : rows) counts.merge(row.get("题型"), 1, Integer::sum);
      check(counts.get("单选题") == 910, "Single count");
      check(counts.get("多选题") == 290, "Multiple count");
      check(counts.get("判断题") == 469, "Boolean count");
      check(rows.stream().noneMatch(x -> x.get("题干").equals("试题内容")), "No repeated headers");
      System.out.println("Actual workbook: " + rows.size() + " questions " + counts);
    }
    System.out.println(
        "PASS: legacy XLS, aliases, numeric types, repeated headers, scenario questions, invalid"
            + " file");
  }
}
