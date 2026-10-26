#include "widgetd.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

#ifdef _WIN32
#include <direct.h>
#define WD_MKDIR(path) _mkdir(path)
#else
#include <sys/stat.h>
#define WD_MKDIR(path) mkdir((path), 0700)
#endif

#ifdef NDEBUG
#error Widgetd tests require assertions
#endif

typedef struct {
    bool nav_render_ok;
    bool music_render_ok;
    bool day_available;
    int day;
    unsigned nav_renders;
    unsigned nav_hides;
    unsigned music_renders;
    unsigned music_hides;
    unsigned music_lines;
    int last_line_index;
} Fake;

static WdCore g_core;
static Fake g_fake;
static char g_reply[WD_REPLY_CAP];

static bool fake_nav_render(void *context, const WdNavFrame *frame) {
    Fake *f = context;
    (void)frame;
    f->nav_renders++;
    return f->nav_render_ok;
}

static void fake_nav_hide(void *context) {
    ((Fake *)context)->nav_hides++;
}

static bool fake_music_render(void *context, const WdMusicFrame *frame) {
    Fake *f = context;
    (void)frame;
    f->music_renders++;
    return f->music_render_ok;
}

static void fake_music_hide(void *context) {
    ((Fake *)context)->music_hides++;
}

static void fake_music_line(void *context, int line_index) {
    Fake *f = context;
    f->music_lines++;
    f->last_line_index = line_index;
}

static bool fake_theme_day(void *context, int *day) {
    Fake *f = context;
    if (!f->day_available) return false;
    *day = f->day;
    return true;
}

static const WdPort PORT = {NULL, fake_nav_render, fake_nav_hide, fake_music_render,
                            fake_music_hide, fake_music_line, fake_theme_day};

static void boot_dir(const char *dir) {
    memset(&g_fake, 0, sizeof(g_fake));
    g_fake.nav_render_ok = true;
    g_fake.music_render_ok = true;
    g_fake.day_available = true;
    g_fake.day = 1;
    WdConfig cfg;
    memset(&cfg, 0, sizeof(cfg));
    strncpy(cfg.config_dir, dir, sizeof(cfg.config_dir) - 1u);
    cfg.epoch = 7;
    WdPort port = PORT;
    port.context = &g_fake;
    assert(wd_init(&g_core, &cfg, &port));
    wd_tick(&g_core, 1000);
}

static void boot(void) {
    boot_dir("");
}

static const char *req(int channel, const char *json) {
    size_t n = wd_handle_line(&g_core, channel, json, strlen(json), g_reply, sizeof(g_reply));
    assert(n > 0 && n < sizeof(g_reply));
    return g_reply;
}

static void is_ok(const char *r) {
    assert(strstr(r, "\"ok\":true") != NULL);
    assert(strstr(r, "\"result\":\"ok\"") != NULL);
}

static void is_err(const char *r, const char *code) {
    assert(strstr(r, "\"ok\":false") != NULL);
    char needle[80];
    snprintf(needle, sizeof(needle), "\"error\":\"%s\"", code);
    assert(strstr(r, needle) != NULL);
}

static void has(const char *r, const char *sub) {
    assert(strstr(r, sub) != NULL);
}

static void theme_get(void) {
    boot();
    const char *r = req(WD_CHANNEL_HMI, "{\"op\":\"theme.get\"}");
    is_ok(r);
    has(r, "\"schema\":2,\"service\":\"hmi\"");
    has(r, "\"day\":true");
    has(r, "\"appearance_valid\":true");
    has(r, "\"appearance_day\":1");
    has(r, "\"theme_id\":\"default\"");
}

static void theme_get_unavailable(void) {
    boot();
    g_fake.day_available = false;
    const char *r = req(WD_CHANNEL_HMI, "{\"op\":\"theme.get\"}");
    is_ok(r);
    has(r, "\"day\":false");
    has(r, "\"appearance_valid\":false");
    has(r, "\"appearance_day\":-1");
}

static void invalid_json_empty(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "");
    is_err(r, "invalid_json");
}

static void invalid_json_trailing(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"} x");
    is_err(r, "invalid_json");
}

static void invalid_json_trailing_comma(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\",}");
    is_err(r, "invalid_json");
}

