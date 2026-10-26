package com.byd.dashcast.phase1.widget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.byd.dashcast.phase1.ncm.LogHub;

public class NavReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if (i == null) return;

            NavState s = NavCollector.parse(i);
            if (s == null) {
                String act = String.valueOf(i.getAction());
                if (!act.equals(NULL_LOGGED.getAndSet(act)) && LogHub.oncePer("navrx_null", 60000)) {
                    LogHub.d("navrx", "→ 广播忽略（action=" + act
                            + "，KEY_TYPE=" + i.getIntExtra("KEY_TYPE", -1)
                            + "，EXTRA_STATE=" + i.getIntExtra("EXTRA_STATE", -1) + "）");
                }
                return;
            }
            NULL_LOGGED.set("");
            s.src = "broadcast";

            deliver(s);
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
    }

    public static void resetSession() {
        synchronized (MERGE) {
            MERGE.reset();
        }
    }

    public static void deliver(NavState s) {
        try {
            String src = s.src;
            synchronized (MERGE) {
                if (!s.active) {
                    MERGE.reset();
                }
                MERGE.mergeNonEmpty(s);
                MERGE.active = s.active;
                s = MERGE.copy();
            }

            String summary = "来源=" + src
                    + " 路名=" + s.currentRoad + " 目的地=" + s.destination
                    + " 距离=" + s.distanceText + " 转向=" + s.turn + " 限速=" + s.limitKph
                    + " 速度=" + s.speedKph + " 灯=" + s.lightSeconds + "/" + s.lightColor
                    + " 车道=" + s.lanes
                    + " active=" + s.active;
            String struct = "路名=" + s.currentRoad + " 目的地=" + s.destination
                    + " 转向=" + s.turn + " 灯色=" + s.lightColor
                    + " 车道=" + s.lanes + " active=" + s.active;
            if (!struct.equals(LAST_LOGGED.getAndSet(struct))
                    || LogHub.oncePer("navrx_summary", 30000)) {
                LogHub.i("navrx", "→ 解析成功 " + summary);
            }
            WidgetRelay.get().onNav(s);
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
    }

    private static final java.util.concurrent.atomic.AtomicReference<String> LAST_LOGGED =
            new java.util.concurrent.atomic.AtomicReference<>("");

    private static final java.util.concurrent.atomic.AtomicReference<String> NULL_LOGGED =
            new java.util.concurrent.atomic.AtomicReference<>("");

    private static final NavState MERGE = new NavState();
}
