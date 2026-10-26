package com.byd.dashcast.phase1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.IBinder;

import com.byd.dashcast.phase1.ncm.LogHub;

import java.io.IOException;

public final class WidgetService extends Service {
    private static final String CHANNEL = "dashcast-widget";
    private static final String START = "com.byd.dashcast.START_WIDGET";
    private static final String STOP = "com.byd.dashcast.STOP_WIDGET";
    private static final int NOTIFICATION_ID = 51;
    private static volatile WidgetService current;
    private boolean held;
    private String ownedDevice;
    private boolean receiverRegistered;

    private final BroadcastReceiver detach = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction()) && device != null
                    && device.getDeviceName().equals(ownedDevice)) {
                LogHub.w("widget", "WIDGET_USB_DETACHED device=" + ownedDevice);
                stopSelf();
            }
        }
    };

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, WidgetService.class).setAction(START));
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, WidgetService.class).setAction(STOP));
    }

    public static boolean active() {
        return current != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        current = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (held) {
            return START_NOT_STICKY;
        }
        try {
            UsbDevice device = UsbNcmSession.findDevice(this);
            ownedDevice = device.getDeviceName();
            startForeground(NOTIFICATION_ID, notification());
            registerReceiver(detach, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));
            receiverRegistered = true;
            NcmSessionHub.acquire(this);
            held = true;
            LogHub.i("widget", "WIDGET_SESSION_ACQUIRED");
        } catch (IOException | RuntimeException error) {
            LogHub.w("widget", "WIDGET_SESSION_OPEN_FAILED " + error);
            if (receiverRegistered) {
                unregisterReceiver(detach);
                receiverRegistered = false;
            }
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) {
            throw new IllegalStateException("NotificationManager unavailable");
        }
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                getString(R.string.widget_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, ProductActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, WidgetService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.widget_notification_title))
                .setContentText(getString(R.string.widget_notification_text))
                .setOngoing(true).setContentIntent(open)
                .addAction(0, getString(R.string.widget_notification_stop), stop).build();
    }

    @Override
    public void onDestroy() {
        if (held) {
            held = false;
            if (!NcmSessionHub.release()) {
                LogHub.w("widget", "WIDGET_SESSION_CLEANUP_UNCONFIRMED");
            } else {
                LogHub.i("widget", "WIDGET_SESSION_RELEASED");
            }
        }
        if (receiverRegistered) {
            unregisterReceiver(detach);
            receiverRegistered = false;
        }
        if (current == this) {
            current = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}