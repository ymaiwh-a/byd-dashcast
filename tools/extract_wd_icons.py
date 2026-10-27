import hashlib
import os
import struct
import sys

STRIDE = 0x2400
ICON_W = 96
ICON_H = 96
A1 = 0x805BD78
A2 = 0x8143990
A_SLOTS = 103
B_SLOTS = 32

MANEUVER = [
    ("sharp_left", 0, -1),
    ("sharp_right", 1, -1),
    ("uturn", 2, -1),
    ("roundabout", 3, -1),
    ("destination", 4, -1),
    ("unknown", 5, -1),
    ("uturn_right", 6, -1),
    ("continue", 7, -1),
    ("slight_left", 8, -1),
    ("slight_right", 9, -1),
    ("waypoint", 10, -1),
    ("start", 11, -1),
    ("tunnel", 12, -1),
    ("service_area", 13, -1),
    ("toll", 14, -1),
    ("ferry", 15, -1),
    ("car", 16, -1),
    ("garage_exit", 57, -1),
    ("left", 59, -1),
    ("right", 60, -1),
    ("straight", 56, 101),
    ("merge_left", 8, 98),
    ("merge_right", 9, 97),
    ("roundabout_ccw_enter", 17, 70),
    ("roundabout_ccw_exit", 18, 96),
    ("roundabout_ccw_left", 19, 62),
    ("roundabout_ccw_right", 20, 66),
    ("roundabout_ccw_straight", 21, 68),
    ("roundabout_ccw_uturn", 22, 64),
    ("roundabout_ccw_ne", 23, 67),
    ("roundabout_ccw_nw", 24, 61),
    ("roundabout_ccw_se", 25, 65),
    ("roundabout_ccw_sw", 26, 63),
    ("roundabout_cw_enter", 27, 95),
    ("roundabout_cw_exit", 18, 94),
    ("roundabout_cw_left", 28, 93),
    ("roundabout_cw_right", 29, 92),
    ("roundabout_cw_straight", 30, 91),
    ("roundabout_cw_uturn", 31, 90),
    ("roundabout_cw_ne", 32, 89),
    ("roundabout_cw_nw", 33, 88),
    ("roundabout_cw_se", 34, 87),
    ("roundabout_cw_sw", 35, 86),
    ("roundabout_oem_25", 58, 69),
    ("junction_2", 56, 85),
    ("junction_3", 56, 84),
    ("junction_4", 56, 83),
    ("junction_5", 56, 82),
    ("junction_6", 56, 81),
    ("junction_8", 9, 80),
    ("junction_9", 9, 79),
    ("junction_10", 9, 78),
    ("junction_11", 9, 77),
    ("junction_12", 9, 76),
    ("junction_17", 8, 75),
    ("junction_18", 8, 74),
    ("junction_19", 8, 73),
    ("junction_20", 8, 72),
    ("junction_21", 8, 71),
    ("oem_1", 56, -1),
    ("oem_2", 56, 85),
    ("oem_3", 56, 84),
    ("oem_4", 56, 83),
    ("oem_5", 56, 82),
    ("oem_6", 56, 81),
    ("oem_7", 9, -1),
    ("oem_8", 9, 80),
    ("oem_9", 9, 79),
    ("oem_10", 9, 78),
    ("oem_11", 9, 77),
    ("oem_12", 9, 76),
    ("oem_13", 1, -1),
    ("oem_14", 60, -1),
    ("oem_15", 8, -1),
    ("oem_16", 0, -1),
    ("oem_17", 8, 75),
    ("oem_18", 8, 74),
    ("oem_19", 8, 73),
    ("oem_20", 8, 72),
    ("oem_21", 8, 71),
    ("oem_22", 59, -1),
    ("oem_23", 2, -1),
    ("oem_24", 17, 70),
    ("oem_25", 58, 69),
    ("oem_26", 21, 68),
    ("oem_27", 23, 67),
    ("oem_28", 20, 66),
    ("oem_29", 25, 65),
    ("oem_30", 22, 64),
    ("oem_31", 26, 63),
    ("oem_32", 19, 62),
    ("oem_33", 24, -1),
    ("oem_34", 11, -1),
    ("oem_35", 4, -1),
    ("oem_36", 10, -1),
    ("oem_37", 15, -1),
    ("oem_38", 14, -1),
    ("oem_39", 7, -1),
]

