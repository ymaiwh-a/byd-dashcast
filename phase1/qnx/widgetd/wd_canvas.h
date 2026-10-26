#ifndef DASHCAST_WD_CANVAS_H
#define DASHCAST_WD_CANVAS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

typedef struct {
    uint32_t *px;
    int width;
    int height;
    int stride;
} WdCanvas;

void wd_canvas_init(WdCanvas *c, uint32_t *fb, int width, int height, int stride);
void wd_canvas_clear(WdCanvas *c, uint32_t argb);
uint32_t wd_canvas_get(const WdCanvas *c, int x, int y);
void wd_canvas_fill_rect(WdCanvas *c, int x, int y, int w, int h, uint32_t argb);
void wd_canvas_fill_round_rect(WdCanvas *c, int x, int y, int w, int h, int radius, uint32_t argb);
void wd_canvas_blend_mask(WdCanvas *c, int x, int y, int w, int h, int stride,
                          const uint8_t *mask, uint32_t argb);
void wd_canvas_tint_icon(WdCanvas *c, int x, int y, const uint32_t *icon, int icon_w, int icon_h,
                         uint32_t argb);
void wd_canvas_blit_rgba(WdCanvas *c, int x, int y, int dw, int dh, const uint8_t *rgba, int sw,
                         int sh, int radius);

#endif
