package com.byd.dashcast.phase1.widget;

public final class NavState {

    public boolean active;
    public String src = "";
    public String currentRoad = "";
    public String destination = "";
    public String turn = "";
    public String distanceText = "";
    public String eta = "";
    public String serviceArea = "";
    public String trafficText = "";
    public String exitName = "";
    public long distanceM;
    public long remainingM;
    public long remainingS;
    public int speedKph;
    public int limitKph;
    public int lightSeconds;
    public int lightColor;
    public String lightsJson = "";

    public String lanes = "";

    public long lanesRxMs;
    public boolean showNext = true;
    public boolean showAlerts = true;
    public boolean showLimit = true;
    public long nextTtlMs = 60000;
    public long alertsTtlMs = 60000;
    public long ttlMs = 20000;

    public NavState copy() {
        NavState n = new NavState();
        n.active = active;
        n.currentRoad = currentRoad;
        n.destination = destination;
        n.turn = turn;
        n.distanceText = distanceText;
        n.eta = eta;
        n.serviceArea = serviceArea;
        n.trafficText = trafficText;
        n.exitName = exitName;
        n.distanceM = distanceM;
        n.remainingM = remainingM;
        n.remainingS = remainingS;
        n.speedKph = speedKph;
        n.limitKph = limitKph;
        n.lightSeconds = lightSeconds;
        n.lightColor = lightColor;
        n.lightsJson = lightsJson;
        n.lanes = lanes;
        n.lanesRxMs = lanesRxMs;
        n.showNext = showNext;
        n.showAlerts = showAlerts;
        n.showLimit = showLimit;
        n.nextTtlMs = nextTtlMs;
        n.alertsTtlMs = alertsTtlMs;
        n.ttlMs = ttlMs;
        return n;
    }

    public boolean equalsData(NavState o) {
        if (o == null) return false;
        if (active != o.active) return false;
        if (showNext != o.showNext || showAlerts != o.showAlerts || showLimit != o.showLimit) return false;
        if (distanceM != o.distanceM || remainingM != o.remainingM || remainingS != o.remainingS) return false;
        if (speedKph != o.speedKph || limitKph != o.limitKph) return false;
        if (lightSeconds != o.lightSeconds || lightColor != o.lightColor) return false;
        if (!eq(lightsJson, o.lightsJson)) return false;
        if (!eq(lanes, o.lanes)) return false;
        return eq(currentRoad, o.currentRoad) && eq(destination, o.destination) && eq(turn, o.turn)
                && eq(distanceText, o.distanceText) && eq(eta, o.eta) && eq(serviceArea, o.serviceArea)
                && eq(trafficText, o.trafficText) && eq(exitName, o.exitName);
    }

    public void reset() {
        NavState f = new NavState();
        active = f.active;
        currentRoad = f.currentRoad;
        destination = f.destination;
        turn = f.turn;
        distanceText = f.distanceText;
        eta = f.eta;
        serviceArea = f.serviceArea;
        trafficText = f.trafficText;
        exitName = f.exitName;
        distanceM = f.distanceM;
        remainingM = f.remainingM;
        remainingS = f.remainingS;
        speedKph = f.speedKph;
        limitKph = f.limitKph;
        lightSeconds = f.lightSeconds;
        lightColor = f.lightColor;
        lightsJson = f.lightsJson;
        lanes = f.lanes;
        lanesRxMs = f.lanesRxMs;
    }

    public void mergeNonEmpty(NavState o) {
        if (o == null) return;
        if (!eq("", o.currentRoad)) currentRoad = o.currentRoad;
        if (!eq("", o.destination)) destination = o.destination;
        if (!eq("", o.turn)) turn = o.turn;
        if (!eq("", o.distanceText)) distanceText = o.distanceText;
        if (!eq("", o.eta)) eta = o.eta;
        if (!eq("", o.serviceArea)) serviceArea = o.serviceArea;
        if (!eq("", o.trafficText)) trafficText = o.trafficText;
        if (!eq("", o.exitName)) exitName = o.exitName;
        if (o.distanceM > 0) distanceM = o.distanceM;
        if (o.remainingM > 0) remainingM = o.remainingM;
        if (o.remainingS > 0) remainingS = o.remainingS;
        if (o.speedKph > 0) speedKph = o.speedKph;
        if (o.limitKph > 0) limitKph = o.limitKph;
        if (o.lightSeconds > 0) { lightSeconds = o.lightSeconds; lightColor = o.lightColor; }
        if (!eq("", o.lightsJson)) lightsJson = o.lightsJson;

        if (!eq("", o.lanes)) { lanes = o.lanes; lanesRxMs = o.lanesRxMs; }
    }

    private static boolean eq(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        return a.equals(b);
    }
}
