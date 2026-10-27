package dev.dsh.calwall;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Brightness bar: black on the left, fully bright hue on the right. */
public class ValueBarView extends View {

    public interface Listener {
        void onValue(float value);
    }

    private float value = 1f;
    private float hue = 0f;
    private Listener listener;

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public ValueBarView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        thumb.setStyle(Paint.Style.STROKE);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public float value() {
        return value;
    }

    public void setValue(float v) {
        value = Math.max(0f, Math.min(1f, v));
        invalidate();
    }

    public void setHue(float h) {
        hue = h;
        invalidate();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight();
        float inset = dp(12);
        rect.set(0, inset, getWidth(), h - inset);
        int bright = Color.HSVToColor(new float[] { hue, 1f, 1f });
        bar.setShader(new LinearGradient(0, 0, getWidth(), 0, 0xFF000000, bright, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, inset, inset, bar);
        bar.setShader(null);

        float x = value * getWidth();
        float cy = rect.centerY();
        thumb.setStrokeWidth(dp(3));
        thumb.setColor(0xFFFFFFFF);
        c.drawCircle(x, cy, dp(9), thumb);
        thumb.setStrokeWidth(dp(1));
        thumb.setColor(0x66000000);
        c.drawCircle(x, cy, dp(11), thumb);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                value = Math.max(0f, Math.min(1f, e.getX() / Math.max(1f, getWidth())));
                invalidate();
                if (listener != null) listener.onValue(value);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
