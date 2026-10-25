package com.pickq;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import jxl.*;
import jxl.write.*;

public class ImportReaderTest {
  static void check(boolean b, String reason) {
    if (!b) throw new AssertionError(reason);
  }

  static byte[] xlsx(String sheet) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      String[][] files = {
        {"xl/worksheets/sheet1.xml", sheet},
        {
          "xl/workbook.xml",
          "<workbook"
              + " xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><sheets><sheet"
              + " name='电气题库' r:id='rId1'/></sheets></workbook>"
        },
        {
          "xl/_rels/workbook.xml.rels",
          "<Relationships><Relationship Id='rId1' Target='worksheets/sheet1.xml'/></Relationships>"
        }
      };
      for (String[] file : files) {
        zip.putNextEntry(new ZipEntry(file[0]));
        zip.write(file[1].getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  static String cell(String ref, String text) {
    return "<c r='" + ref + "' t='inlineStr'><is><t>" + text + "</t></is></c>";
  }

  public static void main(String[] args) throws Exception {
    String sheet =
        "<worksheet><sheetData><row r='1'>"
            + cell("A1", "题干")
            + cell("B1", "题型")
            + cell("C1", "正确答案")
            + cell("D1", "选项A")
            + cell("E1", "选项B")
            + "</row><row r='7'>"
            + cell("B7", "单选题")
            + cell("C7", "A")
            + cell("D7", "甲")
            + cell("E7", "乙")
            + "</row><row r='8'>"
            + cell("A8", "完整题")
            + cell("B8", "单选题")
            + cell("C8", "A")
            + cell("D8", "甲")
            + cell("E8", "乙")
            + "</row><row r='9'/></sheetData></worksheet>";
    List<Map<String, String>> rows =
        ExcelImporter.readForPreview(new ByteArrayInputStream(xlsx(sheet)));
    check(rows.size() == 2, "Missing-stem rows retained, empty rows skipped");
    check(rows.get(0).get("__row").equals("7"), "Original row number");
    check(rows.get(0).get("__sheet").equals("电气题库"), "Workbook sheet name");
    check(ImportReview.analyze(rows).errors == 1, "Missing title reported");
    rows =
        ExcelImporter.readForPreview(
            new ByteArrayInputStream(
                xlsx(
                    "<worksheet><sheetData><row r='1'>"
                        + cell("A1", "未知表头")
                        + "</row><row r='2'>"
                        + cell("A2", "内容")
                        + "</row></sheetData></worksheet>")));
    check(
        rows.size() == 1 && !rows.get(0).getOrDefault("__error", "").isEmpty(),
        "Missing header diagnostic");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    WritableWorkbook book = Workbook.createWorkbook(bytes);
    WritableSheet page = book.createSheet("旧题库", 0);
    String[][] cells = {
      {"试题内容", "试题类型", "正确选项", "A", "B"}, {"未知题型", "9", "A", "甲", "乙"}, {"(1)", "5", "A", "甲", "乙"}
    };
    for (int r = 0; r < cells.length; r++)
      for (int c = 0; c < cells[r].length; c++) page.addCell(new Label(c, r, cells[r][c]));
    book.write();
    book.close();
    rows = ExcelImporter.readForPreview(new ByteArrayInputStream(bytes.toByteArray()));
    check(rows.size() == 2, "Invalid legacy rows preserved");
    check(
        ImportReview.analyze(rows).errors == 2,
        "Unsupported legacy type and missing scenario visible");
    check(rows.get(1).get("__row").equals("3"), "XLS row source");
    try {
      ExcelImporter.read(new ByteArrayInputStream(bytes.toByteArray()));
      throw new AssertionError("Strict mode changed");
    } catch (IOException expected) {
    }
    System.out.println(
        "PASS: missing stems, sheet/row provenance, missing headers, tolerant legacy errors, strict"
            + " parser compatibility");
  }
}
