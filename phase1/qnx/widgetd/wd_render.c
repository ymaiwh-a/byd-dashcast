#include "wd_render.h"

#include <math.h>
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

typedef struct {
    char names[32];
    int active;
} WdLane;

static int lanes_parse(const char *js, WdLane *out, int cap) {
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
                out[n].names[j] = k[j];
                j++;
            }
            out[n].names[j] = '\0';
            out[n].active = 0;
            const char *a = strstr(ob, "\"active_directions\":\"");
            if (a != NULL && a < cb) {
                a += 21;
                if (a[0] != '\0' && a[0] != '"' && strncmp(a, "unknown", 7) != 0) {
                    out[n].active = 1;
                }
            }
            n++;
        }
        p = cb + 1;
    }
    return n;
}

static uint32_t light_argb(int color) {
    switch (color) {
        case 1: return 0xFFE92D3Bu;
        case 2: return 0xFF008A64u;
        case 3: return 0xFFDFAD26u;
        default: return 0xFF9E9E9Eu;
    }
}

static int rndf(float v) {
    return (int)(v + 0.5f);
}

static void draw_icon_scaled(WdCanvas *c, const WdIconRef *ref, int x, int y, int w, int h,
                             uint32_t ink) {
    if (ref == NULL || w <= 0 || h <= 0) return;
    static uint32_t src[WD_ICON_PX];
    static uint32_t dst[96 * 96];
    if (wd_icon_decode(ref->slot, src) != 0) {
        if (ref->alt < 0 || wd_icon_decode(ref->alt, src) != 0) return;
    }
    if (w > 96) w = 96;
    if (h > 96) h = 96;
    if (w == WD_ICON_W && h == WD_ICON_H) {
        wd_canvas_tint_icon(c, x, y, src, w, h, ink);
        return;
    }
    for (int dy = 0; dy < h; dy++) {
        int sy = dy * WD_ICON_H / h;
        for (int dx = 0; dx < w; dx++) {
            int sx = dx * WD_ICON_W / w;
            dst[dy * w + dx] = src[sy * WD_ICON_W + sx];
        }
    }
    wd_canvas_tint_icon(c, x, y, dst, w, h, ink);
}

static int max1(int v) {
    return v < 1 ? 1 : v;
}

static void draw_lane_token(WdCanvas *c, const char *tok, int x, int y, int w, int h,
                            uint32_t ink) {
    if (strcmp(tok, "none") == 0) return;
    const WdIconRef *ref = wd_icon_find(wd_lane_icons, wd_lane_icon_count, tok);
    if (ref == NULL) {
        ref = wd_icon_find(wd_maneuver_icons, wd_maneuver_icon_count, tok);
    }
    if (ref == NULL) {
        ref = wd_icon_find(wd_maneuver_icons, wd_maneuver_icon_count, "unknown");
    }
    draw_icon_scaled(c, ref, x, y, w, h, ink);
}

