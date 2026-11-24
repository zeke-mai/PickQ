package com.pickq;

import java.math.BigDecimal;
import java.util.Map;

final class ExamAllocation {
  static final BigDecimal CENT = new BigDecimal("0.01");
  final BigDecimal base;
  int remainingExtra;

  ExamAllocation(String rawTotal, Map<String, Integer> counts) {
    BigDecimal total = readExamScore(rawTotal);
    if (total.signum() <= 0 || total.stripTrailingZeros().scale() > 2)
      throw new IllegalArgumentException("总分须大于 0，最多两位小数");
    int questionCount = counts.values().stream().mapToInt(Integer::intValue).sum();
    BigDecimal[] split = total.movePointRight(2).divideAndRemainder(BigDecimal.valueOf(questionCount));
    base = split[0].movePointLeft(2);
    remainingExtra = split[1].intValueExact();
  }

  int takeExtra(int count) {
    int extra = Math.min(count, remainingExtra);
    remainingExtra -= extra;
    return extra;
  }

  static BigDecimal readExamScore(String raw) {
    if (!raw.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
      throw new IllegalArgumentException("请填写有效的非负分值");
    return new BigDecimal(raw);
  }
}
