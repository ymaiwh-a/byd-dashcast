package com.byd.dashcast.phase1.widget;

import org.json.JSONArray;
import org.json.JSONObject;

public final class WidgetData {

    private WidgetData() {
    }

    public static String amapIconName(int icon) {
        switch (icon) {
            case 2: return "left";
            case 3: return "right";
            case 4: return "slight_left";
            case 5: return "slight_right";
            case 6: return "sharp_left";
            case 7: return "sharp_right";
            case 8: return "uturn";
            case 9: return "straight";
            case 10: return "waypoint";
            case 11: return "roundabout";
            case 12: return "roundabout_ccw_exit_1";
            case 13: return "service_area";
            case 14: return "toll";
            case 15: return "destination";
            case 16: return "tunnel";
            case 17: return "roundabout";
            case 18: return "roundabout_cw_exit_1";
            case 19: return "uturn_right";
            case 20: return "continue";
            case 21: case 22: case 23: case 24:
            case 25: case 26: case 27: case 28: return "roundabout";
            default: return "straight";
        }
    }

    public static JSONObject navData(NavState s) {
        JSONObject o = new JSONObject();
        try {
            for (String k : FieldSpec.NAV_PROD) putNavField(o, k, s);
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        return o;
    }

    private static void putNavField(JSONObject o, String k, NavState s) throws org.json.JSONException {
        switch (k) {
            case "active": o.put("active", s.active); break;
            case "turn": if (s.turn != null && !s.turn.isEmpty()) o.put("turn", s.turn); break;
            case "distance_m": if (s.distanceM > 0) o.put("distance_m", s.distanceM); break;
            case "distance_text":
                if (s.distanceText != null && !s.distanceText.isEmpty()) o.put("distance_text", s.distanceText);
                break;
            case "current_road":
                if (s.currentRoad != null && !s.currentRoad.isEmpty()) o.put("current_road", s.currentRoad);
                break;
            case "destination":
                if (s.destination != null && !s.destination.isEmpty()) o.put("destination", cut(s.destination, 512));
                break;
            case "eta":
                if (s.eta != null && !s.eta.isEmpty()) o.put("eta", cut(s.eta, 64));
                break;
            case "show_next": o.put("show_next", s.showNext); break;
            case "show_alerts": o.put("show_alerts", s.showAlerts); break;
            case "show_limit": o.put("show_limit", s.showLimit); break;
            case "remaining_m": if (s.remainingM > 0) o.put("remaining_m", s.remainingM); break;
            case "remaining_s": if (s.remainingS > 0) o.put("remaining_s", s.remainingS); break;
            case "limit_kph": if (s.limitKph > 0) o.put("limit_kph", s.limitKph); break;
            case "ttl_ms": o.put("ttl_ms", s.ttlMs > 0 ? s.ttlMs : 20000L); break;
            case "lights":
                if (s.lightsJson != null && !s.lightsJson.isEmpty()) {
                    JSONArray arr = new JSONArray(s.lightsJson);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.optJSONObject(i);
                        if (e != null) e.put("color", invertLightColor(e.optInt("color", 0)));
                    }
                    o.put("lights", arr);
                } else if (s.active) {
                    o.put("lights", new JSONArray());
                }
                break;
            case "lanes":

                if (s.lanes != null && !s.lanes.isEmpty()) {
                    o.put("lanes", new org.json.JSONArray(s.lanes));
                } else if (s.active) {
                    o.put("lanes", new org.json.JSONArray());
                }
                break;
            default: break;
        }
    }

    public static JSONObject trackLoad(MusicState m, boolean withCover) {
        JSONObject o = new JSONObject();
        try {

            o.put("track_id", m.trackId == null || m.trackId.isEmpty() ? "cast_static" : m.trackId);
            if (m.title != null) o.put("title", m.title);
            if (m.artist != null) o.put("artist", m.artist);
            o.put("playing", m.playing);
            o.put("duration_ms", m.durationMs);
            long pos = m.positionMs + WidgetCfg.lyricOffsetMs;
            if (pos < 0) pos = 0;
            if (pos > m.durationMs) pos = m.durationMs;
            o.put("position_ms", pos);
            JSONArray arr = new JSONArray();
            if (m.lyricLines != null) {
                int n = Math.min(1024, m.lyricLines.length);
                for (int i = 0; i < n; i++) {
                    String v = m.lyricLines[i];
                    arr.put(v == null || v.trim().isEmpty() ? " " : cut(v, 256));
                }
            }
            if (arr.length() == 0) arr.put(" ");
            while (arr.length() < WidgetCfg.lyricLinesSent) arr.put(" ");
            o.put("lyrics", arr);
            int li = m.lyricTimes != null && m.lyricTimes.length > 0
                    ? MusicCollector.computeLineIndex(m.lyricTimes, m.positionMs) : m.lineIndex;
            if (li < -1 || li >= arr.length()) li = -1;
            o.put("line_index", li);

            if (withCover && m.coverBase64 != null && !m.coverBase64.isEmpty()) {
                String b64 = m.coverBase64;
                int len = b64.length();
                int pad = len >= 2 && b64.charAt(len - 2) == '=' ? 2
                        : (len >= 1 && b64.charAt(len - 1) == '=' ? 1 : 0);
                int bytes = len / 4 * 3 - pad;
                int side = (int) Math.sqrt(bytes / 4);
                if (side > 0 && side * side * 4 == bytes) {
                    o.put("cover_rgba", b64);
                    o.put("cover_width", side);
                    o.put("cover_height", side);
                }
            }
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        return o;
    }

    public static String trackLoadBody(MusicState m, boolean withCover) {
        return trackLoad(m, withCover).toString();
    }

    public static String trackSyncBody(String trackId, int lineIndex) {
        JSONObject o = new JSONObject();
        try {
            o.put("track_id", trackId);
            o.put("line_index", lineIndex);
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        return o.toString();
    }

    public static String navDataBody(NavState s) {
        return navData(s).toString();
    }

    private static String cut(String v, int max) {
        return v.length() <= max ? v : v.substring(0, max);
    }

    private static int invertLightColor(int c) {
        if (!Prefs.navLightInvert()) return c;
        return c == 1 ? 2 : (c == 2 ? 1 : c);
    }
}
