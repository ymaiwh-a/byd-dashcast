#include "wd_canvas.h"

static uint32_t blend_px(uint32_t dst, uint32_t src) {
    uint32_t sa = (src >> 24) & 0xFFu;
    if (sa == 0u) return dst;
    if (sa == 255u) return src;
    uint32_t da = (dst >> 24) & 0xFFu;
    uint32_t isa = 255u - sa;
    uint32_t oa = sa + da * isa / 255u;
    if (oa == 0u) return 0u;
    uint32_t sr = (src >> 16) & 0xFFu;
    uint32_t sg = (src >> 8) & 0xFFu;
    uint32_t sb = src & 0xFFu;
    uint32_t dr = (dst >> 16) & 0xFFu;
    uint32_t dg = (dst >> 8) & 0xFFu;
    uint32_t db = dst & 0xFFu;
    uint32_t r = (sr * sa + dr * da * isa / 255u) / oa;
    uint32_t g = (sg * sa + dg * da * isa / 255u) / oa;
    uint32_t b = (sb * sa + db * da * isa / 255u) / oa;
    if (r > 255u) r = 255u;
    if (g > 255u) g = 255u;
    if (b > 255u) b = 255u;
    if (oa > 255u) oa = 255u;
    return (oa << 24) | (r << 16) | (g << 8) | b;
}

void wd_canvas_init(WdCanvas *c, uint32_t *fb, int width, int height, int stride) {
    c->px = fb;
    c->width = width;
    c->height = height;
    c->stride = stride;
}

void wd_canvas_clear(WdCanvas *c, uint32_t argb) {
    for (int y = 0; y < c->height; y++) {
        uint32_t *row = c->px + (size_t)y * (size_t)c->stride;
        for (int x = 0; x < c->width; x++) row[x] = argb;
    }
}

uint32_t wd_canvas_get(const WdCanvas *c, int x, int y) {
    if (x < 0 || y < 0 || x >= c->width || y >= c->height) return 0;
    return c->px[(size_t)y * (size_t)c->stride + (size_t)x];
}

void wd_canvas_fill_rect(WdCanvas *c, int x, int y, int w, int h, uint32_t argb) {
    if ((argb >> 24) == 0u) return;
    int x0 = x < 0 ? 0 : x;
    int y0 = y < 0 ? 0 : y;
    int x1 = x + w;
    int y1 = y + h;
    if (x1 > c->width) x1 = c->width;
    if (y1 > c->height) y1 = c->height;
    for (int py = y0; py < y1; py++) {
        uint32_t *row = c->px + (size_t)py * (size_t)c->stride;
        for (int px = x0; px < x1; px++) row[px] = blend_px(row[px], argb);
    }
}

static bool round_cover(int lx, int ly, int w, int h, int r) {
    if (r <= 0) return true;
    int dx = 0;
    int dy = 0;
    if (lx < r) dx = r - lx;
    else if (lx >= w - r) dx = lx - (w - r) + 1;
    if (ly < r) dy = r - ly;
    else if (ly >= h - r) dy = ly - (h - r) + 1;
    if (dx == 0 || dy == 0) return true;
    return dx * dx + dy * dy <= r * r;
}

void wd_canvas_fill_round_rect(WdCanvas *c, int x, int y, int w, int h, int radius, uint32_t argb) {
    if ((argb >> 24) == 0u) return;
    int r = radius;
    int lim = w < h ? w : h;
    if (r > lim / 2) r = lim / 2;
    int x1 = x + w;
    int y1 = y + h;
    if (x1 < x || y1 < y) return;
    for (int py = y; py < y1; py++) {
        if (py < 0 || py >= c->height) continue;
        uint32_t *row = c->px + (size_t)py * (size_t)c->stride;
        for (int px = x; px < x1; px++) {
            if (px < 0 || px >= c->width) continue;
            if (!round_cover(px - x, py - y, w, h, r)) continue;
            row[px] = blend_px(row[px], argb);
        }
    }
}

void wd_canvas_blend_mask(WdCanvas *c, int x, int y, int w, int h, int stride,
                          const uint8_t *mask, uint32_t argb) {
    uint32_t base_a = (argb >> 24) & 0xFFu;
    if (base_a == 0u) return;
    for (int my = 0; my < h; my++) {
        int py = y + my;
        if (py < 0 || py >= c->height) continue;
        uint32_t *row = c->px + (size_t)py * (size_t)c->stride;
        const uint8_t *m = mask + (size_t)my * (size_t)stride;
        for (int mx = 0; mx < w; mx++) {
            int px = x + mx;
            if (px < 0 || px >= c->width) continue;
            uint32_t cov = m[mx];
            if (cov == 0u) continue;
            uint32_t a = base_a * cov / 255u;
            if (a == 0u) continue;
            uint32_t src = (a << 24) | (argb & 0x00FFFFFFu);
            row[px] = blend_px(row[px], src);
        }
    }
}

void wd_canvas_tint_icon(WdCanvas *c, int x, int y, const uint32_t *icon, int icon_w, int icon_h,
                         uint32_t argb) {
    uint32_t base_a = (argb >> 24) & 0xFFu;
    if (base_a == 0u) return;
    for (int iy = 0; iy < icon_h; iy++) {
        int py = y + iy;
        if (py < 0 || py >= c->height) continue;
        uint32_t *row = c->px + (size_t)py * (size_t)c->stride;
        const uint32_t *src = icon + (size_t)iy * (size_t)icon_w;
        for (int ix = 0; ix < icon_w; ix++) {
            int px = x + ix;
            if (px < 0 || px >= c->width) continue;
            uint32_t cov = (src[ix] >> 24) & 0xFFu;
            if (cov == 0u) continue;
            uint32_t a = base_a * cov / 255u;
            if (a == 0u) continue;
            uint32_t tint = (a << 24) | (argb & 0x00FFFFFFu);
            row[px] = blend_px(row[px], tint);
        }
    }
}

void wd_canvas_blit_rgba(WdCanvas *c, int x, int y, int dw, int dh, const uint8_t *rgba, int sw,
                         int sh, int radius) {
    if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) return;
    int r = radius;
    int lim = dw < dh ? dw : dh;
    if (r > lim / 2) r = lim / 2;
    for (int dy = 0; dy < dh; dy++) {
        int py = y + dy;
        if (py < 0 || py >= c->height) continue;
        uint32_t *row = c->px + (size_t)py * (size_t)c->stride;
        int sy = (int)(((int64_t)dy * (int64_t)sh) / (int64_t)dh);
        if (sy >= sh) sy = sh - 1;
        const uint8_t *srow = rgba + (size_t)sy * (size_t)sw * 4u;
        for (int dx = 0; dx < dw; dx++) {
            int px = x + dx;
            if (px < 0 || px >= c->width) continue;
            if (!round_cover(dx, dy, dw, dh, r)) continue;
            int sx = (int)(((int64_t)dx * (int64_t)sw) / (int64_t)dw);
            if (sx >= sw) sx = sw - 1;
            const uint8_t *p = srow + (size_t)sx * 4u;
            uint32_t src = ((uint32_t)p[3] << 24) | ((uint32_t)p[0] << 16)
                | ((uint32_t)p[1] << 8) | (uint32_t)p[2];
            row[px] = blend_px(row[px], src);
        }
    }
}
