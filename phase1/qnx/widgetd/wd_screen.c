#include "wd_screen.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __QNX__

#include <screen/screen.h>
#include <unistd.h>

struct WdScreen {
    screen_context_t ctx;
    screen_window_t parent;
    screen_window_t child;
    screen_group_t group;
    screen_buffer_t bufs[2];
    uint32_t *ptrs[2];
    int back;
    int width;
    int height;
    int x;
    int y;
};

static bool scr_lock(void *context, WdCanvas *out) {
    WdScreen *s = context;
    if (s->ptrs[0] == NULL || s->ptrs[1] == NULL) return false;
    s->back ^= 1;
    wd_canvas_init(out, s->ptrs[s->back], s->width, s->height, s->width);
    return true;
}

static void scr_present(void *context) {
    WdScreen *s = context;
    if (s->bufs[s->back] == NULL) return;
    screen_post_window(s->child, s->bufs[s->back], 0, NULL, 0);
    screen_flush_context(s->ctx, 0);
}

static const WdSurface SCR_SURFACE = {NULL, scr_lock, scr_present};

WdScreen *wd_screen_create(const char *name, int width, int height) {
    if (name == NULL || width <= 0 || height <= 0) return NULL;
    WdScreen *s = calloc(1, sizeof(*s));
    if (s == NULL) return NULL;
    s->width = width;
    s->height = height;
    if (screen_create_context(&s->ctx, SCREEN_APPLICATION_CONTEXT) != 0) {
        free(s);
        return NULL;
    }
    char pname[96];
    snprintf(pname, sizeof(pname), "byd_%s_demo_%ld", name, (long)getpid());
    if (screen_create_window(&s->parent, s->ctx) != 0) {
        screen_destroy_context(s->ctx);
        free(s);
        return NULL;
    }
    screen_set_window_property_cv(s->parent, SCREEN_PROPERTY_ID_STRING, (int)strlen(pname), pname);
    int tiny[2] = {1, 1};
    int zero[2] = {0, 0};
    int format = SCREEN_FORMAT_RGBA8888;
    screen_set_window_property_iv(s->parent, SCREEN_PROPERTY_BUFFER_SIZE, tiny);
    screen_set_window_property_iv(s->parent, SCREEN_PROPERTY_SOURCE_SIZE, tiny);
    screen_set_window_property_iv(s->parent, SCREEN_PROPERTY_SIZE, tiny);
    screen_set_window_property_iv(s->parent, SCREEN_PROPERTY_SOURCE_POSITION, zero);
    screen_set_window_property_iv(s->parent, SCREEN_PROPERTY_POSITION, zero);
    screen_set_window_property_iv(s->parent, SCREEN_PROPERTY_FORMAT, &format);
    screen_buffer_t pbufs[2];
    if (screen_create_window_buffers(s->parent, 2) == 0
        && screen_get_window_property_pv(s->parent, SCREEN_PROPERTY_RENDER_BUFFERS,
                                         (void **)pbufs, 2) == 0) {
        screen_post_window(s->parent, pbufs[0], 0, NULL, 0);
    }
    if (screen_create_window_group(s->ctx, &s->group) != 0) {
        screen_destroy_window(s->ctx, s->parent);
        screen_destroy_context(s->ctx);
        free(s);
        return NULL;
    }
    char gname[96];
    snprintf(gname, sizeof(gname), "%s_group_%llu", name,
             (unsigned long long)(uint64_t)getpid());
    screen_set_window_group_property_cv(s->group, SCREEN_PROPERTY_ID_STRING, (int)strlen(gname),
                                        gname);
    char cname[96];
    snprintf(cname, sizeof(cname), "byd_%s_content_%ld", name, (long)getpid());
    if (screen_create_window(&s->child, s->ctx) != 0) {
        screen_destroy_window_group(s->ctx, s->group);
        screen_destroy_window(s->ctx, s->parent);
        screen_destroy_context(s->ctx);
        free(s);
        return NULL;
    }
    screen_set_window_property_cv(s->child, SCREEN_PROPERTY_ID_STRING, (int)strlen(cname), cname);
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_PARENT, (const int *)&s->parent);
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_GROUP, (const int *)&s->group);
    int size[2] = {width, height};
    int pos[2] = {0, 0};
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_BUFFER_SIZE, size);
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_SOURCE_SIZE, size);
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_SIZE, size);
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_SOURCE_POSITION, zero);
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_POSITION, pos);
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_FORMAT, &format);
    if (screen_create_window_buffers(s->child, 2) != 0
        || screen_get_window_property_pv(s->child, SCREEN_PROPERTY_RENDER_BUFFERS,
                                         (void **)s->bufs, 2) != 0) {
        screen_destroy_window(s->ctx, s->child);
        screen_destroy_window_group(s->ctx, s->group);
        screen_destroy_window(s->ctx, s->parent);
        screen_destroy_context(s->ctx);
        free(s);
        return NULL;
    }
    for (int i = 0; i < 2; i++) {
        void *ptr = NULL;
        if (screen_get_buffer_property_pv(s->bufs[i], SCREEN_BUFFER_PROPERTY_POINTER, &ptr) == 0) {
            s->ptrs[i] = ptr;
        }
    }
    s->back = 0;
    return s;
}