static void duplicate_field(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\",\"op\":\"navigation.hide\"}");
    is_err(r, "duplicate_field");
}

static void request_too_large(void) {
    boot();
    size_t len = WD_LINE_CAP + 8u;
    char *big = malloc(len + 1u);
    assert(big != NULL);
    memset(big, 'a', len);
    big[len] = '\0';
    size_t n = wd_handle_line(&g_core, WD_CHANNEL_NAV, big, len, g_reply, sizeof(g_reply));
    assert(n > 0);
    is_err(g_reply, "request_too_large");
    free(big);
}

static void invalid_request_no_op(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"x\":1}");
    is_err(r, "invalid_request");
}

static void invalid_request_root_array(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "[1,2]");
    is_err(r, "invalid_request");
}

static void invalid_operation(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"nope\"}");
    is_err(r, "invalid_operation");
}

static void cross_channel_operation(void) {
    boot();
    const char *r = req(WD_CHANNEL_HMI, "{\"op\":\"navigation.show\"}");
    is_err(r, "invalid_operation");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"music.show\"}");
    is_err(r, "invalid_operation");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"update\"}");
    is_err(r, "invalid_operation");
}

static void fields_not_allowed(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\",\"x\":1}");
    is_err(r, "fields_not_allowed_for_operation");
}

static void show_hide_nav(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    is_ok(r);
    has(r, "\"rendered\":false");
    has(r, "\"effective_visible\":false");
    assert(g_fake.nav_renders == 0);
    r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.hide\"}");
    is_ok(r);
    has(r, "\"effective_visible\":false");
    assert(g_fake.nav_hides == 1);
}

static void update_full_fields(void) {
    boot();
    assert(strstr(req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}"), "\"ok\":true") != NULL);
    const char *r = req(WD_CHANNEL_NAV,
        "{\"op\":\"update\",\"active\":true,\"turn\":\"left\",\"distance_m\":120,"
        "\"current_road\":\"Main St\",\"destination\":\"Home\",\"eta\":\"18:30\","
        "\"lights\":[{\"color\":2,\"seconds\":30}],\"lanes\":[{\"dir\":\"straight\"},"
        "{\"dir\":\"left\"}],\"speed_kph\":60,\"limit_kph\":80,\"exit_name\":\"E7\","
        "\"service_area\":\"SA1\",\"traffic_text\":\"slow\",\"show_next\":true,"
        "\"show_alerts\":true,\"show_limit\":true,\"next_ttl_ms\":5000,\"alerts_ttl_ms\":6000}");
    is_ok(r);
    has(r, "\"rendered\":true");
    has(r, "\"effective_visible\":true");
    has(r, "\"lights_visible\":true");
    has(r, "\"lanes_visible\":true");
    has(r, "\"cycle_index\":1");
    has(r, "\"epoch\":7");
    assert(g_fake.nav_renders == 1);
}

static void update_unknown_field(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"bogus\":1}");
    is_err(r, "unknown_field_for_port");
}

static void update_bad_turn_type(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"turn\":5}");
    is_err(r, "invalid_turn");
}

static void update_negative_distance(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"distance_m\":-1}");
    is_err(r, "invalid_range_or_type");
}

static void update_ttl_zero(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"ttl_ms\":0}");
    is_err(r, "invalid_range_or_type");
}

static void update_lights_not_array(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"lights\":{}}");
    is_err(r, "invalid_lights");
}

static void update_lights_color_without_seconds(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"lights\":[{\"color\":2}]}");
    is_err(r, "light_color_and_seconds_required");
}

static void update_lights_mixed(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV,
        "{\"op\":\"update\",\"lights\":[{\"color\":2,\"seconds\":5},{\"state\":1}]}");
    is_err(r, "mixed_light_formats");
}

static void update_lights_color_range(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"lights\":[{\"color\":10,\"seconds\":5}]}");
    is_err(r, "invalid_lights");
}

static void update_lanes_bad_element(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"lanes\":[5]}");
    is_err(r, "invalid_lanes");
}

static void update_empty_arrays(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"lights\":[],\"lanes\":[]}");
    is_ok(r);
    has(r, "\"lights_visible\":false");
    has(r, "\"lanes_visible\":false");
}

