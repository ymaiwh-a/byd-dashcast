package com.byd.dashcast.phase1.widget;

public final class Proto {

    private Proto() {
    }

    public static String overlayOp(String op, String bodyJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"op\":\"").append(op).append('"');
        if (bodyJson != null && !bodyJson.isEmpty()) {
            String b = bodyJson.trim();
            if (b.startsWith("{") && b.endsWith("}")) {
                b = b.substring(1, b.length() - 1);
            }
            if (!b.isEmpty()) sb.append(',').append(b);
        }
        sb.append('}');
        return sb.toString();
    }
}
