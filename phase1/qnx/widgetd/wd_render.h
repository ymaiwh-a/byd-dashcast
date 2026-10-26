#ifndef DASHCAST_WD_RENDER_H
#define DASHCAST_WD_RENDER_H

#include <stdbool.h>

#include "wd_canvas.h"
#include "wd_font.h"
#include "widgetd.h"

typedef struct {
    void *ctx;
    bool (*lock)(void *ctx, WdCanvas *out);
    void (*present)(void *ctx);
} WdSurface;

typedef struct WdRender WdRender;

WdRender *wd_render_create(WdCore *core, const WdSurface *nav, const WdSurface *music,
                           WdFont *font, bool (*theme_day)(void *ctx, int *day), void *theme_ctx);
void wd_render_destroy(WdRender *r);
WdPort wd_render_port(WdRender *r);

#endif
