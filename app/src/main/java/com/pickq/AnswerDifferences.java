package com.pickq;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

/** Marks text differences for review. These marks do not grade an answer. */
final class AnswerDifferences {
  private static final int RED = 0xffb42332;
  private static final long MAX_MATRIX_CELLS = 1_000_000;

  private AnswerDifferences() {}

  static CharSequence[] highlight(String submitted, String reference) {
    if (reference.trim().isEmpty() || submitted.equals(reference))
      return new CharSequence[] {submitted, reference};
    Text left = new Text(submitted), right = new Text(reference);
    int start = 0, endLeft = left.size, endRight = right.size;
    while (start < endLeft && start < endRight && left.points[start] == right.points[start]) start++;
    while (endLeft > start && endRight > start && left.points[endLeft - 1] == right.points[endRight - 1]) {
      endLeft--;
      endRight--;
    }
    int n = endLeft - start, m = endRight - start;
    if (n == 0 || m == 0 || (n + 1L) * (m + 1L) > MAX_MATRIX_CELLS) {
      left.mark(start, endLeft);
      right.mark(start, endRight);
    } else {
      int[][] common = new int[n + 1][m + 1];
      for (int i = n - 1; i >= 0; i--)
        for (int j = m - 1; j >= 0; j--)
          common[i][j] = left.points[start + i] == right.points[start + j]
              ? 1 + common[i + 1][j + 1] : Math.max(common[i + 1][j], common[i][j + 1]);
      int i = 0, j = 0, unmatchedLeft = 0, unmatchedRight = 0;
      while (i < n && j < m) {
        if (left.points[start + i] == right.points[start + j]) {
          left.mark(start + unmatchedLeft, start + i);
          right.mark(start + unmatchedRight, start + j);
          unmatchedLeft = ++i;
          unmatchedRight = ++j;
        } else if (common[i + 1][j] >= common[i][j + 1]) i++;
        else j++;
      }
      left.mark(start + unmatchedLeft, endLeft);
      right.mark(start + unmatchedRight, endRight);
    }
    return new CharSequence[] {left.styled, right.styled};
  }

  private static final class Text {
    final SpannableString styled;
    final int[] points, offsets;
    int size;

    Text(String text) {
      styled = new SpannableString(text);
      points = new int[text.length()];
      offsets = new int[text.length() + 1];
      for (int i = 0; i < text.length();) {
        int cp = text.codePointAt(i);
        if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) {
          offsets[size] = i;
          points[size++] = cp;
        }
        i += Character.charCount(cp);
      }
    }

    void mark(int start, int end) {
      if (start == end) return;
      int last = end - 1;
      styled.setSpan(new ForegroundColorSpan(RED), offsets[start],
          offsets[last] + Character.charCount(points[last]), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }
}
