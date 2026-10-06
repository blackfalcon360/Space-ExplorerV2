package com.blackfalcon.spaceexplorer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Compass dial. Sun, Moon and planets sit around it where they really are in your sky. */
public class SkyDialView extends View {

    public interface Listener { void onSelect(int index); }

    public static final String[] MOON_EMOJI = {
            "\uD83C\uDF11", "\uD83C\uDF12", "\uD83C\uDF13", "\uD83C\uDF14",
            "\uD83C\uDF15", "\uD83C\uDF16", "\uD83C\uDF17", "\uD83C\uDF18"};

    private static final int[] COLORS = {
            Color.parseColor("#FFC107"), Color.parseColor("#E0E0E0"), Color.parseColor("#B0B0B0"),
            Color.parseColor("#E8CDA2"), Color.parseColor("#E2563B"), Color.parseColor("#D9A066"),
            Color.parseColor("#E3C77D"), Color.parseColor("#7DE3E3"), Color.parseColor("#4B70DD")};
    private static final float[] SIZE = {0.040f, 0.0f, 0.022f, 0.027f, 0.025f, 0.034f, 0.027f, 0.026f, 0.026f};

    private static final int RED = Color.parseColor("#FF3B30");
    private static final int GOLD = Color.parseColor("#FFC107");

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Listener listener;
    private float heading = 0f;
    private boolean hasData = false;
    private final double[] az = new double[9];
    private final double[] alt = new double[9];
    private int moonPhase = 0;
    private int selected = 1;

    private float cx, cy, R;

    public SkyDialView(Context c) {
        super(c);
        tp.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) { listener = l; }
    public void setHeading(float h) { heading = h; invalidate(); }
    public void setSelected(int i) { selected = i; invalidate(); }

    public void setBodies(double[] a, double[] h, int phase) {
        System.arraycopy(a, 0, az, 0, 9);
        System.arraycopy(h, 0, alt, 0, 9);
        moonPhase = phase;
        hasData = true;
        invalidate();
    }

    private float px(float r, double deg) { return cx + r * (float) Math.sin(Math.toRadians(deg)); }
    private float py(float r, double deg) { return cy - r * (float) Math.cos(Math.toRadians(deg)); }

