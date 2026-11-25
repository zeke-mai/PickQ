package com.pickq;

import android.util.Xml;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;

final class BankWorkbook {
  private static final String SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final String OFFICE_RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
  private static final String PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
  private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.";
  private static final Pattern SPECIAL_TEXT =
      Pattern.compile("_(?=x[0-9a-fA-F]{4}_)|[\\x00-\\x08\\x0B-\\x0D\\x0E-\\x1F\\uFFFE\\uFFFF]");
  private static final String[] HEADERS = {
    "题目ID", "题型", "题干", "选项A", "选项B", "选项C", "选项D", "选项E",
    "选项F", "选项G", "选项H", "正确答案", "解析", "章节", "难度"
  };

  private BankWorkbook() {}

  static void write(OutputStream destination, JSONArray questions) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(destination)) {
      entry(zip, "[Content_Types].xml",
          "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
              + "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
              + "<Default Extension='xml' ContentType='application/xml'/>"
              + "<Override PartName='/xl/workbook.xml' ContentType='" + MIME + "sheet.main+xml'/>"
              + "<Override PartName='/xl/worksheets/sheet1.xml' ContentType='" + MIME + "worksheet+xml'/>"
              + "</Types>");
      entry(zip, "_rels/.rels", relationship("officeDocument", "xl/workbook.xml"));
      entry(zip, "xl/workbook.xml",
          "<workbook xmlns='" + SHEET_NS + "' xmlns:r='" + OFFICE_RELS + "'>"
              + "<sheets><sheet name='题库' sheetId='1' r:id='rId1'/></sheets></workbook>");
      entry(zip, "xl/_rels/workbook.xml.rels", relationship("worksheet", "worksheets/sheet1.xml"));
      zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      XmlSerializer xml = Xml.newSerializer();
      xml.setOutput(zip, "UTF-8");
      xml.startDocument("UTF-8", true);
      xml.setPrefix("", SHEET_NS);
      xml.startTag(SHEET_NS, "worksheet");
      xml.startTag(SHEET_NS, "sheetData");
      row(xml, 1, HEADERS);
      for (int i = 0; i < questions.length(); i++) {
        JSONObject question = questions.optJSONObject(i);
        JSONObject options = question.optJSONObject("options");
        String[] values = new String[HEADERS.length];
        values[0] = question.optString("id");
        values[1] = question.optString("type");
        values[2] = question.optString("title");
        for (int j = 0; j < 8; j++)
          values[3 + j] = options == null ? "" : options.optString(String.valueOf((char) ('A' + j)));
        values[11] = question.optString("answer");
        values[12] = question.optString("explanation");
        values[13] = question.optString("chapter");
        values[14] = question.optString("difficulty");
        row(xml, i + 2, values);
      }
      xml.endTag(SHEET_NS, "sheetData");
      xml.endTag(SHEET_NS, "worksheet");
      xml.endDocument();
      zip.closeEntry();
    }
  }

  private static String relationship(String type, String target) {
    return "<Relationships xmlns='" + PACKAGE_RELS + "'><Relationship Id='rId1' Type='"
        + OFFICE_RELS + "/" + type + "' Target='" + target + "'/></Relationships>";
  }

  private static void entry(ZipOutputStream zip, String path, String xml) throws IOException {
    zip.putNextEntry(new ZipEntry(path));
    zip.write(xml.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private static void row(XmlSerializer xml, int number, String[] values) throws IOException {
    xml.startTag(SHEET_NS, "row").attribute(null, "r", String.valueOf(number));
    for (int i = 0; i < values.length; i++) {
      if (values[i].isEmpty()) continue;
      xml.startTag(SHEET_NS, "c")
          .attribute(null, "r", String.valueOf((char) ('A' + i)) + number)
          .attribute(null, "t", "inlineStr");
      xml.startTag(SHEET_NS, "is");
      xml.startTag(SHEET_NS, "t")
          .attribute("http://www.w3.org/XML/1998/namespace", "space", "preserve")
          .text(escapeText(values[i]));
      xml.endTag(SHEET_NS, "t").endTag(SHEET_NS, "is").endTag(SHEET_NS, "c");
    }
    xml.endTag(SHEET_NS, "row");
  }

  private static String escapeText(String value) {
    Matcher matcher = SPECIAL_TEXT.matcher(value);
    StringBuilder encoded = new StringBuilder();
    int start = 0;
    while (matcher.find()) {
      encoded.append(value, start, matcher.start())
          .append(String.format(java.util.Locale.ROOT, "_x%04X_", (int) value.charAt(matcher.start())));
      start = matcher.end();
    }
    return encoded.append(value, start, value.length()).toString();
  }
}
