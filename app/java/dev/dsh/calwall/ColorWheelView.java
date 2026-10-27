package dev.dsh.calwall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** HSV disc: angle = hue, radius = saturation. Brightness is applied as a darkening overlay. */
public class ColorWheelView extends View {

    public interface Listener {
        void onHueSat(float hue, float sat);
    }

    private static final int[] HUES = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    private float hue = 0f;
    private float sat = 0f;

    private Listener listener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ColorWheelView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        ring.setStyle(Paint.Style.STROKE);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        sat = hsv[1];
        invalidate();
    }

    public float hue() {
        return hue;
    }

    public float sat() {
        return sat;
    }


    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - dp(8);
        if (r <= 0) return;

        fill.setShader(new SweepGradient(cx, cy, HUES, null));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(new RadialGradient(cx, cy, r, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);


        ring.setStrokeWidth(dp(1));
        ring.setColor(0x33000000);
        c.drawCircle(cx, cy, r, ring);

        double rad = Math.toRadians(hue);
        float sx = (float) (cx + Math.cos(rad) * r * sat);
        float sy = (float) (cy + Math.sin(rad) * r * sat);
        ring.setStrokeWidth(dp(3));
        ring.setColor(0xFFFFFFFF);
        c.drawCircle(sx, sy, dp(9), ring);
        ring.setStrokeWidth(dp(1));
        ring.setColor(0x66000000);
        c.drawCircle(sx, sy, dp(11), ring);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float r = Math.max(1f, Math.min(cx, cy) - dp(8));
                float dx = e.getX() - cx;
                float dy = e.getY() - cy;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                sat = Math.min(1f, dist / r);
                float ang = (float) Math.toDegrees(Math.atan2(dy, dx));
                if (ang < 0) ang += 360f;
                hue = ang;
                invalidate();
                if (listener != null) listener.onHueSat(hue, sat);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}

