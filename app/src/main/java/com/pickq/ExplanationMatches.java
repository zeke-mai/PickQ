package com.pickq;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Matches explanations to existing questions without using row order. */
final class ExplanationMatches {
  static final class Change {
    final JSONObject question;
    final String text;
    final String location;

    Change(JSONObject question, String text, String location) {
      this.question = question;
      this.text = text;
      this.location = location;
    }

    boolean replacesExisting() {
      return !question.optString("explanation").trim().isEmpty();
    }
  }

  final List<Change> changes = new ArrayList<>();
  final List<String> issues = new ArrayList<>();
  int blank, unchanged, additions, replacements;

  static ExplanationMatches find(JSONArray questions, List<Map<String, String>> rows) {
    ExplanationMatches result = new ExplanationMatches();
    Map<String, JSONObject> byId = new HashMap<>();
    Map<String, List<JSONObject>> byContent = new HashMap<>();
    Map<JSONObject, String> keys = new IdentityHashMap<>();
    for (int i = 0; i < questions.length(); i++) {
      JSONObject q = questions.optJSONObject(i);
      if (q == null) continue;
      ImportReview.Entry existing = ImportReview.check(questionRow(q));
      if (!existing.valid()) continue;
      String key = key(existing.row);
      keys.put(q, key);
      byId.put(q.optString("id"), q);
      byContent.computeIfAbsent(key, ignored -> new ArrayList<>()).add(q);
    }

    Map<JSONObject, Change> matched = new LinkedHashMap<>();
    Set<JSONObject> conflicts = new HashSet<>();
    for (Map<String, String> row : rows) {
      ImportReview.Entry entry = ImportReview.check(row);
      String explanation = row.getOrDefault("解析", "").trim();
      if (row.containsKey("__error") || (!explanation.isEmpty() && !entry.valid())) {
        result.issues.add(entry.location() + "： " + String.join("、", entry.errors));
        continue;
      }
      if (explanation.isEmpty()) {
        result.blank++;
        continue;
      }
      String key = key(entry.row);
      String id = row.getOrDefault("题目ID", "").trim();
      JSONObject question;
      if (!id.isEmpty()) {
        question = byId.get(id);
        if (question == null || !key.equals(keys.get(question))) {
          result.issues.add(entry.location() + "：题目 ID 或内容与当前题库不符");
          continue;
        }
      } else {
        List<JSONObject> candidates = byContent.getOrDefault(key, Collections.emptyList());
        if (candidates.size() != 1) {
          result.issues.add(entry.location() + (candidates.isEmpty() ? "：未匹配到题目" : "：匹配到多道题"));
          continue;
        }
        question = candidates.get(0);
      }
      Change change = new Change(question, explanation, entry.location());
      Change first = matched.putIfAbsent(question, change);
      if (first != null && !first.text.equals(change.text)) {
        conflicts.add(question);
        result.issues.add(change.location + "：与 " + first.location + " 的解析冲突");
      }
    }
    for (Change change : matched.values()) {
      if (conflicts.contains(change.question)) continue;
      if (change.text.equals(change.question.optString("explanation").trim())) {
        result.unchanged++;
        continue;
      }
      result.changes.add(change);
      if (change.replacesExisting()) result.replacements++;
      else result.additions++;
    }
    return result;
  }

  private static Map<String, String> questionRow(JSONObject question) {
    Map<String, String> row = new HashMap<>();
    row.put("题型", question.optString("type"));
    row.put("题干", question.optString("title"));
    row.put("正确答案", question.optString("answer"));
    JSONObject options = question.optJSONObject("options");
    if (options != null)
      for (char letter = 'A'; letter <= 'H'; letter++)
        row.put("选项" + letter, options.optString(String.valueOf(letter)));
    return row;
  }

  private static String key(Map<String, String> row) {
    Map<String, String> normalized = new HashMap<>(row);
    List<String> options = new ArrayList<>();
    for (char letter = 'A'; letter <= 'H'; letter++) {
      String option = row.getOrDefault("选项" + letter, "");
      if (!option.isEmpty()) options.add(option);
    }
    if ("单选题".equals(row.get("题型")) && options.size() == 2
        && ImportReview.isTruthPair(options.get(0), options.get(1)))
      normalized.put("题型", "判断题");
    return ImportReview.fingerprint(normalized);
  }
}
