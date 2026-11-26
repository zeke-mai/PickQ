package com.pickq;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Locale;

final class AccuracyRing extends FrameLayout {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float fraction;
  private final float stroke;

  AccuracyRing(MainActivity activity, int right, int done, boolean large) {
    super(activity);
    setWillNotDraw(false);
    fraction = done == 0 ? 0f : (float) right / done;
    stroke = activity.dp(large ? 9 : 4);
    String percent = done == 0 ? "—" : String.format(Locale.CHINA, "%.1f%%", 100.0 * right / done);
    setContentDescription(done == 0 ? "正确率，暂无判分结果" : "正确率 " + percent);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

    TextView value = new TextView(activity);
    value.setText(percent);
    value.setTextColor(0xff244da8);
    value.setTextSize(large ? 26 : 13);
    value.setTypeface(Typeface.DEFAULT_BOLD);
    value.setGravity(Gravity.CENTER);
    value.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    if (large) value.setTranslationY(-activity.dp(8));
    addView(value, new LayoutParams(-1, -1));
    if (large) {
      TextView caption = new TextView(activity);
      caption.setText("正确率");
      caption.setTextColor(0xff516583);
      caption.setTextSize(13);
      caption.setGravity(Gravity.CENTER);
      caption.setTranslationY(activity.dp(18));
      caption.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      addView(caption, new LayoutParams(-1, -1));
    }
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float x = getWidth() / 2f, y = getHeight() / 2f;
    float radius = Math.min(x, y) - stroke;
    if (radius <= 0) return;
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xfff5f8ff);
    canvas.drawCircle(x, y, radius, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(stroke);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setColor(0xffe2eafa);
    canvas.drawCircle(x, y, radius, paint);
    if (fraction > 0) {
      paint.setColor(0xff3564db);
      canvas.drawArc(x - radius, y - radius, x + radius, y + radius, -90, 360 * fraction, false, paint);
    }
  }
}
