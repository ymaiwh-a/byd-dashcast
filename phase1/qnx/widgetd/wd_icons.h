#ifndef DASHCAST_WD_ICONS_H
#define DASHCAST_WD_ICONS_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define WD_ICON_SLOTS 136
#define WD_ICON_W 96
#define WD_ICON_H 96
#define WD_ICON_PX (WD_ICON_W * WD_ICON_H)

typedef struct {
    const char *name;
    int slot;
    int alt;
} WdIconRef;

extern const WdIconRef wd_maneuver_icons[];
extern const size_t wd_maneuver_icon_count;
extern const WdIconRef wd_alert_icons[];
extern const size_t wd_alert_icon_count;
extern const WdIconRef wd_lane_icons[];
extern const size_t wd_lane_icon_count;

const WdIconRef *wd_icon_find(const WdIconRef *table, size_t count, const char *name);
int wd_icon_decode(int slot, uint32_t *out);

#ifdef __cplusplus
}
#endif
#endif
