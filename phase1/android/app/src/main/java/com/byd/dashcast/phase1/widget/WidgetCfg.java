package com.byd.dashcast.phase1.widget;

public final class WidgetCfg {

    public static final int LYRIC_MIN = 1;
    public static final int LYRIC_MAX = 5;

    public static final String[] MUSIC_LAYOUT = {"封面在上", "封面在左", "仅歌词"};

    public static final String[] ALIGN = {"center", "left", "right"};
    public static final String[] ALIGN_LABELS = {"居中", "靠左", "靠右"};

    public static final String[] ANIM = {"开", "关"};

    public static final String GUIDANCE_SCHEMA_HINT =
            "固件字段 guidance_x / guidance_y / guidance_width / guidance_height / show_lights"
                    + " / show_lanes / show_alerts / lane_size / light_size / theme";

    public static final int TTL_MIN = 2;
    public static final int TTL_MAX = 60;

    public static final int SECONDARY_MIN = 10;
    public static final int SECONDARY_MAX = 40;

    public static final int LINE_GAP_MIN = 0;
    public static final int LINE_GAP_MAX = 40;

    public static final int BEFORE_MIN = 0;
    public static final int BEFORE_MAX = 5;
    public static final int AFTER_MIN = 0;
    public static final int AFTER_MAX = 5;

    public static final int BG_RADIUS_MIN = 0;
    public static final int BG_RADIUS_MAX = 40;

    public static final int BG_PAD_MIN = 0;
    public static final int BG_PAD_MAX = 60;

    public static final int PROGRESS_GAP_MIN = 0;
    public static final int PROGRESS_GAP_MAX = 40;

    public static final int TITLE_SIZE_MIN = 0;
    public static final int TITLE_SIZE_MAX = 40;
    public static final int ARTIST_SIZE_MIN = 0;
    public static final int ARTIST_SIZE_MAX = 32;

    public static final int COVER_GAP_MIN = 0;
    public static final int COVER_GAP_MAX = 60;

    public static final String[] THEME_NAMES = {"跟随系统", "亮色", "暗色"};

    public static volatile int musicCoverSize = 200;

    public static volatile int lyricLinesSent = 3;

    public static volatile int lyricOffsetMs = 0;

    public static final int OFFSET_MIN = -5000;
    public static final int OFFSET_MAX = 5000;
    public static final int OFFSET_STEP = 200;

    public static int clampOffset(int v) {
        return Math.max(OFFSET_MIN, Math.min(OFFSET_MAX, v));
    }

    private WidgetCfg() {
    }

    public static String resolveTheme(String t) {
        if (!"auto".equals(t)) return t;
        int d = ClusterState.get().appearanceDay;
        if (d == 1) return "day";
        if (d == 0) return "night";
        return "auto";
    }

