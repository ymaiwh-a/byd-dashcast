#include "widgetd.h"
#include "wd_canvas.h"
#include "wd_font.h"
#include "wd_icons.h"
#include "wd_render.h"

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef NDEBUG
#error Widgetd render tests require assertions
#endif

#define T_W 400
#define T_H 300

typedef struct {
    WdCanvas cv;
    uint32_t *fb;
    unsigned presents;
    bool fail_lock;
} Surf;

static Surf g_nav;
static Surf g_music;
static WdCore g_core;
static WdRender *g_render;
static WdFont *g_font;
static char g_reply[WD_REPLY_CAP];
static int g_day = 1;
static bool g_day_avail = true;

static bool surf_lock(void *context, WdCanvas *out) {
    Surf *s = context;
    if (s->fail_lock) return false;
    *out = s->cv;
    return true;
}

static void surf_present(void *context) {
    ((Surf *)context)->presents++;
}

static bool test_theme_day(void *context, int *day) {
    (void)context;
    if (!g_day_avail) return false;
    *day = g_day;
    return true;
}

static void cleanup(void) {
    if (g_render != NULL) {
        wd_render_destroy(g_render);
        g_render = NULL;
    }
    if (g_font != NULL) {
        wd_font_fake_destroy(g_font);
        g_font = NULL;
    }
    free(g_nav.fb);
    free(g_music.fb);
    memset(&g_nav, 0, sizeof(g_nav));
    memset(&g_music, 0, sizeof(g_music));
    wd_shutdown(&g_core);
    memset(&g_core, 0, sizeof(g_core));
    g_day = 1;
    g_day_avail = true;
}

static void boot(void) {
    cleanup();
    WdConfig cfg;
    memset(&cfg, 0, sizeof(cfg));
    cfg.epoch = 7;
    WdPort empty;
    memset(&empty, 0, sizeof(empty));
    assert(wd_init(&g_core, &cfg, &empty));
    wd_tick(&g_core, 1000);
    g_nav.fb = calloc((size_t)T_W * (size_t)T_H, sizeof(uint32_t));
    g_music.fb = calloc((size_t)T_W * (size_t)T_H, sizeof(uint32_t));
    assert(g_nav.fb != NULL && g_music.fb != NULL);
    wd_canvas_init(&g_nav.cv, g_nav.fb, T_W, T_H, T_W);
    wd_canvas_init(&g_music.cv, g_music.fb, T_W, T_H, T_W);
    g_font = wd_font_fake_create();
    assert(g_font != NULL);
    static WdSurface ns;
    static WdSurface ms;
    ns.ctx = &g_nav;
    ns.lock = surf_lock;
    ns.present = surf_present;
    ms.ctx = &g_music;
    ms.lock = surf_lock;
    ms.present = surf_present;
    g_render = wd_render_create(&g_core, &ns, &ms, g_font, test_theme_day, NULL);
    assert(g_render != NULL);
    g_core.port = wd_render_port(g_render);
}

static const char *req(int channel, const char *json) {
    size_t n = wd_handle_line(&g_core, channel, json, strlen(json), g_reply, sizeof(g_reply));
    assert(n > 0 && n < sizeof(g_reply));
    return g_reply;
}