static void render_guidance(WdRender *r, WdCanvas *c, const WdNavFrame *f, const WdNavCfg *g,
                            uint32_t ink) {
    int bx = g->guidance_x;
    int by = g->guidance_y;
    int bw = g->guidance_width;
    int bh = g->guidance_height;
    if (bw <= 0 || bh <= 0) return;
    float s16 = (float)bw / 640.0f;
    float s16h = (float)bh / 184.0f;
    if (s16h < s16) s16 = s16h;
    float s17 = (float)g->light_size / 24.0f;
    int cell_w = rndf(84.0f * s17);
    int cell_h = rndf(56.0f * s17);
    int gap = rndf(6.0f * s17);
    if (cell_w < 1) cell_w = 1;
    if (cell_h < 1) cell_h = 1;
    if (gap < 1) gap = 1;
    int row_h = rndf(92.0f * s16);
    int block = rndf(184.0f * s16);
    int y0 = by + (bh - block) / 2;
    int coff = (row_h - cell_h) / 2;
    int lights_y = y0 + (g->lights_on_top ? coff : row_h + coff);
    int lanes_y = y0 + (g->lights_on_top ? row_h + coff : coff);
    if (g->show_lights && f->lights_json[0] != '\0') {
        int colors[4];
        int seconds[4];
        int n = lights_parse(f->lights_json, colors, seconds, 4);
        if (n > 0) {
            int total = n * cell_w + (n - 1) * gap;
            int x0 = bx + (bw - total) / 2;
            int dia = rndf((n < 4 ? 40.0f : 34.0f) * s17);
            if (dia < 1) dia = 1;
            char num[16];
            for (int i = 0; i < n; i++) {
                int lx = x0 + i * (cell_w + gap);
                wd_canvas_fill_round_rect(c, lx, lights_y, cell_w, cell_h, cell_h / 2,
                                          0x29A8B8C9u);
                int cx = lx + (cell_w - dia) / 2;
                int cy = lights_y + (cell_h - dia) / 2;
                wd_canvas_fill_round_rect(c, cx, cy, dia, dia, dia / 2, light_argb(colors[i]));
                if (seconds[i] >= 0) {
                    snprintf(num, sizeof(num), "%d", seconds[i]);
                    int size = clampi(dia / 2, 10, 64);
                    int tw = text_width(r, num, size, 0);
                    draw_text(r, c, cx + (dia - tw) / 2, cy + dia / 2 + size / 3, cx + dia, num,
                              size, 0, 0xFFFFFFFFu, 0, 0);
                }
            }
        }
    }
    if (g->show_lanes && f->lanes_json[0] != '\0') {
        static WdLane lanes[8];
        int n = lanes_parse(f->lanes_json, lanes, 8);
        if (n > 0) {
            int total = n * cell_w + (n - 1) * gap;
            int x0 = bx + (bw - total) / 2;
            int iw = rndf((n < 4 ? 40.0f : 34.0f) * s17);
            int ih = cell_h;
            if (iw < 1) iw = 1;
            int dw = max1(rndf(1.0f * s16));
            int dh = rndf(38.0f * s16);
            if (dh < 1) dh = 1;
            int dy = lanes_y + (cell_h - dh) / 2;
            int any_active = 0;
            for (int i = 0; i < n; i++) {
                if (lanes[i].active) any_active = 1;
            }
            for (int i = 0; i < n; i++) {
                int lx = x0 + i * (cell_w + gap);
                if (i > 0) {
                    wd_canvas_fill_rect(c, lx - gap / 2 - dw / 2, dy, dw, dh, 0x28A8B8C9u);
                }
                uint32_t aink =
                    (any_active && !lanes[i].active) ? ((ink & 0x00FFFFFFu) | 0x66000000u) : ink;
                const char *s = lanes[i].names;
                size_t len = strlen(s);
                int k = 0;
                size_t scan = 0;
                while (scan <= len) {
                    size_t bar = scan;
                    while (bar < len && s[bar] != '|') bar++;
                    if (bar - scan > 0 && bar - scan < 32) k++;
                    if (bar >= len) break;
                    scan = bar + 1;
                }
                if (k <= 0) continue;
                int tx = lx + (cell_w - iw) / 2;
                int ty = lanes_y + (cell_h - ih) / 2;
                char combo[64];
                int cn = 0;
                for (size_t q = 0; q < len && cn < 63; q++) {
                    combo[cn++] = s[q] == '|' ? '_' : s[q];
                }
                combo[cn] = '\0';
                const WdIconRef *cref =
                    k > 1 ? wd_icon_find(wd_lane_icons, wd_lane_icon_count, combo) : NULL;
                if (cref != NULL) {
                    draw_icon_scaled(c, cref, tx, ty, iw, ih, aink);
                    continue;
                }
                size_t pos = 0;
                while (pos <= len) {
                    size_t bar = pos;
                    while (bar < len && s[bar] != '|') bar++;
                    size_t tl = bar - pos;
                    if (tl > 0 && tl < 32) {
                        char tok[32];
                        memcpy(tok, s + pos, tl);
                        tok[tl] = '\0';
                        draw_lane_token(c, tok, tx, ty, iw, ih, aink);
                    }
                    if (bar >= len) break;
                    pos = bar + 1;
                }
            }
        }
    }
}

