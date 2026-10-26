#include "wd_render.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "wd_icons.h"

struct WdRender {
    WdCore *core;
    const WdSurface *nav;
    const WdSurface *music;
    WdFont *font;
    bool (*theme_day)(void *ctx, int *day);
    void *theme_ctx;
};

static uint32_t hex_nib(char ch) {
    if (ch >= '0' && ch <= '9') return (uint32_t)(ch - '0');
    if (ch >= 'a' && ch <= 'f') return (uint32_t)(ch - 'a' + 10);
    if (ch >= 'A' && ch <= 'F') return (uint32_t)(ch - 'A' + 10);
    return 0;
}

static uint32_t parse_color(const char *s) {
    if (s == NULL || s[0] != '#' || strlen(s) != 9) return 0;
    uint32_t r = hex_nib(s[1]) * 16u + hex_nib(s[2]);
    uint32_t g = hex_nib(s[3]) * 16u + hex_nib(s[4]);
    uint32_t b = hex_nib(s[5]) * 16u + hex_nib(s[6]);
    uint32_t a = hex_nib(s[7]) * 16u + hex_nib(s[8]);
    return (a << 24) | (r << 16) | (g << 8) | b;
}

static bool is_night(WdRender *r, const char *theme) {
    if (strcmp(theme, "night") == 0) return true;
    if (strcmp(theme, "day") == 0) return false;
    int day = 1;
    if (r->theme_day != NULL && !r->theme_day(r->theme_ctx, &day)) day = 1;
    return day == 0;
}

static int clampi(int v, int lo, int hi) {
    if (v < lo) return lo;
    if (v > hi) return hi;
    return v;
}

static int draw_text(WdRender *r, WdCanvas *c, int x, int baseline, int right, const char *s,
                     int size, int bold, uint32_t argb, int stroke, uint32_t stroke_argb) {
    if (s == NULL || s[0] == '\0') return 0;
    size_t len = strlen(s);
    size_t pos = 0;
    int start = x;
    while (pos < len) {
        uint32_t cp = wd_font_next_cp(s, len, &pos);
        WdGlyph g;
        if (wd_font_glyph(r->font, size, bold, cp, &g) != 0) continue;
        if (right > start && x + g.advance > right) break;
        if (stroke > 0 && g.mask != NULL) {
            for (int dy = -stroke; dy <= stroke; dy += stroke) {
                for (int dx = -stroke; dx <= stroke; dx += stroke) {
                    if (dx == 0 && dy == 0) continue;
                    wd_canvas_blend_mask(c, x + g.left + dx, baseline - g.top + dy, g.width,
                                         g.height, g.stride, g.mask, stroke_argb);
                }
            }
        }
        if (g.mask != NULL) {
            wd_canvas_blend_mask(c, x + g.left, baseline - g.top, g.width, g.height, g.stride,
                                 g.mask, argb);
        }
        x += g.advance;
    }
    return x - start;
}

static int text_width(WdRender *r, const char *s, int size, int bold) {
    return wd_font_text_width(r->font, size, bold, s);
}

static int lights_parse(const char *js, int *colors, int *seconds, int cap) {
    int n = 0;
    const char *p = js;
    while (p != NULL && *p != '\0' && n < cap) {
        const char *ob = strchr(p, '{');
        if (ob == NULL) break;
        const char *cb = strchr(ob, '}');
        if (cb == NULL) break;
        int color = -1;
        int sec = -1;
        const char *k = strstr(ob, "\"color\":");
        if (k != NULL && k < cb) color = atoi(k + 8);
        if (color < 0) {
            k = strstr(ob, "\"state\":");
            if (k != NULL && k < cb) color = atoi(k + 8);
        }
        k = strstr(ob, "\"seconds\":");
        if (k != NULL && k < cb) sec = atoi(k + 10);
        colors[n] = color;
        seconds[n] = sec;
        n++;
        p = cb + 1;
    }
    return n;
}

