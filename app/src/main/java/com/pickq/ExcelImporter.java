package com.pickq;

import java.io.*;
import java.util.*;
import jxl.*;

/** Detects the real file format; treats workbook contents as data, never instructions. */
public final class ExcelImporter {
  public static List<Map<String, String>> read(InputStream input) throws Exception {
    if (input == null) throw new IOException("无法打开文件");
    try (InputStream stream = input;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int n;
      while ((n = stream.read(buffer)) != -1) {
        if (bytes.size() + n > 32 * 1024 * 1024) throw new IOException("题库文件超过 32MB");
        bytes.write(buffer, 0, n);
      }
      byte[] data = bytes.toByteArray();
      if (data.length >= 8
          && (data[0] & 255) == 0xd0
          && (data[1] & 255) == 0xcf
          && (data[2] & 255) == 0x11
          && (data[3] & 255) == 0xe0) return readXls(data);
      if (data.length >= 4 && data[0] == 'P' && data[1] == 'K')
        return normalize(Xlsx.read(new ByteArrayInputStream(data)));
      throw new IOException("文件不是有效的 .xls 或 .xlsx，请用 Excel 另存为这两种格式");
    }
  }

  static List<Map<String, String>> readXls(byte[] data) throws Exception {
    WorkbookSettings settings = new WorkbookSettings();
    settings.setSuppressWarnings(true);
    settings.setGCDisabled(true);
    Workbook workbook = Workbook.getWorkbook(new ByteArrayInputStream(data), settings);
    List<Map<String, String>> result = new ArrayList<>();
    try {
      for (Sheet sheet : workbook.getSheets()) {
        Map<Integer, String> headers = null;
        List<Map<String, String>> rows = new ArrayList<>();
        for (int r = 0; r < sheet.getRows(); r++) {
          if (headers == null) {
            Map<Integer, String> candidate = new HashMap<>();
            for (int c = 0; c < sheet.getColumns(); c++)
              candidate.put(c, Xlsx.header(sheet.getCell(c, r).getContents().trim()));
            if (candidate.containsValue("题干")) headers = candidate;
            continue;
          }
          Map<String, String> row = new HashMap<>();
          for (Map.Entry<Integer, String> entry : headers.entrySet())
            if (!entry.getValue().isEmpty())
              row.put(entry.getValue(), sheet.getCell(entry.getKey(), r).getContents().trim());
          if (!row.getOrDefault("题干", "").isEmpty()) rows.add(row);
        }
        result.addAll(normalize(rows));
      }
    } finally {
      workbook.close();
    }
    if (result.isEmpty()) throw new IOException("没有找到题目，请检查题干或试题内容表头");
    return result;
  }

  static List<Map<String, String>> normalize(List<Map<String, String>> rows) throws IOException {
    List<Map<String, String>> result = new ArrayList<>();
    String context = "";
    for (Map<String, String> original : rows) {
      Map<String, String> row = new HashMap<>(original);
      String title = row.getOrDefault("题干", "").trim();
      String type = row.getOrDefault("题型", row.getOrDefault("试题类型", "单选题")).replaceAll("\\s", "");
      if (Xlsx.header(title).equals("题干") && (type.equals("试题类型") || type.equals("题型"))) {
        context = "";
        continue;
      }
      if (row.containsKey("试题类型")) {
        if (type.equals("4")) {
          context = title;
          continue;
        }
        switch (type) {
          case "1":
            type = "单选题";
            context = "";
            break;
          case "2":
            type = "多选题";
            context = "";
            break;
          case "3":
            type = "判断题";
            context = "";
            break;
          case "5":
            if (context.isEmpty()) throw new IOException("材料小题缺少共用题干");
            row.put("题干", context + "\n\n" + title);
            type =
                row.getOrDefault("正确答案", "").replaceAll("[^A-Ha-h]", "").length() > 1
                    ? "多选题"
                    : "单选题";
            break;
          default:
            throw new IOException("无法识别的试题类型：" + type);
        }
      }
      row.put("题型", type.isEmpty() ? "单选题" : type);
      result.add(row);
    }
    return result;
  }
}