static void update_render_fail(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    g_fake.nav_render_ok = false;
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true}");
    is_err(r, "display_write_failed");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.status\"}");
    has(r, "\"navigation\":false");
}

static void update_without_show(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true}");
    is_ok(r);
    has(r, "\"effective_visible\":false");
    assert(g_fake.nav_renders == 0);
    r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    is_ok(r);
    has(r, "\"effective_visible\":true");
    assert(g_fake.nav_renders == 1);
}

static void lease_expiry(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true,\"ttl_ms\":1000}");
    is_ok(r);
    has(r, "\"effective_visible\":true");
    wd_tick(&g_core, 1999);
    r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.status\"}");
    has(r, "\"navigation\":true");
    wd_tick(&g_core, 2000);
    assert(g_fake.nav_hides == 1);
    r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.status\"}");
    is_ok(r);
    has(r, "\"effective\":{\"music\":false,\"navigation\":false}");
    wd_tick(&g_core, 2500);
    assert(g_fake.nav_hides == 1);
}

static void lease_renew(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true,\"ttl_ms\":1000}");
    wd_tick(&g_core, 2000);
    assert(g_fake.nav_hides == 1);
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true,\"ttl_ms\":1000}");
    is_ok(r);
    has(r, "\"effective_visible\":true");
    wd_tick(&g_core, 2900);
    assert(g_fake.nav_hides == 1);
    wd_tick(&g_core, 3000);
    assert(g_fake.nav_hides == 2);
}

static void hide_clears_lease(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true,\"ttl_ms\":5000}");
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.hide\"}");
    wd_tick(&g_core, 6000);
    assert(g_fake.nav_hides == 1);
}

static void widgets_set(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true}");
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.set\",\"navigation\":false}");
    is_ok(r);
    has(r, "\"selected\":{\"music\":false,\"navigation\":false}");
    has(r, "\"effective\":{\"music\":false,\"navigation\":false}");
    assert(g_fake.nav_hides == 1);
    r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.set\",\"navigation\":true}");
    is_ok(r);
    has(r, "\"selected\":{\"music\":false,\"navigation\":true}");
    has(r, "\"effective\":{\"music\":false,\"navigation\":false}");
    assert(g_fake.nav_renders == 1);
    r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true}");
    is_ok(r);
    has(r, "\"rendered\":true");
    has(r, "\"effective_visible\":true");
    assert(g_fake.nav_renders == 2);
    r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.set\",\"bogus\":true}");
    is_err(r, "unknown_field_for_port");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.set\",\"navigation\":\"yes\"}");
    is_err(r, "invalid_range_or_type");
}

static void widgets_status_fields(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"widgets.status\"}");
    is_ok(r);
    has(r, "\"managed\":true");
    has(r, "\"accepted_revision\":0");
    has(r, "\"cycle_index\":0");
    has(r, "\"guidance\":{\"lights_visible\":false,\"lanes_visible\":false");
}

static void track_load_minimal(void) {
    boot();
    req(WD_CHANNEL_MUSIC, "{\"op\":\"music.show\"}");
    const char *r = req(WD_CHANNEL_MUSIC,
        "{\"op\":\"track.load\",\"track_id\":\"t1\",\"title\":\"T\",\"artist\":\"A\","
        "\"lyrics\":[\"a\",\"b\"],\"line_index\":0}");
    is_ok(r);
    has(r, "\"rendered\":true");
    has(r, "\"effective_visible\":true");
    has(r, "\"track\":{\"track_id\":\"t1\",\"line_count\":2,\"line_index\":0,\"generation\":1}");
    has(r, "\"cover_bytes\":0");
    assert(g_fake.music_renders == 1);
}

static void track_load_missing_fields(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"title\":\"T\","
        "\"lyrics\":[\"a\"],\"line_index\":0}");
    is_err(r, "invalid_track_request");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"\",\"title\":\"T\","
        "\"lyrics\":[\"a\"],\"line_index\":0}");
    is_err(r, "invalid_track_request");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"title\":\"T\","
        "\"line_index\":0}");
    is_err(r, "lyrics_required");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"title\":\"T\","
        "\"lyrics\":[\"a\"]}");
    is_err(r, "line_index_required");
}