static int lanes_parse(const char *js, char names[][32], int cap) {
    int n = 0;
    const char *p = js;
    while (p != NULL && *p != '\0' && n < cap) {
        const char *ob = strchr(p, '{');
        if (ob == NULL) break;
        const char *cb = strchr(ob, '}');
        if (cb == NULL) break;
        const char *k = strstr(ob, "\"directions\":\"");
        if (k != NULL && k < cb) {
            k += 14;
            int j = 0;
            while (k[j] != '\0' && k[j] != '"' && j < 31) {
                names[n][j] = k[j];
                j++;
            }
            names[n][j] = '\0';
            n++;
        }
        p = cb + 1;
    }
    return n;
}

static uint32_t light_argb(int color) {
    switch (color) {
        case 1: return 0xFFE53935u;
        case 2: return 0xFF43A047u;
        case 3: return 0xFFFDD835u;
        default: return 0xFF9E9E9Eu;
    }
}

static void draw_icon_name(WdCanvas *c, const WdIconRef *table, size_t count,
                           const char *name, int x, int y, uint32_t ink) {
    const WdIconRef *ref = wd_icon_find(table, count, name);
    if (ref == NULL) return;
    static uint32_t buf[WD_ICON_PX];
    if (wd_icon_decode(ref->slot, buf) != 0) {
        if (ref->alt < 0 || wd_icon_decode(ref->alt, buf) != 0) return;
    }
    wd_canvas_tint_icon(c, x, y, buf, WD_ICON_W, WD_ICON_H, ink);
}

static void render_lights_row(WdRender *r, WdCanvas *c, const WdNavFrame *f, int row_y, int cx) {
    int colors[4];
    int seconds[4];
    int n = lights_parse(f->lights_json, colors, seconds, 4);
    if (n <= 0) return;
    int d = WD_ICON_H;
    int gap = 8;
    int total = n * d + (n - 1) * gap;
    int x0 = cx - total / 2;
    char num[16];
    for (int i = 0; i < n; i++) {
        int lx = x0 + i * (d + gap);
        wd_canvas_fill_round_rect(c, lx, row_y, d, d, d / 2, light_argb(colors[i]));
        if (seconds[i] >= 0) {
            snprintf(num, sizeof(num), "%d", seconds[i]);
            int size = clampi(d / 2, 10, 64);
            int w = text_width(r, num, size, 0);
            draw_text(r, c, lx + (d - w) / 2, row_y + d / 2 + size / 3, lx + d, num, size, 0,
                      0xFFFFFFFFu, 0, 0);
        }
    }
}

static void draw_lane_token(WdCanvas *c, const char *tok, int x, int y, uint32_t ink) {
    const WdIconRef *ref = wd_icon_find(wd_lane_icons, wd_lane_icon_count, tok);
    if (ref == NULL) {
        ref = wd_icon_find(wd_lane_icons, wd_lane_icon_count, "default");
        if (ref == NULL) return;
    }
    static uint32_t buf[WD_ICON_PX];
    if (wd_icon_decode(ref->slot, buf) == 0) {
        wd_canvas_tint_icon(c, x, y, buf, WD_ICON_W, WD_ICON_H, ink);
    }
    if (ref->alt >= 0 && wd_icon_decode(ref->alt, buf) == 0) {
        wd_canvas_tint_icon(c, x, y, buf, WD_ICON_W, WD_ICON_H, ink);
    }
}

static void render_lanes_row(WdCanvas *c, const WdNavFrame *f, int row_y, int cx,
                             uint32_t ink) {
    static char names[8][32];
    int n = lanes_parse(f->lanes_json, names, 8);
    if (n <= 0) return;
    int d = WD_ICON_H;
    int gap = 8;
    int total = n * d + (n - 1) * gap;
    int x0 = cx - total / 2;
    for (int i = 0; i < n; i++) {
        int x = x0 + i * (d + gap);
        const char *s = names[i];
        size_t len = strlen(s);
        size_t pos = 0;
        while (pos <= len) {
            size_t bar = pos;
            while (bar < len && s[bar] != '|') bar++;
            size_t tl = bar - pos;
            if (tl > 0 && tl < 32) {
                char tok[32];
                memcpy(tok, s + pos, tl);
                tok[tl] = '\0';
                draw_lane_token(c, tok, x, row_y, ink);
            }
            if (bar >= len) break;
            pos = bar + 1;
        }
    }
}

