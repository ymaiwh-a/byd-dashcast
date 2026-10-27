package com.byd.dashcast.phase1.widget;

import android.content.Intent;
import android.os.Bundle;

import com.byd.dashcast.phase1.ncm.LogHub;

import org.json.JSONObject;

public final class NavCollector {

    private NavCollector() {
    }

    public static final String WECAR_ACTION = "WECARNAVIAUTO_STANDARD_BROADCAST_SEND";
    public static final String AMAP_ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND";
    public static final String BAIDU_ACTION = "com.baidu.navi.naviinfo";

    public static NavState parse(Intent i) {
        if (i == null) return null;
        String action = i.getAction();
        if (action == null) return null;
        Bundle b = i.getExtras();
        if (b == null) return null;

        java.util.Map<String, Object> kv = new java.util.HashMap<>();
        JSONObject json = tryJsonPayload(b);
        if (json != null) {
            org.json.JSONArray names = json.names();
            if (names != null) {
                for (int k = 0; k < names.length(); k++) {
                    String key = names.optString(k);
                    if (key != null) kv.put(key, json.opt(key));
                }
            }
        }
        for (String key : b.keySet()) {
            Object v = b.get(key);
            if (v != null) kv.put(key, v);
        }

        return parseKv(action, kv);
    }

    public static NavState parseKv(String action, java.util.Map<String, Object> kv) {
        if (action == null || kv == null) return null;

        NavState s = new NavState();
        boolean wecar = WECAR_ACTION.equals(action);
        boolean amap = AMAP_ACTION.equals(action);

        if (amap) {
            long ktGate = firstLong(kv, "KEY_TYPE");
            if (isArrive(kv) || ktGate == 10006 || ktGate == 10010
                    || ktGate == 10016 || ktGate == 10017) {
                s.active = false;
                return s;
            }
            if (ktGate == 60073) {
                buildLights(kv, s);
                if (s.lightSeconds <= 0) return null;
                s.active = true;
                return s;
            }
            if (ktGate == 13012) {
                String dwGate = firstString(kv, "EXTRA_DRIVE_WAY");
                if (dwGate.isEmpty()) return null;
                s.lanes = parseDriveWay(dwGate);
                if (s.lanes.isEmpty()) return null;
                s.lanesRxMs = System.currentTimeMillis();
                if (LogHub.oncePer("navrx_lane", 30000))
                    LogHub.d("navrx", "车道线 RAW=" + (dwGate.length() > 300 ? dwGate.substring(0, 300) : dwGate)
                            + " → lanes=" + s.lanes);
                s.active = true;
                return s;
            }
            if (ktGate != 10001) return null;
        }

        s.currentRoad = firstString(kv, "CUR_ROAD_NAME", "curRoadName", "NEXT_ROAD_NAME",
                "road_name", "curRoad", "current_road",
                "next_road_name", "roadName", "EXTRA_ROAD_NAME", "nextRoadName");
        s.destination = firstString(kv, "endPOIName", "end_name", "dest_name", "destination",
                "endName", "dest", "poi_name", "EXTRA_DEST_NAME", "destName");
        s.distanceText = firstString(kv, "SEG_REMAIN_DIS_AUTO", "distance", "next_distance",
                "segment_remain_dis", "distance_text", "dis", "EXTRA_NEXT_DIST");
        s.eta = firstString(kv, "ETA_TEXT", "ROUTE_REMAIN_TIME_AUTO",
                "time", "arrive_time", "remain_time_desc");
        s.serviceArea = firstString(kv, "SAPA_NAME", "service_area");
        s.exitName = firstString(kv, "EXIT_NAME_INFO", "EXIT_DIRECTION_INFO", "exit_name");
        s.turn = mapTurn(kv);

        s.distanceM = firstLong(kv, "SEG_REMAIN_DIS", "segment_remain_dis", "distance_m", "seg_dis",
                "EXTRA_NEXT_DIST", "nextRoadDis");
        s.remainingM = firstLong(kv, "ROUTE_REMAIN_DIS", "route_remain_dis", "remaining_m",
                "route_dis", "EXTRA_REMAIN_DISTANCE");
        s.remainingS = firstLong(kv, "ROUTE_REMAIN_TIME", "route_remain_time", "remaining_s",
                "route_time", "EXTRA_REMAIN_TIME");
        s.speedKph = (int) firstLong(kv, "CUR_SPEED", "cur_speed", "speed_kph", "speed",
                "EXTRA_CURRENT_SPEED");
        s.limitKph = (int) firstLong(kv, "LIMITED_SPEED", "CAMERA_SPEED", "limit_speed", "limit_kph",
                "limit", "EXTRA_LIMIT_SPEED", "EXTRA_SPEED_LIMIT");

        buildLights(kv, s);

        String dw = firstString(kv, "EXTRA_DRIVE_WAY");
        if (!dw.isEmpty()) {
            s.lanes = parseDriveWay(dw);
            if (!s.lanes.isEmpty()) {
                s.lanesRxMs = System.currentTimeMillis();
                if (LogHub.oncePer("navrx_lane", 30000))
                    LogHub.d("navrx", "车道线 RAW=" + (dw.length() > 300 ? dw.substring(0, 300) : dw)
                            + " → lanes=" + s.lanes);
            }
        }

        boolean hasContent = !empty(s.currentRoad) || !empty(s.destination)
                || !empty(s.distanceText) || !empty(s.turn)
                || s.distanceM > 0 || s.remainingM > 0 || s.lightSeconds > 0
                || !empty(s.lanes);
        s.active = isNavActive(kv, wecar);
        if (!amap && !hasContent) {
            long kt = firstLong(kv, "KEY_TYPE");
            if (s.active || (kt != 0 && kt != 10001)) return null;
        }
        return s;
    }