void wd_screen_destroy(WdScreen *s) {
    if (s == NULL) return;
    screen_destroy_window(s->ctx, s->child);
    screen_destroy_window_group(s->ctx, s->group);
    screen_destroy_window(s->ctx, s->parent);
    screen_destroy_context(s->ctx);
    free(s);
}

bool wd_screen_surface(WdScreen *s, WdSurface *out) {
    if (s == NULL || out == NULL) return false;
    *out = SCR_SURFACE;
    out->ctx = s;
    return true;
}

void wd_screen_set_geometry(WdScreen *s, int x, int y, int opacity) {
    if (s == NULL) return;
    int pos[2] = {x, y};
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_POSITION, pos);
    int alpha = opacity;
    if (alpha < 0) alpha = 0;
    if (alpha > 255) alpha = 255;
    screen_set_window_property_iv(s->child, SCREEN_PROPERTY_ALPHA, &alpha);
    s->x = x;
    s->y = y;
}

bool wd_screen_theme_day(WdScreen *s, int *day) {
    (void)s;
    (void)day;
    return false;
}

#else

struct WdScreen {
    WdCanvas cv;
    uint32_t *fb;
    int width;
    int height;
    int x;
    int y;
};

static bool host_lock(void *context, WdCanvas *out) {
    WdScreen *s = context;
    if (s->fb == NULL) return false;
    *out = s->cv;
    return true;
}

static void host_present(void *context) {
    (void)context;
}

static const WdSurface HOST_SURFACE = {NULL, host_lock, host_present};

WdScreen *wd_screen_create(const char *name, int width, int height) {
    (void)name;
    if (width <= 0 || height <= 0) return NULL;
    WdScreen *s = calloc(1, sizeof(*s));
    if (s == NULL) return NULL;
    size_t bytes = (size_t)width * (size_t)height * sizeof(uint32_t);
    s->fb = malloc(bytes);
    if (s->fb == NULL) {
        free(s);
        return NULL;
    }
    memset(s->fb, 0, bytes);
    s->width = width;
    s->height = height;
    wd_canvas_init(&s->cv, s->fb, width, height, width);
    return s;
}

void wd_screen_destroy(WdScreen *s) {
    if (s == NULL) return;
    free(s->fb);
    free(s);
}

bool wd_screen_surface(WdScreen *s, WdSurface *out) {
    if (s == NULL || out == NULL) return false;
    *out = HOST_SURFACE;
    out->ctx = s;
    return true;
}

void wd_screen_set_geometry(WdScreen *s, int x, int y, int opacity) {
    if (s == NULL) return;
    s->x = x;
    s->y = y;
    (void)opacity;
}

bool wd_screen_theme_day(WdScreen *s, int *day) {
    (void)s;
    (void)day;
    return false;
}

#endif
