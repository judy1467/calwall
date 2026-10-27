package dev.dsh.calwall;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextPaint;
import android.text.TextUtils;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Calendar overlay on the user's photo: 4 week strips with coloured event bars. The upper part is
 * left free for the lock screen clock.
 */
public final class WallpaperRenderer {

    private static final String[] WD = { "일", "월", "화", "수", "목", "금", "토" };
    private static final float PORTRAIT_ASPECT_FALLBACK = 0.633f;
    private static final int WEEKS = 4;
    private static final int MAX_LANES = 3;

    public static Bitmap render(Context ctx, int canvasW, int canvasH, int screenW, int screenH) {
        Bitmap bmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        draw(ctx, c, canvasW, canvasH, screenW, screenH);
        return bmp;
    }

    private static void draw(Context ctx, Canvas c, int w, int h, int screenW, int screenH) {
        final boolean dark = Prefs.isDark(ctx);
        final int accent = Prefs.accentColor(ctx);

        final int bgTop = dark ? 0xFF0C1218 : 0xFFF3F6F9;
        final int bgBottom = dark ? 0xFF141C25 : 0xFFE9EEF3;
        final int card = dark ? 0xFF121A23 : 0xFFFFFFFF;
        final int text = dark ? 0xFFF2F6FA : 0xFF11181F;
        final int dim = dark ? 0xFF8B99A8 : 0xFF7C8794;
        final int line = dark ? 0x18FFFFFF : 0x14000000;
        final int cWeekday = Prefs.colorWeekday(ctx);
        final int cDate = Prefs.colorDate(ctx);
        final int cEvent = Prefs.colorEvent(ctx);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, bgTop, bgBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        boolean hasPhoto = false;
        String photo = Prefs.photoUri(ctx);
        if (photo.length() > 0) {
            Bitmap bg = decodeCover(ctx, Uri.parse(photo), w, h);
            if (bg != null) {
                drawPhoto(c, ctx, bg, w, h);
                bg.recycle();
                hasPhoto = true;

                Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
                scrim.setShader(new LinearGradient(0, 0, 0, h * 0.58f,
                        dark ? 0x99000000 : 0x59000000, 0x00000000, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h * 0.58f, scrim);
                scrim.setShader(null);
            }
        }

        float aspect = screenH > 0 ? (float) screenW / (float) screenH : PORTRAIT_ASPECT_FALLBACK;
        float cw = Math.min(w, h * aspect);
        cw = Math.min(cw, w * 0.94f);
        float ox = (w - cw) / 2f;

        c.save();
        c.translate(ox, 0);

        final float scX = Prefs.scaleXPct(ctx) / 100f;
        final float scY = Prefs.scaleYPct(ctx) / 100f;
        final float dxPx = Prefs.dxPct(ctx) / 100f * cw;
        final float dyPx = Prefs.dyPct(ctx) / 100f * h;
        final int cardAlpha = Prefs.cardAlpha(ctx) * 255 / 100;
        final int contentAlpha = Prefs.contentAlpha(ctx) * 255 / 100;

        c.save();
        c.translate(dxPx, dyPx);

        // Layout stretch: grow the card box and keep the type size, so widening the calendar
        // simply gives each day more room instead of squashing the glyphs.
        final float ccx = cw / 2f;
        final float ccy = h / 2f;
        float cardLeft = ccx + (cw * 0.055f - ccx) * scX;
        float cardRight = ccx + (cw * 0.945f - ccx) * scX;
        float cardTop = ccy + (h * 0.320f - ccy) * scY;
        float cardBottom = ccy + (h * 0.930f - ccy) * scY;
        RectF cardRect = new RectF(cardLeft, cardTop, cardRight, cardBottom);
        Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
        cp.setColor(card);
        cp.setAlpha(cardAlpha);
        c.drawRoundRect(cardRect, cw * 0.035f, cw * 0.035f, cp);
        cp.setAlpha(255);

        final int contentLayer = c.saveLayerAlpha(cardRect, contentAlpha);

        final float pad = cw * 0.048f;
        final float innerLeft = cardLeft + pad;
        final float innerRight = cardRight - pad;
        final float innerW = innerRight - innerLeft;
        final float colW = innerW / 7f;

        TextPaint wdP = txt(0.024f * cw, cWeekday != 0 ? cWeekday : dim, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        float wdBase = cardTop + h * 0.030f;
        for (int i = 0; i < 7; i++) {
            String s = WD[i];
            float cx = innerLeft + colW * i + colW / 2f;
            c.drawText(s, cx - wdP.measureText(s) / 2f, wdBase, wdP);
        }

        Calendar today = Calendar.getInstance();
        Calendar weekStart = (Calendar) today.clone();
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);
        int dow = weekStart.get(Calendar.DAY_OF_WEEK) - 1;
        weekStart.add(Calendar.DAY_OF_MONTH, -dow);

        long rangeFrom = weekStart.getTimeInMillis();
        Calendar rangeEnd = (Calendar) weekStart.clone();
        rangeEnd.add(Calendar.DAY_OF_MONTH, WEEKS * 7);
        List<CalendarSource.Ev> events = CalendarSource.query(ctx, rangeFrom, rangeEnd.getTimeInMillis());

        float weeksTop = wdBase + h * 0.018f;
        float weeksH = cardBottom - pad * 0.6f - weeksTop;
        float weekH = weeksH / WEEKS;

        TextPaint dayP = txt(0.030f * cw, text, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextPaint barP = txt(0.0235f * cw, text, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        Paint barBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint sep = new Paint(Paint.ANTI_ALIAS_FLAG);
        sep.setColor(line);
        sep.setStrokeWidth(Math.max(1f, h * 0.0006f));

        for (int wIdx = 0; wIdx < WEEKS; wIdx++) {
            Calendar ws = (Calendar) weekStart.clone();
            ws.add(Calendar.DAY_OF_MONTH, wIdx * 7);
            long wsMs = ws.getTimeInMillis();

            float top = weeksTop + weekH * wIdx;
            float dayBase = top + h * 0.028f;

            for (int d = 0; d < 7; d++) {
                Calendar day = (Calendar) ws.clone();
                day.add(Calendar.DAY_OF_MONTH, d);
                boolean isToday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                        && day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

                if (cDate != 0) dayP.setColor(cDate);
                else if (d == 0) dayP.setColor(dark ? 0xFFF87171 : 0xFFE05A5A);
                else if (d == 6) dayP.setColor(dark ? 0xFF60A5FA : 0xFF3B82F6);
                else dayP.setColor(text);
                if (isToday && cDate == 0) dayP.setColor(accent);

                String ds = String.valueOf(day.get(Calendar.DAY_OF_MONTH));
                float cx = innerLeft + colW * d + colW / 2f;
                c.drawText(ds, cx - dayP.measureText(ds) / 2f, dayBase, dayP);

                if (isToday) {
                    Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                    ring.setColor(accent);
                    ring.setStyle(Paint.Style.STROKE);
                    ring.setStrokeWidth(Math.max(1.5f, cw * 0.0022f));
                    c.drawCircle(cx, dayBase - dayP.getTextSize() * 0.36f, colW * 0.225f, ring);
                }
            }

            float laneTop = dayBase + h * 0.012f;
            float laneH = Math.min(h * 0.0265f, (weekH - (laneTop - top) - h * 0.008f) / MAX_LANES);
            if (laneH < h * 0.012f) laneH = h * 0.012f;
            float barH = laneH * 0.80f;

            final long wsDay = dayOf(wsMs, false);

            List<int[]> slots = new ArrayList<int[]>();
            List<CalendarSource.Ev> weekEvents = new ArrayList<CalendarSource.Ev>();
            for (CalendarSource.Ev e : events) {
                long sDay = dayOf(e.begin, e.allDay);
                long eDay = dayOf(Math.max(e.begin, e.end - 1), e.allDay);
                if (eDay < wsDay || sDay > wsDay + 6) continue;
                weekEvents.add(e);
            }
            for (int i = 0; i < weekEvents.size(); i++) {
                for (int k = i + 1; k < weekEvents.size(); k++) {
                    long a = dayOf(weekEvents.get(i).begin, weekEvents.get(i).allDay);
                    long b = dayOf(weekEvents.get(k).begin, weekEvents.get(k).allDay);
                    if (b < a) {
                        CalendarSource.Ev t = weekEvents.get(i);
                        weekEvents.set(i, weekEvents.get(k));
                        weekEvents.set(k, t);
                    }
                }
            }

            for (CalendarSource.Ev e : weekEvents) {
                long sDay = dayOf(e.begin, e.allDay);
                long eDay = dayOf(Math.max(e.begin, e.end - 1), e.allDay);
                int c0 = (int) Math.max(0L, sDay - wsDay);
                int c1 = (int) Math.min(6L, eDay - wsDay);
                if (c1 < 0 || c0 > 6 || c1 < c0) continue;

                int lane = -1;
                for (int l = 0; l < MAX_LANES; l++) {
                    boolean free = true;
                    for (int[] s : slots) {
                        if (s[0] == l && !(c1 < s[1] || c0 > s[2])) {
                            free = false;
                            break;
                        }
                    }
                    if (free) { lane = l; break; }
                }
                if (lane < 0) continue;

                // text stays inside the bar so a one-day event never looks like a multi-day one
                int freeEnd = c1;

                slots.add(new int[] { lane, c0, c1 });

                float left = innerLeft + colW * c0 + cw * 0.004f;
                float right = innerLeft + colW * (c1 + 1) - cw * 0.004f;
                float by = laneTop + laneH * lane + (laneH - barH) / 2f;
                RectF bar = new RectF(left, by, right, by + barH);

                int col = e.color != 0 ? e.color : accent;
                barBg.setColor(col);
                barBg.setAlpha(dark ? 70 : 46);
                c.drawRoundRect(bar, barH * 0.32f, barH * 0.32f, barBg);

                // single-day bars skip the colour chip so more of the title fits
                float textX;
                if (c1 == c0) {
                    textX = left + barH * 0.30f;
                } else {
                    Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
                    chip.setColor(col);
                    float chipR = barH * 0.20f;
                    float chipX = left + barH * 0.55f;
                    float chipY = by + barH / 2f;
                    c.drawCircle(chipX, chipY, chipR, chip);
                    textX = chipX + chipR + barH * 0.30f;
                }

                barP.setColor(cEvent != 0 ? cEvent : (dark ? 0xFFF7FAFC : 0xFF000000));
                float textLimit = innerLeft + colW * (freeEnd + 1) - cw * 0.008f;
                float maxW = textLimit - textX - barH * 0.20f;
                if (maxW > barH * 0.8f) {
                    CharSequence t = TextUtils.ellipsize(e.title, barP, maxW, TextUtils.TruncateAt.END);
                    c.drawText(t, 0, t.length(), textX, by + barH / 2f + barP.getTextSize() * 0.35f, barP);
                }
            }

            if (wIdx < WEEKS - 1) {
                c.drawLine(innerLeft, top + weekH - h * 0.004f, innerRight, top + weekH - h * 0.004f, sep);
            }
        }

        c.restoreToCount(contentLayer);
        c.restore();
        c.restore();
    }

    /**
     * Calendar day number for an instant. All-day events are stored at UTC midnight
     * (dtstart 00:00Z, dtend = the next day 00:00Z), so they must be bucketed in UTC or the
     * last day shifts by one in positive-offset zones such as KST.
     */
    private static long dayOf(long millis, boolean allDay) {
        long offset = allDay ? 0L : TimeZone.getDefault().getOffset(millis);
        return Math.floorDiv(millis + offset, 86400000L);
    }

    /** The chosen photo alone (no calendar), centre cropped to w x h. Null when no photo is set. */
    public static Bitmap photoOnly(Context ctx, int w, int h) {
        String photo = Prefs.photoUri(ctx);
        if (photo.length() == 0) {
            return null;
        }
        Bitmap bg = decodeCover(ctx, Uri.parse(photo), w, h);
        if (bg == null) {
            return null;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Prefs.isDark(ctx) ? 0xFF0C1218 : 0xFFF3F6F9);
        drawPhoto(c, ctx, bg, w, h);
        bg.recycle();
        return out;
    }

    /** Draws the photo with the user's zoom / pan applied (always covers the frame). */
    public static void drawPhoto(Canvas c, Context ctx, Bitmap bg, int w, int h) {
        float base = Math.max((float) w / bg.getWidth(), (float) h / bg.getHeight());
        float s = base * (Prefs.photoZoom(ctx) / 100f);
        float dw = bg.getWidth() * s;
        float dh = bg.getHeight() * s;
        float left = (w - dw) / 2f * (1f - Prefs.photoDx(ctx) / 100f);
        float top = (h - dh) / 2f * (1f - Prefs.photoDy(ctx) / 100f);
        Paint ip = new Paint(Paint.ANTI_ALIAS_FLAG);
        ip.setFilterBitmap(true);
        c.drawBitmap(bg, null, new RectF(left, top, left + dw, top + dh), ip);
    }

    /** Loads the chosen photo for the crop UI. */
    public static Bitmap loadPhotoForCrop(Context ctx, int targetSide) {
        String photo = Prefs.photoUri(ctx);
        if (photo.length() == 0) {
            return null;
        }
        return decodeCover(ctx, Uri.parse(photo), targetSide, targetSide);
    }

    private static Bitmap decodeCover(Context ctx, Uri uri, int targetW, int targetH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= targetW && bounds.outHeight / (sample * 2) >= targetH) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            in.close();
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int mix(int a, int b, float k) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar * (1 - k) + br * k);
        int g = (int) (ag * (1 - k) + bg * k);
        int bl = (int) (ab * (1 - k) + bb * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static TextPaint txt(float size, int color, Typeface tf) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(tf);
        return p;
    }
}















