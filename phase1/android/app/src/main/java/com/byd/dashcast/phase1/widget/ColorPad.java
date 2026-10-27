package com.byd.dashcast.phase1.widget;

import android.content.Context;
import android.view.View;

public class ColorPad extends View {
    public interface Listener {
        void onColor(int rgb);
    }

    private final float[] hsv = new float[]{0f, 1f, 1f};
    private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Paint ring = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final Listener cb;

    public ColorPad(Context c, Listener l) {
        super(c);
        cb = l;
        ring.setStyle(android.graphics.Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 2));
    }

    public void setHsv(float h, float s, float v) {
        hsv[0] = h;
        hsv[1] = s;
        hsv[2] = v;
        invalidate();
    }

    private int stripH() {
        return Ui.dp(getContext(), 26);
    }

    private int gap() {
        return Ui.dp(getContext(), 16);
    }

    private int sq() {
        return Math.min(getWidth(), getHeight() - stripH() - gap());
    }

    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Ui.dp(getContext(), 250));
    }

    protected void onDraw(android.graphics.Canvas canvas) {
        int s = sq();
        int top = s + gap();
        int[] rainbow = {
                android.graphics.Color.RED, android.graphics.Color.YELLOW,
                android.graphics.Color.GREEN, android.graphics.Color.CYAN,
                android.graphics.Color.BLUE, android.graphics.Color.MAGENTA,
                android.graphics.Color.RED
        };
        paint.setShader(new android.graphics.LinearGradient(0, 0, getWidth(), 0,
                rainbow, null, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, top, getWidth(), top + stripH(), paint);
        int full = android.graphics.Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
        paint.setShader(new android.graphics.LinearGradient(0, 0, s, 0,
                0xFFFFFFFF, full, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, s, s, paint);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, s,
                0x00000000, 0xFF000000, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, s, s, paint);
        paint.setShader(null);
        float mx = hsv[1] * s;
        float my = (1f - hsv[2]) * s;
        int lum = (int) (hsv[2] * (1f - hsv[1] * 0.5f) * 255f);
        ring.setColor(lum > 140 ? 0xFF000000 : 0xFFFFFFFF);
        canvas.drawCircle(mx, my, Ui.dp(getContext(), 7), ring);
        float hx = hsv[0] / 360f * getWidth();
        int hw = Ui.dp(getContext(), 3);
        canvas.drawRect(hx - hw, top - Ui.dp(getContext(), 3),
                hx + hw, top + stripH() + Ui.dp(getContext(), 3), ring);
    }

    public boolean onTouchEvent(android.view.MotionEvent e) {
        int a = e.getAction();
        if (a != android.view.MotionEvent.ACTION_DOWN
                && a != android.view.MotionEvent.ACTION_MOVE) return true;
        int s = sq();
        float x = e.getX();
        float y = e.getY();
        if (y <= s) {
            hsv[1] = clamp01(x / s);
            hsv[2] = 1f - clamp01(y / s);
        } else if (y >= s + gap() && y <= s + gap() + stripH()) {
            hsv[0] = clamp01(x / getWidth()) * 360f;
        } else {
            return true;
        }
        invalidate();
        if (cb != null) cb.onColor(android.graphics.Color.HSVToColor(hsv));
        return true;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
