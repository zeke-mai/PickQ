package com.pickq;

/** Gesture decisions independent of Android, also exercised in the JVM regression suite. */
public final class SwipePhysics {
  public static int target(
      float dx, float velocity, float width, float density, int index, int count) {
    if (width <= 0 || count <= 0) return 0;
    boolean distance = Math.abs(dx) >= Math.max(48 * density, width * 0.22f);
    boolean fling =
        Math.abs(dx) >= 12 * density
            && Math.abs(velocity) >= 650 * density
            && Math.signum(dx) == Math.signum(velocity);
    if (!distance && !fling) return 0;
    int delta = dx < 0 ? 1 : -1;
    return index + delta < 0 || index + delta >= count ? 0 : delta;
  }

  public static float edgeScale(float dx, float width) {
    return 1f + 0.045f * (float) (1 - Math.exp(-Math.abs(dx) / Math.max(1, width)));
  }

  public static long duration(float distance, float width) {
    return Math.round(130 + 100 * Math.min(1, distance / Math.max(1, width)));
  }
}
