package com.pickq;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

/** Pure data validation and duplicate detection. Does not modify app storage. */
public final class ImportReview {
  private static final Set<String> TYPES =
      new HashSet<>(
          Arrays.asList("单选题", "多选题", "不定项选择题", "判断题", "填空题", "简答题", "排序题", "计算题", "论述题"));

  public static final class Entry {
    public final Map<String, String> row;
    public final List<String> errors = new ArrayList<>(), warnings = new ArrayList<>();
    public String duplicate = "";

    Entry(Map<String, String> source) {
      row = new HashMap<>(source);
    }

    public String location() {
      return row.getOrDefault("__sheet", "题目") + " · 第 " + row.getOrDefault("__row", "?") + " 行";
    }

    public boolean valid() {
      return errors.isEmpty();
    }

    public String status() {
      return !valid() ? "格式错误" : !duplicate.isEmpty() ? "重复题" : !warnings.isEmpty() ? "需留意" : "正常";
    }
  }

  public static final class Report {
    public final List<Entry> entries;
    public final int errors, duplicates, warnings;

    Report(List<Entry> rows) {
      entries = Collections.unmodifiableList(rows);
      int e = 0, d = 0, w = 0;
      for (Entry row : rows) {
        if (!row.valid()) e++;
        if (!row.duplicate.isEmpty()) d++;
        if (!row.warnings.isEmpty()) w++;
      }
      errors = e;
      duplicates = d;
      warnings = w;
    }

    public List<Entry> selected(boolean skipDuplicates) {
      List<Entry> rows = new ArrayList<>();
      for (Entry e : entries)
        if (e.valid() && (!skipDuplicates || e.duplicate.isEmpty())) rows.add(e);
      return rows;
    }
  }

  public static Report analyze(List<Map<String, String>> incoming) {
    Map<String, String> exact = new HashMap<>();
    Set<String> stems = new HashSet<>();
    List<Entry> result = new ArrayList<>();
    int sequence = 0;
    for (Map<String, String> row : incoming) {
      Entry e = check(row);
      e.row.putIfAbsent("__row", "" + (++sequence));
      e.row.putIfAbsent("__sheet", "题库");
      if (e.valid()) {
        String key = fingerprint(e.row), title = stem(e.row);
        if (exact.containsKey(key)) e.duplicate = "与 " + exact.get(key) + " 完全重复";
        else {
          if (stems.contains(title)) e.warnings.add("存在相同题干，但选项或答案不同，请核对");
          exact.put(key, e.location());
          stems.add(title);
        }
      }
      result.add(e);
    }
    return new Report(result);
  }

