package com.atari.spritemaker.panels;

public final class AnimConfig {
    private AnimConfig() {}

    // ── Pixel Burst ──────────────────────────────────────────────────────────

    public static final int BURST_SPREAD_MIN = 5,   BURST_SPREAD_MAX = 80,   BURST_SPREAD_DEF = 24;
    public static final int BURST_SPEED_MIN  = 100, BURST_SPEED_MAX  = 1500, BURST_SPEED_DEF  = 300;
    public static final int BURST_HOLD_MIN   = 0,   BURST_HOLD_MAX   = 2000, BURST_HOLD_DEF   = 200;

    public static final int BURST_FOCAL_X_MIN = 0, BURST_FOCAL_X_MAX = 100, BURST_FOCAL_X_DEF = 50;
    public static final int BURST_FOCAL_Y_MIN = 0, BURST_FOCAL_Y_MAX = 100, BURST_FOCAL_Y_DEF = 50;

    public static final int BURST_SPIN_STRENGTH_MIN = 0, BURST_SPIN_STRENGTH_MAX = 100, BURST_SPIN_STRENGTH_DEF = 100;

    public static final int BURST_EASING_DEF = 0;
    public static final int BURST_SPIN_DEF   = 0;

    // ── Pixel Pop ────────────────────────────────────────────────────────────

    public static final int POP_EXPLODE_SPEED_MIN = 500,  POP_EXPLODE_SPEED_MAX = 2000,  POP_EXPLODE_SPEED_DEF = 1000;
    public static final int POP_EXPLODE_STRENGTH_MIN = 50, POP_EXPLODE_STRENGTH_MAX = 150, POP_EXPLODE_STRENGTH_DEF = 100;

    public static final int POP_UNSPLODE_SPEED_MIN = 500,  POP_UNSPLODE_SPEED_MAX = 2000,  POP_UNSPLODE_SPEED_DEF = 1000;
    public static final int POP_UNSPLODE_STRENGTH_MIN = 85, POP_UNSPLODE_STRENGTH_MAX = 99, POP_UNSPLODE_STRENGTH_DEF = 95;

    public static final int POP_GRAV_PUSH_MIN = 0, POP_GRAV_PUSH_MAX = 100, POP_GRAV_PUSH_DEF = 50;
    public static final int POP_GRAV_PULL_MIN = 0, POP_GRAV_PULL_MAX = 100, POP_GRAV_PULL_DEF = 50;

    public static final int POP_GRAV_FOCAL_X_MIN = 0, POP_GRAV_FOCAL_X_MAX = 100, POP_GRAV_FOCAL_X_DEF = 50;
    public static final int POP_GRAV_FOCAL_Y_MIN = 0, POP_GRAV_FOCAL_Y_MAX = 100, POP_GRAV_FOCAL_Y_DEF = 100;

    public static final int POP_HOLD_MIN = 0, POP_HOLD_MAX = 100, POP_HOLD_DEF = 0;
    public static final int POP_HOLD_SCALE = 20; // slider value × 20 = actual ms

    public static final int POP_EXTEND_MS_MIN = 0, POP_EXTEND_MS_MAX = 2000, POP_EXTEND_MS_DEF = 500;

    // ── Pixel Twist ──────────────────────────────────────────────────────────

    public static final int TWIST_FIRST_SPEED_MIN  = 0, TWIST_FIRST_SPEED_MAX  = 100, TWIST_FIRST_SPEED_DEF  = 30;
    public static final int TWIST_SECOND_SPEED_MIN = 0, TWIST_SECOND_SPEED_MAX = 100, TWIST_SECOND_SPEED_DEF = 30;
    public static final int TWIST_FIRST_SMOOTH_MIN  = 0, TWIST_FIRST_SMOOTH_MAX  = 100, TWIST_FIRST_SMOOTH_DEF  = 50;
    public static final int TWIST_SECOND_SMOOTH_MIN = 0, TWIST_SECOND_SMOOTH_MAX = 100, TWIST_SECOND_SMOOTH_DEF = 50;
    public static final int TWIST_SPEED_SCALE = 10; // slider value * 10 = milliseconds
    public static final int TWIST_SPREAD_DEF  = 10; // % of cellSize shaved per side when spread is on (20% total)

    // ── Pixel Morph ──────────────────────────────────────────────────────────

    public static final int MORPH_SPEED_MIN = 100,  MORPH_SPEED_MAX = 3000,  MORPH_SPEED_DEF = 600;
    public static final int MORPH_HOLD_MIN  = 0,    MORPH_HOLD_MAX  = 2000,  MORPH_HOLD_DEF  = 300;
}
