package com.byd.dashcast.phase1.widget;

public final class FieldSpec {

    private FieldSpec() {
    }

    public static final String[] NAV_PROD = {
            "active", "turn", "distance_m", "distance_text", "current_road",

            "destination", "eta",
            "remaining_m", "remaining_s", "limit_kph", "ttl_ms",

            "show_next", "show_alerts", "show_limit",

            "lights",

            "lanes"
    };

    public static final String[] NAV_PROBE = {
            "active", "turn", "distance_m", "distance_text", "current_road",
            "remaining_m", "remaining_s", "limit_kph", "ttl_ms",
            "destination", "eta", "service_area", "traffic_text", "exit_name",
            "speed_kph", "lights",
            "show_next", "show_alerts", "show_limit",
            "next_ttl_ms", "alerts_ttl_ms"
    };

    public static final String[] MUSIC_PROD = {
            "track_id", "title", "artist",
            "playing", "duration_ms", "position_ms", "lyrics",

            "line_index", "cover_rgba"
    };

    public static final String[] MUSIC_PROBE = {
            "track_id", "title", "artist",
            "playing", "duration_ms", "position_ms", "lyrics",

            "line_index"
    };
}