    private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold) {
        tp.setTextSize(size);
        tp.setColor(color);
        tp.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        c.drawText(s, x, y, tp);
    }

    // where body i is drawn
    private float bodyRadius(int i) {
        if (alt[i] > 0) return R * (0.90f - 0.55f * (float) (alt[i] / 90.0));
        return R * 0.97f;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float unit = Math.min(w, h);
        c.drawColor(Color.parseColor("#050510"));
        cx = w / 2f; cy = h / 2f; R = unit * 0.37f;

        // faint horizon circle
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(unit * 0.003f);
        p.setColor(Color.parseColor("#33334A"));
        c.drawCircle(cx, cy, R * 0.90f, p);
        c.drawCircle(cx, cy, R * 0.35f, p);

        // outer ring + ticks (turn with the phone so North stays North)
        p.setStrokeWidth(unit * 0.007f);
        p.setColor(Color.WHITE);
        c.drawCircle(cx, cy, R, p);
        for (int d = 0; d < 360; d += 5) {
            double a = d - heading;
            float len = (d % 30 == 0) ? R * 0.07f : R * 0.035f;
            p.setStrokeWidth(unit * (d % 30 == 0 ? 0.005f : 0.0025f));
            c.drawLine(px(R, a), py(R, a), px(R + len, a), py(R + len, a), p);
        }

        // N E S W outside the ring
        String[] card = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double a = i * 90 - heading;
            float size = i == 0 ? R * 0.20f : R * 0.14f;
            text(c, card[i], px(R * 1.17f, a), py(R * 1.17f, a) + size * 0.35f, size, i == 0 ? RED : Color.WHITE, true);
        }

        // centre = straight up
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.parseColor("#6666AA"));
        c.drawCircle(cx, cy, unit * 0.008f, p);

        if (hasData) {
            // draw below-horizon first (dim), then visible ones on top
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < 9; i++) {
                    boolean up = alt[i] > 0;
                    if ((pass == 0) == up) continue;
                    drawBody(c, i, unit, up);
                }
            }
        }

        // white marker = the way the top of the phone points
        Path tri = new Path();
        float tw = unit * 0.028f;
        float ty = cy - R * 1.36f;
        tri.moveTo(cx, ty + tw * 1.5f);
        tri.lineTo(cx - tw, ty);
        tri.lineTo(cx + tw, ty);
        tri.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawPath(tri, p);
    }

    private float[] pos(int i) {
        float r = bodyRadius(i);
        return new float[]{px(r, az[i] - heading), py(r, az[i] - heading)};
    }

    private void drawBody(Canvas c, int i, float unit, boolean up) {
        float[] xy = pos(i);
        float x = xy[0], y = xy[1];
        int alpha = up ? 255 : 120;
        float rad = unit * SIZE[i] * (up ? 1f : 0.8f);
        float labelGap;

        if (i == 1) { // Moon shows today's real phase
            float s = unit * 0.075f;
            tp.setStyle(Paint.Style.FILL);
            tp.setTextSize(s);
            tp.setTypeface(Typeface.DEFAULT);
            tp.setColor(Color.WHITE);
            tp.setAlpha(alpha);
            c.drawText(MOON_EMOJI[moonPhase], x, y + s * 0.35f, tp);
            tp.setAlpha(255);
            labelGap = unit * 0.06f;
        } else {
            // crisp icon: a dark rim keeps neighbouring icons apart, no glow
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#050510"));
            c.drawCircle(x, y, rad + unit * 0.007f, p);

            if (i == 6) { // Saturn: ring behind the planet
                c.save();
                c.rotate(-20f, x, y);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(unit * 0.005f);
                p.setColor(Color.argb(alpha, 0xE3, 0xC7, 0x7D));
                c.drawOval(new RectF(x - rad * 2.0f, y - rad * 0.6f, x + rad * 2.0f, y + rad * 0.6f), p);
                c.restore();
            }

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(alpha, Color.red(COLORS[i]), Color.green(COLORS[i]), Color.blue(COLORS[i])));
            c.drawCircle(x, y, rad, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(unit * 0.003f);
            p.setColor(Color.argb(up ? 150 : 60, 255, 255, 255));
            c.drawCircle(x, y, rad, p);
            p.setStyle(Paint.Style.FILL);
            labelGap = rad * (i == 6 ? 1.6f : 1.0f) + unit * 0.035f;
        }

        // put the name above the icon when another icon is close by, so names never pile up
        boolean above = false;
        for (int j = 0; j < i; j++) {
            float[] o = pos(j);
            if (Math.hypot(o[0] - x, o[1] - y) < unit * 0.09f) { above = true; break; }
        }
        float ls = unit * 0.03f;
        float ly = above ? y - labelGap + ls * 0.3f : y + labelGap;

        tp.setTextSize(ls);
        tp.setTypeface(Typeface.DEFAULT_BOLD);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(unit * 0.008f);
        tp.setColor(Color.parseColor("#050510"));
        c.drawText(Astro.NAMES[i], x, ly, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(Color.WHITE);
        tp.setAlpha(up ? 255 : 130);
        c.drawText(Astro.NAMES[i], x, ly, tp);
        tp.setAlpha(255);

        if (i == selected) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(unit * 0.005f);
            p.setColor(GOLD);
            c.drawCircle(x, y, unit * 0.06f, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return hasData;
        if (e.getAction() == MotionEvent.ACTION_UP && hasData) {
            float unit = Math.min(getWidth(), getHeight());
            int best = -1;
            float bestD = unit * 0.10f;
            for (int i = 0; i < 9; i++) {
                float r = bodyRadius(i);
                float x = px(r, az[i] - heading), y = py(r, az[i] - heading);
                float d = (float) Math.hypot(e.getX() - x, e.getY() - y);
                if (d < bestD) { bestD = d; best = i; }
            }
            if (best >= 0) {
                selected = best;
                invalidate();
                if (listener != null) listener.onSelect(best);
            }
            return true;
        }
        return true;
    }
}