for i in range(1, 11):
    MANEUVER.append(("roundabout_ccw_exit_%d" % i, 35 + i, -1))
    MANEUVER.append(("roundabout_cw_exit_%d" % i, 45 + i, -1))

ALERT = [
    ("speed_camera", 109),
    ("red_light", 110),
    ("phone", 111),
    ("bus_lane", 112),
    ("emergency_lane", 113),
    ("lane_camera", 114),
    ("warning", 115),
    ("bicycle_lane", 116),
    ("etc_speed", 117),
    ("crosswalk", 118),
    ("illegal_parking", 119),
    ("headlights", 120),
    ("horn", 121),
    ("wrong_way", 122),
    ("railway", 123),
    ("lane_direction", 124),
    ("following_distance", 125),
    ("hov", 126),
    ("capture", 127),
    ("seatbelt", 128),
]

LANE = [
    ("straight", 129, -1),
    ("left", 130, -1),
    ("right", 131, -1),
    ("uturn", 132, -1),
    ("uturn_right", 133, -1),
    ("left_uturn", 135, -1),
]

EXTRA_ICONS = ["gb_left_uturn.hex"]


def sections(buf):
    e_shoff = struct.unpack_from("<I", buf, 0x20)[0]
    e_shentsize = struct.unpack_from("<H", buf, 0x2E)[0]
    e_shnum = struct.unpack_from("<H", buf, 0x30)[0]
    shstrtab = struct.unpack_from(
        "<I", buf,
        e_shoff + e_shentsize * struct.unpack_from("<H", buf, 0x32)[0] + 0x10)[0]
    out = {}
    for i in range(e_shnum):
        base = e_shoff + e_shentsize * i
        name = struct.unpack_from("<I", buf, base)[0]
        addr = struct.unpack_from("<I", buf, base + 0xC)[0]
        off = struct.unpack_from("<I", buf, base + 0x10)[0]
        size = struct.unpack_from("<I", buf, base + 0x14)[0]
        end = buf.find(b"\x00", shstrtab + name)
        out[buf[shstrtab + name:end].decode()] = (addr, off, size)
    return out


def slot_pixels(buf, ro, va):
    fo = ro[1] + (va - ro[0])
    raw = buf[fo:fo + STRIDE]
    px = []
    for y in range(ICON_H):
        for x in range(ICON_W):
            a = raw[y * ICON_W + x]
            px.append(a << 24)
    return px


def rle(px):
    out = []
    i = 0
    while i < len(px):
        j = i
        while j < len(px) and px[j] == px[i]:
            j += 1
        out.append((j - i, px[i]))
        i = j
    return out


