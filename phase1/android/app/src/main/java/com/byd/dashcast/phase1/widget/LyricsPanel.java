package com.byd.dashcast.phase1.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import static com.byd.dashcast.phase1.widget.Ui.SCard;
import static com.byd.dashcast.phase1.widget.Ui.sectionCard;

public final class LyricsPanel {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int APPLY_LYRIC = 1;

    private final Context c;
    private final WidgetCfg.Cfg lyric = new WidgetCfg.Cfg();
    private boolean musicOn;
    private boolean showProgress = true;
    private int lyricTheme = 0;
    private View lxBox, lyBox, lwBox, lhBox;
    private View coverBox;
    private View fontBox;
    private View secBox, titleSizeBox, artistSizeBox, lyricStepBox;
    private View radiusBox, bgPadBox, strokeWBox;
    private TextView dayColorField, dayPrevField, dayNextField;
    private TextView nightColorField, nightPrevField, nightNextField, strokeColorField;
    private LinearLayout lyModeSeg;
    private SCard lyLayout;
    private SCard lyColor;
    private final Handler applyHandler = new Handler();
    private Runnable applyPending;

    private LyricsPanel(Context c) {
        this.c = c;
    }

    public static LinearLayout build(android.content.Context c, boolean night) {
        Ui.night = night;
        return new LyricsPanel(c).make();
    }