    public static String musicConfigSet(int x, int y, int w, int h, boolean geomKnown, Cfg c) {
        StringBuilder sb = new StringBuilder();
        if (geomKnown) {
            lyricFitW = Math.max(200, w - 40);
            sb.append("\"x\":").append(x);
            sb.append(",\"y\":").append(y);
            sb.append(",\"width\":").append(w);
            sb.append(",\"height\":").append(h);
            sb.append(",\"opacity\":255");
        }

        sb.append(geomKnown ? ",\"day_colors\":{\"color\":\"" : "\"day_colors\":{\"color\":\"")
                .append(hex(c.color))
                .append("\",\"previous_color\":\"").append(hex(c.previousColor))
                .append("\",\"next_color\":\"").append(hex(c.nextColor)).append("\"}");
        sb.append(",\"night_colors\":{\"color\":\"").append(hex(c.nightColor))
                .append("\",\"previous_color\":\"").append(hex(c.nightPrevColor))
                .append("\",\"next_color\":\"").append(hex(c.nightNextColor)).append("\"}");
        sb.append(",\"show_cover\":").append(c.coverSize > 0 ? "true" : "false");
        sb.append(",\"show_progress\":").append(c.showProgress ? "true" : "false");
        sb.append(",\"show_metadata\":true");
        sb.append(",\"music_layout\":").append(Math.max(1, Math.min(3, c.musicLayout + 1)));

        if (c.coverSize > 0) {
            sb.append(",\"cover_size\":").append(c.coverSize);
        }
        sb.append(",\"cover_radius\":").append(c.coverRadius);
        sb.append(",\"progress_width\":").append(c.progressWidth);

        musicCoverSize = Math.min(c.coverSize, 200);
        sb.append(",\"background_color\":\"").append(hex(c.backgroundColor)).append('"');
        sb.append(",\"background_radius\":").append(c.backgroundRadius);
        sb.append(",\"background_padding_top\":").append(c.backgroundPaddingTop);
        sb.append(",\"background_padding_bottom\":").append(c.backgroundPaddingBottom);
        sb.append(",\"theme\":\"").append(resolveTheme(c.theme)).append('"');
        sb.append(",\"ttl_ms\":20000");

        lyricLinesSent = Math.max(1, Math.min(5, c.lyricLines));
        sb.append(",\"lyric_lines\":").append(lyricLinesSent);
        sb.append(",\"font_size\":").append(clamp(c.fontSize, FONT_MIN, FONT_MAX));
        sb.append(",\"secondary_size\":").append(clamp(c.secondarySize, SECONDARY_MIN, SECONDARY_MAX));
        sb.append(",\"title_size\":").append(clamp(c.titleSize, TITLE_SIZE_MIN, TITLE_SIZE_MAX));
        sb.append(",\"artist_size\":").append(clamp(c.artistSize, ARTIST_SIZE_MIN, ARTIST_SIZE_MAX));
        sb.append(",\"stroke_width\":").append(clamp(c.strokeWidth, STROKE_MIN, STROKE_MAX));
        sb.append(",\"stroke_color\":\"").append(hex(c.strokeColor)).append('"');
        return sb.toString();
    }

    public static final int FONT_MIN = 20;
    public static final int FONT_MAX = 28;
    public static final int STROKE_MIN = 0;
    public static final int STROKE_MAX = 8;
    public static volatile int lyricFitW = 460;

    private static final android.graphics.Paint lyricPaint = new android.graphics.Paint();

