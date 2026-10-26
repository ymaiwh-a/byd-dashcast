package com.byd.dashcast.phase1.widget;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String NAME = "widget";
    private static SharedPreferences SP;

    public static final String K_NAV_WIDGET_INTENT = "nav_widget_intent";
    public static final String K_MUSIC_WIDGET_INTENT = "music_widget_intent";

    public static final String K_CAST_HIDE_NAV = "cast_hide_nav";
    public static final String K_CAST_HIDE_LYRIC = "cast_hide_lyric";

    public static final String K_NAV_GUIDE_CFG = "nav_guide_cfg";
    public static final String K_MUSIC_STYLE_CFG = "music_style_cfg";

    public static final String K_NAV_LIGHT_INVERT = "nav_light_invert";

    public static final String K_NAV_X = "nav_x";
    public static final String K_NAV_Y = "nav_y";
    public static final String K_NAV_W = "nav_w";
    public static final String K_NAV_H = "nav_h";
    public static final String K_LYRIC_X = "lyric_x";
    public static final String K_LYRIC_Y = "lyric_y";
    public static final String K_LYRIC_W = "lyric_w";
    public static final String K_LYRIC_H = "lyric_h";

    public static final String K_NAV_OX = "nav_ox";
    public static final String K_NAV_OY = "nav_oy";
    public static final String K_NAV_OW = "nav_ow";
    public static final String K_NAV_OH = "nav_oh";
    public static final String K_LYRIC_OX = "lyric_ox";
    public static final String K_LYRIC_OY = "lyric_oy";
    public static final String K_LYRIC_OW = "lyric_ow";
    public static final String K_LYRIC_OH = "lyric_oh";

    public static int navX() { return sp().getInt(K_NAV_X, WidgetCfg.NAV_DEF_X); }
    public static int navY() { return sp().getInt(K_NAV_Y, WidgetCfg.NAV_DEF_Y); }
    public static int navW() { return sp().getInt(K_NAV_W, WidgetCfg.NAV_DEF_W); }
    public static int navH() { return sp().getInt(K_NAV_H, WidgetCfg.NAV_DEF_H); }
    public static void navGeom(int x, int y, int w, int h) {
        sp().edit().putInt(K_NAV_X, x).putInt(K_NAV_Y, y)
                .putInt(K_NAV_W, w).putInt(K_NAV_H, h).commit();
    }
    public static int lyricX() { return sp().getInt(K_LYRIC_X, WidgetCfg.MUSIC_DEF_X); }
    public static int lyricY() { return sp().getInt(K_LYRIC_Y, WidgetCfg.MUSIC_DEF_Y); }
    public static int lyricW() { return sp().getInt(K_LYRIC_W, WidgetCfg.MUSIC_DEF_W); }
    public static int lyricH() { return sp().getInt(K_LYRIC_H, WidgetCfg.MUSIC_DEF_H); }
    public static void lyricGeom(int x, int y, int w, int h) {
        sp().edit().putInt(K_LYRIC_X, x).putInt(K_LYRIC_Y, y)
                .putInt(K_LYRIC_W, w).putInt(K_LYRIC_H, h).commit();
    }

    public static void navOriginal(int x, int y, int w, int h) {
        sp().edit().putInt(K_NAV_OX, x).putInt(K_NAV_OY, y)
                .putInt(K_NAV_OW, w).putInt(K_NAV_OH, h).commit();
    }
    public static int navOx() { return sp().getInt(K_NAV_OX, Integer.MIN_VALUE); }
    public static int navOy() { return sp().getInt(K_NAV_OY, Integer.MIN_VALUE); }
    public static int navOw() { return sp().getInt(K_NAV_OW, Integer.MIN_VALUE); }
    public static int navOh() { return sp().getInt(K_NAV_OH, Integer.MIN_VALUE); }
    public static boolean hasNavOriginal() { return navOx() != Integer.MIN_VALUE; }

    public static void lyricOriginal(int x, int y, int w, int h) {
        sp().edit().putInt(K_LYRIC_OX, x).putInt(K_LYRIC_OY, y)
                .putInt(K_LYRIC_OW, w).putInt(K_LYRIC_OH, h).commit();
    }
    public static int lyricOx() { return sp().getInt(K_LYRIC_OX, Integer.MIN_VALUE); }
    public static int lyricOy() { return sp().getInt(K_LYRIC_OY, Integer.MIN_VALUE); }
    public static int lyricOw() { return sp().getInt(K_LYRIC_OW, Integer.MIN_VALUE); }
    public static int lyricOh() { return sp().getInt(K_LYRIC_OH, Integer.MIN_VALUE); }
    public static boolean hasLyricOriginal() { return lyricOx() != Integer.MIN_VALUE; }

    public static void init(Context c) {
        SP = c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences sp() {
        return SP;
    }

    public static String host() {
        return com.byd.dashcast.phase1.ncm.Prefs.DEF_PEER_IP;
    }

    public static int connectMs() {
        return 10000;
    }

    public static int readMs() {
        return 2500;
    }

    public static boolean navLightInvert() {
        return sp().getBoolean(K_NAV_LIGHT_INVERT, false);
    }

    public static void navLightInvert(boolean v) {
        sp().edit().putBoolean(K_NAV_LIGHT_INVERT, v).commit();
    }

    public static boolean navWidgetIntent() {
        return sp().getBoolean(K_NAV_WIDGET_INTENT, false);
    }

    public static void navWidgetIntent(boolean v) {
        sp().edit().putBoolean(K_NAV_WIDGET_INTENT, v).commit();
    }

    public static boolean musicWidgetIntent() {
        return sp().getBoolean(K_MUSIC_WIDGET_INTENT, false);
    }

    public static void musicWidgetIntent(boolean v) {
        sp().edit().putBoolean(K_MUSIC_WIDGET_INTENT, v).commit();
    }

    public static boolean castHideNav() {
        return sp().getBoolean(K_CAST_HIDE_NAV, false);
    }

    public static void castHideNav(boolean v) {
        sp().edit().putBoolean(K_CAST_HIDE_NAV, v).commit();
    }

    public static boolean castHideLyric() {
        return sp().getBoolean(K_CAST_HIDE_LYRIC, false);
    }

    public static void castHideLyric(boolean v) {
        sp().edit().putBoolean(K_CAST_HIDE_LYRIC, v).commit();
    }

    public static String navGuideCfg() {
        return sp().getString(K_NAV_GUIDE_CFG, "");
    }

    public static void navGuideCfg(String v) {
        sp().edit().putString(K_NAV_GUIDE_CFG, v == null ? "" : v).commit();
    }

    public static String musicStyleCfg() {
        return sp().getString(K_MUSIC_STYLE_CFG, "");
    }

    public static void musicStyleCfg(String v) {
        sp().edit().putString(K_MUSIC_STYLE_CFG, v == null ? "" : v).commit();
    }
}
