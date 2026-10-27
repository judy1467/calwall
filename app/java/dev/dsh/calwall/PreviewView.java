package dev.dsh.calwall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Preview with a draggable calendar block.
 * Right handle = width, bottom handle = height, corner handle = both, inside = move.
 * The guide rectangle uses the same geometry as {@link WallpaperRenderer}.
 */
public class PreviewView extends View {

    public interface Listener {
        void onLayoutChanged(int scaleXPct, int scaleYPct, int dxPct, int dyPct);
    }

    private static final float CARD_LEFT = 0.055f;
    private static final float CARD_RIGHT = 0.945f;
    private static final float CARD_TOP = 0.320f;
    private static final float CARD_BOTTOM = 0.930f;

    private static final int MODE_NONE = 0;
    private static final int MODE_MOVE = 1;
    private static final int MODE_W = 2;
    private static final int MODE_H = 3;
    private static final int MODE_BOTH = 4;

    private Bitmap bmp;
    private Listener listener;

    private float sx = 1f;
    private float sy = 1f;
    private float dxPct = 0f;
    private float dyPct = 0f;

    private int mode = MODE_NONE;
    private float lastX, lastY;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final RectF card = new RectF();

    public PreviewView(Context c, AttributeSet a) {
        super(c, a);
        setClickable(true);
        setFocusable(true);
        dimPaint.setColor(0x55000000);
        handleFill.setColor(0xFFFFFFFF);
        handleStroke.setStyle(Paint.Style.STROKE);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setPreview(Bitmap b) {
        this.bmp = b;
        invalidate();
    }

    public void setValues(int scaleXPct, int scaleYPct, int dx, int dy) {
        this.sx = scaleXPct / 100f;
        this.sy = scaleYPct / 100f;
        this.dxPct = dx;
        this.dyPct = dy;
        invalidate();
    }

    private int accent() {
        return Prefs.accentColor(getContext());
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void computeDst() {
        if (bmp == null) return;
        float vw = getWidth();
        float vh = getHeight();
        float k = Math.min(vw / bmp.getWidth(), vh / bmp.getHeight());
        float w = bmp.getWidth() * k;
        float h = bmp.getHeight() * k;
        dst.set((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f);
    }

    private void computeCard() {
        if (bmp == null) return;
        computeDst();
        float bw = bmp.getWidth();
        float bh = bmp.getHeight();
        float k = dst.width() / bw;

        float cx = bw / 2f;
        float cy = bh / 2f;
        float dx = dxPct / 100f * bw;
        float dy = dyPct / 100f * bh;

        float l = CARD_LEFT * bw;
        float r = CARD_RIGHT * bw;
        float t = CARD_TOP * bh;
        float b = CARD_BOTTOM * bh;

        float l2 = (l - cx) * sx + cx + dx;
        float r2 = (r - cx) * sx + cx + dx;
        float t2 = (t - cy) * sy + cy + dy;
        float b2 = (b - cy) * sy + cy + dy;

        card.set(dst.left + l2 * k, dst.top + t2 * k, dst.left + r2 * k, dst.top + b2 * k);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        computeCard();

        c.drawBitmap(bmp, null, dst, bitmapPaint);

        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        p.addRoundRect(card, dp(10), dp(10), Path.Direction.CW);
        c.drawPath(p, dimPaint);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        borderPaint.setColor(accent());
        c.drawRoundRect(card, dp(10), dp(10), borderPaint);

        handleStroke.setStrokeWidth(dp(2));
        handleStroke.setColor(accent());

        // right handle (width)
        float rx = card.right;
        float ry = card.centerY();
        c.drawCircle(rx, ry, dp(11), handleFill);
        c.drawCircle(rx, ry, dp(11), handleStroke);

        // bottom handle (height)
        float bx = card.centerX();
        float by = card.bottom;
        c.drawCircle(bx, by, dp(11), handleFill);
        c.drawCircle(bx, by, dp(11), handleStroke);

        // corner handle (both)
        c.drawCircle(card.right, card.bottom, dp(15), handleFill);
        c.drawCircle(card.right, card.bottom, dp(15), handleStroke);
    }

    private float distTo(float x, float y, float hx, float hy) {
        float dx = x - hx;
        float dy = y - hy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        computeCard();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float corner = distTo(e.getX(), e.getY(), card.right, card.bottom);
                float right = distTo(e.getX(), e.getY(), card.right, card.centerY());
                float bottom = distTo(e.getX(), e.getY(), card.centerX(), card.bottom);

                if (corner < dp(32)) {
                    mode = MODE_BOTH;
                } else if (right < dp(26)) {
                    mode = MODE_W;
                } else if (bottom < dp(26)) {
                    mode = MODE_H;
                } else if (card.contains(e.getX(), e.getY())) {
                    mode = MODE_MOVE;
                    lastX = e.getX();
                    lastY = e.getY();
                } else {
                    mode = MODE_NONE;
                    return false;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == MODE_MOVE) {
                    float bw = bmp.getWidth();
                    float k = Math.max(0.01f, dst.width() / bw);
                    dxPct = clampF(dxPct + (e.getX() - lastX) / k / bw * 100f, -45f, 45f);
                    dyPct = clampF(dyPct + (e.getY() - lastY) / k / bmp.getHeight() * 100f, -45f, 45f);
                    lastX = e.getX();
                    lastY = e.getY();
                    invalidate();
                } else if (mode != MODE_NONE) {
                    float bw = bmp.getWidth();
                    float bh = bmp.getHeight();
                    float k = Math.max(0.01f, dst.width() / bw);
                    float dxBitmap = dxPct / 100f * bw;
                    float dyBitmap = dyPct / 100f * bh;

                    if (mode == MODE_W || mode == MODE_BOTH) {
                        float xBitmap = (e.getX() - dst.left) / k;
                        float denom = CARD_RIGHT * bw - bw / 2f;
                        sx = clampF((xBitmap - bw / 2f - dxBitmap) / denom, 0.4f, 2.0f);
                    }
                    if (mode == MODE_H || mode == MODE_BOTH) {
                        float yBitmap = (e.getY() - dst.top) / k;
                        float denom = CARD_BOTTOM * bh - bh / 2f;
                        sy = clampF((yBitmap - bh / 2f - dyBitmap) / denom, 0.4f, 2.0f);
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mode != MODE_NONE) {
                    mode = MODE_NONE;
                    if (listener != null) {
                        listener.onLayoutChanged(Math.round(sx * 100f), Math.round(sy * 100f),
                                Math.round(dxPct), Math.round(dyPct));
                    }
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
