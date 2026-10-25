package com.pickq;

public class SwipePhysicsTest {
  static void expect(boolean ok, String what) {
    if (!ok) throw new AssertionError(what);
  }

  public static void main(String[] args) {
    expect(SwipePhysics.target(-100, 0, 400, 1, 0, 3) == 1, "Slow deliberate swipe advances");
    expect(SwipePhysics.target(100, 0, 400, 1, 1, 3) == -1, "Right swipe goes back");
    expect(SwipePhysics.target(-20, -1000, 400, 1, 0, 3) == 1, "Short fast flick advances");
    expect(SwipePhysics.target(-20, 1000, 400, 1, 0, 3) == 0, "Reversed fling does not advance");
    expect(SwipePhysics.target(-5, -2000, 400, 1, 0, 3) == 0, "Tap jitter does not advance");
    expect(SwipePhysics.target(200, 2000, 400, 1, 0, 3) == 0, "First question boundary");
    expect(SwipePhysics.target(-200, -2000, 400, 1, 2, 3) == 0, "Last question boundary");
    expect(SwipePhysics.target(-200, -2000, 400, 1, 0, 1) == 0, "One-question bank");
    expect(SwipePhysics.target(-100, 0, 0, 1, 0, 3) == 0, "No transition before layout");
    expect(
        SwipePhysics.target(-50, -1000, 800, 2, 0, 3) == 0, "Fling threshold scales with density");
    expect(SwipePhysics.edgeScale(0, 400) == 1, "No stretch at rest");
    expect(
        SwipePhysics.edgeScale(200, 400) > 1 && SwipePhysics.edgeScale(100000, 400) < 1.046f,
        "Edge stretch bounded");
    expect(
        SwipePhysics.duration(10, 400) < SwipePhysics.duration(400, 400),
        "Duration follows remaining distance");
    System.out.println(
        "PASS: swipe direction, velocity, cancellation, boundaries, density, elastic stretch and"
            + " duration");
  }
}
