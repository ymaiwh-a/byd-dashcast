package com.byd.dashcast.phase1;

import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Process;

import com.byd.dashcast.phase1.ncm.LogHub;
import com.byd.dashcast.phase1.widget.CrashHandler;
import com.byd.dashcast.phase1.widget.NavCollector;
import com.byd.dashcast.phase1.widget.NavReceiver;
import com.byd.dashcast.phase1.widget.Prefs;
import com.byd.dashcast.phase1.widget.ProtocolClient;
import com.byd.dashcast.phase1.widget.WidgetRelay;

public final class WidgetApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Prefs.init(this);
            CrashHandler.install(this);
            WidgetRelay.get();
            registerNavReceiver(this);
            ProtocolClient.get().start(this);
            LogHub.i("app", "bootstrap pid=" + Process.myPid() + " pkg=" + getPackageName());
        } catch (Throwable t) {
            android.util.Log.e("dashcast", "bootstrap failed", t);
        }
    }

    private void registerNavReceiver(Context c) {
        try {
            NavReceiver r = new NavReceiver();
            IntentFilter f = new IntentFilter();
            f.addAction(NavCollector.WECAR_ACTION);
            f.addAction(NavCollector.AMAP_ACTION);
            f.addAction(NavCollector.BAIDU_ACTION);
            f.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            c.registerReceiver(r, f);
            LogHub.i("app", "导航监听已启动（高德/百度/WeCar 广播）");
        } catch (Throwable t) {
            LogHub.w("app", "导航广播接收器注册失败：" + CrashHandler.describe(t));
            CrashHandler.record(t);
        }
    }
}