    public static String normalize(String raw) {
        return raw.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private static float measure(String t, int size) {
        lyricPaint.setTextSize(size);
        return lyricPaint.measureText(t);
    }

    public static synchronized int fitFontSize(String raw) {
        String t = normalize(raw);
        float w32 = measure(t, FONT_MAX);
        int size = w32 > lyricFitW
                ? clamp((int) (lyricFitW * FONT_MAX / w32), FONT_MIN, FONT_MAX)
                : FONT_MAX;
        while (size > FONT_MIN && measure(t, size) > lyricFitW) {
            size--;
        }
        return size;
    }

    public static synchronized String fitText(String raw, int size) {
        String t = normalize(raw);
        if (measure(t, size) <= lyricFitW) {
            return t;
        }
        int n = t.codePointCount(0, t.length());
        int lo = 0;
        int hi = n;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String cand = t.substring(0, t.offsetByCodePoints(0, mid)) + "…";
            if (measure(cand, size) <= lyricFitW) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return t.substring(0, t.offsetByCodePoints(0, lo)) + "…";
    }

    public static String hex(long argb) {
        return String.format(java.util.Locale.US, "#%06X%02X",
                (argb >> 8) & 0xFFFFFFL, argb & 0xFFL);
    }

    public static final class Cfg {
        public int lyricLines = 3;
        public int musicLayout = 1;
        public int align = 0;
        public int fontSize = 26;

        public int coverSize = 200;
        public int anim = 0;
        public int transitionMs = 260;
        public String theme = "auto";
        public int secondarySize = 20;
        public int lineGap = 6;
        public int beforeLines = 1;
        public int afterLines = 1;
        public int titleSize = 22;
        public int artistSize = 20;
        public int coverGap = 12;
        public int coverRadius = 13;
        public long color = 0x0D47A1FFL;
        public long previousColor = 0x1E88E5FFL;
        public long nextColor = 0x1E88E5FFL;
        public long nightColor = 0x00E676FFL;
        public long nightPrevColor = 0x81C784FFL;
        public long nightNextColor = 0x81C784FFL;
        public long backgroundColor = 0x00000000L;
        public int backgroundRadius = 0;
        public int backgroundPaddingTop = 0;
        public int backgroundPaddingBottom = 0;
        public int strokeWidth = 2;
        public long strokeColor = 0x000000FFL;
        public boolean showProgress = true;
        public int progressWidth = 0;
        public int progressGap = 0;
        public int lyricOffsetMs = 0;
    }

    public static final int NAV_DEF_X = 180, NAV_DEF_Y = 220, NAV_DEF_W = 500, NAV_DEF_H = 400;
    public static final int MUSIC_DEF_X = 1080, MUSIC_DEF_Y = 200, MUSIC_DEF_W = 560, MUSIC_DEF_H = 400;

    public static final int NAV_DEF_GX = 790, NAV_DEF_GY = 120, NAV_DEF_GW = 340, NAV_DEF_GH = 160;

    public static final class Gd {
        public int ttlSec = 10;
        public int x = NAV_DEF_X, y = NAV_DEF_Y, w = NAV_DEF_W, h = NAV_DEF_H;

        public boolean geomKnown = false;

        public int gx = -1, gy = -1, gw = -1, gh = -1;
        public int ugx = -1, ugy = -1, ugw = -1, ugh = -1;
        public boolean lightsOnTop = true;
        public long bgColor = 0x00000000L;
        public int bgRadius = 0, padTop = 0, padBottom = 0;
        public boolean showLights = true, showLanes = true, showAlerts = true;
        public int lanesTtl = 30, lightsTtl = 10, alertsTtl = 10;
        public String theme = "auto";
        public int opacity = 255;
    }

    public static String cfgToJson(Cfg c) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("cfgVer", 2);
            o.put("lyricLines", c.lyricLines);
            o.put("musicLayout", c.musicLayout);
            o.put("align", c.align);
            o.put("fontSize", c.fontSize);
            o.put("coverSize", c.coverSize);
            o.put("anim", c.anim);
            o.put("transitionMs", c.transitionMs);
            o.put("theme", c.theme);
            o.put("secondarySize", c.secondarySize);
            o.put("lineGap", c.lineGap);
            o.put("beforeLines", c.beforeLines);
            o.put("afterLines", c.afterLines);
            o.put("titleSize", c.titleSize);
            o.put("artistSize", c.artistSize);
            o.put("coverGap", c.coverGap);
            o.put("coverRadius", c.coverRadius);
            o.put("color", c.color);
            o.put("previousColor", c.previousColor);
            o.put("nextColor", c.nextColor);
            o.put("nightColor", c.nightColor);
            o.put("nightPrevColor", c.nightPrevColor);
            o.put("nightNextColor", c.nightNextColor);
            o.put("backgroundColor", c.backgroundColor);
            o.put("backgroundRadius", c.backgroundRadius);
            o.put("backgroundPaddingTop", c.backgroundPaddingTop);
            o.put("backgroundPaddingBottom", c.backgroundPaddingBottom);
            o.put("strokeWidth", c.strokeWidth);
            o.put("strokeColor", c.strokeColor);
            o.put("showProgress", c.showProgress);
            o.put("progressWidth", c.progressWidth);
            o.put("progressGap", c.progressGap);
            o.put("lyricOffsetMs", c.lyricOffsetMs);
            return o.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static void cfgFromJson(String raw, Cfg c) {
        org.json.JSONObject o = Json.parse(raw);
        if (o == null || c == null) return;
        c.lyricLines = Json.i(o, "lyricLines", c.lyricLines);
        c.musicLayout = Json.i(o, "musicLayout", c.musicLayout);
        c.align = Json.i(o, "align", c.align);
        c.fontSize = Json.i(o, "fontSize", c.fontSize);
        c.coverSize = Json.i(o, "coverSize", c.coverSize);
        c.anim = Json.i(o, "anim", c.anim);
        c.transitionMs = Json.i(o, "transitionMs", c.transitionMs);
        c.theme = Json.str(o, "theme", c.theme);
        c.secondarySize = Json.i(o, "secondarySize", c.secondarySize);
        c.lineGap = Json.i(o, "lineGap", c.lineGap);
        c.beforeLines = Json.i(o, "beforeLines", c.beforeLines);
        c.afterLines = Json.i(o, "afterLines", c.afterLines);
        c.titleSize = Json.i(o, "titleSize", c.titleSize);
        c.artistSize = Json.i(o, "artistSize", c.artistSize);
        c.coverGap = Json.i(o, "coverGap", c.coverGap);
        c.coverRadius = Json.i(o, "coverRadius", c.coverRadius);
        int cv = Json.i(o, "cfgVer", 0);
        if (cv >= 1) {
            c.color = Json.l(o, "color", c.color);
            c.previousColor = Json.l(o, "previousColor", c.previousColor);
            c.nextColor = Json.l(o, "nextColor", c.nextColor);
            c.nightColor = Json.l(o, "nightColor", c.nightColor);
            c.nightPrevColor = Json.l(o, "nightPrevColor", c.nightPrevColor);
            c.nightNextColor = Json.l(o, "nightNextColor", c.nightNextColor);
            c.strokeWidth = Json.i(o, "strokeWidth", c.strokeWidth);
            c.strokeColor = Json.l(o, "strokeColor", c.strokeColor);
        }
        if (cv < 2 && c.coverSize <= 0) {
            c.coverSize = 200;
        }
        c.backgroundColor = Json.l(o, "backgroundColor", c.backgroundColor);
        c.backgroundRadius = Json.i(o, "backgroundRadius", c.backgroundRadius);
        c.backgroundPaddingTop = Json.i(o, "backgroundPaddingTop", c.backgroundPaddingTop);
        c.backgroundPaddingBottom = Json.i(o, "backgroundPaddingBottom", c.backgroundPaddingBottom);
        int pw = Json.i(o, "progressWidth", 0);
        if (o.has("showProgress")) {
            c.showProgress = o.optBoolean("showProgress", true);
            c.progressWidth = clamp(pw, 0, 1920);
        } else {
            c.showProgress = pw > 0;
            c.progressWidth = 0;
        }
        c.progressGap = Json.i(o, "progressGap", c.progressGap);
        c.lyricOffsetMs = clampOffset(Json.i(o, "lyricOffsetMs", c.lyricOffsetMs));
        lyricOffsetMs = c.lyricOffsetMs;
    }

    public static String gdToJson(Gd g) {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("ttlSec", g.ttlSec);
            o.put("x", g.x);
            o.put("y", g.y);
            o.put("w", g.w);
            o.put("h", g.h);
            o.put("geomKnown", g.geomKnown);
            o.put("gx", g.gx);
            o.put("gy", g.gy);
            o.put("gw", g.gw);
            o.put("gh", g.gh);
            o.put("lightsOnTop", g.lightsOnTop);
            o.put("bgColor", g.bgColor);
            o.put("bgRadius", g.bgRadius);
            o.put("padTop", g.padTop);
            o.put("padBottom", g.padBottom);
            o.put("showLights", g.showLights);
            o.put("showLanes", g.showLanes);
            o.put("showAlerts", g.showAlerts);
            o.put("lanesTtl", g.lanesTtl);
            o.put("lightsTtl", g.lightsTtl);
            o.put("alertsTtl", g.alertsTtl);
            o.put("theme", g.theme);
            o.put("opacity", g.opacity);
            o.put("ugx", g.ugx);
            o.put("ugy", g.ugy);
            o.put("ugw", g.ugw);
            o.put("ugh", g.ugh);
            return o.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    public static void gdFromJson(String raw, Gd g) {
        org.json.JSONObject o = Json.parse(raw);
        if (o == null || g == null) return;
        g.ttlSec = Json.i(o, "ttlSec", g.ttlSec);
        g.x = Json.i(o, "x", g.x);
        g.y = Json.i(o, "y", g.y);
        g.w = Json.i(o, "w", g.w);
        g.h = Json.i(o, "h", g.h);
        g.geomKnown = Json.b(o, "geomKnown", g.geomKnown);
        g.gx = Json.i(o, "gx", g.gx);
        g.gy = Json.i(o, "gy", g.gy);
        g.gw = Json.i(o, "gw", g.gw);
        g.gh = Json.i(o, "gh", g.gh);
        g.lightsOnTop = Json.b(o, "lightsOnTop", g.lightsOnTop);
        g.bgColor = Json.l(o, "bgColor", g.bgColor);
        g.bgRadius = Json.i(o, "bgRadius", g.bgRadius);
        g.padTop = Json.i(o, "padTop", g.padTop);
        g.padBottom = Json.i(o, "padBottom", g.padBottom);
        g.showLights = Json.b(o, "showLights", g.showLights);
        g.showLanes = Json.b(o, "showLanes", g.showLanes);
        g.showAlerts = Json.b(o, "showAlerts", g.showAlerts);
        g.lanesTtl = Json.i(o, "lanesTtl", g.lanesTtl);
        g.lightsTtl = Json.i(o, "lightsTtl", g.lightsTtl);
        g.alertsTtl = Json.i(o, "alertsTtl", g.alertsTtl);
        g.theme = Json.str(o, "theme", g.theme);
        g.opacity = Json.i(o, "opacity", g.opacity);
        g.ugx = Json.i(o, "ugx", g.ugx);
        g.ugy = Json.i(o, "ugy", g.ugy);
        g.ugw = Json.i(o, "ugw", g.ugw);
        g.ugh = Json.i(o, "ugh", g.ugh);
    }

    public static String navConfigSet(Gd g) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (g.geomKnown) {
            sb.append("\"x\":").append(g.x);
            sb.append(",\"y\":").append(g.y);
            sb.append(",\"width\":").append(g.w);
            sb.append(",\"height\":").append(g.h);
            sb.append(",\"opacity\":").append(clamp(g.opacity, 0, 255));
            first = false;
        }

        if (g.gx >= 0 && g.gy >= 0) {
            sb.append(first ? "" : ",");
            sb.append("\"guidance_x\":").append(g.gx);
            sb.append(",\"guidance_y\":").append(g.gy);
            first = false;
        }
        if (g.gw > 0 && g.gh > 0) {
            sb.append(first ? "" : ",");
            sb.append("\"guidance_width\":").append(g.gw);
            sb.append(",\"guidance_height\":").append(g.gh);
            first = false;
        }
        sb.append(first ? "" : ",")
                .append("\"guidance_background_color\":")
                .append('"').append(hex(g.bgColor)).append('"');
        sb.append(",\"guidance_background_radius\":").append(g.bgRadius);
        sb.append(",\"guidance_padding_top\":").append(g.padTop);
        sb.append(",\"guidance_padding_bottom\":").append(g.padBottom);
        sb.append(",\"show_lanes\":").append(g.showLanes ? "true" : "false");

        sb.append(",\"lanes_ttl_ms\":").append(clamp(g.lanesTtl * 1000, 1000, 60000));
        sb.append(",\"show_lights\":").append(g.showLights ? "true" : "false");
        sb.append(",\"lights_ttl_ms\":").append(clamp(g.lightsTtl * 1000, 1000, 15000));
        sb.append(",\"show_alerts\":").append(g.showAlerts ? "true" : "false");
        sb.append(",\"alerts_ttl_ms\":").append(clamp(g.alertsTtl * 1000, 1000, 60000));
        sb.append(",\"lights_on_top\":").append(g.lightsOnTop ? "true" : "false");
        sb.append(",\"theme\":\"").append(g.theme).append('"');
        sb.append(",\"ttl_ms\":").append(clamp(g.ttlSec * 1000, 1000, 120000));
        return sb.toString();
    }

    private static long clamp(long v, long lo, long hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static String readback(String raw) {
        try {
            org.json.JSONObject o = Json.parse(raw);
            if (o == null) return null;
            StringBuilder sb = new StringBuilder();
            org.json.JSONObject st = Json.o(o, "style");
            if (st != null) {
                sb.append("行数 ").append(Json.i(st, "lyric_lines", 0));
                sb.append(" · 字号 ").append(Json.i(st, "font_size", 0));
                sb.append(" · 封面 ").append(Json.i(st, "cover_size", 0));
                sb.append(" · 布局 ").append(Json.i(st, "music_layout", 0));
                sb.append('\n');
                sb.append("对齐 ").append(Json.str(st, "alignment", "?"));
                sb.append(" · 动画 ").append(Json.b(st, "lyric_animation", false));
            }
            org.json.JSONObject g = Json.o(o, "guidance");
            if (g != null) {
                sb.append("灯 ").append(Json.b(g, "show_lights", false));
                sb.append(" · 车道 ").append(Json.b(g, "show_lanes", false));
                sb.append(" · 警示 ").append(Json.b(g, "show_alerts", false));
            }
            sb.append("\n窗口可见 ").append(Json.b(o, "effective_visible", false));
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
