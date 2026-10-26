#ifndef DASHCAST_WD_FONT_H
#define DASHCAST_WD_FONT_H

#include <stddef.h>
#include <stdint.h>

typedef struct WdFont WdFont;

typedef struct {
    int advance;
    int left;
    int top;
    int width;
    int height;
    int stride;
    const uint8_t *mask;
} WdGlyph;

typedef struct {
    int (*ascent)(WdFont *f, int size, int bold);
    int (*descent)(WdFont *f, int size, int bold);
    int (*glyph)(WdFont *f, int size, int bold, uint32_t cp, WdGlyph *out);
} WdFontOps;

struct WdFont {
    const WdFontOps *ops;
    void *state;
};

int wd_font_ascent(WdFont *f, int size, int bold);
int wd_font_descent(WdFont *f, int size, int bold);
int wd_font_glyph(WdFont *f, int size, int bold, uint32_t cp, WdGlyph *out);
int wd_font_text_width(WdFont *f, int size, int bold, const char *utf8);
uint32_t wd_font_next_cp(const char *s, size_t len, size_t *pos);

WdFont *wd_font_fake_create(void);
void wd_font_fake_destroy(WdFont *f);
WdFont *wd_font_freetype_create(const char *const *paths, int count);
void wd_font_freetype_destroy(WdFont *f);

#endif
