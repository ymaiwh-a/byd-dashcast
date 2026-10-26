package com.byd.dashcast.phase1.widget;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.graphics.Bitmap;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Base64;

import com.byd.dashcast.phase1.ncm.LogHub;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MusicCollector extends Service {

    public static final String ACTION_LYRIC = "com.byd.Casting.LYRIC";
    public static final String EXTRA_LYRIC_LINES = "lyric_lines";
    public static final String EXTRA_LYRIC_REQUIRED = "lyrics_required";

    public static final String EXTRA_LYRIC_LRC = "lyric_lrc";

    private static final long POLL_MS = 1_000;

    private static volatile MusicCollector INSTANCE;

    private MediaController active;
    private boolean factoryActive;
    private static volatile boolean reflTried;
    private static volatile boolean reflReady;
    private static volatile Method mGetInstance;
    private static volatile Method mInfo;
    private static volatile Method mState;
    private static volatile Method mType;
    private static volatile Method mProgress;
    private static volatile Object fwDevice;
    private long facTickMs;
    private long noSourceSinceMs;
    private String sessionsSig = "";
    private final Object lock = new Object();
    private final MusicState last = new MusicState();

    private String coverCacheTrackId = "";
    private String coverCacheB64;
    private int coverCacheSize;

    private volatile String lyricFetchTrackId = "";
    private volatile String coverFetchTrackId = "";
    private HandlerThread cbThread;
    private Handler cbHandler;

    private final MediaController.Callback cb = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata m) {
            synchronized (lock) {
                noSourceSinceMs = 0L;
            }
            push();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {

            if (s == null || s.getState() == PlaybackState.STATE_STOPPED
                    || s.getState() == PlaybackState.STATE_NONE) {
                return;
            }
            synchronized (lock) {
                noSourceSinceMs = 0L;
            }
            push();
        }
    };

    private final BroadcastReceiver lyricReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            try {
                if (i == null || !ACTION_LYRIC.equals(i.getAction())) return;
                String[] lines = i.getStringArrayExtra(EXTRA_LYRIC_LINES);
                String lrc = i.getStringExtra(EXTRA_LYRIC_LRC);
                boolean required;
                synchronized (lock) {
                    required = i.getBooleanExtra(EXTRA_LYRIC_REQUIRED, last.lyricsRequired);
                    if (lrc != null && !lrc.trim().isEmpty()) {

                        Lrc p = parseLrc(lrc);
                        last.lyricLines = p.texts;
                        last.lyricTimes = p.times.length == 0 ? null : p.times;
                        last.lineIndex = computeLineIndex(last.lyricTimes, last.positionMs);
                        last.lyricDone = true;
                    } else if (lines != null) {

                        last.lyricLines = lines;
                        last.lyricTimes = null;
                        last.lineIndex = -1;
                        last.lyricDone = true;
                    }
                    last.lyricsRequired = required;
                }
                LogHub.i("music", "lyric rx lines=" + (lines == null ? 0 : lines.length)
                        + " lrc=" + (lrc == null ? 0 : lrc.length()) + " required=" + required);
                push();
            } catch (Throwable t) {
                CrashHandler.record(t);
            }
        }
    };

    public static void ensureStarted(Context c) {
        try {
            c.startService(new Intent(c, MusicCollector.class));
        } catch (Throwable t) {
            LogHub.w("music", "音乐采集服务启动失败: " + CrashHandler.describe(t));
        }
    }

    private void sessionGone(String why) {
        synchronized (lock) {
            detach();
            factoryActive = false;
            last.trackId = "";
            last.title = "";
            last.artist = "";
            last.coverRgba = "#00000000";
            last.lyricLines = new String[0];
            last.lyricTimes = null;
            last.lineIndex = -1;
            last.coverBase64 = null;
            last.coverDone = false;
            last.lyricDone = false;
            last.lyricsRequired = false;
            last.playing = false;
            last.durationMs = 0;
            last.positionMs = 0;
            coverCacheTrackId = "";
            coverCacheB64 = null;
            lyricFetchTrackId = "";
        }
        LogHub.i("music", "音乐数据源消失（" + why + "）：清零并推送清除");
        push();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        cbThread = new HandlerThread("music-cb");
        cbThread.start();
        cbHandler = new Handler(cbThread.getLooper());
        try {
            File ldir = new File(getFilesDir(), "lyrics2");
            File marker = new File(ldir, ".v4");
            if (!marker.isFile()) {
                deleteTree(ldir);
                deleteTree(new File(getFilesDir(), "lyrics"));
                deleteTree(new File(getFilesDir(), "covers"));
                ldir.mkdirs();
                new FileOutputStream(marker).close();
                LogHub.i("music", "升级清理旧歌词与封面缓存");
            }
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        try {
            registerReceiver(lyricReceiver, new IntentFilter(ACTION_LYRIC));
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        Thread p = new Thread(new Runnable() {
            public void run() {
                while (INSTANCE == MusicCollector.this) {

                    SystemClock.sleep(POLL_MS);
                    if (INSTANCE != MusicCollector.this) return;
                    try {
                        poll();
                    } catch (Throwable t) {
                        CrashHandler.record(t);
                    }
                }
            }
        }, "music-poll");
        p.setDaemon(true);
        p.start();

        Thread tick = new Thread(new Runnable() {
            public void run() {
                while (INSTANCE == MusicCollector.this) {
                    SystemClock.sleep(1_000);
                    if (INSTANCE != MusicCollector.this) return;
                    try {
                        boolean need;
                        synchronized (lock) {
                            need = last.playing && last.lyricTimes != null && active != null;
                        }
                        if (need && WidgetRelay.get().musicEnabled()) push();
                    } catch (Throwable t) {
                        CrashHandler.record(t);
                    }
                }
            }
        }, "lyric-tick");
        tick.setDaemon(true);
        tick.start();
        LogHub.i("music", "MusicCollector 已启动（歌词广播 + MediaSession 轮询，独立于通知监听实例）");
    }

    @Override
    public void onDestroy() {
        INSTANCE = null;
        try {
            unregisterReceiver(lyricReceiver);
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        synchronized (lock) {
            detach();
        }
        if (cbThread != null) {
            cbThread.quitSafely();
        }
        super.onDestroy();
    }

    private void poll() {
        try {
            if (!WidgetRelay.get().musicEnabled()) {
                synchronized (lock) {
                    if (active != null || factoryActive) {
                        detach();
                        factoryActive = false;
                    }
                }
                return;
            }
            MediaSessionManager msm =
                    (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return;
            List<MediaController> list = msm.getActiveSessions(
                    new ComponentName(this, MusicListener.class));
            StringBuilder sig = new StringBuilder();
            if (list != null) {
                for (MediaController mc : list) {
                    try {
                        PlaybackState ps = mc.getPlaybackState();
                        boolean pl = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                        sig.append(mc.getPackageName()).append(pl ? "=play" : "=idle").append(',');
                    } catch (Throwable t) {
                        CrashHandler.record(t);
                    }
                }
            }
            String now = sig.toString();
            boolean changed = !now.equals(sessionsSig);
            sessionsSig = now;
            if (!now.isEmpty() && (changed || LogHub.oncePer("music_sessions", 120_000)))
                LogHub.i("music", "媒体会话清单" + (changed ? "变化：" : "：") + now);
            MediaController playing = null;
            MediaController fallback = null;
            if (list != null) {
                for (MediaController mc : list) {
                    try {
                        PlaybackState ps = mc.getPlaybackState();
                        int st = ps == null ? PlaybackState.STATE_NONE : ps.getState();
                        if (st == PlaybackState.STATE_STOPPED || st == PlaybackState.STATE_NONE) continue;
                        if (st == PlaybackState.STATE_PLAYING) {
                            if (playing == null) playing = mc;
                        } else if (fallback == null && mc.getMetadata() != null) {
                            fallback = mc;
                        }
                    } catch (Throwable t) {
                        CrashHandler.record(t);
                    }
                }
            }
            FactoryInfo fi = readFactory();
            boolean factoryPlaying = fi != null && fi.playing;
            if (playing != null && factoryPlaying && !sessionHasContent(playing)) {
                if (LogHub.oncePer("factory_preempt", 60000))
                    LogHub.i("music", "媒体会话无内容，改用原厂源 pkg=" + playing.getPackageName());
                playing = null;
            }
            MediaController pick = playing != null ? playing
                    : (factoryPlaying ? null : fallback);
            boolean useFactory = playing == null && factoryPlaying;
            if (pick == null && !useFactory) {
                boolean had;
                long since;
                synchronized (lock) {
                    had = active != null || factoryActive;
                    if (had) {
                        if (noSourceSinceMs == 0L) noSourceSinceMs = SystemClock.elapsedRealtime();
                    } else {
                        noSourceSinceMs = 0L;
                    }
                    since = noSourceSinceMs;
                }
                if (had && SystemClock.elapsedRealtime() - since >= 2500L) {
                    sessionGone("无在播媒体");
                }
                return;
            }
            synchronized (lock) {
                noSourceSinceMs = 0L;
            }
            if (useFactory) {
                synchronized (lock) {
                    if (active != null) detach();
                    factoryActive = true;
                }
                push();
                return;
            }
            synchronized (lock) {
                if (active != null && active.getSessionToken() != null
                        && active.getSessionToken().equals(pick.getSessionToken())) {
                    push();
                    return;
                }
                detach();
                active = pick;
                factoryActive = false;
                try {
                    active.registerCallback(cb, cbHandler);
                } catch (Throwable t) {
                    CrashHandler.record(t);
                }
                LogHub.i("music", "接管媒体会话（轮询）pkg=" + active.getPackageName());
            }
            push();
        } catch (Throwable t) {
            if (LogHub.oncePer("music_poll_err", 30000))
                LogHub.d("music", "MediaSession 轮询不可用: " + CrashHandler.describe(t));
        }
    }

    private void push() {
        MusicState m = new MusicState();
        MediaController cur;
        boolean fac;
        String lastTrack;
        synchronized (lock) {
            m.title = last.title;
            m.artist = last.artist;
            m.coverRgba = last.coverRgba;
            m.lyricLines = last.lyricLines;
            m.lyricTimes = last.lyricTimes;
            m.lyricsRequired = last.lyricsRequired;
            m.coverDone = last.coverDone;
            m.lyricDone = last.lyricDone;
            m.playing = last.playing;
            m.durationMs = last.durationMs;
            m.positionMs = last.positionMs;
            m.lineIndex = last.lineIndex;
            lastTrack = last.trackId;
            cur = active;
            fac = factoryActive;
        }
        if (fac) {
            FactoryInfo fi = readFactory();
            if (fi != null) {
                m.title = fi.title;
                m.artist = fi.artist;
                m.playing = fi.playing;
                String[] na = LyricSource.normalize(m.title, m.artist);
                m.title = na[0];
                m.artist = na[1];
                if (!m.title.isEmpty() || !m.artist.isEmpty()) {
                    m.trackId = "t" + Integer.toHexString(
                            (m.title + "|" + m.artist).hashCode());
                }
                ensureCover(m);
                long nowTick = SystemClock.elapsedRealtime();
                if (fi.duration >= 1000 && fi.duration <= 7200000) m.durationMs = fi.duration;
                if (fi.progress >= 0) {
                    m.positionMs = fi.progress;
                } else if (!m.trackId.equals(lastTrack)) {
                    m.positionMs = 0;
                } else if (m.playing && facTickMs > 0) {
                    long gap = nowTick - facTickMs;
                    if (gap < 0 || gap > 30000) gap = 1000;
                    m.positionMs += gap;
                }
                if (m.durationMs > 0 && m.positionMs > m.durationMs) m.positionMs = m.durationMs;
                facTickMs = nowTick;
            }
        } else if (cur != null) {
            try {
                MediaMetadata md = cur.getMetadata();
                MediaDescription desc = md == null ? null : md.getDescription();
                if (desc != null) {
                    CharSequence t = desc.getTitle();
                    CharSequence a = desc.getSubtitle();
                    if (t != null && t.length() > 0) m.title = t.toString();
                    if (a != null && a.length() > 0) m.artist = a.toString();
                    String[] na = LyricSource.normalize(m.title, m.artist);
                    m.title = na[0];
                    m.artist = na[1];
                }
                if (md != null) {
                    long d = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    if (d > 0) m.durationMs = d;
                }
                PlaybackState ps = cur.getPlaybackState();
                if (ps != null) {
                    m.playing = (ps.getState() == PlaybackState.STATE_PLAYING);
                    long pos = ps.getPosition();
                    if (pos < 0) pos = 0;
                    if (m.playing) {
                        float sp = ps.getPlaybackSpeed();
                        if (sp <= 0f) sp = 1f;
                        long upd = ps.getLastPositionUpdateTime();
                        if (upd > 0) {
                            pos += (long) ((SystemClock.elapsedRealtime() - upd) * sp);
                        }
                    }
                    m.positionMs = pos;
                }

                if (!m.title.isEmpty() || !m.artist.isEmpty()) {
                    m.trackId = "t" + Integer.toHexString((m.title + "|" + m.artist).hashCode());
                }

                m.coverBase64 = coverFor(desc == null ? null : desc.getIconBitmap(), m.trackId);
                if (m.coverBase64 == null) ensureCover(m);
            } catch (Throwable t) {
                CrashHandler.record(t);
            }
        }

        boolean newTrack;
        synchronized (lock) {
            newTrack = !m.trackId.isEmpty() && !m.trackId.equals(last.trackId);
            if (newTrack) {
                last.lyricLines = new String[0];
                last.lyricTimes = null;
                last.lineIndex = -1;
                last.coverDone = false;
                last.lyricDone = false;
                LogHub.i("music", "切歌 trackId=" + m.trackId + " 清旧歌词并重新下载");
            }
        }
        if (newTrack) {
            m.lyricLines = new String[0];
            m.lyricTimes = null;
            m.lineIndex = -1;
            m.coverDone = false;
            m.lyricDone = false;
        }
        if (m.coverBase64 != null || WidgetCfg.musicCoverSize <= 0) m.coverDone = true;

        if (m.lyricTimes != null) {
            m.lineIndex = computeLineIndex(m.lyricTimes, m.positionMs);
        }
        synchronized (lock) {
            last.trackId = m.trackId;
            last.title = m.title;
            last.artist = m.artist;
            last.durationMs = m.durationMs;
            last.playing = m.playing;
            last.positionMs = m.positionMs;
            last.lineIndex = m.lineIndex;
            last.coverBase64 = m.coverBase64;
            if (m.coverDone) last.coverDone = true;
            if (m.lyricDone) last.lyricDone = true;
        }

        if (!m.trackId.isEmpty() && m.lyricLines.length == 0
                && !m.trackId.equals(lyricFetchTrackId)) {
            lyricFetchTrackId = m.trackId;
            final String tid = m.trackId;
            final String tt = m.title;
            final String ta = m.artist;
            final long td = m.durationMs;
            final String tp = fac || cur == null ? "" : cur.getPackageName();
            Thread f = new Thread(new Runnable() {
                public void run() {
                    String lrc = LyricSource.fetch(MusicCollector.this, tp, tid, tt, ta, td);
                    if (lrc == null) {
                        synchronized (lock) {
                            if (tid.equals(last.trackId)) last.lyricDone = true;
                        }
                        push();
                        return;
                    }
                    Lrc p = parseLrc(lrc);
                    synchronized (lock) {
                        if (!tid.equals(last.trackId)
                                || (last.lyricLines != null && last.lyricLines.length > 0)) {
                            return;
                        }
                        last.lyricLines = p.texts;
                        last.lyricTimes = p.times.length == 0 ? null : p.times;
                        last.lineIndex = computeLineIndex(last.lyricTimes, last.positionMs);
                        last.lyricDone = true;
                    }
                    push();
                }
            }, "lyric-fetch");
            f.setDaemon(true);
            f.start();
        }
        WidgetRelay.get().onMusic(m);
    }

    private void ensureCover(MusicState m) {
        if (WidgetCfg.musicCoverSize <= 0) return;
        if (m.trackId == null || m.trackId.isEmpty()) return;
        String cached = null;
        synchronized (lock) {
            if (m.trackId.equals(coverCacheTrackId) && coverCacheB64 != null
                    && coverCacheSize == WidgetCfg.musicCoverSize) {
                cached = coverCacheB64;
            }
        }
        if (cached != null) {
            m.coverBase64 = cached;
            m.coverDone = true;
            return;
        }
        if (m.trackId.equals(coverFetchTrackId)) return;
        coverFetchTrackId = m.trackId;
        final String ctid = m.trackId;
        final String ctt = m.title;
        final String cta = m.artist;
        Thread fc = new Thread(new Runnable() {
            public void run() {
                Bitmap b = LyricSource.fetchCover(MusicCollector.this, ctid, ctt, cta);
                String b64 = b == null ? null : coverFor(b, ctid);
                synchronized (lock) {
                    if (ctid.equals(last.trackId)) {
                        if (b64 != null) last.coverBase64 = b64;
                        last.coverDone = true;
                    }
                }
                push();
            }
        }, "cover-fetch");
        fc.setDaemon(true);
        fc.start();
    }

    private String coverFor(Bitmap bmp, String trackId) {
        int size = WidgetCfg.musicCoverSize;
        if (size <= 0 || trackId == null || trackId.isEmpty()) return null;
        String fresh = null;
        try {
            if (bmp != null && !bmp.isRecycled()) {
                Bitmap sq = bmp.getWidth() == size && bmp.getHeight() == size ? bmp
                        : Bitmap.createScaledBitmap(bmp, size, size, true);
                int[] px = new int[size * size];
                sq.getPixels(px, 0, size, 0, 0, size, size);
                byte[] rgba = new byte[size * size * 4];
                for (int i = 0; i < px.length; i++) {
                    int p = px[i];
                    rgba[i * 4] = (byte) ((p >> 16) & 0xFF);
                    rgba[i * 4 + 1] = (byte) ((p >> 8) & 0xFF);
                    rgba[i * 4 + 2] = (byte) (p & 0xFF);
                    rgba[i * 4 + 3] = (byte) ((p >>> 24) & 0xFF);
                }
                fresh = Base64.encodeToString(rgba, Base64.NO_WRAP);
            }
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        if (fresh != null && !fresh.isEmpty()) {
            boolean changed;
            synchronized (lock) {
                changed = !trackId.equals(coverCacheTrackId) || coverCacheB64 == null
                        || coverCacheSize != size || !fresh.equals(coverCacheB64);
                if (changed) {
                    coverCacheTrackId = trackId;
                    coverCacheB64 = fresh;
                    coverCacheSize = size;
                }
            }
            if (changed) LogHub.i("music", "封面更新 trackId=" + trackId + " size=" + size
                    + " bytes=" + fresh.length());
            return fresh;
        }
        if (LogHub.oncePer("cover_none_" + trackId, 30000))
            LogHub.d("music", "封面提取为空 trackId=" + trackId
                    + " artwork=" + (bmp == null ? "无" : "有图但编码失败"));
        return null;
    }

    private static void deleteTree(File d) {
        File[] fs = d.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) deleteTree(f);
                else if (!f.delete()) f.deleteOnExit();
            }
        }
    }

    private static boolean sessionHasContent(MediaController mc) {
        try {
            MediaMetadata md = mc.getMetadata();
            if (md == null) return false;
            CharSequence t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            return (t != null && t.length() > 0) || (a != null && a.length() > 0);
        } catch (Throwable t) {
            return false;
        }
    }

    private FactoryInfo readFactory() {
        try {
            if (!reflTried) {
                reflTried = true;
                try {
                    Class<?> dev = Class.forName(
                            "android.hardware.bydauto.multimedia.BYDAutoMultimediaDevice");
                    mGetInstance = dev.getMethod("getInstance", Context.class);
                    mInfo = dev.getMethod("getPlayMediaInfo");
                    mState = dev.getMethod("getPlayState");
                    mType = dev.getMethod("getMediaType");
                    reflReady = true;
                    try {
                        mProgress = dev.getMethod("getPlayProgress");
                    } catch (Throwable ignored) {
                    }
                } catch (Throwable t) {
                    reflReady = false;
                    LogHub.w("music", "原厂多媒体 SDK 不可用: " + CrashHandler.describe(t));
                }
            }
            if (!reflReady) return null;
            Object dev = fwDevice;
            if (dev == null) {
                dev = mGetInstance.invoke(null, this);
                if (dev == null) return null;
                fwDevice = dev;
            }
            Object info = mInfo.invoke(dev);
            if (info == null) return null;
            Object mtv = mType.invoke(dev);
            int mediaType = mtv instanceof Integer ? (Integer) mtv : -1;
            if (mediaType != 7 && mediaType != 8 && mediaType != 10
                    && mediaType != 12 && mediaType != 14 && mediaType != 16) return null;
            Class<?> c = info.getClass();
            FactoryInfo r = new FactoryInfo();
            Object tv = c.getField("fileName").get(info);
            Object av = c.getField("artistName").get(info);
            r.title = tv == null ? "" : tv.toString().trim();
            r.artist = av == null ? "" : av.toString().trim();
            if (r.title.isEmpty()) return null;
            Object sv = mState.invoke(dev);
            int state = sv instanceof Integer ? (Integer) sv : -1;
            r.playing = state == 0;
            int progress = -1;
            int duration = -1;
            try {
                if (mProgress != null) {
                    Object pv = mProgress.invoke(dev);
                    if (pv instanceof Integer) progress = (Integer) pv;
                }
                Object dv = c.getField("duration").get(info);
                if (dv instanceof Integer) duration = (Integer) dv;
            } catch (Throwable ignored) {
            }
            r.progress = progress;
            r.duration = duration;
            if (LogHub.oncePer("factory_probe_" + mediaType + "_" + state, 60000))
                LogHub.i("music", "原厂媒体中心 mediaType=" + mediaType + " state=" + state
                        + " progress=" + progress + " duration=" + duration
                        + " title=" + r.title);
            return r;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class FactoryInfo {
        String title = "";
        String artist = "";
        boolean playing;
        int progress = -1;
        int duration = -1;
    }

    private static final class Lrc {
        final long[] times;
        final String[] texts;
        Lrc(long[] t, String[] x) { times = t; texts = x; }
    }

    private static Lrc parseLrc(String raw) {
        List<long[]> ts = new ArrayList<>();
        List<String> tx = new ArrayList<>();
        String[] src = raw.split("\n");
        for (String line : src) {
            line = line.replace("\r", "");
            if (line.isEmpty()) continue;

            List<Long> tags = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            int i = 0;
            while (i < line.length() && line.charAt(i) == '[') {
                int close = line.indexOf(']', i);
                if (close < 0) break;
                String tag = line.substring(i + 1, close);
                int colon = tag.indexOf(':');
                if (colon < 0) break;
                try {
                    long min = Long.parseLong(tag.substring(0, colon).trim());
                    String secPart = tag.substring(colon + 1).trim();
                    long sec;
                    long ms = 0;
                    int dot = secPart.indexOf('.');
                    if (dot < 0) {
                        sec = Long.parseLong(secPart);
                    } else {
                        sec = Long.parseLong(secPart.substring(0, dot));
                        String frac = secPart.substring(dot + 1);
                        if (frac.length() == 1) frac += "0";
                        while (frac.length() < 3) frac += "0";
                        ms = Long.parseLong(frac.substring(0, 3));
                    }
                    tags.add(min * 60_000 + sec * 1000 + ms);
                } catch (NumberFormatException nfe) {
                    break;
                }
                i = close + 1;
            }
            if (tags.isEmpty()) continue;
            text.append(line.substring(i).trim());
            String t = text.toString();
            if (t.isEmpty()) continue;
            for (Long tag : tags) ts.add(new long[]{tag});
            for (int k = 0; k < tags.size(); k++) tx.add(t);
        }

        int n = ts.size();
        long[] times = new long[n];
        String[] texts = new String[n];
        for (int a = 0; a < n; a++) { times[a] = ts.get(a)[0]; texts[a] = tx.get(a); }
        Integer[] order = new Integer[n];
        for (int a = 0; a < n; a++) order[a] = a;
        java.util.Arrays.sort(order, (x, y) -> Long.compare(times[x], times[y]));
        long[] st = new long[n];
        String[] sx = new String[n];
        for (int a = 0; a < n; a++) { st[a] = times[order[a]]; sx[a] = texts[order[a]]; }
        return new Lrc(st, sx);
    }

    public static int computeLineIndex(long[] times, long positionMs) {
        if (times == null || times.length == 0) return -1;
        long pos = positionMs + WidgetCfg.lyricOffsetMs + 1000;
        if (pos < 0) pos = 0;
        int lo = 0, hi = times.length - 1, ans = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= pos) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private void detach() {
        if (active != null) {
            try {
                active.unregisterCallback(cb);
            } catch (Throwable t) {
                CrashHandler.record(t);
            }
            active = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