  public static Entry check(Map<String, String> source) {
    Entry e = new Entry(source);
    Map<String, String> r = e.row;
    String title = r.getOrDefault("题干", "").trim(),
        type = r.getOrDefault("题型", "单选题").replaceAll("\\s", "");
    String answer = Normalizer.normalize(r.getOrDefault("正确答案", "").trim(), Normalizer.Form.NFKC);
    TreeMap<String, String> options = new TreeMap<>();
    for (char c = 'A'; c <= 'H'; c++) {
      String key = "选项" + c;
      String value = r.getOrDefault(key, "").trim();
      if (!value.isEmpty()) {
        r.put(key, value);
        options.put("" + c, value);
      }
    }
    if ("1".equals(r.get("__typeMissing")) || type.isEmpty()) {
      type =
          truth(answer) != null
              ? "判断题"
              : options.isEmpty() ? "简答题" : letters(answer).length() > 1 ? "多选题" : "单选题";
      e.warnings.add("未填写题型，已推断为" + type);
    }
    if (type.equals("单选")) type = "单选题";
    if (type.equals("多选")) type = "多选题";
    if (type.equals("判断")) type = "判断题";
    if (type.equals("填空")) type = "填空题";
    if (type.equals("问答题")) type = "简答题";
    if (!r.getOrDefault("__error", "").isEmpty()) e.errors.add(r.get("__error"));
    if (!TYPES.contains(type)) e.errors.add("不支持的题型：" + type);
    if (title.isEmpty()) e.errors.add("题干不能为空");
    boolean choice = type.contains("选") || type.equals("判断题"), sorted = type.equals("排序题");
    if (type.equals("判断题")) {
      if (options.isEmpty()) {
        options.put("A", "正确");
        options.put("B", "错误");
        r.put("选项A", "正确");
        r.put("选项B", "错误");
      }
      Boolean value = truth(answer);
      if (value != null) {
        String found = "";
        for (Map.Entry<String, String> opt : options.entrySet())
          if (value.equals(truth(opt.getValue()))) found = opt.getKey();
        if (found.isEmpty()) e.errors.add("判断题答案与选项内容不匹配");
        else answer = found;
      }
      if (options.size() != 2) e.errors.add("判断题应有两个选项，或不填选项由应用生成");
    }
    if (choice || sorted) {
      if (options.size() < 2) e.errors.add("至少需要两个非空选项");
      Set<String> texts = new HashSet<>();
      for (String text : options.values()) if (!texts.add(normal(text))) e.errors.add("存在内容相同的选项");
      answer = letters(answer);
      if (!answer.isEmpty()) {
        Set<Character> used = new HashSet<>(),
            missing = new TreeSet<>(),
            repeated = new TreeSet<>();
        boolean badCharacters = false;
        for (char c : answer.toCharArray()) {
          if (c < 'A' || c > 'H') badCharacters = true;
          else if (!options.containsKey("" + c)) missing.add(c);
          else if (!used.add(c)) repeated.add(c);
        }
        if (badCharacters) e.errors.add("选择或排序题的答案应填写 A–H 选项字母");
        if (!missing.isEmpty()) e.errors.add("答案引用了不存在的选项：" + missing);
        if (!repeated.isEmpty()) e.errors.add("答案中选项重复：" + repeated);
        if ((type.equals("单选题") || type.equals("判断题")) && answer.length() != 1)
          e.errors.add("单选或判断题只能有一个正确选项");
        if (type.equals("多选题") && answer.length() < 2) e.errors.add("多选题应至少有两个正确选项");
        if (sorted && used.size() != options.size()) e.errors.add("排序答案应覆盖全部选项且不重复");
      }
    } else if (type.equals("填空题") && answer.isEmpty() && !options.isEmpty())
      answer = String.join("|", options.values());
    if (answer.isEmpty()) e.warnings.add("缺少标准答案，导入后需要人工判分");
    r.put("题干", title);
    r.put("题型", type);
    r.put("正确答案", answer);
    return e;
  }

  static Boolean truth(String value) {
    String s = normal(value).toLowerCase(Locale.ROOT);
    if (Arrays.asList("正确", "对", "是", "√", "true", "1").contains(s)) return true;
    if (Arrays.asList("错误", "错", "否", "×", "false", "0").contains(s)) return false;
    return null;
  }

  static String letters(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replaceAll("[\\s,，、;；|/]+", "")
        .toUpperCase(Locale.ROOT);
  }

  static String normal(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[\\s\\u3000\\u00a0]+", "");
  }

  static String stem(Map<String, String> row) {
    return hash(Arrays.asList(row.getOrDefault("题型", ""), row.getOrDefault("题干", "")));
  }

  static String fingerprint(Map<String, String> row) {
    List<String> parts = new ArrayList<>();
    String type = row.getOrDefault("题型", "");
    parts.add(type);
    parts.add(row.getOrDefault("题干", ""));
    List<String> options = new ArrayList<>(), answers = new ArrayList<>();
    for (char c = 'A'; c <= 'H'; c++)
      if (!row.getOrDefault("选项" + c, "").isEmpty()) options.add(normal(row.get("选项" + c)));
    String answer = row.getOrDefault("正确答案", "");
    if (type.contains("选") || type.equals("判断题") || type.equals("排序题")) {
      for (char c : answer.toCharArray()) answers.add(normal(row.getOrDefault("选项" + c, "")));
      if (!type.equals("排序题")) Collections.sort(answers);
      Collections.sort(options);
    } else answers.add(normal(answer));
    if (!(type.contains("选") || type.equals("判断题") || type.equals("排序题"))) options.clear();
    parts.add("options:" + options.size());
    parts.addAll(options);
    parts.add("answers:" + answers.size());
    parts.addAll(answers);
    return hash(parts);
  }

  static String hash(List<String> parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String part : parts) {
        String value = normal(part);
        digest.update((value.length() + ":").getBytes(StandardCharsets.UTF_8));
        digest.update(value.getBytes(StandardCharsets.UTF_8));
      }
      StringBuilder out = new StringBuilder();
      for (byte b : digest.digest()) {
        out.append("0123456789abcdef".charAt((b >>> 4) & 15));
        out.append("0123456789abcdef".charAt(b & 15));
      }
      return out.toString();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