static void track_load_lyrics_bad(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":\"a\",\"line_index\":0}");
    is_err(r, "invalid_lyrics");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"lyrics\":[],"
        "\"line_index\":0}");
    is_err(r, "invalid_lyrics");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"],\"line_index\":0}");
    is_err(r, "lyric_lines_must_be_1_to_5");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[1],\"line_index\":0}");
    is_err(r, "invalid_lyrics");
}

static void track_load_line_index_range(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\",\"b\"],\"line_index\":2}");
    is_err(r, "line_index_out_of_range");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\",\"b\"],\"line_index\":-2}");
    is_err(r, "line_index_out_of_range");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\",\"b\"],\"line_index\":-1}");
    is_ok(r);
}

static void track_load_position_bounds(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\"],\"line_index\":0,\"duration_ms\":1000,\"position_ms\":1001}");
    is_err(r, "invalid_range_or_type");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"lyrics\":[\"a\"],"
        "\"line_index\":0,\"duration_ms\":1000,\"position_ms\":999}");
    is_ok(r);
}

static void track_load_cover(void) {
    boot();
    req(WD_CHANNEL_MUSIC, "{\"op\":\"music.show\"}");
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\"],\"line_index\":0,\"cover_rgba\":\"AAAA\",\"cover_width\":4,"
        "\"cover_height\":4}");
    is_ok(r);
    has(r, "\"cover_bytes\":3");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"lyrics\":[\"a\"],"
        "\"line_index\":0,\"cover_rgba\":\"A!==\"}");
    is_err(r, "invalid_cover_or_allocation_failed");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"lyrics\":[\"a\"],"
        "\"line_index\":0,\"cover_rgba\":\"AB\"}");
    is_err(r, "invalid_cover_or_allocation_failed");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"lyrics\":[\"a\"],"
        "\"line_index\":0,\"cover_rgba\":5}");
    is_err(r, "invalid_cover_or_allocation_failed");
}

static void track_load_unknown_field(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\"],\"line_index\":0,\"bogus\":1}");
    is_err(r, "unknown_field_for_port");
}

static void track_sync(void) {
    boot();
    req(WD_CHANNEL_MUSIC, "{\"op\":\"music.show\"}");
    req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"lyrics\":[\"a\",\"b\"],"
        "\"line_index\":0}");
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\","
        "\"line_index\":1}");
    is_ok(r);
    has(r, "\"line_index\":1");
    assert(g_fake.music_lines == 1);
    assert(g_fake.last_line_index == 1);
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t2\",\"line_index\":1}");
    is_err(r, "track_mismatch");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\",\"line_index\":5}");
    is_err(r, "line_index_out_of_range");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\",\"title\":\"x\","
        "\"line_index\":1}");
    is_err(r, "sync_fields_only");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"line_index\":1}");
    is_err(r, "invalid_track_request");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\"}");
    is_err(r, "line_index_required");
}

static void track_sync_before_load(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\","
        "\"line_index\":0}");
    is_err(r, "track_mismatch");
}

static void config_get_defaults(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"config.get\"}");
    is_ok(r);
    has(r, "\"x\":180");
    has(r, "\"guidance_x\":790");
    has(r, "\"ttl_ms\":20000");
    has(r, "\"theme\":\"auto\"");
    has(r, "\"persistent\":false");
    has(r, "\"storage_error\":null");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.get\"}");
    is_ok(r);
    has(r, "\"x\":1080");
    has(r, "\"lyric_lines\":3");
    has(r, "\"font_size\":26");
    has(r, "\"music_layout\":2");
    has(r, "\"cover_size\":200");
    has(r, "\"style\":{\"lyric_lines\":3");
}

static void config_set_roundtrip(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"x\":100,\"width\":800,"
        "\"theme\":\"night\"}");
    is_ok(r);
    has(r, "\"x\":100");
    has(r, "\"width\":800");
    has(r, "\"theme\":\"night\"");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.get\"}");
    is_ok(r);
    has(r, "\"x\":100");
    has(r, "\"saved_config\"");
}

