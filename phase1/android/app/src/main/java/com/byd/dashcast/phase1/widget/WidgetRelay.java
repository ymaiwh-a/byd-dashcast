package com.byd.dashcast.phase1.widget;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONArray;
import org.json.JSONObject;

import com.byd.dashcast.phase1.ncm.LogHub;

public final class WidgetRelay {

    private static final WidgetRelay INSTANCE = new WidgetRelay();

    public static WidgetRelay get() {
        return INSTANCE;
    }

    private final NavState nav = new NavState();
    private final MusicState music = new MusicState();

    private final AtomicBoolean navEnabled = new AtomicBoolean(false);
    private final AtomicBoolean musicEnabled = new AtomicBoolean(false);

    private volatile boolean navShown;

    private final AtomicLong lastNavContentMs = new AtomicLong();

    private static final long NAV_IDLE_TIMEOUT_MS = 60_000;

    private volatile boolean musicShown;

    private volatile int configuredFontSize = WidgetCfg.FONT_MAX;
    private volatile int fontCap = WidgetCfg.FONT_MAX;
    private final AtomicInteger musicReloadGen = new AtomicInteger();
    private volatile boolean lyricProgressShown = true;
    private volatile boolean progressForceShown;

    public void onUserProgressToggle(boolean on) {
        progressForceShown = on;
    }

    public void requestMusicReload() {
        musicReloadGen.incrementAndGet();
        lyricProgressShown = true;
        pushMusic();
    }

    private final AtomicLong lastMusicContentMs = new AtomicLong();

    private final AtomicLong lastMusicPushMs = new AtomicLong();

    private static final long MUSIC_IDLE_TIMEOUT_MS = 30_000;

    private final AtomicLong lastReassertNavMs = new AtomicLong();
    private final AtomicLong lastReassertMusicMs = new AtomicLong();

    private static final class Reject {
        String code;
        int streak;
        long untilMs;
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Reject> rejects =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final AtomicLong lastLightMs = new AtomicLong();

    private final AtomicBoolean lightsCfgSent = new AtomicBoolean(false);

    private final AtomicBoolean musicCfgApplied = new AtomicBoolean(false);

    private volatile String loadedTrackId = "";
    private volatile boolean lyricsLoaded;

    private final ScheduledExecutorService pool =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "widget-relay");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean musicDirty = new AtomicBoolean(false);
    private final AtomicBoolean musicDraining = new AtomicBoolean(false);

    private WidgetRelay() {
        pool.scheduleAtFixedRate(() -> {
            try {
                heartbeat();
            } catch (Throwable t) {
                CrashHandler.record(t);
            }
        }, 10_000, 10_000, TimeUnit.MILLISECONDS);
        pool.scheduleAtFixedRate(() -> {
            try {
                ClusterState.get().pollAppearance();
            } catch (Throwable t) {
                CrashHandler.record(t);
            }
        }, 1_000, 1_000, TimeUnit.MILLISECONDS);
    }

    private volatile boolean navHiddenByCast;
    private volatile boolean musicHiddenByCast;

    public void setNavEnabled(boolean on) {
        boolean effective = on && !navHiddenByCast;
        boolean changed = navEnabled.compareAndSet(!effective, effective);
        if (!changed) return;
        if (effective) {

            if (hasNavContent()) {
                navShown = true;
                lastNavContentMs.set(System.currentTimeMillis());
                setNavVisible(true);
                pushNav();
            } else {
                LogHub.i("relay", "导航开关已开：等待真实导航内容到达后再显示框");
            }
        } else {

            clearNav();
        }
    }

    public void setMusicEnabled(boolean on) {
        boolean effective = on && !musicHiddenByCast;
        boolean changed = musicEnabled.compareAndSet(!effective, effective);
        if (!changed) return;
        if (effective) {

            if (hasMusicContent()) {
                musicShown = true;
                lastMusicContentMs.set(System.currentTimeMillis());
                setMusicVisible(true);
                pushMusic();
            } else {
                LogHub.i("relay", "歌词开关已开：等待真实音乐内容到达后再显示框");
            }
        } else {

            clearMusic();
        }
    }

    public void setNavHiddenByCast(boolean hidden) {
        navHiddenByCast = hidden;
        setNavEnabled(ClusterState.get().navIntent);
    }

    public void setMusicHiddenByCast(boolean hidden) {
        musicHiddenByCast = hidden;
        setMusicEnabled(ClusterState.get().musicIntent);
    }

    public boolean navEnabled() {
        return navEnabled.get();
    }

