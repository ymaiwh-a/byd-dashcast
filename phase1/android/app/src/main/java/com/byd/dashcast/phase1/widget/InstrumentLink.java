package com.byd.dashcast.phase1.widget;

import com.byd.dashcast.phase1.ncm.LogHub;
import com.byd.dashcast.phase1.ncm.Net;
import com.byd.dashcast.phase1.ncm.TcpConn;
import com.byd.dashcast.phase1.ncm.Wire;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public final class InstrumentLink {

    public static final int MODE_NEWLINE = 0;
    public static final int MODE_JSON = 1;
    public static final int MODE_EOF = 2;

    public static final String NO_PLANE =
            "用户态数据面未就绪：请先在「仪表服务」页选「连接仪表」（App 直连 CDC-NCM，不经内核 usb0）";

    public static final class Reply {
        public String raw;
        public JSONObject json;
        public long ms;
        public int bytes;
        public boolean parsed;
        public String code;

        public boolean okSuccess() {
            boolean b = Json.b(json, "ok", false);
            String r = Json.str(json, "result", null);
            return b && (r == null || (!"false".equalsIgnoreCase(r) && !"error".equalsIgnoreCase(r)));
        }
    }

    public static final class LinkException extends Exception {
        public final String code;

        public LinkException(String code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    private static final class TcpInput extends java.io.InputStream {
        private final TcpConn conn;
        private final int timeoutMs;
        TcpInput(TcpConn c, int t) { conn = c; timeoutMs = t; }
        public int read() throws java.io.IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : (one[0] & 0xFF);
        }
        public int read(byte[] b, int off, int len) throws java.io.IOException {
            int n = conn.read(b, off, len, timeoutMs);
            if (n == 0) throw new java.io.InterruptedIOException("read timeout " + timeoutMs + "ms");
            return n;
        }
    }

    private final String host;
    private final int port;
    private final Object lock = new Object();

    public InstrumentLink(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Reply request(String requestJson) throws Exception {
        return request(requestJson, Prefs.connectMs(), Prefs.readMs());
    }

    public Reply request(String requestJson, int connectMs, int readMs) throws Exception {
        Reply r;
        synchronized (lock) {
            r = exchange(requestJson, connectMs, readMs);
        }
        return r;
    }

    public Reply uploadRequest(String headerJson, byte[] body, int connectMs, int readMs) throws Exception {
        Reply r;
        synchronized (lock) {
            r = uploadExchange(headerJson, body, connectMs, readMs);
        }
        return r;
    }

    public Reply downloadRequest(String headerJson, int connectMs, int readMs, File sink) throws Exception {
        Reply r;
        synchronized (lock) {
            r = downloadExchange(headerJson, connectMs, readMs, sink);
        }
        return r;
    }

    private Reply uploadExchange(String headerJson, byte[] body, int connectMs, int readMs) throws Exception {
        long t0 = System.currentTimeMillis();
        TcpConn s = null;
        boolean linked = false;
        try {
            Net net = WidgetNet.net();
            if (net == null) {
                throw new LinkException("LOCAL_NO_NETWORK", NO_PLANE);
            }
            s = net.connect(Wire.ip(host), port, connectMs);
            if (!s.isEstablished()) throw new java.io.IOException("handshake timeout " + connectMs + "ms");
            linked = true;
            int uploadWriteMs = body == null ? 8000
                    : (int) Math.min(60000, Math.max(15000, (long) body.length / 200 + 4000));
            byte[] wb = (headerJson + "\n").getBytes("UTF-8");
            if (s.send(wb, wb.length, uploadWriteMs) < 0) throw new java.io.IOException("send_failed");
            InputStream is = new TcpInput(s, readMs);
            String readyRaw = readFrame(is);
            Reply ready = new Reply();
            ready.raw = readyRaw;
            ready.json = Json.parse(readyRaw);
            if (ready.json != null) {
                boolean readyOk = Json.b(ready.json, "ok", false);
                boolean readyFlag = Json.b(ready.json, "ready", true);
                if (!readyOk || !readyFlag) {
                    String rc = Json.str(ready.json, "result", null);
                    if (rc == null || rc.isEmpty()) rc = Json.str(ready.json, "error", null);
                    if (rc == null || rc.isEmpty()) rc = "UPLOAD_NOT_READY";
                    throw new LinkException(rc, "上传头被拒或未就绪：" + LogHub.brief(readyRaw));
                }
            }
            if (body != null && body.length > 0) {
                if (s.send(body, body.length, uploadWriteMs) < 0) throw new java.io.IOException("send_failed");
            }
            String raw = readFrame(is);
            Reply rep = new Reply();
            rep.raw = raw;
            rep.bytes = raw == null ? 0 : raw.getBytes("UTF-8").length;
            rep.ms = System.currentTimeMillis() - t0;
            if (raw == null || raw.trim().isEmpty()) {
                rep.code = "LOCAL_EMPTY_REPLY";
                throw new LinkException(rep.code, "对方没有返回任何字节（端口 " + port + "）");
            }
            rep.json = Json.parse(raw);
            if (rep.json == null) {
                rep.code = "LOCAL_BAD_JSON";
                rep.parsed = false;
                return rep;
            }
            rep.parsed = true;
            String res = Json.str(rep.json, "result", null);
            boolean ok = Json.b(rep.json, "ok", false);
            if (res != null && !res.isEmpty() && !"ok".equalsIgnoreCase(res)) {
                rep.code = res;
            } else if (!ok) {
                String err = Json.str(rep.json, "error", null);
                rep.code = err != null ? err : "unknown";
            } else if (res != null) {
                rep.code = res;
            } else {
                rep.code = "ok";
            }
            return rep;
        } catch (LinkException le) {
            throw le;
        } catch (java.io.InterruptedIOException t) {
            throw new LinkException("LOCAL_TIMEOUT", "超时: " + String.valueOf(t.getMessage()));
        } catch (java.io.IOException ioe) {
            String m = String.valueOf(ioe.getMessage());
            if (!linked) throw new LinkException("connect_failed", "连不上 " + host + ":" + port + "（" + m + "）");
            throw new LinkException("LOCAL_IO", "IO 异常: " + m);
        } catch (Throwable t) {
            CrashHandler.record(t);
            throw new LinkException("LOCAL_IO", CrashHandler.describe(t));
        } finally {
            if (s != null) {
                try {
                    s.close(1000);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private Reply downloadExchange(String headerJson, int connectMs, int readMs, File sink) throws Exception {
        long t0 = System.currentTimeMillis();
        TcpConn s = null;
        boolean linked = false;
        try {
            Net net = WidgetNet.net();
            if (net == null) {
                throw new LinkException("LOCAL_NO_NETWORK", NO_PLANE);
            }
            s = net.connect(Wire.ip(host), port, connectMs);
            if (!s.isEstablished()) throw new java.io.IOException("handshake timeout " + connectMs + "ms");
            linked = true;
            byte[] wb = (headerJson + "\n").getBytes("UTF-8");
            if (s.send(wb, wb.length, readMs) < 0) throw new java.io.IOException("send_failed");
            InputStream is = new TcpInput(s, readMs);
            String raw = readFrame(is);
            Reply rep = new Reply();
            rep.raw = raw;
            rep.bytes = raw == null ? 0 : raw.getBytes("UTF-8").length;
            rep.ms = System.currentTimeMillis() - t0;
            if (raw == null || raw.trim().isEmpty()) {
                rep.code = "LOCAL_EMPTY_REPLY";
                throw new LinkException(rep.code, "对方没有返回任何字节（端口 " + port + "）");
            }
            rep.json = Json.parse(raw);
            if (rep.json == null) {
                rep.code = "LOCAL_BAD_JSON";
                rep.parsed = false;
                return rep;
            }
            rep.parsed = true;
            rep.code = "ok";
            long size = Json.l(rep.json, "size", 0L);
            if (size > 0 && sink != null) {
                FileOutputStream fos = new FileOutputStream(sink);
                try {
                    byte[] buf = new byte[16384];
                    long got = 0;
                    while (got < size) {
                        int need = (int) Math.min(buf.length, size - got);
                        int n = is.read(buf, 0, need);
                        if (n < 0) break;
                        fos.write(buf, 0, n);
                        got += n;
                    }
                } finally {
                    try { fos.close(); } catch (Throwable ignored) { }
                }
                rep.bytes = (int) Math.min(size, Integer.MAX_VALUE);
            }
            return rep;
        } catch (LinkException le) {
            throw le;
        } catch (java.io.InterruptedIOException t) {
            throw new LinkException("LOCAL_TIMEOUT", "超时: " + String.valueOf(t.getMessage()));
        } catch (java.io.IOException ioe) {
            String m = String.valueOf(ioe.getMessage());
            if (!linked) throw new LinkException("connect_failed", "连不上 " + host + ":" + port + "（" + m + "）");
            throw new LinkException("LOCAL_IO", "IO 异常: " + m);
        } catch (Throwable t) {
            CrashHandler.record(t);
            throw new LinkException("LOCAL_IO", CrashHandler.describe(t));
        } finally {
            if (s != null) {
                try {
                    s.close(1000);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private Reply exchange(String requestJson, int connectMs, int readMs) throws Exception {
        long t0 = System.currentTimeMillis();
        TcpConn s = null;
        boolean linked = false;
        try {
            Net net = WidgetNet.net();
            if (net == null) {
                throw new LinkException("LOCAL_NO_NETWORK", NO_PLANE);
            }
            s = net.connect(Wire.ip(host), port, connectMs);
            if (!s.isEstablished()) throw new java.io.IOException("handshake timeout " + connectMs + "ms");
            linked = true;
            InputStream is = new TcpInput(s, readMs);
            byte[] wb = (requestJson + "\n").getBytes("UTF-8");
            if (s.send(wb, wb.length, readMs) < 0) throw new java.io.IOException("send_failed");

            String raw;
            try {
                raw = readFrame(is);
            } catch (java.io.InterruptedIOException timeout) {
                throw new LinkException("LOCAL_TIMEOUT", "读取超时 " + readMs + "ms（可到诊断页加大超时或切分帧模式）");
            }

            Reply rep = new Reply();
            rep.raw = raw;
            rep.bytes = raw == null ? 0 : raw.getBytes("UTF-8").length;
            rep.ms = System.currentTimeMillis() - t0;
            if (raw == null || raw.trim().isEmpty()) {
                rep.code = "LOCAL_EMPTY_REPLY";
                throw new LinkException(rep.code, "对方没有返回任何字节（端口 " + port + "）");
            }
            rep.json = Json.parse(raw);
            if (rep.json == null) {
                rep.code = "LOCAL_BAD_JSON";
                rep.parsed = false;
                return rep;
            }
            rep.parsed = true;
            String res = Json.str(rep.json, "result", null);
            boolean ok = Json.b(rep.json, "ok", false);
            if (res != null && !res.isEmpty() && (res.equals("ok") == false)) {
                rep.code = res;
            } else if (!ok) {
                String err = Json.str(rep.json, "error", null);
                rep.code = err != null ? err : "unknown";
            } else if (res != null) {
                rep.code = res;
            } else {
                rep.code = "ok";
            }
            return rep;
        } catch (LinkException le) {
            throw le;
        } catch (java.io.InterruptedIOException t) {
            throw new LinkException("LOCAL_TIMEOUT", "超时: " + String.valueOf(t.getMessage()));
        } catch (java.io.IOException ioe) {
            String m = String.valueOf(ioe.getMessage());
            if (!linked) throw new LinkException("connect_failed", "连不上 " + host + ":" + port + "（" + m + "）");
            throw new LinkException("LOCAL_IO", "IO 异常: " + m);
        } catch (Throwable t) {
            CrashHandler.record(t);
            throw new LinkException("LOCAL_IO", CrashHandler.describe(t));
        } finally {
            if (s != null) {
                try {
                    s.close(1000);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static String readFrame(InputStream is) throws java.io.IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        boolean started = false;
        while (true) {
            int c = is.read();
            if (c < 0) break;
            buf.write(c);
            char ch = (char) c;
            if (!started) {
                if (ch == '{' || ch == '[' || Character.isWhitespace(ch)) {
                    if (!Character.isWhitespace(ch)) started = true;
                } else {
                }
            }
            if (started) {
                if (inStr) {
                    if (esc) esc = false;
                    else if (ch == '\\') esc = true;
                    else if (ch == '"') inStr = false;
                } else {
                    if (ch == '"') inStr = true;
                    else if (ch == '{' || ch == '[') depth++;
                    else if (ch == '}' || ch == ']') depth--;
                }
            }
            if (ch == '\n') break;
            if (started && depth == 0 && !inStr) {
                String s = buf.toString("UTF-8").trim();
                if (s.startsWith("{") && s.endsWith("}")) break;
                if (s.startsWith("[") && s.endsWith("]")) break;
            }
            if (buf.size() > 4 * 1024 * 1024) break;
        }
        String out = null;
        try {
            out = buf.toString("UTF-8");
        } catch (Throwable ignored) {
        }
        if (out != null) out = out.trim();
        if (out != null && out.isEmpty()) return "";
        return out;
    }

    public static String fmt(long ms) {
        return String.format(Locale.US, "%dms", ms);
    }
}
