package com.byd.dashcast.phase1.widget;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "crash";
    private static final String DIR = "crash";

    private static volatile CrashHandler INSTANCE;
    private static Thread.UncaughtExceptionHandler sPrevious;
    private static Context sCtx;
    private static volatile byte[] RESERVE;

    private CrashHandler(Context c) {
        sCtx = c.getApplicationContext();
    }

    public static synchronized void install(Context c) {
        if (INSTANCE != null) return;
        INSTANCE = new CrashHandler(c.getApplicationContext());
        sPrevious = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(INSTANCE);
        try {
            RESERVE = new byte[256 * 1024];
        } catch (Throwable ignored) {
        }
        sweepOld(c);
    }

    public static void record(Throwable t) {
        if (t == null) return;
        try {
            if (sCtx == null) return;
            write(sCtx, t, false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        RESERVE = null;
        try {
            System.gc();
        } catch (Throwable ignored) {
        }
        try {
            write(sCtx, ex, true);
        } catch (Throwable inner) {
            Log.e(TAG, "crash log write failed", inner);
        }
        try {
            if (sPrevious != null) sPrevious.uncaughtException(thread, ex);
        } catch (Throwable ignored) {
        }
    }

    private static File write(Context c, Throwable t, boolean fatal) throws Exception {
        File dir = new File(c.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new java.io.IOException("cannot mkdir " + dir);
        }
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File f = new File(dir, (fatal ? "fatal-" : "caught-") + ts + ".log");
        PrintWriter pw = new PrintWriter(
                new java.io.OutputStreamWriter(new FileOutputStream(f, false), "UTF-8"));
        pw.println("=== " + (fatal ? "FATAL" : "CAUGHT") + " ===");
        pw.println("time      : " + new Date());
        pw.println("app       : " + appVer(c));
        pw.println("device    : " + Build.MANUFACTURER + " " + Build.MODEL + " / " + Build.DEVICE);
        pw.println("sdk       : " + Build.VERSION.SDK_INT + " rel=" + Build.VERSION.RELEASE);
        pw.println("abi       : " + Arrays.toString(Build.SUPPORTED_ABIS));
        try {
            pw.println("display   : " + Build.DISPLAY);
        } catch (Throwable ignored) {
        }
        pw.println("thread    : " + Thread.currentThread().getName());
        pw.println("reason    : " + describe(t));
        pw.println();
        t.printStackTrace(pw);
        pw.flush();
        pw.close();
        sweepOld(c);
        return f;
    }

    private static volatile String sAppVer;

    private static String appVer(Context c) {
        if (sAppVer == null) {
            String v = "unknown";
            try {
                android.content.pm.PackageInfo pi =
                        c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
                v = pi.packageName + " " + pi.versionCode + " (" + pi.versionName + ")";
            } catch (Throwable ignored) {
            }
            sAppVer = v;
        }
        return sAppVer;
    }

    public static String describe(Throwable t) {
        if (t == null) return "none";

        Throwable r = t;
        while (r instanceof java.lang.reflect.InvocationTargetException
                || r instanceof java.lang.reflect.UndeclaredThrowableException) {
            if (r.getCause() == null) break;
            r = r.getCause();
        }
        String msg = r.getMessage();
        String s = r.getClass().getName() + (msg == null ? "" : ": " + msg);
        if (r != t) s = s + "（由 " + t.getClass().getSimpleName() + " 包裹）";
        return s;
    }

    public static String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void sweepOld(Context c) {
        try {
            File dir = new File(c.getFilesDir(), DIR);
            File[] fs = dir.listFiles();
            if (fs == null || fs.length <= 30) return;
            Arrays.sort(fs, new java.util.Comparator<File>() {
                public int compare(File a, File b) {
                    return Long.compare(b.lastModified(), a.lastModified());
                }
            });
            for (int i = 30; i < fs.length; i++) {
                fs[i].delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
