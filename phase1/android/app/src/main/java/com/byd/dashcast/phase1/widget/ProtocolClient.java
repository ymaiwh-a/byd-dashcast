package com.byd.dashcast.phase1.widget;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import com.byd.dashcast.phase1.ncm.LogHub;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProtocolClient {

    private static final String ACTION = "action.com.autosdk.protocol.ProtocolService";
    private static final String TARGET_PKG = "com.byd.automap";
    private static final String IFACE = "com.autosdk.protocol.IProtocolAidlInterface";
    private static final String CB_IFACE = "com.autosdk.protocol.listener.IProtocolCallback";
    private static final String PERMIT_PKG = "com.byd.protocolclient";

    private static final int CODE_REGISTER = 3;
    private static final int CODE_REGIST = 2;
    private static final int CODE_SET_MODEL = 1;

    private static final int PROTO_TRAFFIC = 30003;
    private static final int ACT_REMAIN = 2;
    private static final int ACT_LIGHT = 4;
    private static final int ACT_DEST_NAME = 8;

    private static final int[] PUSH_IDS = {32001, 32002, 32003, 32004, 32005, 32010, 32011};

    private static final Pattern HOUR = Pattern.compile("(\\d+)\\s*(?:小时|h)");
    private static final Pattern MIN = Pattern.compile("(\\d+)\\s*(?:分钟|分|min)");
    private static final Pattern KM = Pattern.compile("([0-9.]+)\\s*(?:公里|千米|km)");
    private static final Pattern METRE = Pattern.compile("([0-9.]+)\\s*(?:米|m)");

    private static final ProtocolClient INSTANCE = new ProtocolClient();

    public static ProtocolClient get() {
        return INSTANCE;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Callback cb = new Callback();

    private Context ctx;
    private volatile IBinder svc;
    private volatile boolean navActive;
    private volatile boolean navEnded;
    private volatile boolean bound;
    private volatile boolean bindFailLogged;

    private final Runnable bindTask = new Runnable() {
        @Override
        public void run() {
            if (svc != null) return;
            try {
                Intent i = new Intent(ACTION);
                i.setPackage(TARGET_PKG);
                if (ctx.bindService(i, conn, 0)) {
                    bound = true;
                } else {
                    if (!bindFailLogged) {
                        bindFailLogged = true;
                        LogHub.d("proto", "定制导航协议服务未运行，等待中");
                    }
                    scheduleRebind();
                }
            } catch (Throwable t) {
                CrashHandler.record(t);
                scheduleRebind();
            }
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!navActive || svc == null) return;
            regist();
            query(ACT_REMAIN);
            query(ACT_LIGHT);
            query(ACT_DEST_NAME);
            NavState hb = new NavState();
            hb.src = "autosdk";
            hb.active = true;
            NavReceiver.deliver(hb);
            handler.postDelayed(this, 1000);
        }
    };

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            svc = binder;
            bindFailLogged = false;
            try {
                binder.linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        onLost();
                    }
                }, 0);
            } catch (Throwable t) {
                CrashHandler.record(t);
            }
            LogHub.i("proto", "已连接定制导航协议服务，订阅推送通道");
            navEnded = false;
            regist();
            for (int id : PUSH_IDS) register(id);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onLost();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            LogHub.w("proto", "协议服务返回空 Binder");
            onLost();
        }
    };

    public void start(Context c) {
        ctx = c.getApplicationContext();
        handler.post(bindTask);
    }

    private void scheduleRebind() {
        handler.removeCallbacks(bindTask);
        handler.postDelayed(bindTask, 15_000L);
    }

    private void onLost() {
        svc = null;
        navActive = false;
        handler.removeCallbacks(tick);
        LogHub.w("proto", "定制导航协议服务断开");
        try {
            NavState n = new NavState();
            n.src = "autosdk";
            n.active = false;
            NavReceiver.deliver(n);
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
        if (bound) {
            bound = false;
            try {
                ctx.unbindService(conn);
            } catch (IllegalArgumentException already) {
            } catch (Throwable t) {
                CrashHandler.record(t);
            }
        }
        scheduleRebind();
    }

    private void register(int id) {
        IBinder s = svc;
        if (s == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IFACE);
            data.writeStrongBinder(cb);
            data.writeInt(id);
            s.transact(CODE_REGISTER, data, reply, 0);
            reply.readException();
        } catch (Throwable t) {
            CrashHandler.record(t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void regist() {
        IBinder s = svc;
        if (s == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IFACE);
            data.writeStrongBinder(cb);
            s.transact(CODE_REGIST, data, reply, 0);
            reply.readException();
        } catch (Throwable t) {
            CrashHandler.record(t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void query(int actionType) {
        IBinder s = svc;
        if (s == null) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            Model m = new Model();
            m.protocolID = PROTO_TRAFFIC;
            m.actionType = actionType;
            m.timeStamp = System.currentTimeMillis();
            data.writeInterfaceToken(IFACE);
            data.writeInt(1);
            m.writeToParcel(data, 0);
            s.transact(CODE_SET_MODEL, data, reply, 0);
            reply.readException();
        } catch (Throwable t) {
            CrashHandler.record(t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private final class Callback extends Binder {

        @Override
        public String getInterfaceDescriptor() {
            return CB_IFACE;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code >= 1 && code <= 3) {
                data.enforceInterface(CB_IFACE);
                if (code == 2) {
                    String s = data.readString();
                    reply.writeNoException();
                    handleJson(s);
                    return true;
                }
                if (code == 3) {
                    String s = data.readString();
                    if (LogHub.oncePer("proto_ok", 30000))
                        LogHub.i("proto", "协议回调 onSuccess: " + s);
                }
                if (code == 1) {
                    if (LogHub.oncePer("proto_fail", 60000))
                        LogHub.d("proto", "协议服务回调 onFail");
                }
                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }

    private void handleJson(String raw) {
        try {
            if (raw == null || raw.isEmpty()) return;
            JSONObject o = new JSONObject(raw);
            JSONArray names = o.names();
            if (names == null) return;
            boolean useful = false;
            for (int i = 0; i < names.length(); i++) {
                String v = String.valueOf(o.opt(names.getString(i)));
                if (!v.isEmpty() && !"null".equals(v)) {
                    useful = true;
                    break;
                }
            }
            if (!useful) return;
            for (int i = 0; i < names.length(); i++) {
                String k = names.getString(i);
                apply(k, String.valueOf(o.opt(k)));
            }
        } catch (Throwable t) {
            CrashHandler.record(t);
        }
    }

    private void apply(String k, String sv) {
        if (sv == null || sv.isEmpty() || "null".equals(sv)) return;
        try {
            if ("navistatus".equals(k)) {
                long v = Long.parseLong(sv.trim());
                boolean a = v == 2;
                if (a == navActive) return;
                navActive = a;
                handler.removeCallbacks(tick);
                if (a) {
                    if (navEnded) {
                        navEnded = false;
                        for (int id : PUSH_IDS) register(id);
                    }
                    NavState old = new NavState();
                    old.src = "autosdk";
                    old.active = false;
                    NavReceiver.deliver(old);
                    query(ACT_DEST_NAME);
                    handler.postDelayed(tick, 1000);
                } else {
                    navEnded = true;
                }
                NavState n = new NavState();
                n.src = "autosdk";
                n.active = a;
                NavReceiver.deliver(n);
                return;
            }
            NavState n = new NavState();
            n.src = "autosdk";
            n.active = navActive;
            boolean emit = false;
            if ("tbtinfo".equals(k) || "maneuverId".equals(k)) {
                String name = WidgetData.amapIconName((int) Long.parseLong(sv.trim()));
                if (!name.isEmpty()) {
                    n.turn = name;
                    emit = true;
                }
            } else if ("actiondis".equals(k)) {
                long m = Long.parseLong(sv.trim());
                if (m > 0) {
                    n.distanceM = m;
                    n.distanceText = m >= 1000 ? (Math.round(m / 100.0) / 10.0) + "公里" : m + "米";
                    emit = true;
                }
            } else if ("roadlimit".equals(k) || "speedlimit".equals(k)) {
                int v = (int) Long.parseLong(sv.trim());
                if (v > 0) {
                    n.limitKph = v;
                    emit = true;
                }
            } else if ("destName".equals(k)) {
                n.destination = sv;
                emit = true;
            } else if ("remaininfo".equals(k)) {
                applyRemain(sv, n);
                emit = !n.eta.isEmpty() || n.remainingM > 0 || n.remainingS > 0;
            } else if ("restarea".equals(k)) {
                long m = Long.parseLong(sv.trim());
                if (m > 0) {
                    n.serviceArea = m >= 1000 ? (m / 1000) + "公里" : m + "米";
                    emit = true;
                }
            }
            if (emit) NavReceiver.deliver(n);
        } catch (Throwable t) {
            if (LogHub.oncePer("proto_apply_" + k, 60000))
                LogHub.d("proto", "解析异常 key=" + k + " value=" + sv);
        }
    }

    private void applyRemain(String txt, NavState n) {
        String[] p = txt.split(";");
        if (p.length >= 1) {
            n.remainingS = parseTimeSec(p[0].trim());
        }
        if (p.length >= 2) {
            n.remainingM = parseDistM(p[1].trim());
        }
        if (p.length >= 3) {
            String a = p[2].trim();
            if (!a.isEmpty()) n.eta = a;
        }
        if (n.eta.isEmpty() && n.remainingS > 0) {
            n.eta = fmtArrive(System.currentTimeMillis() + n.remainingS * 1000L);
        }
    }

    private static String fmtArrive(long ms) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        return String.format(java.util.Locale.CHINA, "%02d:%02d",
                c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
    }

    private static long parseTimeSec(String s) {
        String v = s.trim();
        long sec = 0;
        Matcher h = HOUR.matcher(v);
        if (h.find()) sec += Long.parseLong(h.group(1)) * 3600L;
        Matcher m = MIN.matcher(v);
        if (m.find()) sec += Long.parseLong(m.group(1)) * 60L;
        if (sec > 0) return sec;
        String[] c = v.split(":");
        if (c.length == 3) {
            try {
                return Long.parseLong(c[0].trim()) * 3600L + Long.parseLong(c[1].trim()) * 60L
                        + Long.parseLong(c[2].trim());
            } catch (Throwable t) {
                return 0;
            }
        }
        if (c.length == 2) {
            try {
                return Long.parseLong(c[0].trim()) * 60L + Long.parseLong(c[1].trim());
            } catch (Throwable t) {
                return 0;
            }
        }
        if (v.matches("\\d+")) {
            try {
                return Long.parseLong(v);
            } catch (Throwable t) {
                return 0;
            }
        }
        return 0;
    }

    private static long parseDistM(String s) {
        Matcher k = KM.matcher(s);
        if (k.find()) return (long) (Double.parseDouble(k.group(1)) * 1000L);
        Matcher mt = METRE.matcher(s);
        if (mt.find()) return (long) Double.parseDouble(mt.group(1));
        try {
            return (long) Double.parseDouble(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static final class Model implements Parcelable {
        int protocolID;
        long timeStamp;
        int callbackId;
        String protocolVersion = "1";
        String packageName = PERMIT_PKG;
        String var1 = "";
        int actionType;
        int operaType;
        String searchKey = "";
        String destPoiName = "";
        int errorCode;
        String destLatitude = "";
        String destLongitude = "";
        String passPoiName = "";
        String passLatitude = "";
        String passLongitude = "";

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel p, int flags) {
            p.writeInt(protocolID);
            p.writeLong(timeStamp);
            p.writeInt(callbackId);
            p.writeString(protocolVersion);
            p.writeString(packageName);
            p.writeString(var1);
            p.writeInt(actionType);
            p.writeInt(operaType);
            p.writeString(searchKey);
            p.writeString(destPoiName);
            p.writeInt(errorCode);
            p.writeString(destLatitude);
            p.writeString(destLongitude);
            p.writeString(passPoiName);
            p.writeString(passLatitude);
            p.writeString(passLongitude);
        }

        public static final Creator<Model> CREATOR = new Creator<Model>() {
            @Override
            public Model createFromParcel(Parcel p) {
                Model m = new Model();
                m.protocolID = p.readInt();
                m.timeStamp = p.readLong();
                m.callbackId = p.readInt();
                m.protocolVersion = p.readString();
                m.packageName = p.readString();
                m.var1 = p.readString();
                m.actionType = p.readInt();
                m.operaType = p.readInt();
                m.searchKey = p.readString();
                m.destPoiName = p.readString();
                m.errorCode = p.readInt();
                m.destLatitude = p.readString();
                m.destLongitude = p.readString();
                m.passPoiName = p.readString();
                m.passLatitude = p.readString();
                m.passLongitude = p.readString();
                return m;
            }

            @Override
            public Model[] newArray(int size) {
                return new Model[size];
            }
        };
    }
}
