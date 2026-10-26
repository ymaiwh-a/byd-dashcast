package com.byd.dashcast.phase1;

import android.content.Context;

import com.byd.dashcast.phase1.ncm.LogHub;
import com.byd.dashcast.phase1.widget.WidgetNet;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NcmSessionHub {

    private NcmSessionHub() {
    }

    private static UsbNcmSession session;
    private static int refs;
    private static final AtomicBoolean dead = new AtomicBoolean();

    public static synchronized UsbNcmSession acquire(Context context) throws IOException {
        if (session == null) {
            dead.set(false);
            session = UsbNcmSession.open(context, dead);
            WidgetNet.set(session.net());
        }
        refs++;
        return session;
    }

    public static synchronized boolean release() {
        if (session == null) {
            return true;
        }
        if (--refs > 0) {
            return true;
        }
        UsbNcmSession owned = session;
        session = null;
        WidgetNet.set(null);
        try {
            owned.close();
        } catch (IOException | RuntimeException error) {
            LogHub.throwable("session", "SHARED_RELEASE_CLOSE_FAILED", error);
        }
        return owned.localResourcesClosed();
    }

    public static synchronized void releaseDetached() throws IOException {
        if (session == null) {
            return;
        }
        UsbNcmSession owned = session;
        session = null;
        refs = 0;
        WidgetNet.set(null);
        dead.set(true);
        owned.closeDetached();
    }

    public static synchronized boolean localResourcesClosed() {
        return session == null || session.localResourcesClosed();
    }
}