def main():
    elf = sys.argv[1]
    outdir = sys.argv[2]
    buf = open(elf, "rb").read()
    sh = sections(buf)
    ro = sh[".rodata"]
    slots = []
    for k in range(A_SLOTS):
        slots.append(slot_pixels(buf, ro, A1 + k * STRIDE))
    for k in range(B_SLOTS):
        slots.append(slot_pixels(buf, ro, A2 + k * STRIDE))
    here0 = os.path.dirname(os.path.abspath(__file__))
    for fname in EXTRA_ICONS:
        text = open(os.path.join(here0, fname), "r").read()
        data = bytes.fromhex("".join(text.split()))
        assert len(data) == ICON_W * ICON_H
        slots.append([b << 24 for b in data])
    blob = []
    offs = [0]
    for px in slots:
        for count, value in rle(px):
            blob.append(count)
            blob.append(value)
        offs.append(len(blob))
    digest = hashlib.sha256()
    for px in slots:
        for p in px:
            digest.update(struct.pack("<I", p))
    h = []
    h.append("#ifndef DASHCAST_WD_ICONS_H")
    h.append("#define DASHCAST_WD_ICONS_H")
    h.append("")
    h.append("#include <stddef.h>")
    h.append("#include <stdint.h>")
    h.append("")
    h.append("#ifdef __cplusplus")
    h.append("extern \"C\" {")
    h.append("#endif")
    h.append("")
    h.append("#define WD_ICON_SLOTS %d" % len(slots))
    h.append("#define WD_ICON_W %d" % ICON_W)
    h.append("#define WD_ICON_H %d" % ICON_H)
    h.append("#define WD_ICON_PX (WD_ICON_W * WD_ICON_H)")
    h.append("")
    h.append("typedef struct {")
    h.append("    const char *name;")
    h.append("    int slot;")
    h.append("    int alt;")
    h.append("} WdIconRef;")
    h.append("")
    h.append("extern const WdIconRef wd_maneuver_icons[];")
    h.append("extern const size_t wd_maneuver_icon_count;")
    h.append("extern const WdIconRef wd_alert_icons[];")
    h.append("extern const size_t wd_alert_icon_count;")
    h.append("extern const WdIconRef wd_lane_icons[];")
    h.append("extern const size_t wd_lane_icon_count;")
    h.append("")
    h.append("const WdIconRef *wd_icon_find(const WdIconRef *table, size_t count, const char *name);")
    h.append("int wd_icon_decode(int slot, uint32_t *out);")
    h.append("")
    h.append("#ifdef __cplusplus")
    h.append("}")
    h.append("#endif")
    h.append("#endif")
    h.append("")
    c = []
    c.append("#include \"wd_icons.h\"")
    c.append("")
    c.append("#include <string.h>")
    c.append("")
    c.append("static const uint32_t wd_icon_blob[] = {")
    for i in range(0, len(blob), 8):
        c.append("    " + ",".join("0x%08x" % v for v in blob[i:i + 8]) + ",")
    c.append("};")
    c.append("")
    c.append("static const uint32_t wd_icon_off[WD_ICON_SLOTS + 1] = {")
    for i in range(0, len(offs), 8):
        c.append("    " + ",".join(str(v) for v in offs[i:i + 8]) + ",")
    c.append("};")
    c.append("")
    c.append("const WdIconRef wd_maneuver_icons[] = {")
    for name, s1, s2 in MANEUVER:
        c.append('    {"%s", %d, %d},' % (name, s1, s2))
    c.append("};")
    c.append("const size_t wd_maneuver_icon_count = sizeof(wd_maneuver_icons) / sizeof(wd_maneuver_icons[0]);")
    c.append("")
    c.append("const WdIconRef wd_alert_icons[] = {")
    for name, s in ALERT:
        c.append('    {"%s", %d, -1},' % (name, s))
    c.append("};")
    c.append("const size_t wd_alert_icon_count = sizeof(wd_alert_icons) / sizeof(wd_alert_icons[0]);")
    c.append("")
    c.append("const WdIconRef wd_lane_icons[] = {")
    for name, s1, s2 in LANE:
        c.append('    {"%s", %d, %d},' % (name, s1, s2))
    c.append("};")
    c.append("const size_t wd_lane_icon_count = sizeof(wd_lane_icons) / sizeof(wd_lane_icons[0]);")
    c.append("")
    c.append("const WdIconRef *wd_icon_find(const WdIconRef *table, size_t count, const char *name) {")
    c.append("    size_t i;")
    c.append("    for (i = 0; i < count; i++) {")
    c.append("        if (strcmp(table[i].name, name) == 0) {")
    c.append("            return &table[i];")
    c.append("        }")
    c.append("    }")
    c.append("    return NULL;")
    c.append("}")
    c.append("")
    c.append("int wd_icon_decode(int slot, uint32_t *out) {")
    c.append("    uint32_t i;")
    c.append("    int n = 0;")
    c.append("    if (slot < 0 || slot >= WD_ICON_SLOTS || out == NULL) {")
    c.append("        return -1;")
    c.append("    }")
    c.append("    for (i = wd_icon_off[slot]; i < wd_icon_off[slot + 1]; i += 2) {")
    c.append("        uint32_t count = wd_icon_blob[i];")
    c.append("        uint32_t value = wd_icon_blob[i + 1];")
    c.append("        while (count--) {")
    c.append("            out[n++] = value;")
    c.append("        }")
    c.append("    }")
    c.append("    return n == WD_ICON_PX ? 0 : -1;")
    c.append("}")
    c.append("")
    os.makedirs(outdir, exist_ok=True)
    with open(os.path.join(outdir, "wd_icons.h"), "w", newline="\n") as f:
        f.write("\n".join(h))
    with open(os.path.join(outdir, "wd_icons.c"), "w", newline="\n") as f:
        f.write("\n".join(c))
    here = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(here, "wd_icons.sha256"), "w", newline="\n") as f:
        f.write(digest.hexdigest() + "\n")
    print("slots", len(slots), "blob_pairs", len(blob) // 2, "sha256", digest.hexdigest())


if __name__ == "__main__":
    main()