    private static boolean isArrive(java.util.Map<String, Object> kv) {
        Object v = kv.get("ARRIVE_STATUS");
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof String) return "true".equalsIgnoreCase(((String) v).trim());
        return false;
    }

    private static void buildLights(java.util.Map<String, Object> kv, NavState s) {
        try {
            String ld = firstString(kv, "lightsData");
            if (!ld.isEmpty()) {
                org.json.JSONArray in = new org.json.JSONArray(ld);
                org.json.JSONArray out = new org.json.JSONArray();
                for (int i = 0; i < in.length() && out.length() < 4; i++) {
                    JSONObject it = in.optJSONObject(i);
                    if (it == null) continue;
                    int st = it.optInt("status", 0);
                    int cd = it.optInt("countdown", -1);
                    int dir = it.optInt("dir", -1);
                    int color;
                    if (st == 1) {
                        color = 1;
                    } else if (st == 4) {
                        color = 2;
                    } else {
                        continue;
                    }
                    JSONObject e = new JSONObject();
                    e.put("direction", dirName(dir));
                    e.put("color", color);
                    if (cd >= 0) e.put("seconds", Math.min(999, Math.max(0, cd)));
                    out.put(e);
                }
                s.lightsJson = out.toString();
                if (out.length() > 0) {
                    JSONObject f = out.optJSONObject(0);
                    s.lightColor = f.optInt("color", 0);
                    s.lightSeconds = f.optInt("seconds", 0);
                }
                return;
            }
            Object oRed = kv.get("redLightCountDownSeconds");
            Object oGreen = kv.get("greenLightLastSecond");
            Object oWait = kv.get("waitRound");
            Object oSt = kv.get("trafficLightStatus");

            long st = firstLong(kv, "trafficLightStatus");
            long secs = firstLong(kv, "redLightCountDownSeconds");
            int color;
            color = st == 1 ? 1 : (st == 4 ? 2 : 0);
            if (color > 0 && secs > 0) {
                JSONObject e = new JSONObject();
                e.put("direction", dirName((int) firstLong(kv, "dir")));
                e.put("color", color);
                e.put("seconds", Math.min(999, (int) secs));
                org.json.JSONArray out = new org.json.JSONArray();
                out.put(e);
                s.lightsJson = out.toString();
                s.lightSeconds = (int) secs;
                s.lightColor = color;
            }
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
    }

    private static String dirName(int dir) {
        if (dir == 1) return "left";
        if (dir == 2) return "right";
        if (dir == 4) return "straight";
        return "uturn";
    }

    private static String parseDriveWay(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!o.optBoolean("drive_way_enabled", true)) return "[]";
            org.json.JSONArray info = o.optJSONArray("drive_way_info");
            org.json.JSONArray out = new org.json.JSONArray();
            if (info != null) {
                for (int i = 0; i < info.length() && out.length() < 8; i++) {
                    JSONObject it = info.optJSONObject(i);
                    if (it == null) continue;
                    String dir = laneIconToken(it.optString("drive_way_lane_Back_icon", ""));
                    boolean advised = it.optBoolean("trafficLaneAdvised", false);
                    JSONObject lane = new JSONObject();
                    lane.put("directions", dir);
                    lane.put("active_directions", advised ? dir : "unknown");
                    lane.put("recommended", advised);
                    out.put(lane);
                }
            }
            return out.toString();
        } catch (Throwable t) {
            CrashHandler.record(t);
            return "";
        }
    }

    private static String laneIconToken(String rawIcon) {
        int icon;
        try {
            icon = Integer.parseInt(rawIcon.trim());
        } catch (Throwable t) {
            CrashHandler.record(t);
            return "unknown";
        }
        switch (icon) {
            case 0: return "straight";
            case 1: return "left";
            case 2: return "straight|left";
            case 3: return "right";
            case 4: return "straight|right";
            case 5: return "uturn";
            case 6: return "left|right";
            case 7: return "straight|left|right";
            case 8: return "uturn_right";
            case 9: return "straight|uturn";
            case 10: return "straight|uturn_right";
            case 11: return "left|uturn";
            case 12: return "right|uturn_right";
            case 13: return "straight|continue";
            case 14: return "left|uturn|continue";
            case 16: return "straight|left|uturn";
            case 17: return "right|uturn";
            case 18: return "left|uturn|right";
            case 19: return "straight|right|uturn";
            case 20: return "left|uturn_right";
            case 21: return "unknown";
            case 23: return "unknown";
            default: return "unknown";
        }
    }

    private static JSONObject tryJsonPayload(Bundle b) {
        for (String key : new String[]{"data", "json", "payload", "content"}) {
            Object o = b.get(key);
            if (o instanceof String && !((String) o).isEmpty()) {
                try {
                    return new JSONObject((String) o);
                } catch (Throwable t) {
                    CrashHandler.record(t);
                }
            }
        }
        return null;
    }

    private static boolean isNavActive(java.util.Map<String, Object> kv, boolean wecar) {
        Integer state = firstInt(kv, "EXTRA_STATE", "navi_state", "nav_state",
                "KEY_NAVI_STATE", "state", "mode");
        if (state != null) return state != 0 && state != 2;
        Boolean navi = firstBool(kv, "navigating", "is_navigating", "navi", "navigating_on");
        if (navi != null) return navi;
        Object r = kv.get(wecar ? "KEY_KEY_TYPE" : "KEY_TYPE");
        if (r == null) r = kv.get("key_type");
        if (r instanceof Integer) return ((Integer) r) != 0;
        return true;
    }

    private static String mapTurn(java.util.Map<String, Object> kv) {
        String[] keys = {"ICON", "EXTRA_TYPE", "NEW_ICON", "icon", "maneuver", "turn"};
        Object v = null;
        String src = null;
        for (String k : keys) {
            v = kv.get(k);
            if (v != null) { src = k; break; }
        }
        if (v == null) return "";
        Integer num = null;
        if (v instanceof Integer) num = (Integer) v;
        else if (v instanceof Long) num = ((Long) v).intValue();
        else if (v instanceof String) {
            String s = ((String) v).trim().toLowerCase();
            if (s.isEmpty() || "null".equals(s)) return "";
            if (s.matches("\\d+") && s.length() <= 9) num = Integer.parseInt(s);
            else if ("straight".equals(s)) return "";
            else return s;
        }
        if (num == null) return "";
        String name = WidgetData.amapIconName(num);
        if (LogHub.oncePer("navrx_turn_" + num, 30000))
            LogHub.i("navrx", "turn 原始 key=" + src + " value=" + num
                    + " → " + (name.isEmpty() ? "(无定义，不推)" : name));
        return name;
    }

    private static String firstString(java.util.Map<String, Object> kv, String... keys) {
        for (String k : keys) {
            Object v = kv.get(k);
            if (v instanceof String) {
                String s = (String) v;
                if (!s.isEmpty()) return s;
            } else if (v != null) {
                String s = String.valueOf(v);
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static long firstLong(java.util.Map<String, Object> kv, String... keys) {
        for (String k : keys) {
            Object v = kv.get(k);
            if (v instanceof Long) return (Long) v;
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof String) {
                try {
                    return Long.parseLong(((String) v).trim());
                } catch (Throwable t) {
                    CrashHandler.record(t);
                }
            }
        }
        return 0;
    }

    private static Integer firstInt(java.util.Map<String, Object> kv, String... keys) {
        for (String k : keys) {
            Object v = kv.get(k);
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof Long) return (int) (long) (Long) v;
            if (v instanceof String) {
                try {
                    return Integer.parseInt(((String) v).trim());
                } catch (Throwable t) {
                    CrashHandler.record(t);
                }
            }
        }
        return null;
    }

    private static Boolean firstBool(java.util.Map<String, Object> kv, String... keys) {
        for (String k : keys) {
            Object v = kv.get(k);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof Integer) return (Integer) v != 0;
        }
        return null;
    }

    private static boolean empty(String s) {
        return s == null || s.isEmpty();
    }
}
