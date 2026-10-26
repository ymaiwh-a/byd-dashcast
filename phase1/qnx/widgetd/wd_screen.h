#ifndef DASHCAST_WD_SCREEN_H
#define DASHCAST_WD_SCREEN_H

#include <stdbool.h>

#include "wd_canvas.h"
#include "wd_render.h"

typedef struct WdScreen WdScreen;

WdScreen *wd_screen_create(const char *name, int width, int height);
void wd_screen_destroy(WdScreen *s);
bool wd_screen_surface(WdScreen *s, WdSurface *out);
void wd_screen_set_geometry(WdScreen *s, int x, int y, int opacity);
bool wd_screen_theme_day(WdScreen *s, int *day);

#endif
