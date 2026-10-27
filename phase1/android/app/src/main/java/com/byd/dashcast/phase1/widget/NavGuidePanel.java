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

public final class NavGuidePanel {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int APPLY_NAV = 2;

    private final Context c;
    private final WidgetCfg.Gd gd = new WidgetCfg.Gd();
    private boolean lanesOn;
    private boolean navOn;
    private int navTheme = 0;
    private LinearLayout navModeSeg;
    private SCard navBox;
    private SCard lightsBox;
    private View gHBox, gWBox, gXBox, gYBox;
    private View gOpacityBox, gTtlBox, gLanesTtlBox, gLightsTtlBox, gAlertsTtlBox;
    private View gBgRadiusBox, gPadTopBox, gPadBottomBox;
    private View guXBox, guYBox, guWBox, guHBox;
    private TextView navColorCell;
    private final Handler applyHandler = new Handler();
    private Runnable applyPending;

    private NavGuidePanel(Context c) {
        this.c = c;
    }

    public static LinearLayout build(android.content.Context c, boolean night) {
        Ui.night = night;
        return new NavGuidePanel(c).make();
    }

    private LinearLayout make() {
        WidgetCfg.gdFromJson(Prefs.navGuideCfg(), gd);
        navTheme = themeIndexOf(gd.theme);
        lanesOn = gd.showLanes;
        LinearLayout out = Ui.column(c);
        ClusterState cs = ClusterState.get();
        navOn = cs.navIntent;

        SCard navMain = sectionCard(c, "简易地图开关", "▣");
        navMain.body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        LinearLayout navSw = Ui.switchRow(c, "开启简易地图", null, navOn, new Runnable() {
            public void run() {
                navOn = !navOn;
                final boolean want = navOn;
                ClusterState.get().setNavIntent(want);
                WidgetRelay.get().setNavEnabled(want);
                say(want ? "导航已开启" : "导航已关闭");
                Ui.setCellEnabled(navModeSeg, want);
                Ui.setSCardEnabled(navBox, want, "地图未开启");
                Ui.setSCardEnabled(lightsBox, want, "地图未开启");
            }
        });
        LinearLayout.LayoutParams nsp = (LinearLayout.LayoutParams) navSw.getLayoutParams();
        if (nsp != null) { nsp.topMargin = 0; nsp.bottomMargin = 0; }
        final String[] modeNames = {"自动", "白天", "夜晚"};
        final LinearLayout[] navSegH = new LinearLayout[1];
        navModeSeg = Ui.segment(c, modeNames, navTheme, new Ui.OnPick() {
            public void onPick(int i) {
                navTheme = i;
                Ui.pickSeg(navSegH[0], i);
                applyNav();
            }
        });
        navSegH[0] = navModeSeg;
        navMain.body.addView(navSw);
        navMain.body.addView(navModeSeg, lpPane(Ui.S_2, 0, 0, 0));

        LinearLayout lightsSw = Ui.switchRow(c, "开启红绿灯", null, gd.showLights, new Runnable() {
            public void run() {
                gd.showLights = !gd.showLights;
                final boolean want = gd.showLights;
                applyNav(ok -> {
                    if (ok) {
                        say(want ? "红绿灯已开启" : "红绿灯已关闭");
                        return;
                    }
                    gd.showLights = !want;
                    Prefs.navGuideCfg(WidgetCfg.gdToJson(gd));
                    say("设置失败，已恢复");
                });
            }
        });
        LinearLayout.LayoutParams klslp = (LinearLayout.LayoutParams) lightsSw.getLayoutParams();
        if (klslp != null) { klslp.topMargin = 0; klslp.bottomMargin = 0; }
        centerCtl(lightsSw);

        navBox = sectionCard(c, null, null);
        navBox.body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
        LinearLayout routeHead = Ui.group(c, "◈", "路线窗口设置");
        routeHead.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        routeHead.addView(act("恢复", false, new Runnable() {
            public void run() {
                restoreOriginalGeom();
            }
        }), Ui.lpBtnFit(c));
        navBox.body.addView(routeHead);
        gd.x = Prefs.navX(); gd.y = Prefs.navY(); gd.w = Prefs.navW(); gd.h = Prefs.navH();
        gHBox = box(gd.h, 80, 720, 10, APPLY_NAV);
        gWBox = box(gd.w, 160, 1919, 20, APPLY_NAV);
        gXBox = box(gd.x, 0, 1919, 10, APPLY_NAV);
        gYBox = box(gd.y, 0, 719, 10, APPLY_NAV);
        gOpacityBox = box(gd.opacity, 0, 255, 5, APPLY_NAV);
        gTtlBox = box(gd.ttlSec, 1, 120, 5, APPLY_NAV);
        gLanesTtlBox = box(gd.lanesTtl, 1, 60, 1, APPLY_NAV);
        gLightsTtlBox = box(gd.lightsTtl, 1, 15, 1, APPLY_NAV);
        gAlertsTtlBox = box(gd.alertsTtl, 1, 60, 1, APPLY_NAV);
        gBgRadiusBox = box(gd.bgRadius, 0, 40, 2, APPLY_NAV);
        gPadTopBox = box(gd.padTop, 0, 60, 2, APPLY_NAV);
        gPadBottomBox = box(gd.padBottom, 0, 60, 2, APPLY_NAV);
        guXBox = gBox(gd.ugx >= 0 ? gd.ugx : (cs.navGx >= 0 ? cs.navGx : WidgetCfg.NAV_DEF_GX), 0, 1919, 10, 0);
        guYBox = gBox(gd.ugy >= 0 ? gd.ugy : (cs.navGy >= 0 ? cs.navGy : WidgetCfg.NAV_DEF_GY), 0, 719, 10, 1);
        guWBox = gBox(gd.ugw >= 0 ? gd.ugw : (cs.navGw >= 0 ? cs.navGw : WidgetCfg.NAV_DEF_GW), 0, 1919, 20, 2);
        guHBox = gBox(gd.ugh >= 0 ? gd.ugh : (cs.navGh >= 0 ? cs.navGh : WidgetCfg.NAV_DEF_GH), 0, 719, 10, 3);
        navColorCell = new TextView(c);
        navColorCell.setText(hex8(gd.bgColor));
        navColorCell.setGravity(Gravity.CENTER);
        navColorCell.setMinWidth(Ui.dp(c, 76));
        navColorCell.setPadding(Ui.dp(c, 8), Ui.dp(c, 7), Ui.dp(c, 8), Ui.dp(c, 7));
        Ui.setTextSizePx(navColorCell, 20);
        tintNavColor();
        navColorCell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showNavColorPicker();
            }
        });
        LinearLayout navRow1 = Ui.row(c, 0);
        navRow1.setGravity(Gravity.TOP);
        navRow1.addView(Ui.setting(c, "横向位置", "0 ~ 1919", gXBox, false), thirdPair());
        navRow1.addView(Ui.setting(c, "纵向位置", "0 ~ 719", gYBox, false), thirdPair());
        navRow1.addView(Ui.setting(c, "窗口宽度", "160 ~ 1919", gWBox, false), thirdPair());
        navBox.body.addView(navRow1, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout navRow2 = Ui.row(c, 0);
        navRow2.setGravity(Gravity.TOP);
        navRow2.addView(Ui.setting(c, "窗口高度", "80 ~ 720", gHBox, false), thirdPair());
        navRow2.addView(Ui.setting(c, "窗口透明", "0 ~ 255", gOpacityBox, false), thirdPair());
        navRow2.addView(Ui.setting(c, "窗口超时", "1 ~ 120秒", gTtlBox, false), thirdPair());
        navBox.body.addView(navRow2, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout navRow3 = Ui.row(c, 0);
        navRow3.setGravity(Gravity.TOP);
        navRow3.addView(Ui.setting(c, "车道超时", "1 ~ 60秒", gLanesTtlBox, false), thirdPair());
        navRow3.addView(Ui.setting(c, "警示超时", "1 ~ 60秒", gAlertsTtlBox, false), thirdPair());
        navRow3.addView(lightsSw, thirdPair());
        lightsSw.setMinimumHeight(Ui.dp(c, 42));
        LinearLayout.LayoutParams lwsLp = (LinearLayout.LayoutParams) lightsSw.getLayoutParams();
        lwsLp.gravity = Gravity.CENTER_VERTICAL;
        lightsSw.setLayoutParams(lwsLp);
        navBox.body.addView(navRow3, lpPane(0, 0, 0, 0));
        LinearLayout alertsSw = Ui.switchRow(c, "显示警示", null, gd.showAlerts, new Runnable() {
            public void run() {
                gd.showAlerts = !gd.showAlerts;
                final boolean want = gd.showAlerts;
                applyNav(ok -> {
                    if (ok) {
                        say(want ? "警示已开启" : "警示已关闭");
                        return;
                    }
                    gd.showAlerts = !want;
                    Prefs.navGuideCfg(WidgetCfg.gdToJson(gd));
                    say("设置失败，已恢复");
                });
            }
        });
        LinearLayout.LayoutParams kasp = (LinearLayout.LayoutParams) alertsSw.getLayoutParams();
        if (kasp != null) kasp.topMargin = 0;
        centerCtl(alertsSw);
        LinearLayout lanesSw = Ui.switchRow(c, "显示车道", null, lanesOn, new Runnable() {
            public void run() {
                lanesOn = !lanesOn;
                final boolean want = lanesOn;
                applyNav(ok -> {
                    if (ok) {
                        say(want ? "车道已开启" : "车道已关闭");
                        return;
                    }
                    lanesOn = !want;
                    gd.showLanes = lanesOn;
                    Prefs.navGuideCfg(WidgetCfg.gdToJson(gd));
                    say("设置失败，已恢复");
                });
            }
        });
        LinearLayout.LayoutParams klpslp = (LinearLayout.LayoutParams) lanesSw.getLayoutParams();
        if (klpslp != null) klpslp.topMargin = 0;
        centerCtl(lanesSw);
        LinearLayout topSw = Ui.switchRow(c, "灯窗置顶", null, gd.lightsOnTop, new Runnable() {
            public void run() {
                gd.lightsOnTop = !gd.lightsOnTop;
                final boolean want = gd.lightsOnTop;
                applyNav(ok -> {
                    if (ok) {
                        say(want ? "置顶已开启" : "置顶已关闭");
                        return;
                    }
                    gd.lightsOnTop = !want;
                    Prefs.navGuideCfg(WidgetCfg.gdToJson(gd));
                    say("设置失败，已恢复");
                });
            }
        });
        LinearLayout.LayoutParams ktpp = (LinearLayout.LayoutParams) topSw.getLayoutParams();
        if (ktpp != null) ktpp.topMargin = 0;
        centerCtl(topSw);
        LinearLayout navRow4 = Ui.row(c, 0);
        navRow4.setGravity(Gravity.TOP);
        navRow4.addView(alertsSw, thirdPair());
        navRow4.addView(lanesSw, thirdPair());
        navRow4.addView(topSw, thirdPair());
        alertsSw.setMinimumHeight(Ui.dp(c, 44));
        lanesSw.setMinimumHeight(Ui.dp(c, 44));
        topSw.setMinimumHeight(Ui.dp(c, 44));
        navBox.body.addView(navRow4, lpPane(0, 0, Ui.S_2, 0));
        lightsBox = sectionCard(c, null, null);
        lightsBox.body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 5));
        LinearLayout lightsHead = Ui.group(c, "◉", "红绿灯设置");
        lightsHead.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        lightsHead.addView(act("恢复", false, new Runnable() {
            public void run() {
                restoreLightsGeom();
            }
        }), Ui.lpBtnFit(c));
        lightsBox.body.addView(lightsHead);
        LinearLayout lRow1 = Ui.row(c, 0);
        lRow1.setGravity(Gravity.TOP);
        lRow1.addView(Ui.setting(c, "信号超时", "1 ~ 15秒", gLightsTtlBox, false), thirdPair());
        lRow1.addView(Ui.setting(c, "背景圆角", "0 ~ 40", gBgRadiusBox, false), thirdPair());
        lRow1.addView(Ui.setting(c, "上内边距", "0 ~ 60", gPadTopBox, false), thirdPair());
        lightsBox.body.addView(lRow1, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout lRow2 = Ui.row(c, 0);
        lRow2.setGravity(Gravity.TOP);
        lRow2.addView(Ui.setting(c, "下内边距", "0 ~ 60", gPadBottomBox, false), thirdPair());
        lRow2.addView(Ui.setting(c, "灯窗横向", "0 ~ 1919", guXBox, false), thirdPair());
        lRow2.addView(Ui.setting(c, "灯窗纵向", "0 ~ 719", guYBox, false), thirdPair());
        lightsBox.body.addView(lRow2, lpPane(0, 0, Ui.S_2, 0));
        LinearLayout lRow3 = Ui.row(c, 0);
        lRow3.setGravity(Gravity.TOP);
        lRow3.addView(Ui.setting(c, "灯窗宽度", "0 ~ 1919", guWBox, false), thirdPair());
        lRow3.addView(Ui.setting(c, "灯窗高度", "0 ~ 719", guHBox, false), thirdPair());
        lRow3.addView(Ui.setting(c, "灯窗背景", "", navColorCell, false), thirdPair());
        lightsBox.body.addView(lRow3, lpPane(0, 0, Ui.S_2, 0));
        trimBodyTrailing(navBox.body);
        Ui.setCellEnabled(navModeSeg, navOn);
        Ui.setSCardEnabled(navBox, navOn, "地图未开启");
        Ui.setSCardEnabled(lightsBox, navOn, "地图未开启");
        out.addView(navMain.card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        out.addView(navBox.card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        out.addView(lightsBox.card, new LinearLayout.LayoutParams(
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

    private void tintNavColor() {
        tintCell(navColorCell, gd.bgColor);
    }

    private void tintCell(TextView cell, long v) {
        if (cell == null) return;
        int rgb = (int) ((v >> 8) & 0xFFFFFFL);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        cell.setBackground(Ui.pill(c, 0xFF000000 | rgb, 0x33FFFFFF, Ui.R_BTN));
        cell.setTextColor(lum > 140 ? 0xFF000000 : 0xFFFFFFFF);
    }

    private void showNavColorPicker() {
        final Ui.Sheet sh = Ui.Sheet.of(c, "选择颜色");
        final EditText hex = Ui.field(c, "8位RGBA，如 800000C0", hex8(gd.bgColor));
        hex.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        final TextView prev = new TextView(c);
        prev.setText(hex8(gd.bgColor));
        prev.setGravity(Gravity.CENTER);
        prev.setPadding(Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8));
        Ui.setTextSizePx(prev, 18);
        tintCell(prev, parseHex8(hex, gd.bgColor));
        hex.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int cc) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int cc) {
            }

            public void afterTextChanged(android.text.Editable s) {
                tintCell(prev, parseHex8(hex, gd.bgColor));
            }
        });
        ColorPad pad = new ColorPad(c, new ColorPad.Listener() {
            public void onColor(int rgb) {
                hex.setText(String.format(java.util.Locale.US, "%06X%02X",
                        Integer.valueOf(rgb & 0xFFFFFF), Long.valueOf(gd.bgColor & 0xFFL)));
                tintCell(prev, parseHex8(hex, gd.bgColor));
            }
        });
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(0xFF000000
                | (int) ((gd.bgColor >> 8) & 0xFFFFFFL), hsv);
        pad.setHsv(hsv[0], hsv[1], hsv[2]);
        sh.content(pad);
        sh.content(prev);
        sh.content(hex);
        sh.button("确定", true, new Runnable() {
            public void run() {
                long v = parseHex8(hex, -1L);
                if (v < 0) {
                    say("颜色代码不是 8 位十六进制");
                    return;
                }
                gd.bgColor = v;
                Prefs.navGuideCfg(WidgetCfg.gdToJson(gd));
                navColorCell.setText(hex8(v));
                tintNavColor();
                scheduleApply(APPLY_NAV);
            }
        });
        sh.button("取消", false, null);
        sh.show();
    }

    private String navThemeValue() {
        return themeValue(navTheme);
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

    private void applyNavInner(java.util.function.Consumer<Boolean> onDone) {
        if (!ready()) {
            if (onDone != null) onDone.accept(Boolean.FALSE);
            return;
        }
        gd.h = Ui.numOf(gHBox, gd.h);
        gd.w = Ui.numOf(gWBox, gd.w);
        gd.x = Ui.numOf(gXBox, gd.x);
        gd.y = Ui.numOf(gYBox, gd.y);
        gd.opacity = Ui.numOf(gOpacityBox, gd.opacity);
        gd.ttlSec = Ui.numOf(gTtlBox, gd.ttlSec);
        gd.lanesTtl = Ui.numOf(gLanesTtlBox, gd.lanesTtl);
        gd.lightsTtl = Ui.numOf(gLightsTtlBox, gd.lightsTtl);
        gd.alertsTtl = Ui.numOf(gAlertsTtlBox, gd.alertsTtl);
        gd.bgRadius = Ui.numOf(gBgRadiusBox, gd.bgRadius);
        gd.padTop = Ui.numOf(gPadTopBox, gd.padTop);
        gd.padBottom = Ui.numOf(gPadBottomBox, gd.padBottom);
        gd.bgColor = parseHex8(navColorCell, gd.bgColor);
        ClusterState s = ClusterState.get();

        gd.geomKnown = s.navGeomKnown;

        gd.gx = gd.ugx >= 0 ? gd.ugx : s.navGx;
        gd.gy = gd.ugy >= 0 ? gd.ugy : s.navGy;
        gd.gw = gd.ugw >= 0 ? gd.ugw : s.navGw;
        gd.gh = gd.ugh >= 0 ? gd.ugh : s.navGh;
        if (s.navGeomKnown) Prefs.navGeom(gd.x, gd.y, gd.w, gd.h);
        gd.showLanes = lanesOn;
        gd.theme = navThemeValue();
        Prefs.navGuideCfg(WidgetCfg.gdToJson(gd));
        String body = WidgetCfg.navConfigSet(gd);
        WidgetRelay.get().applyNavConfig("{" + body + "}",
                () -> {
                    if (onDone != null) onDone.accept(Boolean.TRUE);
                },
                () -> {
                    if (onDone != null) onDone.accept(Boolean.FALSE);
                });
    }

    private void applyNav(java.util.function.Consumer<Boolean> onDone) {
        if (!ready()) {
            if (onDone != null) onDone.accept(Boolean.FALSE);
            return;
        }
        applyNavInner(onDone);
    }

    private void applyNav() {
        applyNav(null);
    }

    private void restoreLightsGeom() {
        ClusterState s = ClusterState.get();
        gd.lightsTtl = 10;
        gd.bgRadius = 40;
        gd.padTop = 24;
        gd.padBottom = 20;
        gd.bgColor = 0x00000000L;
        gd.lightsOnTop = true;
        gd.ugx = -1;
        gd.ugy = -1;
        gd.ugw = -1;
        gd.ugh = -1;
        s.navGx = WidgetCfg.NAV_DEF_GX;
        s.navGy = WidgetCfg.NAV_DEF_GY;
        s.navGw = WidgetCfg.NAV_DEF_GW;
        s.navGh = WidgetCfg.NAV_DEF_GH;
        Ui.setNum(gLightsTtlBox, 10);
        Ui.setNum(gBgRadiusBox, 40);
        Ui.setNum(gPadTopBox, 24);
        Ui.setNum(gPadBottomBox, 20);
        Ui.setNum(guXBox, WidgetCfg.NAV_DEF_GX);
        Ui.setNum(guYBox, WidgetCfg.NAV_DEF_GY);
        Ui.setNum(guWBox, WidgetCfg.NAV_DEF_GW);
        Ui.setNum(guHBox, WidgetCfg.NAV_DEF_GH);
        if (navColorCell != null) {
            navColorCell.setText(hex8(gd.bgColor));
            tintNavColor();
        }
        say("已恢复红绿灯默认设置");
        scheduleApply(APPLY_NAV);
    }

    private void restoreOriginalGeom() {
        ClusterState s = ClusterState.get();
        Ui.setNum(gXBox, WidgetCfg.NAV_DEF_X);
        Ui.setNum(gYBox, WidgetCfg.NAV_DEF_Y);
        Ui.setNum(gWBox, WidgetCfg.NAV_DEF_W);
        Ui.setNum(gHBox, WidgetCfg.NAV_DEF_H);
        s.navGeomKnown = true;

        s.navGx = WidgetCfg.NAV_DEF_GX;
        s.navGy = WidgetCfg.NAV_DEF_GY;
        s.navGw = WidgetCfg.NAV_DEF_GW;
        s.navGh = WidgetCfg.NAV_DEF_GH;
        Prefs.navGeom(WidgetCfg.NAV_DEF_X, WidgetCfg.NAV_DEF_Y,
                WidgetCfg.NAV_DEF_W, WidgetCfg.NAV_DEF_H);
        gd.opacity = 255;
        gd.ttlSec = 10;
        gd.lanesTtl = 30;
        gd.lightsTtl = 10;
        gd.alertsTtl = 10;
        gd.bgRadius = 40;
        gd.padTop = 24;
        gd.padBottom = 20;
        gd.bgColor = 0x00000000L;
        gd.lightsOnTop = true;
        gd.showLanes = true;
        lanesOn = true;
        gd.ugx = -1;
        gd.ugy = -1;
        gd.ugw = -1;
        gd.ugh = -1;
        Ui.setNum(gOpacityBox, 255);
        Ui.setNum(gTtlBox, 10);
        Ui.setNum(gLanesTtlBox, 10);
        Ui.setNum(gLightsTtlBox, 10);
        Ui.setNum(gAlertsTtlBox, 10);
        Ui.setNum(gBgRadiusBox, 40);
        Ui.setNum(gPadTopBox, 24);
        Ui.setNum(gPadBottomBox, 20);
        Ui.setNum(guXBox, WidgetCfg.NAV_DEF_GX);
        Ui.setNum(guYBox, WidgetCfg.NAV_DEF_GY);
        Ui.setNum(guWBox, WidgetCfg.NAV_DEF_GW);
        Ui.setNum(guHBox, WidgetCfg.NAV_DEF_GH);
        if (navColorCell != null) {
            navColorCell.setText(hex8(gd.bgColor));
            tintNavColor();
        }
        say("已恢复导航到默认位置");
        scheduleApply(APPLY_NAV);
    }

    private LinearLayout box(int v, int min, int max, int step, final int kind) {
        return Ui.stepper(c, v, min, max, step, "", new Ui.OnNum() {
            public void onNum(int value) {
                scheduleApply(kind);
            }
        });
    }

    private View gBox(int v0, int min, int max, int step, final int slot) {
        return Ui.stepper(c, v0, min, max, step, "", new Ui.OnNum() {
            public void onNum(int value) {
                if (slot == 0) gd.ugx = value;
                else if (slot == 1) gd.ugy = value;
                else if (slot == 2) gd.ugw = value;
                else gd.ugh = value;
                clampGuidePairs();
                scheduleApply(APPLY_NAV);
            }
        });
    }

    private void clampGuidePairs() {
        ClusterState s = ClusterState.get();
        int w = gd.ugw >= 0 ? gd.ugw : Math.max(1, gv(gd.ugw, s.navGw));
        int h = gd.ugh >= 0 ? gd.ugh : Math.max(1, gv(gd.ugh, s.navGh));
        if (gd.ugx >= 0 && gd.ugx + w > 1920) {
            gd.ugx = Math.max(0, 1920 - w);
            Ui.setNum(guXBox, gd.ugx);
        }
        if (gd.ugy >= 0 && gd.ugy + h > 720) {
            gd.ugy = Math.max(0, 720 - h);
            Ui.setNum(guYBox, gd.ugy);
        }
        if (gd.ugw >= 0 && gv(gd.ugx, s.navGx) + gd.ugw > 1920) {
            gd.ugw = Math.max(1, 1920 - gv(gd.ugx, s.navGx));
            Ui.setNum(guWBox, gd.ugw);
        }
        if (gd.ugh >= 0 && gv(gd.ugy, s.navGy) + gd.ugh > 720) {
            gd.ugh = Math.max(1, 720 - gv(gd.ugy, s.navGy));
            Ui.setNum(guHBox, gd.ugh);
        }
    }

    private static int gv(int user, int read) {
        if (user >= 0) return user;
        return read >= 0 ? read : 0;
    }

    private static String hex8(long v) {
        return String.format(java.util.Locale.US, "%08X", v & 0xFFFFFFFFL);
    }

    private static long parseHex8(TextView e, long def) {
        if (e == null) return def;
        String s = e.getText().toString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 8) return Long.parseLong(s, 16);
            if (s.length() == 6) return (Long.parseLong(s, 16) << 8) | 0xFFL;
        } catch (Throwable t) {
            return def;
        }
        return def;
    }

    private void scheduleApply(final int kind) {
        if (applyPending != null) applyHandler.removeCallbacks(applyPending);
        applyPending = new Runnable() {
            public void run() {
                if (kind == APPLY_NAV) applyNav();
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
