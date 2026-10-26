package com.byd.dashcast.phase1.widget;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public final class NavCollectorParseKvTest {

    @Test
    public void nullActionOrKvReturnsNull() {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 10001);
        assertNull(NavCollector.parseKv(null, kv));
        assertNull(NavCollector.parseKv(NavCollector.AMAP_ACTION, null));
    }

    @Test
    public void amapUnknownKeyTypeIsGatedToNull() {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 10002);
        kv.put("CUR_ROAD_NAME", "Ring Road");
        assertNull(NavCollector.parseKv(NavCollector.AMAP_ACTION, kv));
    }

    @Test
    public void amapKeyType10006ReturnsInactiveState() {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 10006);
        NavState s = NavCollector.parseKv(NavCollector.AMAP_ACTION, kv);
        assertNotNull(s);
        assertFalse(s.active);
    }

    @Test
    public void amapKeyType60073ParsesFlatTrafficLightFields() throws Exception {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 60073);
        kv.put("trafficLightStatus", 1);
        kv.put("redLightCountDownSeconds", 30);
        kv.put("dir", 1);
        NavState s = NavCollector.parseKv(NavCollector.AMAP_ACTION, kv);
        assertNotNull(s);
        assertTrue(s.active);
        assertEquals(30, s.lightSeconds);
        assertEquals(1, s.lightColor);
        org.json.JSONArray lights = new org.json.JSONArray(s.lightsJson);
        assertEquals(1, lights.length());
        assertEquals("left", lights.getJSONObject(0).getString("direction"));
        assertEquals(1, lights.getJSONObject(0).getInt("color"));
        assertEquals(30, lights.getJSONObject(0).getInt("seconds"));
    }

    @Test
    public void amapKeyType60073ParsesLightsDataArray() throws Exception {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 60073);
        kv.put("lightsData", "[{\"status\":4,\"countdown\":12,\"dir\":2}]");
        NavState s = NavCollector.parseKv(NavCollector.AMAP_ACTION, kv);
        assertNotNull(s);
        assertTrue(s.active);
        assertEquals(2, s.lightColor);
        assertEquals(12, s.lightSeconds);
        org.json.JSONArray lights = new org.json.JSONArray(s.lightsJson);
        assertEquals(1, lights.length());
        assertEquals("right", lights.getJSONObject(0).getString("direction"));
        assertEquals(2, lights.getJSONObject(0).getInt("color"));
        assertEquals(12, lights.getJSONObject(0).getInt("seconds"));
    }

    @Test
    public void amapKeyType60073WithoutLightSecondsReturnsNull() {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 60073);
        kv.put("trafficLightStatus", 0);
        assertNull(NavCollector.parseKv(NavCollector.AMAP_ACTION, kv));
    }

    @Test
    public void amapKeyType13012ParsesLaneDriveWay() throws Exception {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 13012);
        kv.put("EXTRA_DRIVE_WAY", "{\"drive_way_enabled\":true,\"drive_way_info\":["
                + "{\"drive_way_lane_Back_icon\":\"0\",\"trafficLaneAdvised\":true},"
                + "{\"drive_way_lane_Back_icon\":\"1\",\"trafficLaneAdvised\":false}]}");
        NavState s = NavCollector.parseKv(NavCollector.AMAP_ACTION, kv);
        assertNotNull(s);
        assertTrue(s.active);
        assertTrue(s.lanesRxMs > 0L);
        org.json.JSONArray lanes = new org.json.JSONArray(s.lanes);
        assertEquals(2, lanes.length());
        assertEquals("straight", lanes.getJSONObject(0).getString("directions"));
        assertEquals("straight", lanes.getJSONObject(0).getString("active_directions"));
        assertTrue(lanes.getJSONObject(0).getBoolean("recommended"));
        assertEquals("left", lanes.getJSONObject(1).getString("directions"));
        assertEquals("unknown", lanes.getJSONObject(1).getString("active_directions"));
        assertFalse(lanes.getJSONObject(1).getBoolean("recommended"));
    }

    @Test
    public void amapKeyType13012WithoutDriveWayReturnsNull() {
        Map<String, Object> kv = new HashMap<>();
        kv.put("KEY_TYPE", 13012);
        assertNull(NavCollector.parseKv(NavCollector.AMAP_ACTION, kv));
    }
}