    public boolean musicEnabled() {
        return musicEnabled.get();
    }

    public void onNav(NavState s) {
        if (s == null) return;
        if (!navEnabled.get()) return;
        boolean lightChanged = false;
        boolean changed;
        boolean ended;
        synchronized (nav) {
            NavState copy = s.copy();
            if (copy.lanesRxMs <= nav.lanesRxMs) copy.lanes = nav.lanes;
            changed = !copy.equalsData(nav);

            if (hasContentOf(copy)) lastNavContentMs.set(System.currentTimeMillis());

            if (copy.lanesRxMs > nav.lanesRxMs) nav.lanesRxMs = copy.lanesRxMs;
            if (changed) {
                lightChanged = copy.lightSeconds > 0
                        && (copy.lightSeconds != nav.lightSeconds || copy.lightColor != nav.lightColor);
                nav.active = copy.active;
                nav.currentRoad = copy.currentRoad;
                nav.destination = copy.destination;
                nav.turn = copy.turn;
                nav.distanceText = copy.distanceText;
                nav.eta = copy.eta;
                nav.serviceArea = copy.serviceArea;
                nav.trafficText = copy.trafficText;
                nav.exitName = copy.exitName;
                nav.distanceM = copy.distanceM;
                nav.remainingM = copy.remainingM;
                nav.remainingS = copy.remainingS;
                nav.speedKph = copy.speedKph;
                nav.limitKph = copy.limitKph;
                nav.lightSeconds = copy.lightSeconds;
                nav.lightColor = copy.lightColor;
                nav.lightsJson = copy.lightsJson;
                nav.lanes = copy.lanes;
                nav.showNext = copy.showNext;
                nav.showAlerts = copy.showAlerts;
                nav.showLimit = copy.showLimit;
                nav.nextTtlMs = copy.nextTtlMs;
                nav.alertsTtlMs = copy.alertsTtlMs;
            }

            ended = !nav.active && (navShown || hasContentOf(nav));
        }
        if (lightChanged) lastLightMs.set(System.currentTimeMillis());
        if (ended) {
            LogHub.i("relay", "收到导航结束广播（active=false）：清内容、推仪表清除并关框");
            clearNav();
            return;
        }
        boolean justOpened = false;
        if (hasNavContent()) {
            if (!navShown) {
                navShown = true;
                justOpened = true;
                setNavVisible(true);
                LogHub.i("relay", "收到真实导航内容：使能仪表导航框");
            }
        }
        if (!changed && !justOpened) return;
        boolean needLights;
        synchronized (nav) {
            needLights = nav.lightSeconds > 0 && navShown
                    && lightsCfgSent.compareAndSet(false, true);
        }
        if (needLights) pool.execute(this::ensureLightsConfig);
        pushNav();
    }

    private void ensureLightsConfig() {
        try {
            WidgetCfg.Gd g = new WidgetCfg.Gd();
            WidgetCfg.gdFromJson(Prefs.navGuideCfg(), g);
            if (!g.showLights) {
                LogHub.i("relay", "红绿灯显示用户意图为关：跳过自动补发，不覆盖用户意图");
                return;
            }
            runPush("nav", Ops.NAV_CONFIG_SET, "{\"show_lights\":true}",
                    () -> LogHub.i("relay", "红绿灯显示配置已下发 show_lights=true（仪表持久化）"),
                    () -> lightsCfgSent.set(false));
        } catch (Throwable t) {
            lightsCfgSent.set(false);
            LogHub.w("relay", "红绿灯显示配置异常: " + CrashHandler.describe(t));
        }
    }

    public boolean hasNavContent() {
        synchronized (nav) {
            return hasContentOf(nav);
        }
    }

    private static boolean hasContentOf(NavState n) {
        return notEmpty(n.currentRoad) || notEmpty(n.turn)
                || notEmpty(n.destination) || notEmpty(n.distanceText)
                || n.distanceM > 0 || n.remainingM > 0;
    }

