#include "wd_font.h"

#include <string.h>

int wd_font_ascent(WdFont *f, int size, int bold) {
    return f->ops->ascent(f, size, bold);
}

int wd_font_descent(WdFont *f, int size, int bold) {
    return f->ops->descent(f, size, bold);
}

int wd_font_glyph(WdFont *f, int size, int bold, uint32_t cp, WdGlyph *out) {
    return f->ops->glyph(f, size, bold, cp, out);
}

uint32_t wd_font_next_cp(const char *s, size_t len, size_t *pos) {
    size_t i = *pos;
    unsigned char c0 = (unsigned char)s[i];
    uint32_t cp = 0xFFFDu;
    size_t n = 1;
    if (c0 < 0x80u) {
        cp = c0;
    } else if ((c0 >> 5) == 0x6u && i + 1 < len) {
        cp = ((uint32_t)(c0 & 0x1Fu) << 6) | (uint32_t)((unsigned char)s[i + 1] & 0x3Fu);
        n = 2;
    } else if ((c0 >> 4) == 0xEu && i + 2 < len) {
        cp = ((uint32_t)(c0 & 0x0Fu) << 12)
            | ((uint32_t)((unsigned char)s[i + 1] & 0x3Fu) << 6)
            | (uint32_t)((unsigned char)s[i + 2] & 0x3Fu);
        n = 3;
    } else if ((c0 >> 3) == 0x1Eu && i + 3 < len) {
        cp = ((uint32_t)(c0 & 0x07u) << 18)
            | ((uint32_t)((unsigned char)s[i + 1] & 0x3Fu) << 12)
            | ((uint32_t)((unsigned char)s[i + 2] & 0x3Fu) << 6)
            | (uint32_t)((unsigned char)s[i + 3] & 0x3Fu);
        n = 4;
    }
    if (cp >= 0xD800u && cp <= 0xDBFFu && i + 5 < len) {
        unsigned char c3 = (unsigned char)s[i + 3];
        unsigned char c4 = (unsigned char)s[i + 4];
        unsigned char c5 = (unsigned char)s[i + 5];
        if ((c3 >> 4) == 0xEu && (c4 & 0xC0u) == 0x80u && (c5 & 0xC0u) == 0x80u) {
            uint32_t lo = ((uint32_t)(c3 & 0x0Fu) << 12) | ((uint32_t)(c4 & 0x3Fu) << 6)
                | (uint32_t)(c5 & 0x3Fu);
            if (lo >= 0xDC00u && lo <= 0xDFFFu) {
                cp = 0x10000u + ((cp - 0xD800u) << 10) + (lo - 0xDC00u);
                n = 6;
            }
        }
    }
    *pos = i + n;
    return cp;
}

int wd_font_text_width(WdFont *f, int size, int bold, const char *utf8) {
    size_t len = strlen(utf8);
    size_t pos = 0;
    int w = 0;
    while (pos < len) {
        uint32_t cp = wd_font_next_cp(utf8, len, &pos);
        WdGlyph g;
        if (f->ops->glyph(f, size, bold, cp, &g) == 0) w += g.advance;
    }
    return w;
}