static void is_ok(const char *r) {
    if (strstr(r, "\"ok\":true") == NULL) {
        printf("ISOK_FAIL %s\n", r);
        fflush(stdout);
    }
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

static int count_color(const WdCanvas *cv, int x0, int y0, int x1, int y1, uint32_t argb) {
    int n = 0;
    for (int y = y0; y < y1 && y < cv->height; y++) {
        for (int x = x0; x < x1 && x < cv->width; x++) {
            if (x < 0 || y < 0) continue;
            if (wd_canvas_get(cv, x, y) == argb) n++;
        }
    }
    return n;
}

static int count_alpha(const WdCanvas *cv, int x0, int y0, int x1, int y1) {
    int n = 0;
    for (int y = y0; y < y1 && y < cv->height; y++) {
        for (int x = x0; x < x1 && x < cv->width; x++) {
            if (x < 0 || y < 0) continue;
            if ((wd_canvas_get(cv, x, y) >> 24) != 0u) n++;
        }
    }
    return n;
}

static void set_nav_cfg(const char *fields) {
    char buf[512];
    snprintf(buf, sizeof(buf), "{\"op\":\"config.set\",%s}", fields);
    is_ok(req(WD_CHANNEL_NAV, buf));
}

static void set_music_cfg(const char *fields) {
    char buf[1024];
    snprintf(buf, sizeof(buf), "{\"op\":\"config.set\",%s}", fields);
    is_ok(req(WD_CHANNEL_MUSIC, buf));
}

static void show_nav(void) {
    is_ok(req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}"));
}

static void update_nav(const char *fields) {
    char buf[1024];
    snprintf(buf, sizeof(buf), "{\"op\":\"update\",%s}", fields);
    is_ok(req(WD_CHANNEL_NAV, buf));
}

static void show_music(void) {
    is_ok(req(WD_CHANNEL_MUSIC, "{\"op\":\"music.show\"}"));
}

static void load_track(const char *fields) {
    char buf[2048];
    snprintf(buf, sizeof(buf), "{\"op\":\"track.load\",%s}", fields);
    is_ok(req(WD_CHANNEL_MUSIC, buf));
}

static void base_nav_cfg(void) {
    set_nav_cfg("\"theme\":\"day\",\"guidance_x\":50,\"guidance_y\":100,"
                "\"guidance_width\":200,\"guidance_height\":80,"
                "\"guidance_background_color\":\"#20304080\","
                "\"guidance_background_radius\":0");
}

static void base_music_cfg(void) {
    set_music_cfg("\"theme\":\"day\",\"x\":0,\"y\":0,\"width\":300,\"height\":200,"
                  "\"cover_size\":100,\"cover_radius\":0,\"cover_gap\":10,"
                  "\"music_layout\":2,\"show_cover\":true,\"show_progress\":false,"
                  "\"show_metadata\":false,\"lyric_lines\":3,\"before_lines\":1,"
                  "\"after_lines\":1,\"font_size\":20,\"line_gap\":6,"
                  "\"progress_width\":0,\"progress_gap\":0,"
                  "\"background_color\":\"#00000000\",\"stroke_color\":\"#000000FF\","
                  "\"stroke_width\":0");
}

static void guidance_bg(void) {
    boot();
    base_nav_cfg();
    show_nav();
    update_nav("\"active\":true");
    assert(wd_canvas_get(&g_nav.cv, 100, 140) == 0x80203040u);
    assert(wd_canvas_get(&g_nav.cv, 300, 140) == 0u);
    assert(g_nav.presents >= 1u);
}

static void guidance_radius(void) {
    boot();
    base_nav_cfg();
    set_nav_cfg("\"theme\":\"day\",\"guidance_background_radius\":20");
    show_nav();
    update_nav("\"active\":true");
    assert(wd_canvas_get(&g_nav.cv, 50, 100) == 0u);
    assert(wd_canvas_get(&g_nav.cv, 150, 140) == 0x80203040u);
}

static void maneuver_icon(void) {
    boot();
    base_nav_cfg();
    set_nav_cfg("\"theme\":\"day\",\"guidance_background_color\":\"#00000000\"");
    show_nav();
    update_nav("\"active\":true,\"turn\":\"left\",\"distance_m\":500");
    assert(count_color(&g_nav.cv, 58, 116, 106, 164, 0xFF000000u) > 0);
    assert(count_color(&g_nav.cv, 114, 100, 250, 180, 0xFF000000u) > 0);
}

static void lights_top_row(void) {
    boot();
    base_nav_cfg();
    set_nav_cfg("\"theme\":\"day\",\"guidance_background_color\":\"#00000000\"");
    show_nav();
    update_nav("\"active\":true,\"lights\":[{\"color\":1,\"seconds\":30}]");
    assert(count_color(&g_nav.cv, 0, 44, T_W, 92, 0xFFE53935u) > 0);
    assert(count_color(&g_nav.cv, 0, 188, T_W, 236, 0xFFE53935u) == 0);
}

static void lights_bottom_row(void) {
    boot();
    base_nav_cfg();
    set_nav_cfg("\"theme\":\"day\",\"lights_on_top\":false,"
                "\"guidance_background_color\":\"#00000000\"");
    show_nav();
    update_nav("\"active\":true,\"lights\":[{\"color\":2,\"seconds\":5}]");
    assert(count_color(&g_nav.cv, 0, 188, T_W, 236, 0xFF43A047u) > 0);
    assert(count_color(&g_nav.cv, 0, 44, T_W, 92, 0xFF43A047u) == 0);
}

static void lanes_row(void) {
    boot();
    base_nav_cfg();
    set_nav_cfg("\"theme\":\"day\",\"guidance_background_color\":\"#00000000\"");
    show_nav();
    update_nav("\"active\":true,\"lanes\":[{\"directions\":\"straight\",\"active_directions\":"
               "\"unknown\",\"recommended\":false},{\"directions\":\"left|uturn_left\","
               "\"active_directions\":\"left\",\"recommended\":true}]");
    assert(count_color(&g_nav.cv, 0, 188, T_W, 236, 0xFF000000u) > 0);
}

static void lanes_unknown_default(void) {
    boot();
    base_nav_cfg();
    set_nav_cfg("\"theme\":\"day\",\"guidance_background_color\":\"#00000000\"");
    show_nav();
    update_nav("\"active\":true,\"lanes\":[{\"directions\":\"variable\"}]");
    assert(count_color(&g_nav.cv, 0, 188, T_W, 236, 0xFF000000u) > 0);
}

static void lanes_absent_no_draw(void) {
    boot();
    base_nav_cfg();
    set_nav_cfg("\"theme\":\"day\",\"guidance_background_color\":\"#00000000\"");
    show_nav();
    update_nav("\"active\":true,\"lanes\":[{\"dir\":\"straight\"}]");
    assert(count_color(&g_nav.cv, 0, 188, T_W, 236, 0xFF000000u) == 0);
}

static void music_cover(void) {
    boot();
    base_music_cfg();
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\"],\"line_index\":0,"
               "\"cover_rgba\":\"/wAA/wD/AP8AAP////8A/w==\",\"cover_width\":2,"
               "\"cover_height\":2");
    has(g_reply, "\"cover_bytes\":16");
    assert(wd_canvas_get(&g_music.cv, 10, 10) == 0xFFFF0000u);
    assert(wd_canvas_get(&g_music.cv, 60, 10) == 0xFF00FF00u);
    assert(wd_canvas_get(&g_music.cv, 10, 60) == 0xFF0000FFu);
    assert(wd_canvas_get(&g_music.cv, 60, 60) == 0xFFFFFF00u);
}