static bool r_nav_render(void *context, const WdNavFrame *f) {
    WdRender *r = context;
    WdCanvas cv;
    if (r->nav == NULL || r->nav->lock == NULL || !r->nav->lock(r->nav->ctx, &cv)) return false;
    const WdNavCfg *g = &r->core->nav_cfg;
    wd_canvas_clear(&cv, 0x00000000u);
    uint32_t bg = parse_color(g->guidance_bg_color);
    wd_canvas_fill_round_rect(&cv, g->guidance_x, g->guidance_y, g->guidance_width,
                              g->guidance_height, g->guidance_bg_radius, bg);
    bool night = is_night(r, g->theme);
    uint32_t ink = night ? 0xFFFFFFFFu : 0xFF000000u;
    int gx = g->guidance_x;
    int gy = g->guidance_y;
    int gw = g->guidance_width;
    int gh = g->guidance_height;
    int inner_y = gy + g->guidance_pad_top;
    int inner_h = gh - g->guidance_pad_top - g->guidance_pad_bottom;
    if (inner_h < 10) inner_h = 10;
    int icon_y = inner_y + (inner_h - WD_ICON_H) / 2;
    if (icon_y < inner_y) icon_y = inner_y;
    if (f->turn[0] != '\0') {
        draw_icon_name(&cv, wd_maneuver_icons, wd_maneuver_icon_count, f->turn, gx + 8, icon_y,
                       ink);
    }
    int tx = gx + 8 + WD_ICON_W + 8;
    int right = gx + gw - 8;
    char dist[64];
    if (f->distance_text[0] != '\0') {
        size_t dl = strlen(f->distance_text);
        if (dl > sizeof(dist) - 1u) dl = sizeof(dist) - 1u;
        memcpy(dist, f->distance_text, dl);
        dist[dl] = '\0';
    } else if (f->distance_m > 0) {
        if (f->distance_m >= 1000) {
            snprintf(dist, sizeof(dist), "%lld.%lldkm", (long long)(f->distance_m / 1000),
                     (long long)((f->distance_m % 1000) / 100));
        } else {
            snprintf(dist, sizeof(dist), "%lldm", (long long)f->distance_m);
        }
    } else {
        dist[0] = '\0';
    }
    int dist_size = clampi(inner_h * 2 / 5, 10, 64);
    int road_size = clampi(inner_h / 3, 10, 64);
    int asc = wd_font_ascent(r->font, dist_size, 1);
    if (dist[0] != '\0') {
        draw_text(r, &cv, tx, inner_y + asc, right, dist, dist_size, 1, ink, 0, 0);
    }
    int road_asc = wd_font_ascent(r->font, road_size, 0);
    int road_y = inner_y + asc + road_asc + 4;
    if (f->current_road[0] != '\0') {
        draw_text(r, &cv, tx, road_y, right, f->current_road, road_size, 0, ink, 0, 0);
    }
    if (g->show_alerts && f->show_alerts) {
        int alert_y = road_y + road_asc + road_size / 2 + 2;
        if (f->exit_name[0] != '\0') {
            draw_text(r, &cv, tx, alert_y, right, f->exit_name, road_size, 0, ink, 0, 0);
            alert_y += road_asc + 2;
        }
        if (f->service_area[0] != '\0' && alert_y < gy + gh) {
            draw_text(r, &cv, tx, alert_y, right, f->service_area, road_size, 0, ink, 0, 0);
            alert_y += road_asc + 2;
        }
        if (f->traffic_text[0] != '\0' && alert_y < gy + gh) {
            draw_text(r, &cv, tx, alert_y, right, f->traffic_text, road_size, 0, ink, 0, 0);
        }
    }
    int cx = gx + gw / 2;
    if (g->show_lights && f->lights_json[0] != '\0') {
        int row_y = g->lights_on_top ? gy - WD_ICON_H - 8 : gy + gh + 8;
        if (row_y >= 0 && row_y + WD_ICON_H <= cv.height) {
            render_lights_row(r, &cv, f, row_y, cx);
        }
    }
    if (g->show_lanes && f->lanes_json[0] != '\0') {
        int row_y = g->lights_on_top ? gy + gh + 8 : gy - WD_ICON_H - 8;
        if (!g->lights_on_top && row_y + WD_ICON_H > cv.height) row_y = gy + gh + 8;
        if (row_y >= 0 && row_y + WD_ICON_H <= cv.height) {
            render_lanes_row(&cv, f, row_y, cx, ink);
        }
    }
    if (r->nav->present != NULL) r->nav->present(r->nav->ctx);
    return true;
}

