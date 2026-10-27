package dev.dsh.calwall;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_CAL = 11;
    private static final int REQ_PHOTO = 21;
    private static final int BG = 0xFF0B1016;
    private static final int CARD = 0xFF121A22;
    private static final int TEXT = 0xFFF1F5F9;
    private static final int DIM = 0xFF8B99A8;

    private PreviewView preview;
    private TextView status;
    private TextView permNote;
    private TextView photoNote;
    private Button applyBtn;
    private Button homeBtn;
    private Button themeBtn;
    private Button clearPhotoBtn;
    private Button photoAreaBtn;
    private Button[] colorBtns = new Button[3];
    private static final int[] PALETTE = { 0, 0xFF000000, 0xFFFFFFFF, 0xFF8B99A8, 0xFF3B82F6, 0xFFE05A5A, 0xFF34D399 };
    private final Button[][] textColorBtns = new Button[3][PALETTE.length];
    private final Button[] customColorBtns = new Button[3];
    private Switch autoSwitch;

    private SeekBar contentAlphaBar;
    private SeekBar cardAlphaBar;
    private TextView contentAlphaVal;
    private TextView cardAlphaVal;



    private final SimpleDateFormat fmt = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREAN);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.READ_CALENDAR }, REQ_CAL);
        }
        Scheduler.ensure(this);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        refresh();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PHOTO && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
            Prefs.setPhotoUri(this, uri.toString());
            Prefs.setPhotoTransform(this, 100, 0, 0);
            refresh();
            openPhotoCrop();
            Toast.makeText(this, "사진 영역을 조정해 주세요", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("CalWall");
        title.setTextSize(28);
        title.setTextColor(TEXT);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("잠금 화면 캘린더 · 매일 새벽에 자동 갱신");
        sub.setTextSize(12);
        sub.setTextColor(DIM);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        preview = new PreviewView(this, null);
        preview.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(CARD);
        pbg.setCornerRadius(dp(14));
        preview.setBackground(pbg);
        preview.setListener(new PreviewView.Listener() {
            public void onLayoutChanged(int scaleXPct, int scaleYPct, int dx, int dy) {
                Prefs.setLayout(MainActivity.this, scaleXPct, scaleYPct, dx, dy);
                refresh();
            }
        });
        root.addView(preview);

        TextView hint = new TextView(this);
        hint.setText("드래그 = 이동 · 오른쪽/아래 손잡이 = 가로·세로 · 모서리 = 둘 다");
        hint.setTextSize(11);
        hint.setTextColor(0xFF5E6B78);
        hint.setPadding(0, dp(6), 0, 0);
        root.addView(hint);

        permNote = new TextView(this);
        permNote.setTextSize(12);
        permNote.setTextColor(0xFFFFB86B);
        permNote.setPadding(0, dp(10), 0, 0);
        root.addView(permNote);

        applyBtn = new Button(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        alp.topMargin = dp(12);
        applyBtn.setLayoutParams(alp);
        applyBtn.setTextSize(16);
        applyBtn.setTextColor(0xFF08111A);
        applyBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyNow();
            }
        });
        root.addView(applyBtn);

        homeBtn = new Button(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        hlp.topMargin = dp(8);
        homeBtn.setLayoutParams(hlp);
        homeBtn.setText("선택한 사진을 홈 화면에 적용");
        homeBtn.setTextSize(14);
        homeBtn.setTextColor(0xFFE6EDF3);
        homeBtn.setBackgroundTintList(ColorStateList.valueOf(0xFF1B242D));
        homeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyHome();
            }
        });
        root.addView(homeBtn);

        // ---- opacity sliders ----
        contentAlphaVal = new TextView(this);
        cardAlphaVal = new TextView(this);
        contentAlphaBar = sliderRow(root, "캘린더 투명도", Prefs.contentAlpha(this), contentAlphaVal,
                new Runnable() {
                    public void run() {
                        Prefs.setContentAlpha(MainActivity.this, contentAlphaBar.getProgress());
                        contentAlphaVal.setText(contentAlphaBar.getProgress() + "%");
                        schedulePreview();
                    }
                });
        cardAlphaBar = sliderRow(root, "카드 배경", Prefs.cardAlpha(this), cardAlphaVal,
                new Runnable() {
                    public void run() {
                        Prefs.setCardAlpha(MainActivity.this, cardAlphaBar.getProgress());
                        cardAlphaVal.setText(cardAlphaBar.getProgress() + "%");
                        schedulePreview();
                    }
                });

        Button resetBtn = new Button(this);
        resetBtn.setText("위치 · 크기 초기화");
        resetBtn.setTextSize(12);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.resetLayout(MainActivity.this);
                refresh();
            }
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        rlp.topMargin = dp(6);
        resetBtn.setLayoutParams(rlp);
        root.addView(resetBtn);

        // ---- photo row ----
        LinearLayout photoRow = new LinearLayout(this);
        photoRow.setGravity(Gravity.CENTER_VERTICAL);
        photoRow.setPadding(0, dp(12), 0, 0);
        photoRow.addView(label("배경 사진"));

        Button pickBtn = new Button(this);
        pickBtn.setText("사진 선택");
        pickBtn.setTextSize(13);
        pickBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, REQ_PHOTO);
            }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(96), dp(42));
        blp.leftMargin = dp(6);
        photoRow.addView(pickBtn, blp);

        photoAreaBtn = new Button(this);
        photoAreaBtn.setText("영역");
        photoAreaBtn.setTextSize(13);
        photoAreaBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openPhotoCrop();
            }
        });
        LinearLayout.LayoutParams alpp = new LinearLayout.LayoutParams(dp(64), dp(42));
        alpp.leftMargin = dp(6);
        photoRow.addView(photoAreaBtn, alpp);

        clearPhotoBtn = new Button(this);
        clearPhotoBtn.setText("사진 없음");
        clearPhotoBtn.setTextSize(13);
        clearPhotoBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setPhotoUri(MainActivity.this, "");
                refresh();
            }
        });
        LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(dp(88), dp(42));
        clp2.leftMargin = dp(6);
        photoRow.addView(clearPhotoBtn, clp2);
        root.addView(photoRow);

        photoNote = new TextView(this);
        photoNote.setTextSize(11);
        photoNote.setTextColor(DIM);
        photoNote.setPadding(dp(90), dp(4), 0, 0);
        root.addView(photoNote);

        // ---- theme + colours ----
        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        themeRow.setPadding(0, dp(12), 0, 0);
        themeRow.addView(label("테마"));
        themeBtn = new Button(this);
        themeBtn.setTextSize(13);
        themeBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setDark(MainActivity.this, !Prefs.isDark(MainActivity.this));
                refresh();
            }
        });
        themeRow.addView(themeBtn, new LinearLayout.LayoutParams(dp(120), dp(42)));
        root.addView(themeRow);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(0, dp(8), 0, 0);
        colorRow.addView(label("색상"));
        String[] names = { "블루", "코랄", "민트" };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button cb = new Button(this);
            cb.setText(names[i]);
            cb.setTextSize(12);
            cb.setTextColor(0xFF08111A);
            cb.setBackgroundTintList(ColorStateList.valueOf(Prefs.ACCENTS[i]));
            cb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.setAccentIndex(MainActivity.this, idx);
                    refresh();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(42));
            lp.leftMargin = dp(6);
            colorRow.addView(cb, lp);
            colorBtns[i] = cb;
        }
        root.addView(colorRow);

        addColorRow(root, "요일 글자", 0);
        addColorRow(root, "날짜", 1);
        addColorRow(root, "일정 글자", 2);

        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setGravity(Gravity.CENTER_VERTICAL);
        autoRow.setPadding(0, dp(12), 0, 0);
        autoRow.addView(label("매일 자동 갱신"));
        autoSwitch = new Switch(this);
        autoSwitch.setChecked(Prefs.isAuto(this));
        autoSwitch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean on = autoSwitch.isChecked();
                Prefs.setAuto(MainActivity.this, on);
                if (on) {
                    Scheduler.ensure(MainActivity.this);
                } else {
                    Scheduler.cancelDaily(MainActivity.this);
                }
                refresh();
            }
        });
        autoRow.addView(autoSwitch);
        root.addView(autoRow);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(DIM);
        status.setPadding(0, dp(14), 0, 0);
        status.setLineSpacing(dp(3), 1f);
        root.addView(status);

        TextView foot = new TextView(this);
        foot.setText("잠금 화면 = 캘린더 포함 · 홈 화면 = 선택한 사진만");
        foot.setTextSize(11);
        foot.setTextColor(0xFF5E6B78);
        foot.setPadding(0, dp(18), 0, 0);
        root.addView(foot);

        setContentView(scroll);
    }

    private SeekBar sliderRow(LinearLayout parent, String name, int value, TextView valueView, final Runnable onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        TextView l = new TextView(this);
        l.setText(name);
        l.setTextSize(13);
        l.setTextColor(DIM);
        l.setWidth(dp(90));
        row.addView(l);

        SeekBar bar = new SeekBar(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        row.addView(bar, blp);
        bar.setMax(100);
        bar.setProgress(value);

        valueView.setText(value + "%");
        valueView.setTextSize(12);
        valueView.setTextColor(Prefs.accentColor(this));
        valueView.setGravity(Gravity.END);
        valueView.setWidth(dp(54));
        row.addView(valueView);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) onChange.run();
            }

            public void onStartTrackingTouch(SeekBar sb) {
            }

            public void onStopTrackingTouch(SeekBar sb) {
            }
        });

        parent.addView(row);
        return bar;
    }


    private void schedulePreview() {
        preview.postDelayed(new Runnable() {
            public void run() {
                redrawPreview();
            }
        }, 90L);
    }

    private void redrawPreview() {
        Bitmap bmp = WallpaperRenderer.render(this, 540, 854, 540, 854);
        preview.setPreview(bmp);
        preview.setValues(Prefs.scaleXPct(this), Prefs.scaleYPct(this), Prefs.dxPct(this), Prefs.dyPct(this));
    }

    private static boolean isLight(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150;
    }

    private void addColorRow(LinearLayout parent, String name, final int which) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        TextView l = new TextView(this);
        l.setText(name);
        l.setTextSize(12);
        l.setTextColor(DIM);
        l.setWidth(dp(74));
        row.addView(l);

        for (int i = 0; i < PALETTE.length; i++) {
            final int color = PALETTE[i];
            final Button b = new Button(this);
            b.setTextSize(10);
            b.setPadding(0, 0, 0, 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            if (color == 0) {
                b.setText("자동");
                b.setTextColor(0xFFB9C4CF);
                b.setBackgroundTintList(ColorStateList.valueOf(0xFF232C36));
            } else {
                b.setText("");
                b.setBackgroundTintList(ColorStateList.valueOf(color));
            }
            final int index = i;
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Prefs.setColor(MainActivity.this, which, color);
                    refresh();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(color == 0 ? 40 : 30), dp(30));
            lp.leftMargin = dp(5);
            row.addView(b, lp);
            textColorBtns[which][index] = b;
        }

        Button custom = new Button(this);
        custom.setText("+");
        custom.setTextSize(16);
        custom.setTextColor(0xFFE6EDF3);
        custom.setPadding(0, 0, 0, 0);
        custom.setMinWidth(0);
        custom.setMinimumWidth(0);
        custom.setBackgroundTintList(ColorStateList.valueOf(0xFF2A333D));
        custom.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openColorDialog(which);
            }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(30), dp(30));
        clp.leftMargin = dp(5);
        row.addView(custom, clp);
        customColorBtns[which] = custom;

        parent.addView(row);
    }

    private int currentColor(int which) {
        return which == 0 ? Prefs.colorWeekday(this)
                : (which == 1 ? Prefs.colorDate(this) : Prefs.colorEvent(this));
    }

    private static String hexOf(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private void openColorDialog(final int which) {
        ColorPickerDialog.show(this, currentColor(which), new ColorPickerDialog.OnPick() {
            public void onColor(int color) {
                Prefs.setColor(MainActivity.this, which, color);
                refresh();
            }
        });
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(DIM);
        t.setWidth(dp(90));
        return t;
    }

    private void applyNow() {
        applyBtn.setEnabled(false);
        applyBtn.setText("적용 중…");
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = Applier.apply(MainActivity.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        applyBtn.setEnabled(true);
                        if (ok) {
                            Toast.makeText(MainActivity.this, "잠금 화면에 적용했습니다", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "적용 실패 — 로그를 확인하세요", Toast.LENGTH_LONG).show();
                        }
                        refresh();
                    }
                });
            }
        }, "calwall-apply").start();
    }

    private void openPhotoCrop() {
        if (Prefs.photoUri(this).length() == 0) {
            Toast.makeText(this, "먼저 배경 사진을 선택해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bmp = WallpaperRenderer.loadPhotoForCrop(this, 1200);
        if (bmp == null) {
            Toast.makeText(this, "사진을 불러오지 못했습니다. 다시 선택해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        final PhotoCropView view = new PhotoCropView(this, null);
        view.setBitmap(bmp);
        view.setTransform(Prefs.photoZoom(this), Prefs.photoDx(this), Prefs.photoDy(this));

        int p = dp(14);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(p, p, p, p);

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(dp(210), dp(332));
        flp.gravity = Gravity.CENTER;
        frame.addView(view, flp);
        wrap.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(332)));

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setGravity(Gravity.CENTER);
        zoomRow.setPadding(0, dp(10), 0, 0);

        Button minus = new Button(this);
        minus.setText("-");
        minus.setTextSize(16);
        Button plus = new Button(this);
        plus.setText("+");
        plus.setTextSize(16);
        final TextView zoomLabel = new TextView(this);
        zoomLabel.setText(view.zoom() + "%");
        zoomLabel.setTextSize(13);
        zoomLabel.setGravity(Gravity.CENTER);
        zoomLabel.setWidth(dp(56));

        minus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                view.setZoom(view.zoom() - 10);
                zoomLabel.setText(view.zoom() + "%");
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                view.setZoom(view.zoom() + 10);
                zoomLabel.setText(view.zoom() + "%");
            }
        });
        Button reset = new Button(this);
        reset.setText("초기화");
        reset.setTextSize(12);
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                view.reset();
                zoomLabel.setText(view.zoom() + "%");
            }
        });

        zoomRow.addView(minus, new LinearLayout.LayoutParams(dp(58), dp(42)));
        zoomRow.addView(zoomLabel);
        zoomRow.addView(plus, new LinearLayout.LayoutParams(dp(58), dp(42)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(74), dp(42));
        rlp.leftMargin = dp(6);
        zoomRow.addView(reset, rlp);
        wrap.addView(zoomRow);

        new android.app.AlertDialog.Builder(this)
                .setTitle("사진 영역 설정")
                .setView(wrap)
                .setNegativeButton("취소", null)
                .setPositiveButton("적용", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        Prefs.setPhotoTransform(MainActivity.this, view.zoom(), view.dx(), view.dy());
                        refresh();
                    }
                })
                .show();
    }

    private void applyHome() {
        if (Prefs.photoUri(this).length() == 0) {
            Toast.makeText(this, "먼저 배경 사진을 선택해 주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        homeBtn.setEnabled(false);
        homeBtn.setText("적용 중…");
        new Thread(new Runnable() {
            public void run() {
                final boolean ok = Applier.applyHomePhoto(MainActivity.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        homeBtn.setEnabled(true);
                        homeBtn.setText("선택한 사진을 홈 화면에 적용");
                        Toast.makeText(MainActivity.this,
                                ok ? "홈 화면에 사진을 적용했습니다" : "적용 실패 — 사진을 다시 선택해 주세요",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "calwall-home").start();
    }

    private void refresh() {
        boolean dark = Prefs.isDark(this);
        int accent = Prefs.accentColor(this);

        applyBtn.setText("잠금 화면에 적용");
        applyBtn.setBackgroundTintList(ColorStateList.valueOf(accent));
        themeBtn.setText(dark ? "다크" : "라이트");
        themeBtn.setTextColor(accent);
        for (int i = 0; i < 3; i++) {
            colorBtns[i].setAlpha(Prefs.accentIndex(this) == i ? 1f : 0.55f);
        }
        contentAlphaVal.setTextColor(accent);
        cardAlphaVal.setTextColor(accent);

        boolean granted = checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
        permNote.setVisibility(granted ? View.GONE : View.VISIBLE);
        permNote.setText("캘린더 권한이 없어 일정이 표시되지 않습니다. 설정에서 허용해 주세요.");

        String photo = Prefs.photoUri(this);
        photoNote.setText(photo.length() == 0
                ? "선택 안 함 — 그라데이션 배경을 사용합니다"
                : "선택한 사진을 배경으로 사용합니다");
        clearPhotoBtn.setEnabled(photo.length() > 0);
        clearPhotoBtn.setAlpha(photo.length() > 0 ? 1f : 0.5f);
        photoAreaBtn.setEnabled(photo.length() > 0);
        photoAreaBtn.setAlpha(photo.length() > 0 ? 1f : 0.45f);
        homeBtn.setEnabled(photo.length() > 0);
        homeBtn.setAlpha(photo.length() > 0 ? 1f : 0.45f);

        int[] current = { Prefs.colorWeekday(this), Prefs.colorDate(this), Prefs.colorEvent(this) };
        for (int w = 0; w < 3; w++) {
            for (int i = 0; i < PALETTE.length; i++) {
                Button b = textColorBtns[w][i];
                if (b == null) continue;
                boolean sel = current[w] == PALETTE[i];
                if (PALETTE[i] == 0) {
                    b.setAlpha(sel ? 1f : 0.5f);
                } else {
                    b.setText(sel ? "✓" : "");
                    b.setTextColor(isLight(PALETTE[i]) ? 0xFF101010 : 0xFFFFFFFF);
                }
            }
            boolean custom = current[w] != 0;
            for (int i = 1; i < PALETTE.length; i++) {
                if (current[w] == PALETTE[i]) {
                    custom = false;
                    break;
                }
            }
            Button cb = customColorBtns[w];
            if (cb != null) {
                cb.setBackgroundTintList(ColorStateList.valueOf(custom ? current[w] : 0xFF2A333D));
                cb.setTextColor(custom ? (isLight(current[w]) ? 0xFF101010 : 0xFFFFFFFF) : 0xFFE6EDF3);
            }
        }

        contentAlphaBar.setProgress(Prefs.contentAlpha(this));
        cardAlphaBar.setProgress(Prefs.cardAlpha(this));
        contentAlphaVal.setText(Prefs.contentAlpha(this) + "%");
        cardAlphaVal.setText(Prefs.cardAlpha(this) + "%");

        redrawPreview();

        autoSwitch.setChecked(Prefs.isAuto(this));
        StringBuilder sb = new StringBuilder();
        if (Prefs.isAuto(this)) {
            sb.append("다음 자동 갱신: ").append(fmt.format(new Date(Scheduler.nextRunMillis())));
        } else {
            sb.append("자동 갱신이 꺼져 있습니다.");
        }
        sb.append("\n크기 ").append(Prefs.scaleXPct(this)).append("% × ").append(Prefs.scaleYPct(this)).append("%")
                .append(" · 위치 ").append(Prefs.dxPct(this)).append(", ").append(Prefs.dyPct(this));
        sb.append("\n마지막 적용: ").append(Prefs.lastApplyDay(this) == 0 ? "없음"
                : (Prefs.lastApplyDay(this) == Prefs.todayKey() ? "오늘" : "오늘 아님"));
        status.setText(sb.toString());
    }
}