static void config_set_nav_validation(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"theme\":\"dusk\"}");
    is_err(r, "theme_must_be_auto_day_or_night");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"x\":2000}");
    is_err(r, "window_out_of_bounds");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"width\":0}");
    is_err(r, "window_out_of_bounds");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"guidance_x\":1900}");
    is_err(r, "guidance_window_out_of_bounds");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"guidance_background_color\":\"#12\"}");
    is_err(r, "invalid_palette");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"opacity\":300}");
    is_err(r, "invalid_range_or_type");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"ttl_ms\":500}");
    is_err(r, "invalid_range_or_type");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"bogus\":1}");
    is_err(r, "configuration_fields_only");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.get\"}");
    has(r, "\"x\":180");
}

static void config_set_music_validation(void) {
    boot();
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"lyric_lines\":6}");
    is_err(r, "lyric_lines_must_be_1_to_5");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"music_layout\":4}");
    is_err(r, "invalid_range_or_type");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"stroke_width\":9}");
    is_err(r, "invalid_range_or_type");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"day_colors\":{\"color\":\"#zzzzzzzz\"}}");
    is_err(r, "invalid_palette");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"day_colors\":[1]}");
    is_err(r, "invalid_palette");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"stroke_color\":\"red\"}");
    is_err(r, "invalid_palette");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"font_size\":20,"
        "\"day_colors\":{\"color\":\"#11223344\"},\"night_colors\":{\"color\":\"#55667788\"}}");
    is_ok(r);
    has(r, "\"color\":\"#11223344\"");
    has(r, "\"color\":\"#55667788\"");
}

static void config_set_save_failure(void) {
    boot_dir("wd_missing_dir");
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"x\":100}");
    is_err(r, "config_save_failed_state_unchanged");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.get\"}");
    has(r, "\"x\":180");
}

static void config_persistence(void) {
    remove("wd_cfg/navigation.json");
    remove("wd_cfg/music.json");
    boot_dir("wd_cfg");
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"x\":100,\"width\":800}");
    is_ok(r);
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"font_size\":30}");
    is_ok(r);
    wd_shutdown(&g_core);
    boot_dir("wd_cfg");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"config.get\"}");
    has(r, "\"x\":100");
    has(r, "\"width\":800");
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"config.get\"}");
    has(r, "\"font_size\":30");
    wd_shutdown(&g_core);
}

static void appearance_resolution(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    has(r, "\"appearance\":{\"requested\":\"auto\",\"effective\":\"day\"");
    has(r, "\"source_available\":true");
    g_fake.day = 0;
    r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    has(r, "\"effective\":\"night\"");
    req(WD_CHANNEL_NAV, "{\"op\":\"config.set\",\"theme\":\"day\"}");
    g_fake.day = 0;
    r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    has(r, "\"requested\":\"day\",\"effective\":\"day\"");
    g_fake.day_available = false;
    r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    has(r, "\"requested\":\"day\",\"effective\":\"day\"");
    has(r, "\"source_available\":false");
}

static void unicode_escape(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"current_road\":\"\\u4e2d\\u6587\"}");
    is_ok(r);
    assert(g_fake.nav_renders == 1);
}

static void revision_monotonic(void) {
    boot();
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    has(r, "\"revision\":1");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"widgets.status\"}");
    has(r, "\"revision\":1");
    has(r, "\"accepted_revision\":1");
    r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.hide\"}");
    has(r, "\"revision\":2");
}

static void ttl_default(void) {
    boot();
    req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true}");
    has(r, "\"ttl_ms\":20000");
    wd_tick(&g_core, 20999);
    assert(g_fake.nav_hides == 0);
    wd_tick(&g_core, 21000);
    assert(g_fake.nav_hides == 1);
}

static void music_ttl_from_config(void) {
    boot();
    req(WD_CHANNEL_MUSIC, "{\"op\":\"music.show\"}");
    req(WD_CHANNEL_MUSIC, "{\"op\":\"config.set\",\"ttl_ms\":5000}");
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\"],\"line_index\":0}");
    is_ok(r);
    has(r, "\"ttl_ms\":5000");
    wd_tick(&g_core, 5999);
    assert(g_fake.music_hides == 0);
    wd_tick(&g_core, 6000);
    assert(g_fake.music_hides == 1);
}