static void cover_mismatch(void) {
    boot();
    base_music_cfg();
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\"],\"line_index\":0,"
               "\"cover_rgba\":\"AAAA\",\"cover_width\":4,\"cover_height\":4");
    has(g_reply, "\"cover_bytes\":3");
    assert(count_alpha(&g_music.cv, 0, 0, 100, 100) == 0);
}

static void lyric_colors(void) {
    boot();
    base_music_cfg();
    set_music_cfg("\"theme\":\"night\"");
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\",\"b\",\"c\"],\"line_index\":1");
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFF00E676u) > 0);
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFF81C784u) > 0);
}

static void progress_ratio(void) {
    boot();
    base_music_cfg();
    set_music_cfg("\"show_progress\":true,\"progress_width\":8,\"progress_gap\":2");
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\"],\"line_index\":0,"
               "\"duration_ms\":1000,\"position_ms\":250");
    assert(wd_canvas_get(&g_music.cv, 120, 196) == 0xFF0D47A1u);
    assert(wd_canvas_get(&g_music.cv, 200, 196) == 0xFF1E88E5u);
}

static void theme_auto_flip(void) {
    boot();
    base_music_cfg();
    set_music_cfg("\"theme\":\"auto\"");
    show_music();
    g_day = 0;
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\",\"b\",\"c\"],\"line_index\":1");
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFF00E676u) > 0);
    g_day = 1;
    is_ok(req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\",\"line_index\":2}"));
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFF0D47A1u) > 0);
}

static void lyric_window(void) {
    boot();
    base_music_cfg();
    set_music_cfg("\"day_colors\":{\"color\":\"#0D47A1FF\",\"previous_color\":\"#FF0000FF\","
                  "\"next_color\":\"#00FF00FF\"}");
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\",\"b\",\"c\"],\"line_index\":0");
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFF00FF00u) > 0);
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFFFF0000u) == 0);
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFF0D47A1u) > 0);
    is_ok(req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\",\"line_index\":2}"));
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFFFF0000u) > 0);
}

