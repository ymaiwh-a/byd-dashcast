package com.byd.dashcast.phase1.widget;

import com.byd.dashcast.phase1.ncm.Net;

public final class WidgetNet {

    private WidgetNet() {
    }

    private static volatile Net net;

    public static void set(Net n) {
        net = n;
    }

    public static Net net() {
        return net;
    }
}
