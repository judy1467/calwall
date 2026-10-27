package dev.dsh.calwall;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    static final String NAME = "calwall";
    static final String KEY_DARK = "dark";
    static final String KEY_ACCENT = "accent";
    static final String KEY_AUTO = "auto";
    static final String KEY_LAST_DAY = "lastApplyDay";
    static final String KEY_PHOTO = "photoUri";
    static final String KEY_CARD_ALPHA = "cardAlpha";
    static final String KEY_CONTENT_ALPHA = "contentAlpha";
    static final String KEY_SCALE = "scale";
    static final String KEY_SCALE_X = "scaleX";
    static final String KEY_SCALE_Y = "scaleY";
    static final String KEY_DX = "dx";
    static final String KEY_DY = "dy";
    static final String KEY_PHOTO_ZOOM = "photoZoom";
    static final String KEY_PHOTO_DX = "photoDx";
    static final String KEY_PHOTO_DY = "photoDy";
    static final String KEY_C_WEEKDAY = "cWeekday";
    static final String KEY_C_DATE = "cDate";
    static final String KEY_C_EVENT = "cEvent";

    static final int[] ACCENTS = { 0xFF4C8DFF, 0xFFFF6B6B, 0xFF34D399 };

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static boolean isDark(Context c) {
        return p(c).getBoolean(KEY_DARK, false);
    }

    static void setDark(Context c, boolean v) {
        p(c).edit().putBoolean(KEY_DARK, v).apply();
    }

    static int accentIndex(Context c) {
        int i = p(c).getInt(KEY_ACCENT, 0);
        return (i < 0 || i >= ACCENTS.length) ? 0 : i;
    }

    static void setAccentIndex(Context c, int i) {
        p(c).edit().putInt(KEY_ACCENT, i).apply();
    }

    static int accentColor(Context c) {
        return ACCENTS[accentIndex(c)];
    }

    static boolean isAuto(Context c) {
        return p(c).getBoolean(KEY_AUTO, true);
    }

    static void setAuto(Context c, boolean v) {
        p(c).edit().putBoolean(KEY_AUTO, v).apply();
    }

    static String photoUri(Context c) {
        return p(c).getString(KEY_PHOTO, "");
    }

    static void setPhotoUri(Context c, String uri) {
        p(c).edit().putString(KEY_PHOTO, uri == null ? "" : uri).apply();
    }

    static int photoZoom(Context c) {
        return clamp(p(c).getInt(KEY_PHOTO_ZOOM, 100), 100, 400);
    }

    static int photoDx(Context c) {
        return clamp(p(c).getInt(KEY_PHOTO_DX, 0), -100, 100);
    }

    static int photoDy(Context c) {
        return clamp(p(c).getInt(KEY_PHOTO_DY, 0), -100, 100);
    }

    static void setPhotoTransform(Context c, int zoom, int dx, int dy) {
        p(c).edit()
                .putInt(KEY_PHOTO_ZOOM, clamp(zoom, 100, 400))
                .putInt(KEY_PHOTO_DX, clamp(dx, -100, 100))
                .putInt(KEY_PHOTO_DY, clamp(dy, -100, 100))
                .apply();
    }

    static int colorWeekday(Context c) {
        return p(c).getInt(KEY_C_WEEKDAY, 0);
    }

    static int colorDate(Context c) {
        return p(c).getInt(KEY_C_DATE, 0);
    }

    static int colorEvent(Context c) {
        return p(c).getInt(KEY_C_EVENT, 0);
    }

    static void setColor(Context c, int which, int color) {
        String k = which == 0 ? KEY_C_WEEKDAY : (which == 1 ? KEY_C_DATE : KEY_C_EVENT);
        p(c).edit().putInt(k, color).apply();
    }

    static int cardAlpha(Context c) {
        return clamp(p(c).getInt(KEY_CARD_ALPHA, 94), 0, 100);
    }

    static int contentAlpha(Context c) {
        return clamp(p(c).getInt(KEY_CONTENT_ALPHA, 100), 0, 100);
    }

    static int scalePct(Context c) {
        return scaleXPct(c);
    }

    static int scaleXPct(Context c) {
        return clamp(p(c).getInt(KEY_SCALE_X, p(c).getInt(KEY_SCALE, 100)), 40, 200);
    }

    static int scaleYPct(Context c) {
        return clamp(p(c).getInt(KEY_SCALE_Y, p(c).getInt(KEY_SCALE, 100)), 40, 200);
    }

    static int dxPct(Context c) {
        return clamp(p(c).getInt(KEY_DX, 0), -45, 45);
    }

    static int dyPct(Context c) {
        return clamp(p(c).getInt(KEY_DY, 0), -45, 45);
    }

    static void setCardAlpha(Context c, int v) {
        p(c).edit().putInt(KEY_CARD_ALPHA, clamp(v, 0, 100)).apply();
    }

    static void setContentAlpha(Context c, int v) {
        p(c).edit().putInt(KEY_CONTENT_ALPHA, clamp(v, 0, 100)).apply();
    }

    static void setLayout(Context c, int scaleX, int scaleY, int dx, int dy) {
        p(c).edit()
                .putInt(KEY_SCALE_X, clamp(scaleX, 40, 200))
                .putInt(KEY_SCALE_Y, clamp(scaleY, 40, 200))
                .putInt(KEY_DX, clamp(dx, -45, 45))
                .putInt(KEY_DY, clamp(dy, -45, 45))
                .apply();
    }

    static void resetLayout(Context c) {
        p(c).edit().putInt(KEY_SCALE_X, 100).putInt(KEY_SCALE_Y, 100).putInt(KEY_DX, 0).putInt(KEY_DY, 0).apply();
    }

    static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static int todayKey() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR);
    }

    static int lastApplyDay(Context c) {
        return p(c).getInt(KEY_LAST_DAY, 0);
    }

    static void markApplied(Context c) {
        p(c).edit().putInt(KEY_LAST_DAY, todayKey()).apply();
    }

    static boolean staleToday(Context c) {
        return lastApplyDay(c) != todayKey();
    }
}





