package com.byd.dashcast.phase1.widget;

import android.annotation.SuppressLint;
import android.service.notification.NotificationListenerService;

import com.byd.dashcast.phase1.ncm.LogHub;

@SuppressLint("OverrideAbstract")
public class MusicListener extends NotificationListenerService {

    public static volatile boolean sBound;

    @Override
    public void onCreate() {
        super.onCreate();
        LogHub.i("music", "MusicListener（NLS）已被系统拉起");
        MusicCollector.ensureStarted(this);
    }

    @Override
    public void onDestroy() {
        sBound = false;
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sBound = true;
        LogHub.i("music", "通知监听已连接");
        MusicCollector.ensureStarted(this);
    }
}
