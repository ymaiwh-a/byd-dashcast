package com.byd.dashcast.phase1.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.byd.dashcast.phase1.ncm.LogHub;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LyricSource {

    private static final String UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/108.0.0.0 Mobile Safari/537.36";
    private static final int TIMEOUT_MS = 10_000;

    private static final Object CACHE_LOCK = new Object();

    private static final Map<String, String> MEM = new HashMap<>();

    private static final Set<String> FAILED = new HashSet<>();

    private static final Set<String> COVER_FAILED = new HashSet<>();

    private LyricSource() {
    }

    public static String fetch(Context c, String pkg, String trackId, String title, String artist, long durationMs) {
        if (trackId == null || trackId.isEmpty()) return null;
        if ((title == null || title.trim().isEmpty())
                && (artist == null || artist.trim().isEmpty())) return null;
        synchronized (CACHE_LOCK) {
            String hit = MEM.get(trackId);
            if (hit != null) return hit;
            if (FAILED.contains(trackId)) return null;
        }
        String disk = readDisk(c, trackId);
        if (disk != null) {
            synchronized (CACHE_LOCK) {
                MEM.put(trackId, disk);
            }
            LogHub.d("lyric", "歌词磁盘缓存命中 trackId=" + trackId + " len=" + disk.length());
            return disk;
        }
        String[] na = normalize(title, artist);
        na[0] = stripTail(na[0]);
        String p = pkg == null ? "" : pkg.toLowerCase();
        String[] order;
        if (p.contains("luna") || p.contains("qqmusic") || p.contains("tencent")) {
            order = new String[]{"qq", "kugou", "netease"};
        } else if (p.contains("kugou") || p.contains("kuwo")) {
            order = new String[]{"kugou", "qq", "netease"};
        } else {
            order = new String[]{"netease", "qq", "kugou"};
        }
        try {
            String src = null;
            String lrc = null;
            for (String s : order) {
                try {
                    String r = "qq".equals(s) ? viaQQ(na[0], na[1], durationMs)
                            : "kugou".equals(s) ? viaKugou(na[0], na[1], durationMs)
                            : viaNetease(na[0], na[1], durationMs);
                    if (r != null && !r.trim().isEmpty()) {
                        src = s;
                        lrc = r;
                        break;
                    }
                } catch (Throwable t) {
                    LogHub.d("lyric", s + "源失败: " + CrashHandler.describe(t));
                }
            }
            if (lrc == null) throw new Exception("所有源歌词为空");
            writeDisk(c, trackId, lrc);
            synchronized (CACHE_LOCK) {
                MEM.put(trackId, lrc);
            }
            LogHub.i("lyric", "在线歌词检索成功 trackId=" + trackId + " src=" + src
                    + " pkg=" + (p.isEmpty() ? "原厂" : pkg) + " kw=" + keyword(na[0], na[1])
                    + " len=" + lrc.length());
            return lrc;
        } catch (Throwable t) {
            synchronized (CACHE_LOCK) {
                FAILED.add(trackId);
            }
            LogHub.w("lyric", "在线歌词检索失败 trackId=" + trackId + ": " + CrashHandler.describe(t));
            return null;
        }
    }

    public static Bitmap fetchCover(Context c, String trackId, String title, String artist) {
        if (trackId == null || trackId.isEmpty()) return null;
        File f = new File(new File(c.getFilesDir(), "covers"), trackId + ".img");
        if (f.isFile()) {
            byte[] b = readBytes(f);
            if (b != null) {
                Bitmap bmp = BitmapFactory.decodeByteArray(b, 0, b.length);
                if (bmp != null) return bmp;
            }
        }
        synchronized (CACHE_LOCK) {
            if (COVER_FAILED.contains(trackId)) return null;
        }
        String[] na = normalize(title, artist);
        String t = stripTail(na[0]);
        byte[] img = null;
        String src = null;
        try {
            img = coverNetease(t, na[1]);
            src = "netease";
        } catch (Throwable e) {
            LogHub.d("lyric", "netease封面源失败: " + CrashHandler.describe(e));
        }
        if (img == null) {
            try {
                img = coverQQ(t, na[1]);
                src = "qq";
            } catch (Throwable e) {
                LogHub.d("lyric", "qq封面源失败: " + CrashHandler.describe(e));
            }
        }
        if (img == null) {
            synchronized (CACHE_LOCK) {
                COVER_FAILED.add(trackId);
            }
            LogHub.w("lyric", "在线封面获取失败 trackId=" + trackId + ": 所有源封面为空");
            return null;
        }
        Bitmap bmp = BitmapFactory.decodeByteArray(img, 0, img.length);
        if (bmp == null) {
            synchronized (CACHE_LOCK) {
                COVER_FAILED.add(trackId);
            }
            LogHub.w("lyric", "在线封面获取失败 trackId=" + trackId + ": 封面解码失败 src=" + src);
            return null;
        }
        File dir = f.getParentFile();
        if (!dir.isDirectory()) dir.mkdirs();
        writeBytes(f, img);
        LogHub.i("lyric", "在线封面获取成功 trackId=" + trackId + " src=" + src + " bytes=" + img.length);
        return bmp;
    }

    private static byte[] coverNetease(String title, String artist) throws Exception {
        JSONObject s = searchNetease(title, artist, 0).optJSONObject(0);
        JSONObject album = s.optJSONObject("al");
        String pic = album == null ? null : album.optString("picUrl", null);
        if (pic == null || pic.isEmpty()) throw new Exception("无封面地址");
        return httpGetBytes(pic + "?param=400x400", "https://music.163.com");
    }

    private static byte[] coverQQ(String title, String artist) throws Exception {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=5&w="
                + URLEncoder.encode(keyword(title, artist), "UTF-8");
        JSONObject j = new JSONObject(httpGet(url, "https://y.qq.com/"));
        JSONObject data = j.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        if (list == null || list.length() == 0) throw new Exception("搜索无结果");
        int best = -1;
        String albummid = "";
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String cand = stripTail(o.optString("songname"));
            if (!titleHit(title, cand)) continue;
            int sc = score(cand, o.optJSONArray("singer"),
                    o.optLong("interval") * 1000L, title, artist, 0);
            if (sc > best) {
                best = sc;
                albummid = o.optString("albummid", "");
            }
        }
        if (albummid == null || albummid.isEmpty()) throw new Exception("搜索无同名词曲");
        return httpGetBytes("https://y.gtimg.cn/music/photo_new/T002R300x300M000"
                + albummid + ".jpg", "https://y.qq.com/");
    }

    static String[] normalize(String title, String artist) {
        String t = title == null ? "" : title.trim();
        String a = artist == null ? "" : artist.trim();
        t = t.replaceAll("^[0-9]{1,4}[ .、_\\-]+", "");
        int dot = t.lastIndexOf('.');
        if (dot > 0 && t.length() - dot <= 5) t = t.substring(0, dot);
        int dash = t.indexOf(" - ");
        if (a.isEmpty() && dash > 0) {
            a = t.substring(0, dash).trim();
            t = t.substring(dash + 3).trim();
        }
        if (!a.isEmpty() && t.startsWith(a)) {
            String rest = t.substring(a.length());
            String cut = rest.replaceFirst("^\\s*[-－—]", "");
            if (!cut.equals(rest)) t = cut.trim();
        }
        return new String[]{t.trim(), a};
    }

    static String stripTail(String t) {
        if (t == null) return "";
        String s = t.trim();
        String p;
        do {
            p = s;
            s = s.replaceAll("\\s*[（(][^（()）]*[)）]$", "").trim();
        } while (!s.equals(p) && !s.isEmpty());
        return s;
    }

    private static boolean titleHit(String want, String cand) {
        if (want.isEmpty() || cand.isEmpty()) return want.isEmpty();
        return want.equalsIgnoreCase(cand) || cand.contains(want) || want.contains(cand);
    }

    private static JSONArray searchNetease(String title, String artist, long durationMs) throws Exception {
        String url = "http://music.163.com/api/cloudsearch/pc?s="
                + URLEncoder.encode(keyword(title, artist), "UTF-8")
                + "&type=1&limit=5";
        JSONObject j = new JSONObject(httpGet(url, "https://music.163.com"));
        JSONObject r = j.optJSONObject("result");
        JSONArray songs = r == null ? null : r.optJSONArray("songs");
        if (songs == null || songs.length() == 0) throw new Exception("搜索无结果");
        java.util.ArrayList<JSONObject> hits = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> scores = new java.util.ArrayList<>();
        for (int i = 0; i < songs.length(); i++) {
            JSONObject s = songs.getJSONObject(i);
            String cand = stripTail(s.optString("name"));
            if (!titleHit(title, cand)) continue;
            hits.add(s);
            scores.add(score(cand, s.optJSONArray("ar"),
                    s.optLong("dt"), title, artist, durationMs));
        }
        if (hits.isEmpty()) throw new Exception("搜索无同名词曲");
        JSONArray out = new JSONArray();
        boolean[] used = new boolean[hits.size()];
        for (int k = 0; k < hits.size(); k++) {
            int bi = -1;
            for (int m = 0; m < hits.size(); m++) {
                if (!used[m] && (bi < 0 || scores.get(m) > scores.get(bi))) bi = m;
            }
            used[bi] = true;
            out.put(hits.get(bi));
        }
        return out;
    }

    private static int score(String name, JSONArray artists, long durMs,
                             String wantTitle, String wantArtist, long durationMs) {
        String want = wantTitle == null ? "" : wantTitle.trim();
        String cand = stripTail(name == null ? "" : name.trim());
        int sc = 0;
        if (want.equalsIgnoreCase(cand)) sc += 4;
        else if (!want.isEmpty() && (cand.contains(want) || want.contains(cand))) sc += 1;
        String a = wantArtist == null ? "" : wantArtist.trim();
        if (!a.isEmpty() && artists != null) {
            for (int k = 0; k < artists.length(); k++) {
                try {
                    if (a.equals(artists.getJSONObject(k).optString("name"))) {
                        sc += 3;
                        break;
                    }
                } catch (Throwable t) {
                    CrashHandler.record(t);
                }
            }
        }
        if (durationMs > 0 && durMs > 0 && Math.abs(durMs - durationMs) <= 15000) sc += 2;
        return sc;
    }

    private static String viaNetease(String title, String artist, long durationMs) throws Exception {
        JSONArray cand = searchNetease(title, artist, durationMs);
        for (int i = 0; i < cand.length(); i++) {
            long id = cand.getJSONObject(i).optLong("id", 0);
            if (id <= 0) continue;
            JSONObject l = new JSONObject(httpGet(
                    "https://music.163.com/api/song/lyric?id=" + id + "&lv=1&kv=1&tv=-1",
                    "https://music.163.com"));
            JSONObject lrc = l.optJSONObject("lrc");
            String t = lrc == null ? null : lrc.optString("lyric", null);
            if (t != null && !t.trim().isEmpty()) return t;
        }
        return null;
    }

    private static String viaQQ(String title, String artist, long durationMs) throws Exception {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=5&w="
                + URLEncoder.encode(keyword(title, artist), "UTF-8");
        JSONObject j = new JSONObject(httpGet(url, "https://y.qq.com/"));
        JSONObject data = j.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        if (list == null || list.length() == 0) throw new Exception("搜索无结果");
        int best = -1;
        String mid = "";
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            String cand = stripTail(o.optString("songname"));
            if (!titleHit(title, cand)) continue;
            int sc = score(cand, o.optJSONArray("singer"),
                    o.optLong("interval") * 1000L, title, artist, durationMs);
            if (sc > best) {
                best = sc;
                mid = o.optString("songmid", "");
            }
        }
        if (mid.isEmpty()) throw new Exception("搜索无同名词曲");
        JSONObject l = new JSONObject(httpGet(
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=" + mid
                        + "&songtype=0&format=json&g_tk=5381&nobase64=1",
                "https://y.qq.com/portal/player.html"));
        return l.optString("lyric", null);
    }

    private static String viaKugou(String title, String artist, long durationMs) throws Exception {
        String url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&page=1&pagesize=5&keyword="
                + URLEncoder.encode(keyword(title, artist), "UTF-8");
        JSONObject j = new JSONObject(httpGet(url, "https://www.kugou.com/"));
        JSONObject data = j.optJSONObject("data");
        JSONArray info = data == null ? null : data.optJSONArray("info");
        if (info == null || info.length() == 0) throw new Exception("搜索无结果");
        int best = -1;
        String hash = "";
        long dur = 0;
        for (int i = 0; i < info.length(); i++) {
            JSONObject o = info.getJSONObject(i);
            String cand = stripTail(o.optString("songname"));
            if (!titleHit(title, cand)) continue;
            JSONArray ar = new JSONArray();
            JSONObject a = new JSONObject();
            a.put("name", o.optString("singername", ""));
            ar.put(a);
            int sc = score(cand, ar, o.optLong("duration") * 1000L, title, artist, durationMs);
            if (sc > best) {
                best = sc;
                hash = o.optString("hash", "");
                dur = o.optLong("duration") * 1000L;
            }
        }
        if (hash.isEmpty()) throw new Exception("搜索无同名词曲");
        JSONArray cand = new JSONObject(httpGet(
                "http://lyrics.kugou.com/search?ver=1&man=yes&client=mobi&duration=" + dur
                        + "&hash=" + hash + "&keyword=" + URLEncoder.encode(keyword(title, artist), "UTF-8"),
                "https://www.kugou.com/")).optJSONArray("candidates");
        if (cand == null || cand.length() == 0) throw new Exception("歌词候选为空");
        for (int i = 0; i < cand.length(); i++) {
            JSONObject o = cand.getJSONObject(i);
            String id = o.optString("id", "");
            String key = o.optString("accesskey", "");
            if (id.isEmpty() || key.isEmpty()) continue;
            String b64 = new JSONObject(httpGet("http://lyrics.kugou.com/download?ver=1&client=pc&id=" + id
                    + "&accesskey=" + key + "&fmt=lrc", "https://www.kugou.com/")).optString("content", "");
            if (b64.isEmpty()) continue;
            String lrc = new String(Base64.decode(b64, Base64.NO_WRAP), "UTF-8");
            if (!lrc.trim().isEmpty()) return lrc;
        }
        return null;
    }

    private static String keyword(String title, String artist) {
        String kw = title == null ? "" : title.trim();
        String a = artist == null ? "" : artist.trim();
        if (!a.isEmpty()) {
            kw = kw.isEmpty() ? a : kw + " " + a;
        }
        return kw;
    }

    private static String httpGet(String url, String referer) throws Exception {
        return new String(httpGetBytes(url, referer), "UTF-8");
    }

    private static byte[] httpGetBytes(String url, String referer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Referer", referer);
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            InputStream in = conn.getInputStream();
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            } finally {
                in.close();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static File cacheFile(Context c, String trackId) {
        File dir = new File(c.getFilesDir(), "lyrics2");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, trackId + ".lrc");
    }

    private static String readDisk(Context c, String trackId) {
        File f = cacheFile(c, trackId);
        if (!f.isFile()) return null;
        byte[] b = readBytes(f);
        if (b == null) return null;
        try {
            String s = new String(b, "UTF-8");
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readBytes(File f) {
        try {
            InputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeBytes(File f, byte[] b) {
        try {
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(b);
            } finally {
                out.close();
            }
        } catch (Throwable t) {
            LogHub.d("lyric", "缓存写入失败: " + CrashHandler.describe(t));
        }
    }

    private static void writeDisk(Context c, String trackId, String lrc) {
        try {
            writeBytes(cacheFile(c, trackId), lrc.getBytes("UTF-8"));
        } catch (Throwable t) {
            LogHub.d("lyric", "歌词磁盘缓存写入失败: " + CrashHandler.describe(t));
        }
    }
}
