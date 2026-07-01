package com.atari.spritemaker.panels;

/**
 * Shared per-pixel physics integrator (semi-implicit Euler).
 *
 * Operates in place on parallel float arrays of positions and velocities so any
 * transform can drive stateful motion. Callers advance one fixed tick per frame.
 */
public final class PixelIntegrator {
    private PixelIntegrator() {}

    /**
     * Damped spring toward a per-pixel home target.
     * accel = -k*(pos-home) - c*vel, integrated with step {@code dt}.
     * {@code c} below the critical value (2*sqrt(k)) yields overshoot/wobble.
     */
    public static void stepSpring(float[] px, float[] py,
                                  float[] vx, float[] vy,
                                  int[][] home, float k, float c, float dt) {
        for (int i = 0; i < px.length; i++) {
            float ax = -k * (px[i] - home[i][0]) - c * vx[i];
            float ay = -k * (py[i] - home[i][1]) - c * vy[i];
            vx[i] += ax * dt; vy[i] += ay * dt;
            px[i] += vx[i] * dt; py[i] += vy[i] * dt;
        }
    }

    /** Constant gravity plus linear drag. Kept for future gravity/wind effects. */
    public static void step(float[] px, float[] py,
                            float[] vx, float[] vy,
                            float gx, float gy, float drag, float dt) {
        for (int i = 0; i < px.length; i++) {
            vx[i] += (gx - drag * vx[i]) * dt;
            vy[i] += (gy - drag * vy[i]) * dt;
            px[i] += vx[i] * dt; py[i] += vy[i] * dt;
        }
    }
}
