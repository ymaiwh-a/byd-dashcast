package com.byd.dashcast.phase1.widget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

public final class Json {

    private Json() {
    }

    public static JSONObject parse(String s) {
        if (s == null || s.length() == 0) return null;
        try {
            String t = s.trim();
            if (!t.startsWith("{") && !t.startsWith("[")) return null;
            return new JSONObject(t);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String str(JSONObject o, String k, String def) {
        try {
            if (o == null || !o.has(k) || o.isNull(k)) return def;
            return o.getString(k);
        } catch (Throwable t) {
            return def;
        }
    }

    public static int i(JSONObject o, String k, int def) {
        try {
            if (o == null || !o.has(k) || o.isNull(k)) return def;
            return o.getInt(k);
        } catch (Throwable t) {
            return def;
        }
    }

    public static long l(JSONObject o, String k, long def) {
        try {
            if (o == null || !o.has(k) || o.isNull(k)) return def;
            return o.getLong(k);
        } catch (Throwable t) {
            return def;
        }
    }

    public static boolean b(JSONObject o, String k, boolean def) {
        try {
            if (o == null || !o.has(k) || o.isNull(k)) return def;
            Object v = o.get(k);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof Number) return ((Number) v).intValue() != 0;
            return "true".equalsIgnoreCase(String.valueOf(v));
        } catch (Throwable t) {
            return def;
        }
    }

    public static JSONObject o(JSONObject o, String k) {
        try {
            if (o == null || !o.has(k) || o.isNull(k)) return null;
            return o.getJSONObject(k);
        } catch (Throwable t) {
            return null;
        }
    }

    public static JSONArray a(JSONObject o, String k) {
        try {
            if (o == null || !o.has(k) || o.isNull(k)) return null;
            return o.getJSONArray(k);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String pretty(JSONObject o) {
        if (o == null) return "(null)";
        try {
            return o.toString(2);
        } catch (Throwable t) {
            return "(unprintable)";
        }
    }

    public static String flat(JSONObject o) {
        if (o == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            Object v;
            try {
                v = o.get(k);
            } catch (Throwable t) {
                v = "?";
            }
            sb.append(k).append(" = ").append(String.valueOf(v)).append('\n');
        }
        return sb.toString();
    }
}