static void render_summary(WdRender *r, WdCanvas *c, const WdNavFrame *f, const WdNavCfg *g,
                           uint32_t ink) {
    int bx = g->x;
    int by = g->y;
    int bw = g->width;
    int bh = g->height;
    if (bw <= 0 || bh <= 0) return;
    float s = (float)bw / 640.0f;
    float alt = (float)bh / 510.0f;
    if (alt < s) s = alt;
    int cy = by + (bh - rndf(510.0f * s)) / 2;
    int col = bx + max1(rndf(148.0f * s));
    int right = bx + bw - max1(rndf(156.0f * s));
    int ds = clampi(g->distance_size, 1, 120);
    int fs = clampi(g->font_size, 1, 96);
    int top = cy + max1(rndf(15.0f * s));
    int road_top = cy + max1(rndf(94.0f * s));
    const char *road = f->current_road[0] != '\0' ? f->current_road : "等待道路信息";
    draw_text(r, c, col, road_top + wd_font_ascent(r->font, fs, 0), right, road, fs, 0, ink, 0, 0);
    char num[64];
    const char *unit = "";
    num[0] = '\0';
    if (f->distance_text[0] != '\0') {
        snprintf(num, sizeof(num), "%s", f->distance_text);
    } else if (f->distance_m > 0 && f->distance_m < 1000) {
        snprintf(num, sizeof(num), "%lld", (long long)f->distance_m);
        unit = "米";
    } else if (f->distance_m >= 1000 && f->distance_m <= 99999) {
        snprintf(num, sizeof(num), "%lld.%lld", (long long)(f->distance_m / 1000),
                 (long long)((f->distance_m % 1000) / 100));
        unit = "公里";
    } else if (f->distance_m > 99999) {
        snprintf(num, sizeof(num), "%lld", (long long)(f->distance_m / 1000));
        unit = "公里";
    }
    if (num[0] != '\0') {
        int w = text_width(r, num, ds, 0);
        draw_text(r, c, col, top + wd_font_ascent(r->font, ds, 0), right, num, ds, 0, ink, 0,
                  0);
        if (unit[0] != '\0') {
            int us = max1(rndf(26.0f * s));
            int uoff = max1(rndf(28.0f * s));
            int xg = max1(rndf(4.0f * s));
            int utop = top + ds - uoff;
            draw_text(r, c, col + w + xg, utop + wd_font_ascent(r->font, us, 0), right, unit,
                      us, 0, 0xFFA8B8C9u, 0, 0);
        }
    }
    if (f->remaining_m > 0 || f->remaining_s > 0 || f->eta[0] != '\0') {
        int ss = clampi(g->secondary_size, 1, 96);
        int strip_x = bx + rndf(20.0f * s);
        int strip_w = rndf(600.0f * s);
        if (strip_w > 0) {
            wd_canvas_fill_rect(c, strip_x, cy + rndf(180.0f * s), strip_w, 1, 0x2DA8B8C9u);
            int cw = strip_w / 3;
            int text_y = cy + rndf(209.0f * s) + wd_font_ascent(r->font, ss, 1);
            char cell[64];
            if (f->remaining_m > 0) {
                if (f->remaining_m < 1000) {
                    snprintf(cell, sizeof(cell), "%lld米", (long long)f->remaining_m);
                } else if (f->remaining_m <= 99999) {
                    snprintf(cell, sizeof(cell), "%lld.%lld公里",
                             (long long)(f->remaining_m / 1000),
                             (long long)((f->remaining_m % 1000) / 100));
                } else {
                    snprintf(cell, sizeof(cell), "%lld公里", (long long)(f->remaining_m / 1000));
                }
                draw_text(r, c, strip_x, text_y, strip_x + cw, cell, ss, 1, ink, 0, 0);
            }
            if (f->remaining_s > 0) {
                int minutes = (int)((f->remaining_s + 59) / 60);
                if (minutes > 59) {
                    snprintf(cell, sizeof(cell), "%d时%d分", minutes / 60, minutes % 60);
                } else {
                    snprintf(cell, sizeof(cell), "%d分钟", minutes);
                }
                int tw2 = text_width(r, cell, ss, 1);
                int x2 = strip_x + cw + (cw - tw2) / 2;
                if (x2 < strip_x + cw) x2 = strip_x + cw;
                draw_text(r, c, x2, text_y, strip_x + 2 * cw, cell, ss, 1, ink, 0, 0);
            }
            if (f->eta[0] != '\0') {
                char cell3[80];
                const char *e = f->eta;
                while (*e == ' ') e++;
                if (strncmp(e, "预计", 6) == 0) {
                    e += 6;
                    while (*e == ' ') e++;
                }
                size_t el = strlen(e);
                if (el > 5 && el >= 6 && strcmp(e + el - 6, "到达") == 0) {
                    size_t sb = el - 6;
                    while (sb > 0 && e[sb - 1] == ' ') sb--;
                    if (sb > 0 && sb + 4 < sizeof(cell3)) {
                        memcpy(cell3, e, sb);
                        memcpy(cell3 + sb, " 到", 4);
                        cell3[sb + 4] = '\0';
                        e = cell3;
                    }
                }
                int tw3 = text_width(r, e, ss, 1);
                int x3 = strip_x + strip_w - tw3;
                if (x3 < strip_x + 2 * cw) x3 = strip_x + 2 * cw;
                draw_text(r, c, x3, text_y, bx + bw, e, ss, 1, ink, 0, 0);
            }
            int voff = max1(rndf(5.0f * s));
            int vy = cy + rndf(213.0f * s);
            int vh = max1(rndf(26.0f * s));
            wd_canvas_fill_rect(c, strip_x + cw - voff, vy, 1, vh, 0x3CA8B8C9u);
            wd_canvas_fill_rect(c, strip_x + 2 * cw - voff, vy, 1, vh, 0x3CA8B8C9u);
        }
    }
    if (f->turn[0] != '\0') {
        const WdIconRef *ref = wd_icon_find(wd_maneuver_icons, wd_maneuver_icon_count, f->turn);
        if (ref != NULL) {
            int ic = max1(rndf(78.0f * s));
            int ix = bx + max1(rndf(20.0f * s));
            int block_bot = road_top + fs;
            int iy = top + (block_bot - top - ic) / 2;
            if (iy < by) iy = by;
            draw_icon_scaled(c, ref, ix, iy, ic, ic, 0xFF00B8FFu);
        }
    }
    if (f->show_limit && f->limit_kph > 0) {
        int dia = max1(rndf(78.0f * s));
        if (dia > 128) dia = 128;
        int ring_w = max1(rndf(5.0f * s));
        int lx0 = bx + bw - max1(rndf(20.0f * s)) - dia;
        int ly0 = cy + max1(rndf(18.0f * s));
        static uint8_t ring_mask[128 * 128];
        static uint8_t disc_mask[128 * 128];
        float half = (float)dia / 2.0f;
        for (int row = 0; row < dia; row++) {
            float dy = (float)row + 0.5f - half;
            for (int col2 = 0; col2 < dia; col2++) {
                float dx = (float)col2 + 0.5f - half;
                float dist = sqrtf(dx * dx + dy * dy);
                float o = half + 0.5f - dist;
                if (o > 1.0f) o = 1.0f;
                if (o < 0.0f) o = 0.0f;
                float i2 = half - (float)ring_w + 0.5f - dist;
                if (i2 > 1.0f) i2 = 1.0f;
                if (i2 < 0.0f) i2 = 0.0f;
                int ra = (int)((o - i2) * 255.0f);
                if (ra < 0) ra = 0;
                if (ra > 255) ra = 255;
                int ia = (int)(i2 * 255.0f);
                if (ia < 0) ia = 0;
                if (ia > 255) ia = 255;
                ring_mask[row * dia + col2] = (uint8_t)ra;
                disc_mask[row * dia + col2] = (uint8_t)ia;
            }
        }
        wd_canvas_blend_mask(c, lx0, ly0, dia, dia, dia, ring_mask, 0xFFFF4048u);
        wd_canvas_blend_mask(c, lx0, ly0, dia, dia, dia, disc_mask, 0xD9F8F9FBu);
        char lim[32];
        snprintf(lim, sizeof(lim), "%lld", (long long)f->limit_kph);
        int ns = max1(rndf(34.0f * s));
        int nw = text_width(r, lim, ns, 0);
        draw_text(r, c, lx0 + (dia - nw) / 2, ly0 + dia / 2 + ns / 3, lx0 + dia, lim, ns, 0,
                  0xFF101A2Au, 0, 0);
        int ls = clampi(g->secondary_size, 1, 96);
        int lbox = lx0 - max1(rndf(6.0f * s));
        int lbw = dia + max1(rndf(12.0f * s));
        int ltw = text_width(r, "限速", ls, 0);
        draw_text(r, c, lbox + (lbw - ltw) / 2, ly0 + dia + max1(rndf(12.0f * s)) +
                         wd_font_ascent(r->font, ls, 0), lbox + lbw, "限速", ls, 0, 0xFFA8B8C9u, 0,
                         0);
    }
}

