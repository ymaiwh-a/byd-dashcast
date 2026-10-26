package com.byd.dashcast.phase1.widget;

import com.byd.dashcast.phase1.ncm.LogHub;

public final class Cluster {

    private static volatile Cluster INSTANCE;

    private InstrumentLink hmiLink;
    private InstrumentLink navLink;
    private InstrumentLink musicLink;
    private String host;

    private Cluster() {
        host = Prefs.host();
        hmiLink = new InstrumentLink(host, WidgetProtocol.HMI_PORT);
        navLink = new InstrumentLink(host, WidgetProtocol.NAV_PORT);
        musicLink = new InstrumentLink(host, WidgetProtocol.MUSIC_PORT);
    }

    public static Cluster get() {
        if (INSTANCE == null) {
            synchronized (Cluster.class) {
                if (INSTANCE == null) INSTANCE = new Cluster();
            }
        }
        return INSTANCE;
    }

    public String host() {
        return host;
    }

    public boolean ready() {
        return WidgetNet.net() != null;
    }

    private void ensureLinked() throws InstrumentLink.LinkException {
        if (!ready()) throw new InstrumentLink.LinkException("LOCAL_NO_NETWORK", InstrumentLink.NO_PLANE);
    }

    public InstrumentLink.Reply hmi(String op, String body) throws Exception {
        ensureLinked();
        InstrumentLink l = hmiLink;
        String req = Proto.overlayOp(op, body);
        if (LogHub.oncePer("tx_31770_" + op, 120000)) LogHub.d("tx", "31770 " + req);
        InstrumentLink.Reply r = l.request(req, Prefs.connectMs(), Math.max(Prefs.readMs(), 4000));
        logReply("31770", op, r);
        return r;
    }

    public InstrumentLink.Reply overlay(String which, String op, String body) throws Exception {
        ensureLinked();
        InstrumentLink l = "music".equals(which) ? musicLink : navLink;
        int port = "music".equals(which) ? WidgetProtocol.MUSIC_PORT : WidgetProtocol.NAV_PORT;
        String req = Proto.overlayOp(op, body);
        if (LogHub.oncePer("tx_" + port + "_" + op, 120000)) LogHub.d("tx", port + " " + brief(req));
        InstrumentLink.Reply r = l.request(req, Prefs.connectMs(), Math.max(Prefs.readMs(), 4000));
        logReply(String.valueOf(port), op, r);
        return r;
    }

    private void logReply(String port, String op, InstrumentLink.Reply r) {
        if (LogHub.oncePer("rx_" + port + "_" + op, 120000))
            LogHub.d("rx", port + " " + op + " (" + r.ms + "ms, " + r.bytes + "B) " + trunc(r.raw, 400));
    }

    private static String trunc(String s, int n) {
        if (s == null) return "(null)";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String brief(String s) {
        if (s == null) return "(null)";
        int k = s.indexOf("\"lyrics\":[");
        if (k >= 0) {
            return s.substring(0, k) + "…歌词内容已省略(共" + s.length() + "B"
                    + (s.contains("cover_rgba") ? " 含封面" : "") + ")";
        }
        if (s.length() <= 400) return s;
        return s.substring(0, 400) + "…(共" + s.length() + "B"
                + (s.contains("cover_rgba") ? " 含封面" : "") + ")";
    }
}
