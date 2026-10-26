#include "wd_font.h"

#include <stdlib.h>
#include <string.h>

#ifdef __QNX__

#include <ft2build.h>
#include FT_FREETYPE_H

typedef struct {
    WdFont font;
    FT_Library lib;
    FT_Face face;
    int loaded_size;
    int loaded_bold;
} WdFtFont;

static int ft_select_size(WdFtFont *st, int size, int bold) {
    if (st->face == NULL) return -1;
    if (st->loaded_size == size && st->loaded_bold == bold) return 0;
    if (FT_Set_Pixel_Sizes(st->face, 0, (FT_UInt)size) != 0) return -1;
    st->loaded_size = size;
    st->loaded_bold = bold;
    return 0;
}

static int ft_ascent(WdFont *f, int size, int bold) {
    WdFtFont *st = f->state;
    if (ft_select_size(st, size, bold) != 0) return size * 3 / 4;
    long a = (long)st->face->size->metrics.ascender;
    if (a >= 0) return (int)((a + 63) >> 6);
    return (int)(-((-a + 63) >> 6));
}

static int ft_descent(WdFont *f, int size, int bold) {
    WdFtFont *st = f->state;
    if (ft_select_size(st, size, bold) != 0) return (size + 3) / 4;
    long d = (long)st->face->size->metrics.descender;
    if (d <= 0) return (int)((-d + 63) >> 6);
    return (int)((d + 63) >> 6);
}

static int ft_glyph(WdFont *f, int size, int bold, uint32_t cp, WdGlyph *out) {
    WdFtFont *st = f->state;
    if (ft_select_size(st, size, bold) != 0) return -1;
    if (FT_Load_Char(st->face, (FT_ULong)cp, 4) != 0) return -1;
    FT_GlyphSlot g = st->face->glyph;
    out->advance = (int)(g->advance.x >> 6);
    out->left = (int)g->bitmap_left;
    out->top = (int)g->bitmap_top;
    out->width = (int)g->bitmap.width;
    out->height = (int)g->bitmap.rows;
    out->stride = (int)g->bitmap.pitch;
    out->mask = g->bitmap.buffer;
    return 0;
}

static const WdFontOps FT_OPS = {ft_ascent, ft_descent, ft_glyph};

WdFont *wd_font_freetype_create(const char *const *paths, int count) {
    WdFtFont *st = calloc(1, sizeof(*st));
    if (st == NULL) return NULL;
    if (FT_Init_FreeType(&st->lib) != 0) {
        free(st);
        return NULL;
    }
    for (int i = 0; i < count; i++) {
        if (FT_New_Face(st->lib, paths[i], 0, &st->face) == 0) {
            st->font.ops = &FT_OPS;
            st->font.state = st;
            st->loaded_size = -1;
            st->loaded_bold = -1;
            return &st->font;
        }
    }
    FT_Done_FreeType(st->lib);
    free(st);
    return NULL;
}

void wd_font_freetype_destroy(WdFont *f) {
    if (f == NULL) return;
    WdFtFont *st = f->state;
    if (st->face != NULL) FT_Done_Face(st->face);
    FT_Done_FreeType(st->lib);
    free(st);
}

#else

WdFont *wd_font_freetype_create(const char *const *paths, int count) {
    (void)paths;
    (void)count;
    return NULL;
}

void wd_font_freetype_destroy(WdFont *f) {
    (void)f;
}

#endif
