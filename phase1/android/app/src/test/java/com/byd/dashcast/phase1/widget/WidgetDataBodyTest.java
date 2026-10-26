package com.byd.dashcast.phase1.widget;

import org.junit.Test;

import static org.junit.Assert.*;

public final class WidgetDataBodyTest {

    @Test
    public void navDataBodyEmitsPresentFields() throws Exception {
        NavState s = new NavState();
        s.active = true;
        s.turn = "left";
        s.distanceM = 500L;
        s.distanceText = "500 m";
        s.currentRoad = "Ring Road";
        s.destination = "Home";
        s.eta = "12 min";
        s.remainingM = 2000L;
        s.remainingS = 90L;
        s.limitKph = 80;
        s.lanes = "[{\"directions\":\"left\",\"recommended\":true}]";
        org.json.JSONObject o = new org.json.JSONObject(WidgetData.navDataBody(s));
        assertTrue(o.getBoolean("active"));
        assertEquals("left", o.getString("turn"));
        assertEquals(500L, o.getLong("distance_m"));
        assertEquals("500 m", o.getString("distance_text"));
        assertEquals("Ring Road", o.getString("current_road"));
        assertEquals("Home", o.getString("destination"));
        assertEquals("12 min", o.getString("eta"));
        assertEquals(2000L, o.getLong("remaining_m"));
        assertEquals(90L, o.getLong("remaining_s"));
        assertEquals(80, o.getInt("limit_kph"));
        assertEquals(20000L, o.getLong("ttl_ms"));
        assertTrue(o.getBoolean("show_next"));
        assertTrue(o.getBoolean("show_alerts"));
        assertTrue(o.getBoolean("show_limit"));
        assertEquals(0, o.getJSONArray("lights").length());
        assertEquals(1, o.getJSONArray("lanes").length());
        assertEquals("left", o.getJSONArray("lanes").getJSONObject(0).getString("directions"));
        assertTrue(o.getJSONArray("lanes").getJSONObject(0).getBoolean("recommended"));
    }

    @Test
    public void navDataBodyOmitsEmptyOptionalFields() throws Exception {
        NavState s = new NavState();
        org.json.JSONObject o = new org.json.JSONObject(WidgetData.navDataBody(s));
        assertFalse(o.getBoolean("active"));
        assertTrue(o.getBoolean("show_next"));
        assertTrue(o.getBoolean("show_alerts"));
        assertTrue(o.getBoolean("show_limit"));
        assertEquals(20000L, o.getLong("ttl_ms"));
        assertFalse(o.has("turn"));
        assertFalse(o.has("distance_m"));
        assertFalse(o.has("distance_text"));
        assertFalse(o.has("current_road"));
        assertFalse(o.has("destination"));
        assertFalse(o.has("eta"));
        assertFalse(o.has("remaining_m"));
        assertFalse(o.has("remaining_s"));
        assertFalse(o.has("limit_kph"));
        assertFalse(o.has("lights"));
        assertFalse(o.has("lanes"));
    }

    @Test
    public void navDataBodyCutsOverlongDestinationAndEta() throws Exception {
        NavState s = new NavState();
        StringBuilder dest = new StringBuilder();
        for (int i = 0; i < 600; i++) dest.append('d');
        s.destination = dest.toString();
        StringBuilder eta = new StringBuilder();
        for (int i = 0; i < 100; i++) eta.append('e');
        s.eta = eta.toString();
        org.json.JSONObject o = new org.json.JSONObject(WidgetData.navDataBody(s));
        assertEquals(512, o.getString("destination").length());
        assertEquals(64, o.getString("eta").length());
    }

    @Test
    public void trackSyncBodyCarriesTrackIdAndLineIndex() throws Exception {
        org.json.JSONObject o = new org.json.JSONObject(WidgetData.trackSyncBody("t0a1b2c", 5));
        assertEquals("t0a1b2c", o.getString("track_id"));
        assertEquals(5, o.getInt("line_index"));
        assertEquals(2, o.length());
    }

    @Test
    public void trackLoadBodyEmitsCoreMetadataFields() throws Exception {
        MusicState m = new MusicState();
        m.trackId = "";
        m.title = "Song";
        m.artist = "Singer";
        m.playing = true;
        m.durationMs = 123456L;
        org.json.JSONObject withoutCover = new org.json.JSONObject(WidgetData.trackLoadBody(m, false));
        assertEquals("cast_static", withoutCover.getString("track_id"));
        assertEquals("Song", withoutCover.getString("title"));
        assertEquals("Singer", withoutCover.getString("artist"));
        assertTrue(withoutCover.getBoolean("playing"));
        assertEquals(123456L, withoutCover.getLong("duration_ms"));
        assertFalse(withoutCover.has("cover_rgba"));
        org.json.JSONObject withCover = new org.json.JSONObject(WidgetData.trackLoadBody(m, true));
        assertEquals("cast_static", withCover.getString("track_id"));
        assertEquals("Song", withCover.getString("title"));
        assertEquals("Singer", withCover.getString("artist"));
        assertTrue(withCover.getBoolean("playing"));
        assertEquals(123456L, withCover.getLong("duration_ms"));
        assertFalse(withCover.has("cover_rgba"));
    }

    @Test
    public void trackLoadBodyKeepsExplicitTrackId() throws Exception {
        MusicState m = new MusicState();
        m.trackId = "t0abc";
        m.title = "A";
        m.artist = "B";
        org.json.JSONObject o = new org.json.JSONObject(WidgetData.trackLoadBody(m, false));
        assertEquals("t0abc", o.getString("track_id"));
    }
}