static void r_nav_hide(void *context) {
    WdRender *r = context;
    WdCanvas cv;
    if (r->nav == NULL || r->nav->lock == NULL || !r->nav->lock(r->nav->ctx, &cv)) return;
    wd_canvas_clear(&cv, 0x00000000u);
    if (r->nav->present != NULL) r->nav->present(r->nav->ctx);
}

static bool r_music_render(void *context, const WdMusicFrame *f) {
    WdRender *r = context;
    WdCanvas cv;
    if (r->music == NULL || r->music->lock == NULL || !r->music->lock(r->music->ctx, &cv)) {
        return false;
    }
    const WdMusicCfg *m = &r->core->music_cfg;
    wd_canvas_clear(&cv, 0x00000000u);
    uint32_t bg = parse_color(m->background_color);
    int ox = m->x;
    int oy = m->y;
    int bw = m->width;
    int bh = m->height;
    if (bw > cv.width) bw = cv.width;
    if (bh > cv.height) bh = cv.height;
    wd_canvas_fill_round_rect(&cv, ox, oy, bw, bh, m->background_radius, bg);
    bool night = is_night(r, m->theme);
    uint32_t ink = night ? 0xFFFFFFFFu : 0xFF000000u;
    uint32_t cur = parse_color(night ? m->night_color : m->day_color);
    uint32_t prev = parse_color(night ? m->night_prev_color : m->day_prev_color);
    uint32_t next = parse_color(night ? m->night_next_color : m->day_next_color);
    uint32_t stroke = parse_color(m->stroke_color);
    int pad_t = m->background_padding_top;
    int pad_b = m->background_padding_bottom;
    if (pad_t > bh) pad_t = bh;
    if (pad_b > bh - pad_t) pad_b = bh - pad_t;
    int right = ox + bw - 4;
    int text_x = ox;
    int cover_d = 0;
    bool have_cover = m->show_cover && m->music_layout != 3 && r->core->cover_px != NULL
        && r->core->cover_w > 0 && r->core->cover_h > 0;
    if (have_cover) {
        cover_d = m->cover_size;
        int max_d = bh - pad_t - pad_b;
        if (cover_d > max_d) cover_d = max_d;
        if (m->music_layout == 1) {
            int cx = ox + (bw - cover_d) / 2;
            if (cx < ox) cx = ox;
            wd_canvas_blit_rgba(&cv, cx, oy + pad_t, cover_d, cover_d, r->core->cover_px,
                                r->core->cover_w, r->core->cover_h, m->cover_radius);
        } else {
            wd_canvas_blit_rgba(&cv, ox, oy + pad_t, cover_d, cover_d, r->core->cover_px,
                                r->core->cover_w, r->core->cover_h, m->cover_radius);
            text_x = ox + cover_d + m->cover_gap;
        }
    } else if (m->show_cover && m->music_layout == 2) {
        text_x = ox + m->cover_size + m->cover_gap;
    }
    int area_top = oy + pad_t;
    if (m->music_layout == 1 && have_cover) area_top = oy + pad_t + cover_d + m->cover_gap;
    int area_bottom = oy + bh - pad_b;
    if (m->show_progress && m->progress_width > 0) {
        area_bottom -= m->progress_width + m->progress_gap;
    }
    int meta_h = 0;
    if (m->show_metadata) {
        int title_size = m->title_size > 0 ? m->title_size : m->secondary_size;
        int artist_size = m->artist_size > 0 ? m->artist_size : m->secondary_size;
        if (title_size > 0 && f->title[0] != '\0') {
            int asc = wd_font_ascent(r->font, title_size, 1);
            draw_text(r, &cv, text_x, area_top + asc, right, f->title, title_size, 1, ink,
                      m->stroke_width, stroke);
            meta_h += asc + title_size / 2;
        }
        if (artist_size > 0 && f->artist[0] != '\0') {
            int asc = wd_font_ascent(r->font, artist_size, 0);
            draw_text(r, &cv, text_x, area_top + meta_h + asc, right, f->artist,
                      artist_size, 0, ink, 0, 0);
            meta_h += asc + artist_size / 2;
        }
        if (meta_h > 0) meta_h += m->line_gap;
    }
    int lyric_top = area_top + meta_h;
    int font_size = clampi(m->font_size, 10, 64);
    int lh = wd_font_ascent(r->font, font_size, 0) + wd_font_descent(r->font, font_size, 0);
    int step = lh + m->line_gap;
    int block_h = (m->lyric_lines > 0 ? m->lyric_lines : 1) * step - m->line_gap;
    int center = lyric_top + (area_bottom - lyric_top - block_h) / 2 + lh;
    if (center < lyric_top + lh) center = lyric_top + lh;
    int before = m->before_lines;
    int after = m->after_lines;
    int total_lines = m->lyric_lines > WD_LYRIC_LINES_MAX ? WD_LYRIC_LINES_MAX : m->lyric_lines;
    int first = f->line_index - before;
    int last = f->line_index + after;
    if (last - first + 1 > total_lines) {
        if (f->line_index <= 0) first = 0;
        last = first + total_lines - 1;
    }
    if (first < 0) first = 0;
    if (last >= f->line_count) last = f->line_count - 1;
    for (int i = first; i <= last; i++) {
        int baseline = center + (i - f->line_index) * step;
        uint32_t color = cur;
        int bold = 1;
        int sw = m->stroke_width;
        if (i < f->line_index) {
            color = prev;
            bold = 0;
            sw = 0;
        } else if (i > f->line_index) {
            color = next;
            bold = 0;
            sw = 0;
        }
        draw_text(r, &cv, text_x, baseline, right, f->lines[i], font_size, bold, color, sw,
                  stroke);
    }
    if (m->show_progress && m->progress_width > 0 && f->duration_ms > 0) {
        int bar_w = right - text_x;
        if (bar_w > 0) {
            int bar_y = oy + bh - pad_b - m->progress_width;
            wd_canvas_fill_rect(&cv, text_x, bar_y, bar_w, m->progress_width, prev);
            int64_t pos = f->position_ms;
            if (pos < 0) pos = 0;
            if (pos > f->duration_ms) pos = f->duration_ms;
            int fill_w = (int)((int64_t)bar_w * pos / f->duration_ms);
            if (fill_w > bar_w) fill_w = bar_w;
            wd_canvas_fill_rect(&cv, text_x, bar_y, fill_w, m->progress_width, cur);
        }
    }
    if (r->music->present != NULL) r->music->present(r->music->ctx);
    return true;
}