    private LinearLayout make() {
        WidgetCfg.cfgFromJson(Prefs.musicStyleCfg(), lyric);
        lyricTheme = themeIndexOf(lyric.theme);
        showProgress = lyric.showProgress;
        LinearLayout out = Ui.column(c);
        ClusterState cs = ClusterState.get();
        musicOn = cs.musicIntent;

        SCard lyMain = sectionCard(c, "仪表歌词", "♪");
        lyMain.body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 6));
        LinearLayout lyricSw = Ui.switchRow(c, "开启仪表歌词", "",
                musicOn, new Runnable() {
            public void run() {
                musicOn = !musicOn;
                final boolean want = musicOn;
                ClusterState.get().setMusicIntent(want);
                WidgetRelay.get().setMusicEnabled(want);
                say(want ? "歌词已开启" : "歌词已关闭");
                if (want && !notifGranted(c)) {
                    say("未授予通知权限，歌词可能为空");
                }
                Ui.setCellEnabled(lyModeSeg, want);
                Ui.setSCardEnabled(lyLayout, want, "歌词未开启");
                Ui.setSCardEnabled(lyColor, want, "歌词未开启");
            }
        });
        LinearLayout.LayoutParams lsp = (LinearLayout.LayoutParams) lyricSw.getLayoutParams();
        if (lsp != null) { lsp.topMargin = 0; lsp.bottomMargin = 0; }
        lyMain.body.addView(lyricSw);
        final String[] modeNames = {"自动", "白天", "夜晚"};
        final LinearLayout[] lySegH = new LinearLayout[1];
        lyModeSeg = Ui.segment(c, modeNames, lyricTheme, new Ui.OnPick() {
            public void onPick(int i) {
                lyricTheme = i;
                Ui.pickSeg(lySegH[0], i);
                applyLyric();
            }
        });
        lySegH[0] = lyModeSeg;
        lyMain.body.addView(lyModeSeg, lpPane(Ui.S_2, 0, 0, 0));

        lyLayout = sectionCard(c, "位置与大小", "⊞");
        lyLayout.body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 6));
        LinearLayout lyLayoutHead = (LinearLayout) lyLayout.body.getChildAt(0);
        lyLayoutHead.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        lyLayoutHead.addView(act("恢复", false, new Runnable() {
            public void run() {
                restoreOriginalGeom();
            }
        }), Ui.lpBtnFit(c));
        lxBox = box(Prefs.lyricX(), 0, 1919, 20, APPLY_LYRIC);
        lyBox = box(Prefs.lyricY(), 0, 719, 10, APPLY_LYRIC);
        lwBox = box(Prefs.lyricW(), 160, 1919, 20, APPLY_LYRIC);
        lhBox = box(Prefs.lyricH(), 100, 720, 10, APPLY_LYRIC);
        coverBox = box(lyric.coverSize, 0, 200, 8, APPLY_LYRIC);
        fontBox = box(lyric.fontSize, WidgetCfg.FONT_MIN, WidgetCfg.FONT_MAX, 2, APPLY_LYRIC);
        secBox = box(lyric.secondarySize, WidgetCfg.SECONDARY_MIN, WidgetCfg.SECONDARY_MAX, 2, APPLY_LYRIC);
        lyricStepBox = box(lyric.lyricOffsetMs, WidgetCfg.OFFSET_MIN, WidgetCfg.OFFSET_MAX,
                WidgetCfg.OFFSET_STEP, APPLY_LYRIC);
        titleSizeBox = box(lyric.titleSize, WidgetCfg.TITLE_SIZE_MIN, WidgetCfg.TITLE_SIZE_MAX, 2, APPLY_LYRIC);
        artistSizeBox = box(lyric.artistSize, WidgetCfg.ARTIST_SIZE_MIN, WidgetCfg.ARTIST_SIZE_MAX, 2, APPLY_LYRIC);
        LinearLayout lyGeoRow1 = Ui.row(c, 0);
        lyGeoRow1.setGravity(Gravity.TOP);
        lyGeoRow1.addView(Ui.setting(c, "横向位置", "0 ~ 1919", lxBox, false), thirdPair());
        lyGeoRow1.addView(Ui.setting(c, "纵向位置", "0 ~ 719", lyBox, false), thirdPair());
        lyGeoRow1.addView(Ui.setting(c, "窗口宽度", "160 ~ 1919", lwBox, false), thirdPair());
        lyLayout.body.addView(lyGeoRow1, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout lyGeoRow2 = Ui.row(c, 0);
        lyGeoRow2.setGravity(Gravity.TOP);
        lyGeoRow2.addView(Ui.setting(c, "窗口高度", "100 ~ 720", lhBox, false), thirdPair());
        lyGeoRow2.addView(Ui.setting(c, "封面边长", "0=无封面", coverBox, false), thirdPair());
        lyGeoRow2.addView(Ui.setting(c, "唱到大小",
                WidgetCfg.FONT_MIN + " ~ " + WidgetCfg.FONT_MAX, fontBox, false), thirdPair());
        lyLayout.body.addView(lyGeoRow2, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout lyGeoRow3 = Ui.row(c, 0);
        lyGeoRow3.setGravity(Gravity.TOP);
        lyGeoRow3.addView(Ui.setting(c, "未唱大小",
                WidgetCfg.SECONDARY_MIN + " ~ " + WidgetCfg.SECONDARY_MAX, secBox, false), thirdPair());
        lyGeoRow3.addView(Ui.setting(c, "歌名大小",
                WidgetCfg.TITLE_SIZE_MIN + " ~ " + WidgetCfg.TITLE_SIZE_MAX, titleSizeBox, false), thirdPair());
        lyGeoRow3.addView(Ui.setting(c, "歌手大小",
                WidgetCfg.ARTIST_SIZE_MIN + " ~ " + WidgetCfg.ARTIST_SIZE_MAX, artistSizeBox, false), thirdPair());
        lyLayout.body.addView(lyGeoRow3, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout lyProgRow = Ui.row(c, 0);
        lyProgRow.setGravity(Gravity.TOP);
        lyProgRow.addView(Ui.setting(c, "歌词步进", "-5000 ~ 5000", lyricStepBox, false), halfPair());
        final View progToggle = Ui.toggle(c, showProgress, new Runnable() {
            public void run() {
                showProgress = !showProgress;
                WidgetRelay.get().onUserProgressToggle(showProgress);
                applyLyric();
            }
        });
        LinearLayout progSet = Ui.setting(c, "播放进度", "", progToggle, false);
        progSet.setClickable(true);
        progSet.setFocusable(true);
        progSet.setMinimumHeight(Ui.dp(c, 48));
        Ui.onClick(progSet, new Runnable() {
            public void run() {
                progToggle.performClick();
            }
        });
        centerCtl(progSet);
        lyProgRow.addView(progSet, halfPair());
        lyLayout.body.addView(lyProgRow, lpPane(0, 0, Ui.S_2, 0));
        radiusBox = box(lyric.backgroundRadius, 0, 40, 2, APPLY_LYRIC);
        bgPadBox = box(lyric.backgroundPaddingTop, WidgetCfg.BG_PAD_MIN, WidgetCfg.BG_PAD_MAX, 2, APPLY_LYRIC);
        strokeWBox = box(lyric.strokeWidth, WidgetCfg.STROKE_MIN, WidgetCfg.STROKE_MAX, 1, APPLY_LYRIC);
        dayColorField = colorCellView(lyric.color);
        dayPrevField = colorCellView(lyric.previousColor);
        dayNextField = colorCellView(lyric.nextColor);
        nightColorField = colorCellView(lyric.nightColor);
        nightPrevField = colorCellView(lyric.nightPrevColor);
        nightNextField = colorCellView(lyric.nightNextColor);
        strokeColorField = colorCellView(lyric.strokeColor);

        lyColor = sectionCard(c, "歌词颜色", "◉");
        lyColor.body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 5));
        LinearLayout lyColorHead = (LinearLayout) lyColor.body.getChildAt(0);
        lyColorHead.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        lyColorHead.addView(act("恢复", false, new Runnable() {
            public void run() {
                restoreLyricColors();
            }
        }), Ui.lpBtnFit(c));
        LinearLayout lyRow1 = Ui.row(c, 0);
        lyRow1.setGravity(Gravity.TOP);
        lyRow1.addView(Ui.setting(c, "白天", "", null, false), thirdPair());
        lyRow1.addView(Ui.setting(c, "唱到", "", dayColorField, false), thirdPair());
        lyRow1.addView(Ui.setting(c, "已唱", "", dayPrevField, false), thirdPair());
        lyRow1.addView(Ui.setting(c, "未唱", "", dayNextField, false), thirdPair());
        lyColor.body.addView(lyRow1, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout lyRow2 = Ui.row(c, 0);
        lyRow2.setGravity(Gravity.TOP);
        lyRow2.addView(Ui.setting(c, "夜晚", "", null, false), thirdPair());
        lyRow2.addView(Ui.setting(c, "唱到", "", nightColorField, false), thirdPair());
        lyRow2.addView(Ui.setting(c, "已唱", "", nightPrevField, false), thirdPair());
        lyRow2.addView(Ui.setting(c, "未唱", "", nightNextField, false), thirdPair());
        lyColor.body.addView(lyRow2, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout lyRow3 = Ui.row(c, 0);
        lyRow3.setGravity(Gravity.TOP);
        lyRow3.addView(Ui.setting(c, "包边颜色", "", strokeColorField, false), thirdPair());
        lyRow3.addView(Ui.setting(c, "包边宽度", "0 ~ 8", strokeWBox, false), thirdPair());
        lyColor.body.addView(lyRow3, lpPane(0, 0, Ui.S_2, 0));
        for (int i = 0; i < lyRow1.getChildCount(); i++)
            centerCtl((LinearLayout) lyRow1.getChildAt(i));
        for (int i = 0; i < lyRow2.getChildCount(); i++)
            centerCtl((LinearLayout) lyRow2.getChildAt(i));
        centerCtl((LinearLayout) lyRow3.getChildAt(0));
        trimBodyTrailing(lyLayout.body);
        trimBodyTrailing(lyColor.body);
        Ui.setCellEnabled(lyModeSeg, musicOn);
        Ui.setSCardEnabled(lyLayout, musicOn, "歌词未开启");
        Ui.setSCardEnabled(lyColor, musicOn, "歌词未开启");

        out.addView(lyMain.card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        out.addView(lyLayout.card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        out.addView(lyColor.card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return out;
    }

    private void centerCtl(LinearLayout row) {
        if (row.getChildCount() < 2) return;
        View names = row.getChildAt(0);
        LinearLayout.LayoutParams nlp = (LinearLayout.LayoutParams) names.getLayoutParams();
        if (nlp != null) {
            nlp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            nlp.weight = 0f;
        }
        View ctl = row.getChildAt(1);
        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) ctl.getLayoutParams();
        if (clp != null) clp.leftMargin = 0;
        row.removeView(ctl);
        LinearLayout slot = Ui.row(c, 0);
        slot.setGravity(Gravity.CENTER);
        slot.addView(ctl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(slot, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private LinearLayout.LayoutParams lpPane(int top, int right, int bottom, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, top);
        lp.rightMargin = Ui.dp(c, right);
        lp.bottomMargin = Ui.dp(c, bottom);
        lp.leftMargin = Ui.dp(c, left);
        return lp;
    }

    private LinearLayout.LayoutParams thirdPair() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Ui.dp(c, 24);
        return lp;
    }

    private LinearLayout.LayoutParams halfPair() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Ui.dp(c, 36);
        return lp;
    }

    private void trimBodyTrailing(ViewGroup vg) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View ch = vg.getChildAt(i);
            if (ch instanceof ViewGroup) trimBodyTrailing((ViewGroup) ch);
        }
        if (vg.getChildCount() > 0) {
            View last = vg.getChildAt(vg.getChildCount() - 1);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) last.getLayoutParams();
            if (lp != null) lp.bottomMargin = 0;
        }
    }

    private LinearLayout box(int v, int min, int max, int step, final int kind) {
        return Ui.stepper(c, v, min, max, step, "", new Ui.OnNum() {
            public void onNum(int value) {
                scheduleApply(kind);
            }
        });
    }

    private TextView colorCellView(long rgb) {
        final TextView t = new TextView(c);
        t.setText(hex6(rgb));
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(Ui.dp(c, 76));
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 7), Ui.dp(c, 8), Ui.dp(c, 7));
        Ui.setTextSizePx(t, 20);
        tintLabel(t);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showColorPicker(t);
            }
        });
        return t;
    }

    private void tintLabel(TextView t) {
        long v = parseHex6(t, -1L);
        if (v < 0) {
            t.setBackground(Ui.pill(c, Ui.col(c, Ui.SOFT), Ui.col(c, Ui.LINE), Ui.R_BTN));
            t.setTextColor(Ui.col(c, Ui.TEXT));
            return;
        }
        int rgb = (int) ((v >> 8) & 0xFFFFFFL);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        t.setBackground(Ui.pill(c, 0xFF000000 | rgb, 0x33FFFFFF, Ui.R_BTN));
        t.setTextColor(lum > 140 ? 0xFF000000 : 0xFFFFFFFF);
    }

    private void showColorPicker(final TextView target) {
        final Ui.Sheet sh = Ui.Sheet.of(c, "选择颜色");
        final EditText hex = Ui.field(c, "或手输 6 位代码，如 FF0000", "");
        ColorPad pad = new ColorPad(c, new ColorPad.Listener() {
            public void onColor(int rgb) {
                hex.setText(String.format(java.util.Locale.US, "%06X", rgb & 0xFFFFFF));
            }
        });
        long cur = parseHex6(target, 0x0D47A1FFL);
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(0xFF000000 | (int) ((cur >> 8) & 0xFFFFFFL), hsv);
        pad.setHsv(hsv[0], hsv[1], hsv[2]);
        sh.content(pad);
        sh.content(hex);
        sh.button("确定", true, new Runnable() {
            public void run() {
                long v = parseHex6(hex, -1L);
                if (v < 0) {
                    say("颜色代码不是 6 位十六进制");
                    return;
                }
                target.setText(hex6(v));
                tintLabel(target);
                scheduleApply(APPLY_LYRIC);
            }
        });
        sh.button("取消", false, null);
        sh.show();
    }

    private void applyLyric() {
        if (!ready()) return;
        lyric.coverSize = Ui.numOf(coverBox, lyric.coverSize);
        lyric.fontSize = Ui.numOf(fontBox, lyric.fontSize);
        lyric.secondarySize = Ui.numOf(secBox, lyric.secondarySize);
        lyric.titleSize = Ui.numOf(titleSizeBox, lyric.titleSize);
        lyric.artistSize = Ui.numOf(artistSizeBox, lyric.artistSize);
        lyric.color = parseHex6(dayColorField, lyric.color);
        lyric.previousColor = parseHex6(dayPrevField, lyric.previousColor);
        lyric.nextColor = parseHex6(dayNextField, lyric.nextColor);
        lyric.nightColor = parseHex6(nightColorField, lyric.nightColor);
        lyric.nightPrevColor = parseHex6(nightPrevField, lyric.nightPrevColor);
        lyric.nightNextColor = parseHex6(nightNextField, lyric.nightNextColor);
        lyric.strokeColor = parseHex6(strokeColorField, lyric.strokeColor);
        lyric.strokeWidth = Ui.numOf(strokeWBox, lyric.strokeWidth);
        lyric.lyricOffsetMs = WidgetCfg.clampOffset(Ui.numOf(lyricStepBox, lyric.lyricOffsetMs));
        WidgetCfg.lyricOffsetMs = lyric.lyricOffsetMs;
        lyric.showProgress = showProgress;
        int radius = Ui.numOf(radiusBox, lyric.backgroundRadius);
        lyric.backgroundRadius = radius;
        int bgPad = Ui.numOf(bgPadBox, lyric.backgroundPaddingTop);
        lyric.backgroundPaddingTop = bgPad;
        lyric.backgroundPaddingBottom = bgPad;
        int x = Ui.numOf(lxBox, WidgetCfg.MUSIC_DEF_X);
        int y = Ui.numOf(lyBox, WidgetCfg.MUSIC_DEF_Y);
        int w = Ui.numOf(lwBox, WidgetCfg.MUSIC_DEF_W);
        int h = Ui.numOf(lhBox, WidgetCfg.MUSIC_DEF_H);
        lyric.theme = themeValue(lyricTheme);
        ClusterState s = ClusterState.get();
        if (s.musicGeomKnown) Prefs.lyricGeom(x, y, w, h);
        Prefs.musicStyleCfg(WidgetCfg.cfgToJson(lyric));
        String body = WidgetCfg.musicConfigSet(x, y, w, h, s.musicGeomKnown, lyric);
        WidgetRelay.get().applyMusicConfig("{" + body + "}",
                () -> WidgetRelay.get().requestMusicReload(), null);
    }

    private void restoreOriginalGeom() {
        ClusterState s = ClusterState.get();
        Ui.setNum(lxBox, WidgetCfg.MUSIC_DEF_X);
        Ui.setNum(lyBox, WidgetCfg.MUSIC_DEF_Y);
        Ui.setNum(lwBox, WidgetCfg.MUSIC_DEF_W);
        Ui.setNum(lhBox, WidgetCfg.MUSIC_DEF_H);
        s.musicGeomKnown = true;
        Prefs.lyricGeom(WidgetCfg.MUSIC_DEF_X, WidgetCfg.MUSIC_DEF_Y,
                WidgetCfg.MUSIC_DEF_W, WidgetCfg.MUSIC_DEF_H);
        say("已恢复歌词到默认位置");
        scheduleApply(APPLY_LYRIC);
    }

    private void restoreLyricColors() {
        WidgetCfg.Cfg d = new WidgetCfg.Cfg();
        dayColorField.setText(hex6(d.color));
        dayPrevField.setText(hex6(d.previousColor));
        dayNextField.setText(hex6(d.nextColor));
        nightColorField.setText(hex6(d.nightColor));
        nightPrevField.setText(hex6(d.nightPrevColor));
        nightNextField.setText(hex6(d.nightNextColor));
        strokeColorField.setText(hex6(d.strokeColor));
        Ui.setNum(strokeWBox, d.strokeWidth);
        tintLabel(dayColorField);
        tintLabel(dayPrevField);
        tintLabel(dayNextField);
        tintLabel(nightColorField);
        tintLabel(nightPrevField);
        tintLabel(nightNextField);
        tintLabel(strokeColorField);
        applyLyric();
        say("已恢复歌词颜色默认值");
    }

    private static String themeValue(int idx) {
        if (idx == 1) return "day";
        if (idx == 2) return "night";
        return "auto";
    }

    private static int themeIndexOf(String theme) {
        if ("day".equals(theme)) return 1;
        if ("night".equals(theme)) return 2;
        return 0;
    }

    private static boolean notifGranted(Context c) {
        try {
            String cur = android.provider.Settings.Secure.getString(
                    c.getContentResolver(), "enabled_notification_listeners");
            if (cur == null || cur.isEmpty() || "null".equals(cur)) return false;
            android.content.ComponentName self =
                    new android.content.ComponentName(c, MusicListener.class);
            for (String s : cur.split(":")) {
                android.content.ComponentName cn =
                        android.content.ComponentName.unflattenFromString(s);
                if (cn != null && cn.equals(self)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String hex6(long v) {
        return String.format(java.util.Locale.US, "%06X", (v >> 8) & 0xFFFFFFL);
    }

    private static long parseHex6(TextView e, long def) {
        if (e == null) return def;
        String s = e.getText().toString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return def;
        try {
            long v = Long.parseLong(s, 16);
            return (v << 8) | 0xFFL;
        } catch (Throwable t) {
            return def;
        }
    }

    private void scheduleApply(final int kind) {
        if (applyPending != null) applyHandler.removeCallbacks(applyPending);
        applyPending = new Runnable() {
            public void run() {
                if (kind == APPLY_LYRIC) applyLyric();
            }
        };
        applyHandler.postDelayed(applyPending, 350);
    }

    private boolean ready() {
        if (Cluster.get().ready()) return true;
        say("未连仪表：请到「仪表服务」连接仪表");
        return false;
    }

    private Button act(String text, boolean primary, final Runnable action) {
        Button b = Ui.button(c, text, primary);
        Ui.onClick(b, new Runnable() {
            public void run() {
                try {
                    action.run();
                } catch (Throwable t) {
                    say(CrashHandler.describe(t));
                }
            }
        });
        return b;
    }

    private void say(final String msg) {
        MAIN.post(new Runnable() {
            public void run() {
                Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
