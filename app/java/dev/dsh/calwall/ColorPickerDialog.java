package dev.dsh.calwall;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** HSV wheel + brightness bar + hex/RGB readout. */
public final class ColorPickerDialog {

    public interface OnPick {
        void onColor(int color);
    }

    public static void show(final Context ctx, int initial, final OnPick cb) {
        // White/black have no meaningful hue, which made the brightness bar look red for no reason.
        // Start from the app accent colour instead so wheel, bar and hex agree.
        int start = initial != 0 ? initial : Prefs.accentColor(ctx);

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 16);
        box.setPadding(pad, pad, pad, pad);

        final ColorWheelView wheel = new ColorWheelView(ctx, null);
        box.addView(wheel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 220)));

        final ValueBarView bar = new ValueBarView(ctx, null);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 46));
        blp.topMargin = dp(ctx, 10);
        box.addView(bar, blp);

        final EditText hex = new EditText(ctx);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT);
        hex.setTextSize(14);
        hex.setText(hexOf(start));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(ctx, 6);
        box.addView(hex, hlp);

        final TextView info = new TextView(ctx);
        info.setTextSize(13);
        info.setTextColor(0xFFB9C4CF);
        info.setPadding(0, dp(ctx, 4), 0, 0);
        box.addView(info);

        final boolean[] syncing = { false };

        wheel.setColor(start);
        float startValue = colorValue(start);
        if (startValue < 0.05f) {
            startValue = 1f;   // black would make the wheel look dead
        }
        bar.setValue(startValue);
        // achromatic colours (white/grey) have no hue; borrow the accent hue so the bar is not red
        float[] hsvStart = new float[3];
        Color.colorToHSV(start, hsvStart);
        bar.setHue(hsvStart[1] < 0.05f ? colorHue(Prefs.accentColor(ctx)) : hsvStart[0]);
        syncing[0] = true;
        hex.setText(hexOf(composed(wheel, bar)));
        syncing[0] = false;

        final Runnable refresh = new Runnable() {
            public void run() {
                int color = composed(wheel, bar);
                info.setText("R " + Color.red(color) + "   G " + Color.green(color)
                        + "   B " + Color.blue(color));
            }
        };

        wheel.setListener(new ColorWheelView.Listener() {
            public void onHueSat(float h, float s) {
                bar.setHue(h);
                if (!syncing[0]) {
                    syncing[0] = true;
                    hex.setText(hexOf(composed(wheel, bar)));
                    syncing[0] = false;
                }
                refresh.run();
            }
        });

        bar.setListener(new ValueBarView.Listener() {
            public void onValue(float v) {
                if (!syncing[0]) {
                    syncing[0] = true;
                    hex.setText(hexOf(composed(wheel, bar)));
                    syncing[0] = false;
                }
                refresh.run();
            }
        });

        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                Integer parsed = parseHex(s.toString());
                if (parsed == null) return;
                syncing[0] = true;
                wheel.setColor(parsed);
                bar.setValue(colorValue(parsed));
                bar.setHue(colorHue(parsed));
                syncing[0] = false;
                refresh.run();
            }
        });

        refresh.run();

        new AlertDialog.Builder(ctx)
                .setTitle("색 고르기")
                .setView(box)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        cb.onColor(composed(wheel, bar));
                    }
                })
                .show();
    }

    private static int composed(ColorWheelView wheel, ValueBarView bar) {
        return Color.HSVToColor(new float[] { wheel.hue(), wheel.sat(), bar.value() });
    }

    private static float colorHue(int c) {
        float[] h = new float[3];
        Color.colorToHSV(c, h);
        return h[0];
    }

    private static float colorValue(int c) {
        float[] h = new float[3];
        Color.colorToHSV(c, h);
        return h[2];
    }

    private static String hexOf(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private static Integer parseHex(String s) {
        String t = s.trim();
        if (!t.startsWith("#")) t = "#" + t;
        if (!t.matches("#[0-9a-fA-F]{6}")) return null;
        try {
            return 0xFF000000 | Integer.parseInt(t.substring(1), 16);
        } catch (Throwable e) {
            return null;
        }
    }

    private static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}




