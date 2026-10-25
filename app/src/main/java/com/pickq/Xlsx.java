package com.pickq;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Reads values only. Template prose and formulas are never executed. */
public final class Xlsx {
  public static List<Map<String, String>> read(InputStream input) throws Exception {
    return read(input, false);
  }

  static List<Map<String, String>> readForPreview(InputStream input) throws Exception {
    return read(input, true);
  }

  private static List<Map<String, String>> read(InputStream input, boolean tolerant)
      throws Exception {
    Map<String, byte[]> entries = new HashMap<>();
    long total = 0;
    try (ZipInputStream zip = new ZipInputStream(input)) {
      ZipEntry e;
      byte[] buf = new byte[8192];
      while ((e = zip.getNextEntry()) != null) {
        if (!e.getName().endsWith(".xml") && !e.getName().endsWith(".rels")) continue;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = zip.read(buf)) != -1) {
          total += n;
          if (total > 32 * 1024 * 1024) throw new IOException("文件解压后超过 32MB");
          out.write(buf, 0, n);
        }
        entries.put(e.getName(), out.toByteArray());
      }
    }
    List<String> strings = new ArrayList<>();
    if (entries.containsKey("xl/sharedStrings.xml")) {
      NodeList ns = xml(entries.get("xl/sharedStrings.xml")).getElementsByTagName("si");
      for (int i = 0; i < ns.getLength(); i++) strings.add(text((Element) ns.item(i), "t"));
    }
    Map<String, String> sheetNames = new HashMap<>();
    if (entries.containsKey("xl/workbook.xml")
        && entries.containsKey("xl/_rels/workbook.xml.rels")) {
      Map<String, String> targets = new HashMap<>();
      NodeList rels =
          xml(entries.get("xl/_rels/workbook.xml.rels")).getElementsByTagName("Relationship");
      for (int i = 0; i < rels.getLength(); i++) {
        Element rel = (Element) rels.item(i);
        String target = rel.getAttribute("Target");
        targets.put(
            rel.getAttribute("Id"), target.startsWith("/") ? target.substring(1) : "xl/" + target);
      }
      NodeList names = xml(entries.get("xl/workbook.xml")).getElementsByTagName("sheet");
      for (int i = 0; i < names.getLength(); i++) {
        Element item = (Element) names.item(i);
        sheetNames.put(targets.get(item.getAttribute("r:id")), item.getAttribute("name"));
      }
    }
    List<Map<String, String>> result = new ArrayList<>();
    List<String> sheets = new ArrayList<>();
    for (String s : entries.keySet()) if (s.matches("xl/worksheets/sheet[0-9]+.xml")) sheets.add(s);
    Collections.sort(sheets);
    for (String sheet : sheets) {
      Map<Integer, String> headers = null;
      boolean sheetHasData = false;
      NodeList rows = xml(entries.get(sheet)).getElementsByTagName("row");
      for (int i = 0; i < rows.getLength(); i++) {
        Map<Integer, String> cells = new TreeMap<>();
        NodeList cs = ((Element) rows.item(i)).getElementsByTagName("c");
        for (int j = 0; j < cs.getLength(); j++) {
          Element c = (Element) cs.item(j);
          String ref = c.getAttribute("r");
          int col = 0;
          for (char ch : ref.toCharArray()) {
            if (ch < 'A' || ch > 'Z') break;
            col = col * 26 + ch - 'A' + 1;
          }
          String v = text(c, "v");
          if (c.getAttribute("t").equals("s")) v = strings.get(Integer.parseInt(v));
          else if (c.getAttribute("t").equals("inlineStr")) v = text(c, "t");
          cells.put(col, v.trim());
        }
        for (String value : cells.values()) if (!value.isEmpty()) sheetHasData = true;
        if (headers == null) {
          Map<Integer, String> candidate = new HashMap<>();
          for (Map.Entry<Integer, String> e : cells.entrySet())
            candidate.put(e.getKey(), header(e.getValue()));
          if (candidate.containsValue("题干")) headers = candidate;
          continue;
        }
        Map<String, String> row = new HashMap<>();
        for (Map.Entry<Integer, String> e : cells.entrySet())
          if (headers.containsKey(e.getKey())) row.put(headers.get(e.getKey()), e.getValue());
        row.put(
            "__sheet",
            sheetNames.getOrDefault(
                sheet, sheet.substring(sheet.lastIndexOf('/') + 1).replace(".xml", "")));
        String number = ((Element) rows.item(i)).getAttribute("r");
        row.put("__row", number.isEmpty() ? "" + (i + 1) : number);
        if (hasQuestionData(row)) result.add(row);
      }
      if (tolerant && headers == null && sheetHasData) {
        Map<String, String> issue = new HashMap<>();
        issue.put("__sheet", sheetNames.getOrDefault(sheet, sheet));
        issue.put("__row", "1");
        issue.put("__error", "工作表未找到题干、题目或试题内容表头");
        result.add(issue);
      }
    }
    if (result.isEmpty()) throw new IOException("没有找到题目，请保留题干列的表头");
    return result;
  }

  static boolean hasQuestionData(Map<String, String> row) {
    for (Map.Entry<String, String> cell : row.entrySet()) {
      String key = cell.getKey();
      if ((key.equals("题干")
              || key.equals("题型")
              || key.equals("试题类型")
              || key.equals("正确答案")
              || key.equals("解析")
              || key.matches("选项[A-H]"))
          && !cell.getValue().trim().isEmpty()) return true;
    }
    return false;
  }

  static String header(String s) {
    s = s.replaceAll("\\s", "").replaceAll("[（(].*", "");
    if (s.equals("题目") || s.equals("试题内容")) return "题干";
    if (s.equals("答案") || s.equals("正确选项")) return "正确答案";
    if (s.equals("教材名称")) return "章节";
    if (s.matches("[A-Ha-h]")) return "选项" + s.toUpperCase(Locale.ROOT);
    return s;
  }

  static String text(Element e, String tag) {
    NodeList n = e.getElementsByTagName(tag);
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < n.getLength(); i++) s.append(n.item(i).getTextContent());
    return s.toString();
  }

  static Document xml(byte[] b) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    String probe =
        new String(b, java.nio.charset.StandardCharsets.ISO_8859_1).replace("\u0000", "");
    if (probe.toUpperCase(Locale.ROOT).contains("<!DOCTYPE"))
      throw new IOException("不支持包含 DTD 的 XML");
    f.setExpandEntityReferences(false);
    return f.newDocumentBuilder().parse(new ByteArrayInputStream(b));
  }
}