static void r_music_hide(void *context) {
    WdRender *r = context;
    WdCanvas cv;
    if (r->music == NULL || r->music->lock == NULL || !r->music->lock(r->music->ctx, &cv)) return;
    wd_canvas_clear(&cv, 0x00000000u);
    if (r->music->present != NULL) r->music->present(r->music->ctx);
}

static void r_music_line(void *context, int line_index) {
    WdRender *r = context;
    (void)line_index;
    r_music_render(r, &r->core->music.frame);
}

static bool r_theme_day(void *context, int *day) {
    WdRender *r = context;
    if (r->theme_day == NULL) return false;
    return r->theme_day(r->theme_ctx, day);
}

WdRender *wd_render_create(WdCore *core, const WdSurface *nav, const WdSurface *music,
                           WdFont *font, bool (*theme_day)(void *ctx, int *day), void *theme_ctx) {
    if (core == NULL || font == NULL) return NULL;
    WdRender *r = calloc(1, sizeof(*r));
    if (r == NULL) return NULL;
    r->core = core;
    r->nav = nav;
    r->music = music;
    r->font = font;
    r->theme_day = theme_day;
    r->theme_ctx = theme_ctx;
    return r;
}

void wd_render_destroy(WdRender *r) {
    free(r);
}

WdPort wd_render_port(WdRender *r) {
    WdPort p;
    memset(&p, 0, sizeof(p));
    p.context = r;
    p.nav_render = r_nav_render;
    p.nav_hide = r_nav_hide;
    p.music_render = r_music_render;
    p.music_hide = r_music_hide;
    p.music_line = r_music_line;
    p.theme_day = r_theme_day;
    return p;
}