static void hide_clears_nav(void) {
    boot();
    base_nav_cfg();
    show_nav();
    update_nav("\"active\":true,\"turn\":\"left\"");
    assert(count_alpha(&g_nav.cv, 0, 0, T_W, T_H) > 0);
    is_ok(req(WD_CHANNEL_NAV, "{\"op\":\"navigation.hide\"}"));
    assert(count_alpha(&g_nav.cv, 0, 0, T_W, T_H) == 0);
}

static void render_fail_lock(void) {
    boot();
    base_nav_cfg();
    g_nav.fail_lock = true;
    const char *r = req(WD_CHANNEL_NAV, "{\"op\":\"navigation.show\"}");
    has(r, "\"rendered\":false");
    g_nav.fail_lock = false;
    show_nav();
    g_nav.fail_lock = true;
    r = req(WD_CHANNEL_NAV, "{\"op\":\"update\",\"active\":true}");
    is_err(r, "display_write_failed");
    g_nav.fail_lock = false;
}

static void icon_decode_sanity(void) {
    const WdIconRef *ref = wd_icon_find(wd_maneuver_icons, wd_maneuver_icon_count, "left");
    assert(ref != NULL);
    static uint32_t buf[WD_ICON_PX];
    assert(wd_icon_decode(ref->slot, buf) == 0);
    int n = 0;
    for (int i = 0; i < WD_ICON_PX; i++) {
        if ((buf[i] >> 24) != 0u) n++;
    }
    assert(n > 0);
}

static void text_stroke(void) {
    boot();
    base_music_cfg();
    set_music_cfg("\"theme\":\"night\",\"stroke_width\":3,\"stroke_color\":\"#FF0000FF\"");
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\",\"b\",\"c\"],\"line_index\":1");
    assert(count_color(&g_music.cv, 110, 0, T_W, T_H, 0xFFFF0000u) > 0);
}

static void layout_top_cover(void) {
    boot();
    base_music_cfg();
    set_music_cfg("\"music_layout\":1");
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\"],\"line_index\":0,"
               "\"cover_rgba\":\"/wAA/wD/AP8AAP////8A/w==\",\"cover_width\":2,"
               "\"cover_height\":2");
    assert(wd_canvas_get(&g_music.cv, 110, 10) == 0xFFFF0000u);
    assert(wd_canvas_get(&g_music.cv, 10, 10) == 0u);
}

static void music_line_present(void) {
    boot();
    base_music_cfg();
    show_music();
    load_track("\"track_id\":\"t1\",\"lyrics\":[\"a\",\"b\",\"c\"],\"line_index\":0");
    unsigned before = g_music.presents;
    is_ok(req(WD_CHANNEL_MUSIC, "{\"op\":\"track.sync\",\"track_id\":\"t1\",\"line_index\":1}"));
    assert(g_music.presents == before + 1u);
}

struct Entry {
    const char *name;
    void (*fn)(void);
};

static const struct Entry TABLE[] = {
    {"guidance_bg", guidance_bg},
    {"guidance_radius", guidance_radius},
    {"maneuver_icon", maneuver_icon},
    {"lights_top_row", lights_top_row},
    {"lights_bottom_row", lights_bottom_row},
    {"lanes_row", lanes_row},
    {"lanes_unknown_default", lanes_unknown_default},
    {"lanes_absent_no_draw", lanes_absent_no_draw},
    {"music_cover", music_cover},
    {"cover_mismatch", cover_mismatch},
    {"lyric_colors", lyric_colors},
    {"progress_ratio", progress_ratio},
    {"theme_auto_flip", theme_auto_flip},
    {"lyric_window", lyric_window},
    {"hide_clears_nav", hide_clears_nav},
    {"render_fail_lock", render_fail_lock},
    {"icon_decode_sanity", icon_decode_sanity},
    {"text_stroke", text_stroke},
    {"layout_top_cover", layout_top_cover},
    {"music_line_present", music_line_present}
};

int main(void) {
    unsigned n = 0;
    const size_t count = sizeof(TABLE) / sizeof(TABLE[0]);
    for (size_t i = 0; i < count; i++) {
        TABLE[i].fn();
        cleanup();
        printf("PASS %s\n", TABLE[i].name);
        fflush(stdout);
        n++;
    }
    printf("TESTS_PASSED=%u\n", n);
    return 0;
}
