package dev.dsh.calwall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Lets the user pan / zoom the chosen photo inside the wallpaper frame. */
public class PhotoCropView extends View {

    public interface Listener {
        void onChanged(int zoom, int dx, int dy);
    }

    private Bitmap bmp;
    private int zoom = 100;
    private int dx = 0;
    private int dy = 0;
    private Listener listener;

    private final ScaleGestureDetector scaleDetector;
    private float lastX, lastY;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();

    public PhotoCropView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2));
        border.setColor(0xFFFFFFFF);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoom = clamp(Math.round(zoom * d.getScaleFactor()), 100, 400);
                apply();
                return true;
            }
        });
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setBitmap(Bitmap b) {
        bmp = b;
        invalidate();
    }

    public void setTransform(int z, int x, int y) {
        zoom = clamp(z, 100, 400);
        dx = clamp(x, -100, 100);
        dy = clamp(y, -100, 100);
        invalidate();
    }

    public void setZoom(int z) {
        zoom = clamp(z, 100, 400);
        apply();
    }

    public void reset() {
        zoom = 100;
        dx = 0;
        dy = 0;
        apply();
    }

    public int zoom() {
        return zoom;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void compute() {
        if (bmp == null || getWidth() == 0) return;
        float vw = getWidth();
        float vh = getHeight();
        float base = Math.max(vw / bmp.getWidth(), vh / bmp.getHeight());
        float s = base * (zoom / 100f);
        float dw = bmp.getWidth() * s;
        float dh = bmp.getHeight() * s;
        float left = (vw - dw) / 2f * (1f - dx / 100f);
        float top = (vh - dh) / 2f * (1f - dy / 100f);
        dst.set(left, top, left + dw, top + dh);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        compute();
        c.drawColor(0xFF000000);
        c.drawBitmap(bmp, null, dst, paint);
        c.drawRect(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1), border);
    }

    private void apply() {
        invalidate();
        if (listener != null) listener.onChanged(zoom, dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        scaleDetector.onTouchEvent(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (e.getPointerCount() > 1 || scaleDetector.isInProgress()) {
                    lastX = e.getX();
                    lastY = e.getY();
                    return true;
                }
                compute();
                float rangeX = Math.max(1f, (dst.width() - getWidth()) / 2f);
                float rangeY = Math.max(1f, (dst.height() - getHeight()) / 2f);
                dx = clamp(Math.round(dx + (e.getX() - lastX) / rangeX * 100f), -100, 100);
                dy = clamp(Math.round(dy + (e.getY() - lastY) / rangeY * 100f), -100, 100);
                lastX = e.getX();
                lastY = e.getY();
                apply();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                apply();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}


