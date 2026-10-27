#include <stdio.h>
#include <string.h>

#include "wd_icons.h"

static uint32_t g_px[WD_ICON_PX];
static int g_pass;

static void check(const char *name, int ok) {
    if (ok) {
        g_pass++;
        printf("PASS %s\n", name);
    } else {
        printf("FAIL %s\n", name);
    }
}

static int ref_slot_valid(const WdIconRef *table, size_t count) {
    size_t i;
    for (i = 0; i < count; i++) {
        if (table[i].slot < 0 || table[i].slot >= WD_ICON_SLOTS) {
            return 0;
        }
        if (table[i].alt >= WD_ICON_SLOTS) {
            return 0;
        }
    }
    return 1;
}

static const WdIconRef *find_ref(const WdIconRef *table, size_t count, const char *name) {
    return wd_icon_find(table, count, name);
}

int main(void) {
    FILE *f;
    int slot;
    int all_ok = 1;
    const WdIconRef *r;

    f = fopen("decoded.bin", "wb");
    if (f == NULL) {
        printf("FAIL open_decoded\n");
        return 1;
    }
    for (slot = 0; slot < WD_ICON_SLOTS; slot++) {
        if (wd_icon_decode(slot, g_px) != 0) {
            all_ok = 0;
            break;
        }
        if (fwrite(g_px, sizeof(uint32_t), WD_ICON_PX, f) != WD_ICON_PX) {
            all_ok = 0;
            break;
        }
    }
    fclose(f);
    check("decode_all_slots", all_ok && slot == WD_ICON_SLOTS);
    check("decode_rejects_bad_slot", wd_icon_decode(-1, g_px) == -1 &&
                                    wd_icon_decode(WD_ICON_SLOTS, g_px) == -1 &&
                                    wd_icon_decode(0, NULL) == -1);
    check("maneuver_table_slots_valid", ref_slot_valid(wd_maneuver_icons, wd_maneuver_icon_count));
    check("alert_table_slots_valid", ref_slot_valid(wd_alert_icons, wd_alert_icon_count));
    check("lane_table_slots_valid", ref_slot_valid(wd_lane_icons, wd_lane_icon_count));

    r = find_ref(wd_alert_icons, wd_alert_icon_count, "speed_camera");
    check("alert_speed_camera", r != NULL && r->slot == 109);
    r = find_ref(wd_alert_icons, wd_alert_icon_count, "warning");
    check("alert_warning", r != NULL && r->slot == 115);
    r = find_ref(wd_alert_icons, wd_alert_icon_count, "lane_direction");
    check("alert_lane_direction", r != NULL && r->slot == 124);
    r = find_ref(wd_alert_icons, wd_alert_icon_count, "following_distance");
    check("alert_following_distance", r != NULL && r->slot == 125);
    r = find_ref(wd_alert_icons, wd_alert_icon_count, "hov");
    check("alert_hov", r != NULL && r->slot == 126);
    r = find_ref(wd_alert_icons, wd_alert_icon_count, "capture");
    check("alert_capture", r != NULL && r->slot == 127);
    r = find_ref(wd_alert_icons, wd_alert_icon_count, "seatbelt");
    check("alert_seatbelt", r != NULL && r->slot == 128);

    r = find_ref(wd_lane_icons, wd_lane_icon_count, "left");
    check("lane_left", r != NULL && r->slot == 130);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "right");
    check("lane_right", r != NULL && r->slot == 131);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "straight");
    check("lane_straight", r != NULL && r->slot == 129);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "uturn");
    check("lane_uturn", r != NULL && r->slot == 132);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "uturn_right");
    check("lane_uturn_right", r != NULL && r->slot == 133);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "left_uturn");
    check("lane_left_uturn", r != NULL && r->slot == 135);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "uturn_left");
    check("lane_uturn_left_absent", r == NULL);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "extend");
    check("lane_extend_absent", r == NULL);
    r = find_ref(wd_lane_icons, wd_lane_icon_count, "default");
    check("lane_default_absent", r == NULL);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "uturn");
    check("maneuver_uturn", r != NULL && r->slot == 2);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "uturn_right");
    check("maneuver_uturn_right", r != NULL && r->slot == 6);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "continue");
    check("maneuver_continue", r != NULL && r->slot == 7);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "unknown");
    check("maneuver_unknown", r != NULL && r->slot == 5);

    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "straight");
    check("maneuver_straight", r != NULL && r->slot == 56 && r->alt == 101);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "left");
    check("maneuver_left", r != NULL && r->slot == 59);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "right");
    check("maneuver_right", r != NULL && r->slot == 60);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "merge_left");
    check("maneuver_merge_left", r != NULL && r->slot == 8 && r->alt == 98);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "merge_right");
    check("maneuver_merge_right", r != NULL && r->slot == 9 && r->alt == 97);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "garage_exit");
    check("maneuver_garage_exit", r != NULL && r->slot == 57);
    r = find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "roundabout_cw_exit_10");
    check("maneuver_cw_exit_10", r != NULL && r->slot == 55);
    check("find_unknown", find_ref(wd_maneuver_icons, wd_maneuver_icon_count, "no_such") == NULL);

    printf("TESTS_PASSED=%d\n", g_pass);
    if (g_pass < 25) {
        return 1;
    }
    return 0;
}