static void music_render_fail(void) {
    boot();
    req(WD_CHANNEL_MUSIC, "{\"op\":\"music.show\"}");
    g_fake.music_render_ok = false;
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\","
        "\"lyrics\":[\"a\"],\"line_index\":0}");
    is_err(r, "display_write_failed");
    assert(g_fake.music_hides == 0);
    g_fake.music_render_ok = true;
    r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t2\",\"lyrics\":[\"a\"],"
        "\"line_index\":0}");
    is_ok(r);
    has(r, "\"generation\":2");
}

static void sync_line_hidden(void) {
    boot();
    req(WD_CHANNEL_MUSIC, "{\"op\":\"track.load\",\"track_id\":\"t1\",\"lyrics\":[\"a\",\"b\"],"
        "\"line_index\":0}");
    const char *r = req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\","
        "\"line_index\":1}");
    is_ok(r);
    assert(g_fake.music_lines == 0);
}

struct Entry {
    const char *name;
    void (*fn)(void);
};

static const struct Entry TABLE[] = {
    {"theme_get", theme_get},
    {"theme_get_unavailable", theme_get_unavailable},
    {"invalid_json_empty", invalid_json_empty},
    {"invalid_json_trailing", invalid_json_trailing},
    {"invalid_json_trailing_comma", invalid_json_trailing_comma},
    {"duplicate_field", duplicate_field},
    {"request_too_large", request_too_large},
    {"invalid_request_no_op", invalid_request_no_op},
    {"invalid_request_root_array", invalid_request_root_array},
    {"invalid_operation", invalid_operation},
    {"cross_channel_operation", cross_channel_operation},
    {"fields_not_allowed", fields_not_allowed},
    {"show_hide_nav", show_hide_nav},
    {"update_full_fields", update_full_fields},
    {"update_unknown_field", update_unknown_field},
    {"update_bad_turn_type", update_bad_turn_type},
    {"update_negative_distance", update_negative_distance},
    {"update_ttl_zero", update_ttl_zero},
    {"update_lights_not_array", update_lights_not_array},
    {"update_lights_color_without_seconds", update_lights_color_without_seconds},
    {"update_lights_mixed", update_lights_mixed},
    {"update_lights_color_range", update_lights_color_range},
    {"update_lanes_bad_element", update_lanes_bad_element},
    {"update_empty_arrays", update_empty_arrays},
    {"update_render_fail", update_render_fail},
    {"update_without_show", update_without_show},
    {"lease_expiry", lease_expiry},
    {"lease_renew", lease_renew},
    {"hide_clears_lease", hide_clears_lease},
    {"widgets_set", widgets_set},
    {"widgets_status_fields", widgets_status_fields},
    {"track_load_minimal", track_load_minimal},
    {"track_load_missing_fields", track_load_missing_fields},
    {"track_load_lyrics_bad", track_load_lyrics_bad},
    {"track_load_line_index_range", track_load_line_index_range},
    {"track_load_position_bounds", track_load_position_bounds},
    {"track_load_cover", track_load_cover},
    {"track_load_unknown_field", track_load_unknown_field},
    {"track_sync", track_sync},
    {"track_sync_before_load", track_sync_before_load},
    {"config_get_defaults", config_get_defaults},
    {"config_set_roundtrip", config_set_roundtrip},
    {"config_set_nav_validation", config_set_nav_validation},
    {"config_set_music_validation", config_set_music_validation},
    {"config_set_save_failure", config_set_save_failure},
    {"config_persistence", config_persistence},
    {"appearance_resolution", appearance_resolution},
    {"unicode_escape", unicode_escape},
    {"revision_monotonic", revision_monotonic},
    {"ttl_default", ttl_default},
    {"music_ttl_from_config", music_ttl_from_config},
    {"music_render_fail", music_render_fail},
    {"sync_line_hidden", sync_line_hidden}
};

int main(void) {
    WD_MKDIR("wd_cfg");
    unsigned n = 0;
    const size_t count = sizeof(TABLE) / sizeof(TABLE[0]);
    for (size_t i = 0; i < count; i++) {
        TABLE[i].fn();
        wd_shutdown(&g_core);
        printf("PASS %s\n", TABLE[i].name);
        fflush(stdout);
        n++;
    }
    printf("TESTS_PASSED=%u\n", n);
    return 0;
}
