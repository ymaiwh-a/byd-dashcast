package com.byd.dashcast.phase1.widget;

import com.byd.dashcast.phase1.ncm.LogHub;

import org.json.JSONObject;

public final class ClusterState {

    public boolean navOn;
    public boolean musicOn;
    public boolean lanesOn;

    public volatile boolean navIntent;
    public volatile boolean musicIntent;

    public void setNavIntent(boolean v) {
        navIntent = v;
        Prefs.navWidgetIntent(v);
        if (!v) Prefs.castHideNav(false);
    }

    public void setMusicIntent(boolean v) {
        musicIntent = v;
        Prefs.musicWidgetIntent(v);
        if (!v) Prefs.castHideLyric(false);
    }

    public int navX, navY, navW, navH;
    public int lyricX, lyricY, lyricW, lyricH;

    public int navGx = -1, navGy = -1, navGw = -1, navGh = -1;

    public volatile boolean navGeomKnown;
    public volatile boolean musicGeomKnown;

    public long lastRefreshMs;
    public boolean lastOk;

    public volatile int appearanceDay = -1;

    private static final ClusterState S = new ClusterState();

    public static ClusterState get() {
        return S;
    }

    private ClusterState() {

        try {
            navIntent = Prefs.navWidgetIntent();
            musicIntent = Prefs.musicWidgetIntent();
            if (!navIntent) Prefs.castHideNav(false);
            if (!musicIntent) Prefs.castHideLyric(false);
        } catch (Throwable ignored) {
        }
    }

    public void applyVisibility(JSONObject navStatus, JSONObject musicStatus) {
        if (navStatus != null) {
            JSONObject eff = Json.o(navStatus, "effective");
            if (eff != null) navOn = Json.b(eff, "navigation", navOn);
            JSONObject g = Json.o(navStatus, "guidance");
            if (g != null) lanesOn = Json.b(g, "lanes_visible", lanesOn);
        }
        if (musicStatus != null) {
            JSONObject eff = Json.o(musicStatus, "effective");
            if (eff != null) musicOn = Json.b(eff, "music", musicOn);
        }
    }

    public void applyGeom(JSONObject navCfg, JSONObject musicCfg) {
        if (musicCfg != null) {
            int x = Json.i(musicCfg, "x", Integer.MIN_VALUE);
            int y = Json.i(musicCfg, "y", Integer.MIN_VALUE);
            int w = Json.i(musicCfg, "width", Integer.MIN_VALUE);
            int h = Json.i(musicCfg, "height", Integer.MIN_VALUE);
            if (all(x, y, w, h)) {
                lyricX = x;
                lyricY = y;
                lyricW = w;
                lyricH = h;
                musicGeomKnown = true;
                Prefs.lyricGeom(x, y, w, h);
                Prefs.lyricOriginal(x, y, w, h);
            }
        }
        if (navCfg != null) {
            int x = Json.i(navCfg, "x", Integer.MIN_VALUE);
            int y = Json.i(navCfg, "y", Integer.MIN_VALUE);
            int w = Json.i(navCfg, "width", Integer.MIN_VALUE);
            int h = Json.i(navCfg, "height", Integer.MIN_VALUE);

            if (all(x, y, w, h)) {
                navX = x;
                navY = y;
                navW = w;
                navH = h;
                navGeomKnown = true;
                Prefs.navGeom(x, y, w, h);
                Prefs.navOriginal(x, y, w, h);
            }
            int gx = Json.i(navCfg, "guidance_x", Integer.MIN_VALUE);
            int gy = Json.i(navCfg, "guidance_y", Integer.MIN_VALUE);

            if (gx != Integer.MIN_VALUE && gy != Integer.MIN_VALUE && !(gx == 0 && gy == 0)) {
                navGx = gx;
                navGy = gy;
            }
            int gw = Json.i(navCfg, "guidance_width", Integer.MIN_VALUE);
            int gh = Json.i(navCfg, "guidance_height", Integer.MIN_VALUE);
            if (gw > 0 && gh > 0) {
                navGw = gw;
                navGh = gh;
            }
        }
    }

    private static boolean all(int... v) {
        for (int i : v) if (i == Integer.MIN_VALUE) return false;
        return true;
    }

    private void applyAppearance(JSONObject t) {
        int d = -1;
        if (t != null) {
            JSONObject det = Json.o(t, "details");
            if (det != null && Json.b(det, "appearance_valid", false)) {
                int day = Json.i(det, "appearance_day", -1);
                if (day == 0 || day == 1) d = day;
            }
        }
        if (d == appearanceDay) return;
        appearanceDay = d;
        LogHub.i("state", "仪表日夜状态=" + (d == 1 ? "白天" : d == 0 ? "黑夜" : "未知")
                + "（theme.get appearance_day）");
        WidgetRelay.get().onAppearanceChanged();
    }

