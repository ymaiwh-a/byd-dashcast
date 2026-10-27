#ifndef DASHCAST_WIDGETD_H
#define DASHCAST_WIDGETD_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define WD_CHANNEL_HMI 0
#define WD_CHANNEL_NAV 1
#define WD_CHANNEL_MUSIC 2

#define WD_LINE_CAP 4194304u
#define WD_REPLY_CAP 8192u
#define WD_LYRIC_LINES_MAX 5
#define WD_NAV_W 1920
#define WD_NAV_H 720

typedef struct {
    char current_road[256];
    char destination[512];
    char eta[64];
    char turn[64];
    char distance_text[64];
    char exit_name[64];
    char service_area[64];
    char traffic_text[64];
    char lights_json[2048];
    char lanes_json[2048];
    int64_t distance_m;
    int64_t remaining_m;
    int64_t remaining_s;
    int64_t limit_kph;
    int64_t speed_kph;
    bool active;
    bool show_next;
    bool show_alerts;
    bool show_limit;
    int64_t next_ttl_ms;
    int64_t alerts_ttl_ms;
} WdNavFrame;

typedef struct {
    char track_id[128];
    char title[256];
    char artist[256];
    char lines[WD_LYRIC_LINES_MAX][256];
    int line_count;
    int line_index;
    bool playing;
    int64_t duration_ms;
    int64_t position_ms;
    int64_t cover_bytes;
} WdMusicFrame;

typedef struct {
    void *context;
    bool (*nav_render)(void *context, const WdNavFrame *frame);
    void (*nav_hide)(void *context);
    bool (*music_render)(void *context, const WdMusicFrame *frame);
    void (*music_hide)(void *context);
    void (*music_line)(void *context, int line_index);
    bool (*theme_day)(void *context, int *day);
} WdPort;

typedef struct {
    char config_dir[256];
    uint64_t epoch;
} WdConfig;

typedef struct {
    int x;
    int y;
    int width;
    int height;
    int opacity;
    char theme[8];
    int64_t ttl_ms;
    bool show_lanes;
    bool show_lights;
    bool show_alerts;
    bool lights_on_top;
    int64_t lanes_ttl_ms;
    int64_t lights_ttl_ms;
    int64_t alerts_ttl_ms;
    int guidance_x;
    int guidance_y;
    int guidance_width;
    int guidance_height;
    char guidance_bg_color[16];
    int guidance_bg_radius;
    int guidance_pad_top;
    int guidance_pad_bottom;
    int light_size;
    int lane_size;
    int font_size;
    int secondary_size;
    int distance_size;
    int line_gap;
} WdNavCfg;

typedef struct {
    int x;
    int y;
    int width;
    int height;
    int opacity;
    char theme[8];
    int64_t ttl_ms;
    int lyric_lines;
    int font_size;
    int secondary_size;
    int title_size;
    int artist_size;
    int stroke_width;
    char stroke_color[16];
    bool show_cover;
    bool show_progress;
    bool show_metadata;
    bool lyric_animation;
    int music_layout;
    int cover_size;
    int cover_radius;
    int progress_width;
    int progress_gap;
    int line_gap;
    int before_lines;
    int after_lines;
    int cover_gap;
    int anim;
    int transition_ms;
    char background_color[16];
    int background_radius;
    int background_padding_top;
    int background_padding_bottom;
    char day_color[16];
    char day_prev_color[16];
    char day_next_color[16];
    char night_color[16];
    char night_prev_color[16];
    char night_next_color[16];
} WdMusicCfg;

typedef struct {
    WdNavFrame frame;
    bool selected;
    bool visible;
    bool rendered;
    uint64_t lease_until;
    int64_t ttl_ms;
} WdNavWidget;

typedef struct {
    WdMusicFrame frame;
    bool selected;
    bool visible;
    bool rendered;
    uint64_t lease_until;
    int64_t ttl_ms;
    char loaded_track_id[128];
    uint64_t generation;
} WdMusicWidget;

#define WD_JSON_POOL 6144
#define WD_STR_POOL 4194304u

typedef struct WdJson {
    unsigned char type;
    int64_t i;
    double d;
    const char *s;
    size_t len;
    uint32_t first_child;
    uint32_t next;
    uint32_t n;
    const char *key;
    size_t key_len;
} WdJson;

typedef struct {
    WdJson *pool;
    uint32_t used;
    char *str_pool;
    size_t str_used;
    bool overflow;
    bool duplicate;
} WdParser;

typedef struct {
    WdConfig config;
    WdPort port;
    WdParser parser;
    WdNavWidget nav;
    WdMusicWidget music;
    WdNavCfg nav_cfg;
    WdMusicCfg music_cfg;
    bool nav_cfg_saved;
    bool music_cfg_saved;
    uint64_t revision;
    uint64_t cycle_index;
    uint64_t last_now;
    bool nav_lease_live;
    bool music_lease_live;
    uint8_t *cover_px;
    int cover_w;
    int cover_h;
    size_t cover_cap;
} WdCore;

bool wd_init(WdCore *core, const WdConfig *config, const WdPort *port);
void wd_shutdown(WdCore *core);
size_t wd_handle_line(WdCore *core, int channel, const char *line, size_t len,
                      char *reply, size_t reply_cap);
void wd_tick(WdCore *core, uint64_t monotonic_ms);

#ifdef __cplusplus
}
#endif
#endif
