package com.byd.dashcast.phase1.widget;

import android.os.Handler;
import android.os.Looper;

import com.byd.dashcast.phase1.ncm.LogHub;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class Tasks {

    private static final ExecutorService IO = Executors.newFixedThreadPool(4, new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "widget-io");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Tasks() {
    }

    public static void io(Runnable r) {
        try {
            IO.execute(r);
        } catch (Throwable t) {
            LogHub.throwable("tasks", "rejected", t);
            CrashHandler.record(t);
        }
    }

    public static void main(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            MAIN.post(r);
        }
    }

    public static <T> void chain(Body<T> body, Then<T> then) {
        io(new Runnable() {
            public void run() {
                Result<T> res;
                try {
                    res = Result.ok(body.run());
                } catch (Throwable t) {
                    res = Result.fail(t);
                }
                final Result<T> f = res;
                main(new Runnable() {
                    public void run() {
                        try {
                            then.apply(f);
                        } catch (Throwable t) {
                            LogHub.throwable("tasks", "结果回调异常", t);
                            CrashHandler.record(t);
                        }
                    }
                });
            }
        });
    }

    public interface Body<T> {
        T run() throws Exception;
    }

    public interface Then<T> {
        void apply(Result<T> r);
    }

    public static final class Result<T> {
        public final T value;
        public final Throwable error;

        private Result(T v, Throwable e) {
            value = v;
            error = e;
        }

        public static <T> Result<T> ok(T v) {
            return new Result<T>(v, null);
        }

        public static <T> Result<T> fail(Throwable e) {
            return new Result<T>(null, e);
        }

        public boolean ok() {
            return error == null;
        }
    }
}