    private static JSONObject geomOf(InstrumentLink.Reply r) {
        if (r == null || r.raw == null) return null;
        JSONObject o = Json.parse(r.raw);
        if (o == null) return null;
        JSONObject cfg = Json.o(o, "config");
        return cfg != null ? cfg : o;
    }

    private void reconcileResidue() {
        boolean closeNav = !navIntent && navOn;
        boolean closeMusic = !musicIntent && musicOn;
        if (closeNav || closeMusic) {
            try {
                WidgetRelay.get().closeResidueBox(closeNav, closeMusic);
                if (closeNav) {
                    navOn = false;
                    LogHub.i("state", "对账：App 未要求显示导航框，已经 relay 单写者关仪表残留框");
                }
                if (closeMusic) {
                    musicOn = false;
                    LogHub.i("state", "对账：App 未要求显示歌词框，已经 relay 单写者关仪表残留框");
                }
            } catch (Throwable t) {
                LogHub.w("state", "对账清残留框失败: " + CrashHandler.describe(t));
            }
        }
        if (navIntent && !navOn) {
            LogHub.i("state", "对账：导航主开关为开但仪表无框——合法静默态，静等真实导航数据，不推空框");
        }
        if (musicIntent && !musicOn) {
            LogHub.i("state", "对账：歌词主开关为开但仪表无框——合法静默态，静等真实音乐数据，不推空框");
        }
        if (musicIntent && musicOn && !WidgetRelay.get().hasMusicContent()) {
            WidgetRelay.get().hideMusicBox();
            musicOn = false;
            LogHub.i("state", "对账：歌词开关开但无真实音乐内容，清除下电残留的歌词框（来内容会自动重开）");
        }
        if (navIntent && navOn && !WidgetRelay.get().hasNavContent()) {
            WidgetRelay.get().hideNavBox();
            navOn = false;
            LogHub.i("state", "对账：导航开关开但无真实导航内容，清除下电残留的导航框（来数据会自动重开）");
        }
    }

    public void syncRelay() {
        WidgetRelay.get().setNavEnabled(navIntent);
        WidgetRelay.get().setMusicEnabled(musicIntent);
    }

    public interface Done {
        void onDone(boolean ok);
    }

    public void pollAppearance() {
        if (!Cluster.get().ready()) return;
        InstrumentLink.Reply rh = null;
        try {
            rh = Cluster.get().hmi(Ops.THEME_GET, null);
        } catch (Throwable t) {
            if (LogHub.oncePer("theme_poll_fail", 30000))
                LogHub.w("state", "theme.get 轮询失败: " + CrashHandler.describe(t));
        }
        applyAppearance(Json.parse(rh == null ? null : rh.raw));
    }

    public void refresh(final Done done) {
        if (!Cluster.get().ready()) {
            if (done != null) Tasks.main(new Runnable() {
                public void run() {
                    done.onDone(false);
                }
            });
            return;
        }
        Tasks.chain(new Tasks.Body<Boolean>() {
            public Boolean run() throws Exception {
                Cluster cl = Cluster.get();
                InstrumentLink.Reply rn = null, rm = null, cn = null, cm = null, rh = null;
                for (int attempt = 0; attempt < 4; attempt++) {
                    if (rn == null) rn = cl.overlay("nav", Ops.WIDGETS_STATUS, null);
                    if (rm == null) rm = cl.overlay("music", Ops.WIDGETS_STATUS, null);
                    if (cn == null) cn = cl.overlay("nav", Ops.CONFIG_GET, null);
                    if (cm == null) cm = cl.overlay("music", Ops.CONFIG_GET, null);
                    if (rh == null) {
                        try {
                            rh = cl.hmi(Ops.THEME_GET, null);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (rn != null && rm != null && cn != null && cm != null) break;
                    if (attempt < 3) Thread.sleep(2500L);
                }
                applyVisibility(Json.parse(rn == null ? null : rn.raw),
                        Json.parse(rm == null ? null : rm.raw));
                applyGeom(geomOf(cn), geomOf(cm));
                applyAppearance(Json.parse(rh == null ? null : rh.raw));
                reconcileResidue();
                return (rn != null && rn.okSuccess()) || (rm != null && rm.okSuccess());
            }
        }, new Tasks.Then<Boolean>() {
            public void apply(Tasks.Result<Boolean> res) {
                lastRefreshMs = System.currentTimeMillis();
                lastOk = res.ok() && Boolean.TRUE.equals(res.value);
                if (lastOk) {
                    syncRelay();
                    LogHub.i("state", "仪表状态已同步：导航=" + navOn + " 歌词=" + musicOn
                            + " 车道线=" + lanesOn
                            + " 导航框=(" + navX + "," + navY + "," + navW + "x" + navH + ")"
                            + " 歌词框=(" + lyricX + "," + lyricY + "," + lyricW + "x" + lyricH + ")");
                } else {
                    LogHub.w("state", "仪表状态同步失败: " + CrashHandler.describe(res.error));
                }
                if (done != null) done.onDone(lastOk);
            }
        });
    }
}
