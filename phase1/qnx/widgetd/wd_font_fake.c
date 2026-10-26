#include "wd_font.h"

#include <stdlib.h>

#define WD_FAKE_MAX 96

typedef struct {
    WdFont font;
    uint8_t mask[WD_FAKE_MAX * WD_FAKE_MAX];
} WdFakeFont;

static int fake_ascent(WdFont *f, int size, int bold) {
    (void)f;
    (void)bold;
    if (size < 1) size = 1;
    if (size > WD_FAKE_MAX) size = WD_FAKE_MAX;
    return (size * 3 + 3) / 4;
}

static int fake_descent(WdFont *f, int size, int bold) {
    (void)f;
    (void)bold;
    if (size < 1) size = 1;
    if (size > WD_FAKE_MAX) size = WD_FAKE_MAX;
    return (size + 3) / 4;
}

static int fake_glyph(WdFont *f, int size, int bold, uint32_t cp, WdGlyph *out) {
    WdFakeFont *ff = (WdFakeFont *)f;
    (void)bold;
    if (size < 1) size = 1;
    if (size > WD_FAKE_MAX) size = WD_FAKE_MAX;
    int adv = size / 2;
    if (adv < 1) adv = 1;
    int asc = fake_ascent(f, size, bold);
    int dsc = fake_descent(f, size, bold);
    out->advance = adv;
    out->left = 0;
    out->top = asc;
    out->width = 0;
    out->height = 0;
    out->stride = 0;
    out->mask = NULL;
    if (cp == 0x20u || cp == 0xFFFDu) return 0;
    int h = asc + dsc;
    if (adv > WD_FAKE_MAX) adv = WD_FAKE_MAX;
    if (h > WD_FAKE_MAX) h = WD_FAKE_MAX;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < adv; x++) ff->mask[y * adv + x] = 0xFFu;
    }
    out->width = adv;
    out->height = h;
    out->stride = adv;
    out->mask = ff->mask;
    return 0;
}

static const WdFontOps FAKE_OPS = {fake_ascent, fake_descent, fake_glyph};

WdFont *wd_font_fake_create(void) {
    WdFakeFont *ff = calloc(1, sizeof(*ff));
    if (ff == NULL) return NULL;
    ff->font.ops = &FAKE_OPS;
    ff->font.state = ff;
    return &ff->font;
}

void wd_font_fake_destroy(WdFont *f) {
    free(f);
}