    private static boolean hasContentOf(MusicState m) {
        if (notEmpty(m.title) || notEmpty(m.artist)) return true;
        if (m.lyricLines != null) {
            for (String line : m.lyricLines) {
                if (notEmpty(line) && !line.equals(" ")) return true;
            }
        }
        return false;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    public void onMusic(MusicState m) {
        if (m == null) return;
        if (!musicEnabled.get()) return;
        boolean content = hasContentOf(m);
        boolean changed;
        boolean lineChanged;
        synchronized (music) {
            MusicState copy = m.copy();
            changed = !copy.equalsData(music);
            if (!copy.trackId.equals(music.trackId)) progressForceShown = false;
            lineChanged = copy.lineIndex != music.lineIndex;
            if (changed) {
                music.trackId = copy.trackId;
                music.title = copy.title;
                music.artist = copy.artist;
                music.coverRgba = copy.coverRgba;
                music.lyricLines = copy.lyricLines;
                music.lyricTimes = copy.lyricTimes;
                music.coverBase64 = copy.coverBase64;
                music.coverDone = copy.coverDone;
                music.lyricDone = copy.lyricDone;
                music.lyricsRequired = copy.lyricsRequired;
                music.playing = copy.playing;
                music.durationMs = copy.durationMs;
                music.positionMs = copy.positionMs;
            }
            if (lineChanged) music.lineIndex = copy.lineIndex;
            music.positionMs = copy.positionMs;
        }

        if (content) lastMusicContentMs.set(System.currentTimeMillis());
        boolean justOpened = false;
        if (content && !musicShown) {
            musicShown = true;
            justOpened = true;
            setMusicVisible(true);
            LogHub.i("relay", "收到真实音乐内容：使能仪表歌词框");

            pool.execute(this::ensureMusicConfig);
        }
        if (!content && musicShown) {

            LogHub.i("relay", "音乐内容已清空：清除歌词框内容并关框（意图保持开）");
            clearMusic();
            return;
        }
        long lastPush = lastMusicPushMs.get();
        boolean tickDue = musicShown && System.currentTimeMillis() - lastPush >= 4000;
        if (changed || justOpened || tickDue) {
            pushMusic();
        } else if (lineChanged && musicShown) {
            pushMusicSync();
        }
    }

    public void onAppearanceChanged() {
        musicCfgApplied.set(false);
        pool.execute(this::ensureMusicConfig);
    }

    private void ensureMusicConfig() {
        if (musicCfgApplied.get()) return;
        try {
            WidgetCfg.Cfg c = new WidgetCfg.Cfg();
            WidgetCfg.cfgFromJson(Prefs.musicStyleCfg(), c);
            fontCap = c.fontSize;
            String body = WidgetCfg.musicConfigSet(0, 0, 0, 0, false, c);
            runPush("music", Ops.MUSIC_CONFIG_SET, "{" + body + "}",
                    () -> {
                        musicCfgApplied.set(true);
                        configuredFontSize = fontCap;
                        musicReloadGen.incrementAndGet();
                        lyricProgressShown = true;
                        pushMusic();
                        LogHub.i("relay", "歌词显示配置已对齐持久化样式（封面/进度/尺寸键，省略几何）");
                    },
                    null);
        } catch (Throwable t) {
            LogHub.w("relay", "歌词显示配置异常: " + CrashHandler.describe(t));
        }
    }

    public void pushNavCurrent() {
        pushNav();
    }

    public void pushMusicCurrent() {
        pushMusic();
    }

    private void heartbeat() {
        if (!Cluster.get().ready()) {

            musicCfgApplied.set(false);
            configuredFontSize = WidgetCfg.FONT_MAX;
            return;
        }

        ClusterState cs = ClusterState.get();
        if (cs.navIntent && !navEnabled.get() && !navHiddenByCast) {
            LogHub.i("relay", "上电恢复：导航开关记忆为开，自动重新开启");
            setNavEnabled(true);
        }
        if (cs.musicIntent && !musicEnabled.get() && !musicHiddenByCast) {
            LogHub.i("relay", "上电恢复：歌词开关记忆为开，自动重新开启");
            setMusicEnabled(true);
        }

        long lm = lastLightMs.get();
        if (lm > 0) {
            boolean lightCleared = false;
            synchronized (nav) {
                if (nav.lightSeconds > 0
                        && System.currentTimeMillis() - lm > nav.lightSeconds * 1000L + 5_000L) {
                    nav.lightSeconds = 0;
                    nav.lightColor = 0;
                    nav.lightsJson = "";
                    lightCleared = true;
                }
            }
            if (lightCleared) {
                LogHub.i("relay", "红绿灯倒计时已过期：清除灯字段并重推导航");
                if (navEnabled.get()) pushNav();
            }
        }

        long last = lastNavContentMs.get();
        if (navEnabled.get() && navShown && last > 0
                && System.currentTimeMillis() - last > NAV_IDLE_TIMEOUT_MS) {
            LogHub.i("relay", "导航看门狗：已 " + (System.currentTimeMillis() - last) / 1000
                    + "s 无真实导航广播，视为导航结束，清内容并关框");
            clearNav();
        }

        long mus = lastMusicContentMs.get();
        if (musicEnabled.get() && musicShown && mus > 0
                && System.currentTimeMillis() - mus > MUSIC_IDLE_TIMEOUT_MS) {
            LogHub.i("relay", "歌词看门狗：已 " + (System.currentTimeMillis() - mus) / 1000
                    + "s 无音乐内容数据，清内容并关框");
            clearMusic();
        }

        if (navEnabled.get() && navShown) pushNav();
        if (musicEnabled.get() && musicShown) {

            if (!musicCfgApplied.get()) pool.execute(this::ensureMusicConfig);
            pushMusic();
        }
    }

    private void pushNav() {
        if (!navEnabled.get()) return;
        final String body;
        synchronized (nav) {
            body = WidgetData.navDataBody(nav);
        }
        runPush("nav", Ops.NAV_UPDATE, body, null);
    }

    private void pushMusic() {
        if (!musicEnabled.get()) return;
        musicDirty.set(true);
        if (musicDraining.compareAndSet(false, true)) pool.execute(this::drainMusic);
    }

    private void pushMusicSync() {
        if (!musicEnabled.get()) return;
        final String body;
        synchronized (music) {
            if (!notEmpty(music.trackId)) return;
            String tid = music.trackId + "." + musicReloadGen.get();
            if (!tid.equals(loadedTrackId)) return;
            if (!lyricsLoaded) {
                pushMusic();
                return;
            }
            int li = music.lineIndex;
            if (music.lyricLines != null && li >= 0 && li < music.lyricLines.length) {
                String cur = music.lyricLines[li];
                if (cur != null && !cur.trim().isEmpty()) {
                    int want = Math.min(WidgetCfg.fitFontSize(cur), fontCap);
                    if (want != configuredFontSize
                            || !WidgetCfg.fitText(cur, want).equals(WidgetCfg.normalize(cur))) {
                        pushMusic();
                        return;
                    }
                }
            }
            body = WidgetData.trackSyncBody(tid, li);
        }
        runPush("music", Ops.TRACK_SYNC, body, null);
    }

    private void drainMusic() {
        try {
            if (musicDirty.getAndSet(false)) sendMusicOnce();
        } catch (Throwable t) {
            CrashHandler.record(t);
        } finally {
            musicDraining.set(false);
            if (musicDirty.get() && musicEnabled.get()
                    && musicDraining.compareAndSet(false, true)) {
                pool.execute(this::drainMusic);
            }
        }
    }

    private void sendMusicOnce() {
        final String body;
        final String sentId;
        final boolean withCover;
        final String fontBody;
        final String progBody;
        final long diagPos;
        final long diagDur;
        final boolean diagPlaying;
        synchronized (music) {
            MusicState copy = music.copy();

            boolean ready = copy.coverDone && copy.lyricDone;
            withCover = ready
                    && copy.coverBase64 != null && !copy.coverBase64.isEmpty();
            if (!withCover) copy.coverBase64 = null;
            if (!ready) {
                copy.lyricLines = new String[0];
                copy.lyricTimes = null;
                copy.lineIndex = -1;
            }
            lyricsLoaded = ready && copy.lyricLines != null && copy.lyricLines.length > 0;
            int li = copy.lineIndex;
            int wantFont = configuredFontSize;
            if (copy.lyricLines != null && li >= 0 && li < copy.lyricLines.length) {
                String cur = copy.lyricLines[li];
                if (cur != null && !cur.trim().isEmpty()) {
                    wantFont = Math.min(WidgetCfg.fitFontSize(cur), fontCap);
                    copy.lyricLines[li] = WidgetCfg.fitText(cur, wantFont);
                }
            }
            if (wantFont != configuredFontSize) {
                configuredFontSize = wantFont;
                musicReloadGen.incrementAndGet();
                fontBody = "{\"font_size\":" + wantFont + "}";
            } else {
                fontBody = null;
            }
            boolean wantProg = (ready || progressForceShown) && userShowProgress();
            if (wantProg != lyricProgressShown) {
                lyricProgressShown = wantProg;
                progBody = "{\"show_progress\":" + wantProg + "}";
                LogHub.i("relay", wantProg ? "封面与歌词均已出结果：进度条按用户开关恢复"
                        : "切歌空窗：进度条暂隐");
            } else {
                progBody = null;
            }
            copy.trackId = (copy.trackId == null ? "" : copy.trackId) + "." + musicReloadGen.get();
            sentId = copy.trackId;
            diagPos = copy.positionMs;
            diagDur = copy.durationMs;
            diagPlaying = copy.playing;
            body = WidgetData.trackLoadBody(copy, withCover);
        }
        if (LogHub.oncePer("music_track_diag", 120000)) {
            LogHub.i("relay", "track.load 实发 position=" + diagPos + "ms duration=" + diagDur
                    + "ms playing=" + diagPlaying + " 封面=" + (withCover ? "带" : "无"));
        }
        if (fontBody != null) {
            runPushNow("music", Ops.MUSIC_CONFIG_SET, fontBody, null, null);
        }
        if (progBody != null) {
            runPushNow("music", Ops.MUSIC_CONFIG_SET, progBody, null, null);
        }
        runPushNow("music", Ops.TRACK_LOAD, body, () -> loadedTrackId = sentId,
                () -> {
                    loadedTrackId = "";
                    lyricsLoaded = false;
                });
        lastMusicPushMs.set(System.currentTimeMillis());
    }

    private boolean userShowProgress() {
        try {
            WidgetCfg.Cfg c = new WidgetCfg.Cfg();
            WidgetCfg.cfgFromJson(Prefs.musicStyleCfg(), c);
            return c.showProgress;
        } catch (Throwable t) {
            CrashHandler.record(t);
            return true;
        }
    }

    private void setNavVisible(boolean on) {
        runPush("nav", on ? Ops.NAV_SHOW : Ops.NAV_HIDE, null, null);
    }

    private void setMusicVisible(boolean on) {
        runPush("music", on ? Ops.MUSIC_SHOW : Ops.MUSIC_HIDE, null, null);
    }

    private void clearNav() {
        synchronized (nav) {
            nav.reset();
        }
        NavReceiver.resetSession();
        navShown = false;
        lastNavContentMs.set(0);
        lastLightMs.set(0);
        pushNav();
        setNavVisible(false);
    }

    private void clearMusic() {
        synchronized (music) {
            music.trackId = "";
            music.title = "";
            music.artist = "";
            music.coverRgba = "#00000000";
            music.lyricLines = new String[0];
            music.lyricTimes = null;
            music.lineIndex = -1;
            music.coverBase64 = null;
            music.lyricsRequired = false;
            music.playing = false;
            music.durationMs = 0;
            music.positionMs = 0;
        }
        loadedTrackId = "";
        lyricsLoaded = false;

        musicShown = false;
        lastMusicContentMs.set(0);
        pushMusic();
        setMusicVisible(false);
    }

    public void closeResidueBox(boolean nav, boolean music) {
        if (nav) {
            if (navEnabled.get()) setNavEnabled(false);
            else {
                navShown = false;
                setNavVisible(false);
            }
        }
        if (music) {
            if (musicEnabled.get()) setMusicEnabled(false);
            else {
                musicShown = false;
                setMusicVisible(false);
            }
        }
    }

    public boolean hasMusicContent() {
        synchronized (music) {
            return hasContentOf(music);
        }
    }

    public void hideNavBox() {
        runPush("nav", Ops.NAV_HIDE, null, null);
    }

    public void hideMusicBox() {
        runPush("music", Ops.MUSIC_HIDE, null, null);
    }

    public String probeFields() {
        if (!Cluster.get().ready()) return "未连仪表，无法测试";
        StringBuilder out = new StringBuilder();
        out.append("候选字段取自 PandaCast + 固件 overlay-service 文档；固件内可能有本地值守程序二次打包，"
                + "故以仪表原始回包为最终判据：ok=属于我们这段，"
                + "unknown_field_for_port / fields_not_allowed_for_operation=不属于我们这段。\n\n");

        String[] navKeys = FieldSpec.NAV_PROBE;
        JSONObject nav = new JSONObject();
        try {
            for (String k : navKeys) nav.put(k, probeNavValue(k));
        } catch (Throwable t) {
            CrashHandler.record(t);
        }

        boolean navWasOff = !widgetSelected("nav", "navigation");
        boolean musicWasOff = !widgetSelected("music", "music");
        if (navWasOff) {
            out.append("【前置】导航控件未使能，临时 widgets.set(navigation=true)\n");
            setWidgetProbe("nav", "navigation", true, out);
        }
        if (musicWasOff) {
            out.append("【前置】歌词控件未使能，临时 widgets.set(music=true)\n");
            setWidgetProbe("music", "music", true, out);
        }
        out.append("【导航 update 已发字段】\n");
        for (String k : navKeys) out.append("  ").append(k).append("\n");
        out.append("【仪表回包】\n");
        try {
            InstrumentLink.Reply rn = Cluster.get().overlay("nav", Ops.NAV_UPDATE,
                    nav.toString());
            out.append(rn.raw == null ? "(空)" : rn.raw);
            out.append("\n【渲染判定·导航】").append(verdict(rn.raw)).append('\n');
        } catch (Throwable t) {
            out.append("导航异常:").append(CrashHandler.describe(t));
        }

        String[] musKeys = FieldSpec.MUSIC_PROBE;
        JSONObject mus = new JSONObject();
        try {
            for (String k : musKeys) mus.put(k, probeMusicValue(k));
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        out.append("\n\n【音乐 track_load 已发字段】\n");
        for (String k : musKeys) out.append("  ").append(k).append("\n");
        out.append("【仪表回包】\n");
        try {
            InstrumentLink.Reply rm = Cluster.get().overlay("music", Ops.TRACK_LOAD,
                    mus.toString());
            out.append(rm.raw == null ? "(空)" : rm.raw);
            out.append("\n【渲染判定·歌词】").append(verdict(rm.raw)).append('\n');
        } catch (Throwable t) {
            out.append("音乐异常:").append(CrashHandler.describe(t));
        }

        try {
            Thread.sleep(5000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (navWasOff) {
            if (navEnabled.get() && hasNavContent()) {
                navShown = true;
                out.append("\n【收尾】导航主开关开且有真实数据：保持框使能并重推生产数据\n");
            } else {
                navShown = false;
                out.append("【收尾】导航无真实数据：框恢复关闭（主开关保持，数据到达自动开出）\n");
                setWidgetProbe("nav", "navigation", false, out);
            }
        }
        if (musicWasOff) {
            if (musicEnabled.get() && hasMusicContent()) {
                musicShown = true;
                lastMusicContentMs.set(System.currentTimeMillis());
                out.append("\n【收尾】歌词主开关开且有真实数据：保持框使能并重推生产数据\n");
            } else {
                musicShown = false;
                out.append("【收尾】歌词无真实数据：框恢复关闭（主开关保持，数据到达自动开出）\n");
                setWidgetProbe("music", "music", false, out);
            }
        }

        if (navEnabled.get() && navShown) {
            pushNav();
            out.append("\n【收尾】导航生产状态已用真实数据重推覆盖探测假数据\n");
        }
        if (musicEnabled.get() && musicShown) {
            pushMusic();
            out.append("【收尾】歌词生产状态已用真实数据重推覆盖探测假数据\n");
        }

        out.append("\n\n【导航 config.set 已发字段】\n");
        out.append("  （回推 config.get 读到的当前配置原文——验证字段可写，不改动仪表位置）\n");
        out.append("【仪表回包】\n");
        try {
            InstrumentLink.Reply rg = Cluster.get().overlay("nav", Ops.NAV_CONFIG_GET,
                    "{}");
            String cfg = configOf(rg.raw);
            if (cfg == null) {
                out.append("(config.get 未返回 config 对象，跳过 set 以免覆盖位置)");
            } else {
                InstrumentLink.Reply rc = Cluster.get().overlay("nav", Ops.NAV_CONFIG_SET,
                        cfg);
                out.append(rc.raw == null ? "(空)" : rc.raw);
            }
        } catch (Throwable t) {
            out.append("导航配置异常:").append(CrashHandler.describe(t));
        }

        out.append("\n\n【音乐 config.set 已发字段】\n");
        out.append("  （回推 config.get 读到的当前配置原文——验证字段可写，不改动仪表位置）\n");
        out.append("【仪表回包】\n");
        try {
            InstrumentLink.Reply mg = Cluster.get().overlay("music", Ops.CONFIG_GET,
                    "{}");
            String mcfg = configOf(mg.raw);
            if (mcfg == null) {
                out.append("(config.get 未返回 config 对象，跳过 set 以免覆盖位置)");
            } else {
                InstrumentLink.Reply rmc = Cluster.get().overlay("music", Ops.MUSIC_CONFIG_SET,
                        mcfg);
                out.append(rmc.raw == null ? "(空)" : rmc.raw);
            }
        } catch (Throwable t) {
            out.append("音乐配置异常:").append(CrashHandler.describe(t));
        }
        out.append("\n\n【说明】探测通过=协议+渲染链路通；生产内容是否出图还差数据源：\n"
                + "  导航=公众版高德广播在送真实数据（剩余时间/红绿灯倒计时都来自这里，"
                + "定制版无数据通道）；\n"
                + "  歌词=通知使用权已授予且音乐App出元数据。探测 ok 但生产不出 = 数据源没数据，不是仪表问题。\n");
        return out.toString();
    }

    private static String verdict(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "(空回包)";
        try {
            org.json.JSONObject o = new org.json.JSONObject(raw);
            StringBuilder v = new StringBuilder();
            v.append("ok=").append(o.opt("ok"));
            if (o.has("rendered")) v.append(" rendered=").append(o.opt("rendered"));
            if (o.has("effective_visible")) v.append(" effective_visible=").append(o.opt("effective_visible"));
            if (o.has("actual_layer")) v.append(" actual_layer=").append(o.opt("actual_layer"));
            if (o.has("code")) v.append(" code=").append(o.opt("code"));
            if (o.has("details")) v.append(" details=").append(o.opt("details"));
            return v.toString();
        } catch (Throwable t) {
            return "(非JSON回包) " + (raw.length() > 120 ? raw.substring(0, 120) + "…" : raw);
        }
    }

    private static String configOf(String raw) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(raw);
            org.json.JSONObject c = o.optJSONObject("config");
            return c == null ? null : c.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean widgetSelected(String which, String key) {
        try {
            InstrumentLink.Reply r = Cluster.get().overlay(which, Ops.WIDGETS_STATUS, null);
            if (r == null || r.raw == null) return true;
            org.json.JSONObject o = new org.json.JSONObject(r.raw);
            org.json.JSONObject sel = o.optJSONObject("selected");
            if (sel == null) return true;
            return sel.optBoolean(key, true);
        } catch (Throwable t) {
            return true;
        }
    }

    private void setWidgetProbe(String which, String key, boolean on, StringBuilder out) {
        try {
            InstrumentLink.Reply r = Cluster.get().overlay(which, Ops.WIDGETS_SET,
                    "{\"" + key + "\":" + (on ? "true" : "false") + "}");
            out.append("  widgets.set(").append(key).append('=').append(on ? "true" : "false")
                    .append(") ← ").append(r == null || r.raw == null ? "(空)" : r.raw.trim()).append("\n");
        } catch (Throwable t) {
            out.append("  widgets.set(").append(key).append('=').append(on ? "true" : "false")
                    .append(") 异常: ").append(CrashHandler.describe(t)).append("\n");
        }
    }

    private static boolean isVisibilityOp(String op) {
        return Ops.NAV_SHOW.equals(op) || Ops.NAV_HIDE.equals(op)
                || Ops.MUSIC_SHOW.equals(op) || Ops.MUSIC_HIDE.equals(op);
    }

    public void applyNavConfig(final String body, final Runnable onOk, final Runnable onFail) {
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        runPush("nav", Ops.NAV_CONFIG_SET, body,
                onOk == null ? null : () -> main.post(onOk),
                onFail == null ? null : () -> main.post(onFail));
    }

    public void applyMusicConfig(final String body, final Runnable onOk, final Runnable onFail) {
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        runPush("music", Ops.MUSIC_CONFIG_SET, body,
                onOk == null ? null : () -> main.post(onOk),
                onFail == null ? null : () -> main.post(onFail));
    }

    private void runPush(final String which, final String op, final String body, final Runnable onOk) {
        runPush(which, op, body, onOk, null);
    }

    private static void fire(Runnable r) {
        if (r == null) return;
        try {
            r.run();
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
    }

    private void runPush(final String which, final String op, final String body,
                         final Runnable onOk, final Runnable onFail) {
        pool.execute(() -> runPushNow(which, op, body, onOk, onFail));
    }

    private void runPushNow(final String which, final String op, final String body,
                            final Runnable onOk, final Runnable onFail) {
        final String rk = which + "/" + op;
        Reject rj = rejects.get(rk);
        if (rj != null && System.currentTimeMillis() < rj.untilMs) {
            fire(onFail);
            return;
        }
        {
            try {
                if (!Cluster.get().ready()) {
                    LogHub.w("relay", which + " 未连仪表，本次不推送（下次心跳重推）");
                    fire(onFail);
                    return;
                }
                InstrumentLink.Reply r;
                if (isVisibilityOp(op)) {
                    r = Cluster.get().overlay(which, op, null);
                } else {
                    r = Cluster.get().overlay(which, op, body);
                }
                String code = r.code == null ? "?" : r.code;
                if (!r.okSuccess()) {

                    Reject cur = rejects.get(rk);
                    if (cur == null || !code.equals(cur.code)) {
                        cur = new Reject();
                        cur.code = code;
                        rejects.put(rk, cur);
                    }
                    cur.streak++;
                    long backoff = Math.min(60_000L, 1_000L << Math.min(6, cur.streak - 1));
                    cur.untilMs = System.currentTimeMillis() + backoff;
                    if (cur.streak <= 3) {
                        LogHub.w("relay", which + " 投送仪表被拒 op=" + op + " code=" + code
                                + " " + ErrMap.reason(code) + "（退避 " + backoff / 1000 + "s）");
                    } else {
                        LogHub.d("relay", which + " 投送仍被拒 op=" + op + " code=" + code
                                + " streak=" + cur.streak + "（退避 " + backoff / 1000 + "s）");
                    }
                    if ("music".equals(which) && Ops.TRACK_SYNC.equals(op)
                            && "line_index_out_of_range".equals(code)) {
                        lyricsLoaded = false;
                        pool.execute(this::pushMusic);
                    }
                    fire(onFail);
                    return;
                }
                rejects.remove(rk);
                if (onOk != null) {
                    try {
                        onOk.run();
                    } catch (Throwable t) {
                        CrashHandler.record(t);
                    }
                }

                boolean rendered = Json.b(r.json, "rendered", true);
                boolean visible = Json.b(r.json, "effective_visible", true);
                if (LogHub.oncePer("relay_ok_" + which + "_" + op, 120000)) {
                    LogHub.i("relay", which + " 投送仪表 OK op=" + op + " code=" + code
                            + " rendered=" + rendered + " visible=" + visible);
                }
                if ("music".equals(which) && LogHub.oncePer("music_render_check_" + op, 120000)) {
                    LogHub.d("relay", "music 回执核对 op=" + op + " rendered=" + rendered);
                }
                boolean shown = "nav".equals(which) ? navShown : musicShown;
                boolean dataOp = Ops.NAV_UPDATE.equals(op) || Ops.TRACK_LOAD.equals(op);
                if (!visible && shown && dataOp) {
                    AtomicLong last = "nav".equals(which) ? lastReassertNavMs : lastReassertMusicMs;
                    long now = System.currentTimeMillis();
                    if (now - last.get() >= 3000) {
                        last.set(now);
                        String showOp = "nav".equals(which) ? Ops.NAV_SHOW : Ops.MUSIC_SHOW;
                        InstrumentLink.Reply r2 = Cluster.get().overlay(which, showOp, null);
                        LogHub.i("relay", which + " effective_visible=false 自愈：重发 " + showOp
                                + " code=" + (r2.code == null ? "?" : r2.code));
                        if ("nav".equals(which)) pushNav(); else pushMusic();
                    }
                }
            } catch (Throwable t) {
                LogHub.w("relay", which + " 投送仪表失败：" + CrashHandler.describe(t));
                fire(onFail);
            }
        }
    }

    private static Object probeNavValue(String k) {
        switch (k) {
            case "active": return Boolean.TRUE;
            case "turn": return "uturn";
            case "distance_m": return 500;
            case "distance_text": return "500米";
            case "current_road": return "示例路";
            case "remaining_m": return 3000;
            case "remaining_s": return 240;
            case "limit_kph": return 60;
            case "ttl_ms": return 8000;
            case "destination": return "测试目的地";
            case "eta": return "3 分钟";
            case "service_area": return "服务区 2km";
            case "traffic_text": return "前方拥堵";
            case "exit_name": return "出口 A";
            case "speed_kph": return 0;
            case "lights": return probeLightsSample();
            case "show_next": return Boolean.TRUE;
            case "show_alerts": return Boolean.TRUE;
            case "show_limit": return Boolean.TRUE;
            case "next_ttl_ms": return 60000;
            case "alerts_ttl_ms": return 60000;
            default: return "probe";
        }
    }

    private static Object probeLightsSample() {
        JSONArray arr = new JSONArray();
        try {
            JSONObject e = new JSONObject();
            e.put("direction", "left");
            e.put("color", 1);
            e.put("seconds", 30);
            arr.put(e);
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        return arr;
    }

    private static Object probeMusicValue(String k) {
        switch (k) {
            case "track_id": return "cast_probe_001";
            case "title": return "测试歌曲";
            case "artist": return "测试歌手";
            case "playing": return Boolean.TRUE;
            case "duration_ms": return 210000;
            case "position_ms": return 30000;
            case "lyrics":

                return new org.json.JSONArray().put("测试歌词第一行").put("测试歌词第二行").put("测试歌词第三行");

            case "line_index": return 1;
            default: return "probe";
        }
    }
}