static void fill_box_bg(WdCanvas *cv, int x, int y, int w, int h, int pad_t, int pad_b, int radius,
                        uint32_t bg) {
    if (w <= 0 || h <= 0) return;
    if (pad_t < 0) pad_t = 0;
    if (pad_b < 0) pad_b = 0;
    if (pad_t > h) pad_t = h;
    if (pad_b > h - pad_t) pad_b = h - pad_t;
    int fill_h = h - pad_t - pad_b;
    int rad = radius;
    if (rad > w / 2) rad = w / 2;
    if (rad > fill_h / 2) rad = fill_h / 2;
    if (rad < 0) rad = 0;
    wd_canvas_fill_round_rect(cv, x, y + pad_t, w, fill_h, rad, bg);
}

static bool r_nav_render(void *context, const WdNavFrame *f) {
    WdRender *r = context;
    WdCanvas cv;
    if (r->nav == NULL || r->nav->lock == NULL || !r->nav->lock(r->nav->ctx, &cv)) return false;
    const WdNavCfg *g = &r->core->nav_cfg;
    wd_canvas_clear(&cv, 0x00000000u);
    uint32_t bg = parse_color(g->guidance_bg_color);
    fill_box_bg(&cv, g->x, g->y, g->width, g->height, g->guidance_pad_top, g->guidance_pad_bottom,
                g->guidance_bg_radius, bg);
    fill_box_bg(&cv, g->guidance_x, g->guidance_y, g->guidance_width, g->guidance_height,
                g->guidance_pad_top, g->guidance_pad_bottom, g->guidance_bg_radius, bg);
    bool night = is_night(r, g->theme);
    uint32_t bg_rgb = bg & 0x00FFFFFFu;
    if ((bg >> 24) >= 0x60u && bg_rgb < 0x808080u) night = true;
    uint32_t ink = night ? 0xFFFFFFFFu : 0xFF000000u;
    render_summary(r, &cv, f, g, ink);
    render_guidance(r, &cv, f, g, ink);
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
    fill_box_bg(&cv, ox, oy, bw, bh, m->background_padding_top, m->background_padding_bottom,
                m->background_radius, bg);
    bool night = is_night(r, m->theme);
    uint32_t bg_rgb = bg & 0x00FFFFFFu;
    if ((bg >> 24) >= 0x60u && bg_rgb < 0x808080u) night = true;
    uint32_t ink = night ? 0xFFFFFFFFu : 0xFF000000u;
    uint32_t cur = parse_color(night ? m->night_color : m->day_color);
    uint32_t prev = parse_color(night ? m->night_prev_color : m->day_prev_color);
    uint32_t next = parse_color(night ? m->night_next_color : m->day_next_color);
    uint32_t stroke = parse_color(m->stroke_color);
    int pad_t = m->background_padding_top;
    int pad_b = m->background_padding_bottom;
    if (pad_t > bh) pad_t = bh;
    if (pad_b > bh - pad_t) pad_b = bh - pad_t;
    int right = ox + bw - 14;
    int text_x = ox + 14;
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
    if (m->show_progress) {
        area_bottom -= 18 + m->progress_gap + 2;
    }
    int meta_h = 0;
    if (m->show_metadata) {
        int title_size = m->title_size > 0 ? m->title_size
                                           : (m->secondary_size > 13 ? m->secondary_size - 3 : 10);
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
    if (m->show_progress && f->duration_ms > 0) {
        int bar_x = ox + 14;
        int bar_w = bw - 28;
        if (m->progress_width > 0 && m->progress_width < bar_w) bar_w = m->progress_width;
        if (bar_w > 0) {
            int bar_y = oy + bh - pad_b - 2;
            wd_canvas_fill_rect(&cv, bar_x, bar_y, bar_w, 2, prev);
            int64_t pos = f->position_ms;
            if (pos < 0) pos = 0;
            if (pos > f->duration_ms) pos = f->duration_ms;
            int fill_w = (int)((int64_t)bar_w * pos / f->duration_ms);
            if (fill_w > bar_w) fill_w = bar_w;
            wd_canvas_fill_rect(&cv, bar_x, bar_y, fill_w, 2, cur);
            int tb = bar_y - m->progress_gap - 1;
            char tbuf[16];
            int64_t ps = pos / 1000;
            snprintf(tbuf, sizeof(tbuf), "%d:%02d", (int)(ps / 60), (int)(ps % 60));
            draw_text(r, &cv, bar_x, tb, right, tbuf, 18, 0, prev, 0, 0);
            int64_t ds = f->duration_ms / 1000;
            snprintf(tbuf, sizeof(tbuf), "%d:%02d", (int)(ds / 60), (int)(ds % 60));
            int tw = text_width(r, tbuf, 18, 0);
            draw_text(r, &cv, bar_x + bar_w - tw, tb, right, tbuf, 18, 0, prev, 0, 0);
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
