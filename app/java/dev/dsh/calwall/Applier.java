package dev.dsh.calwall;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;

/** Renders the calendar and applies it to the lock screen; can also put the raw photo on the home screen. */
public final class Applier {
    static final String TAG = "calwall";
    private static final int MAX_SIDE = 3072;

    /** Returns {canvasW, canvasH, screenW, screenH}. */
    private static int[] sizeOf(Context ctx) {
        int sw = 0;
        int sh = 0;
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            sw = b.width();
            sh = b.height();
        }
        if (sw <= 0 || sh <= 0) {
            sw = 1248;
            sh = 1972;
        }

        WallpaperManager wpm = WallpaperManager.getInstance(ctx);
        int cw = wpm.getDesiredMinimumWidth();
        int ch = wpm.getDesiredMinimumHeight();
        if (cw <= 0 || ch <= 0) {
            cw = Math.max(sw, sh) * 2;
            ch = cw;
        }
        int longest = Math.max(cw, ch);
        if (longest > MAX_SIDE) {
            float k = (float) MAX_SIDE / (float) longest;
            cw = Math.max(1, (int) (cw * k));
            ch = Math.max(1, (int) (ch * k));
        }
        return new int[] { cw, ch, sw, sh };
    }

    public static boolean apply(Context ctx) {
        Bitmap bmp = null;
        try {
            int[] s = sizeOf(ctx);
            int cw = s[0];
            int ch = s[1];
            bmp = WallpaperRenderer.render(ctx, cw, ch, s[2], s[3]);
            WallpaperManager wpm = WallpaperManager.getInstance(ctx);
            wpm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK);
            Prefs.markApplied(ctx);
            savePreview(ctx, bmp);
            Log.i(TAG, "lock wallpaper applied canvas=" + cw + "x" + ch + " screen=" + s[2] + "x" + s[3]);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "apply failed", t);
            return false;
        } finally {
            if (bmp != null) {
                bmp.recycle();
            }
        }
    }

    /** Puts the chosen photo (no calendar) on the home screen. */
    public static boolean applyHomePhoto(Context ctx) {
        Bitmap bmp = null;
        try {
            int[] s = sizeOf(ctx);
            bmp = WallpaperRenderer.photoOnly(ctx, s[0], s[1]);
            if (bmp == null) {
                Log.w(TAG, "home photo skipped: no photo selected");
                return false;
            }
            WallpaperManager wpm = WallpaperManager.getInstance(ctx);
            wpm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM);
            Log.i(TAG, "home wallpaper photo applied " + s[0] + "x" + s[1]);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "home photo failed", t);
            return false;
        } finally {
            if (bmp != null) {
                bmp.recycle();
            }
        }
    }

    private static void savePreview(Context ctx, Bitmap src) {
        try {
            File dir = ctx.getExternalFilesDir(null);
            if (dir == null) return;
            int w = 720;
            int h = Math.max(1, (int) ((float) src.getHeight() * w / src.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            File f = new File(dir, "preview.png");
            FileOutputStream os = new FileOutputStream(f);
            small.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.close();
            small.recycle();
        } catch (Throwable ignored) {
        }
    }
}
