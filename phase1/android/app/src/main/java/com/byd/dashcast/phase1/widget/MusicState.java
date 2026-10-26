package com.byd.dashcast.phase1.widget;

public final class MusicState {

    public String trackId = "";
    public String title = "";
    public String artist = "";
    public String coverRgba = "#00000000";
    public String[] lyricLines = new String[0];

    public long[] lyricTimes;

    public int lineIndex = -1;

    public String coverBase64;
    public boolean coverDone;
    public boolean lyricDone;
    public boolean lyricsRequired;
    public boolean playing;
    public long durationMs;
    public long positionMs;

    public MusicState copy() {
        MusicState m = new MusicState();
        m.trackId = trackId;
        m.title = title;
        m.artist = artist;
        m.coverRgba = coverRgba;
        m.lyricLines = lyricLines == null ? new String[0] : lyricLines.clone();
        m.lyricTimes = lyricTimes == null ? null : lyricTimes.clone();
        m.lineIndex = lineIndex;
        m.coverBase64 = coverBase64;
        m.coverDone = coverDone;
        m.lyricDone = lyricDone;
        m.lyricsRequired = lyricsRequired;
        m.playing = playing;
        m.durationMs = durationMs;
        m.positionMs = positionMs;
        return m;
    }

    public boolean equalsData(MusicState o) {
        if (o == null) return false;
        if (playing != o.playing) return false;
        if (durationMs != o.durationMs) return false;
        if (lyricsRequired != o.lyricsRequired) return false;
        if (!eq(trackId, o.trackId) || !eq(title, o.title) || !eq(artist, o.artist)) return false;
        if (!eq(coverRgba, o.coverRgba)) return false;
        if (!eq(coverBase64, o.coverBase64)) return false;
        if (coverDone != o.coverDone) return false;
        if (lyricDone != o.lyricDone) return false;
        if (lyricLines == null || o.lyricLines == null) {
            if (lyricLines != o.lyricLines) return false;
        } else {
            if (lyricLines.length != o.lyricLines.length) return false;
            for (int i = 0; i < lyricLines.length; i++) {
                if (!eq(lyricLines[i], o.lyricLines[i])) return false;
            }
        }
        return true;
    }

    private static boolean eq(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.equals(b);
    }
}
