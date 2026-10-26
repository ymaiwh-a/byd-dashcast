#include "widgetd.h"

#include <errno.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define WD_NONE 0xFFFFFFFFu
#define WD_MAX_DEPTH 8

typedef enum {
    WD_T_NULL, WD_T_BOOL, WD_T_INT, WD_T_DBL, WD_T_STR, WD_T_ARR, WD_T_OBJ
} WdNodeType;

static void node_reset(WdParser *p, uint32_t idx, unsigned char type) {
    WdJson *n = &p->pool[idx];
    n->type = type;
    n->i = 0;
    n->d = 0.0;
    n->s = NULL;
    n->len = 0;
    n->first_child = WD_NONE;
    n->next = WD_NONE;
    n->n = 0;
    n->key = NULL;
    n->key_len = 0;
}

static uint32_t alloc_node(WdParser *p, unsigned char type) {
    if (p->used >= WD_JSON_POOL) {
        p->overflow = true;
        return WD_NONE;
    }
    uint32_t idx = p->used++;
    node_reset(p, idx, type);
    return idx;
}

static char *str_reserve(WdParser *p, size_t len) {
    if (p->str_used + len + 1u > WD_STR_POOL) {
        p->overflow = true;
        return NULL;
    }
    char *out = p->str_pool + p->str_used;
    p->str_used += len + 1u;
    return out;
}

typedef struct {
    const char *s;
    size_t n;
    size_t pos;
    WdParser *p;
    unsigned depth;
    bool failed;
} WdCur;

static void skip_ws(WdCur *c) {
    while (c->pos < c->n) {
        char ch = c->s[c->pos];
        if (ch != ' ' && ch != '\t' && ch != '\r' && ch != '\n') break;
        c->pos++;
    }
}

static bool parse_value(WdCur *c, uint32_t *out);

static bool parse_hex4(const char *s, uint32_t *out) {
    uint32_t v = 0;
    for (int i = 0; i < 4; i++) {
        char ch = s[i];
        v <<= 4;
        if (ch >= '0' && ch <= '9') v |= (uint32_t)(ch - '0');
        else if (ch >= 'a' && ch <= 'f') v |= (uint32_t)(ch - 'a' + 10);
        else if (ch >= 'A' && ch <= 'F') v |= (uint32_t)(ch - 'A' + 10);
        else return false;
    }
    *out = v;
    return true;
}

static bool parse_string_raw(WdCur *c, char **out, size_t *out_len) {
    if (c->pos >= c->n || c->s[c->pos] != '"') {
        c->failed = true;
        return false;
    }
    c->pos++;
    size_t scan = c->pos;
    while (scan < c->n) {
        if (c->s[scan] == '\\') {
            scan += 2;
            continue;
        }
        if (c->s[scan] == '"') break;
        scan++;
    }
    if (scan >= c->n) {
        c->failed = true;
        return false;
    }
    char *buf = str_reserve(c->p, scan - c->pos + 1u);
    if (buf == NULL) {
        c->failed = true;
        return false;
    }
    size_t w = 0;
    while (c->pos < c->n) {
        char ch = c->s[c->pos];
        if (ch == '"') {
            c->pos++;
            buf[w] = '\0';
            *out = buf;
            *out_len = w;
            return true;
        }
        if (ch == '\\') {
            c->pos++;
            if (c->pos >= c->n) break;
            char e = c->s[c->pos];
            c->pos++;
            char lit = 0;
            switch (e) {
                case '"': lit = '"'; break;
                case '\\': lit = '\\'; break;
                case '/': lit = '/'; break;
                case 'b': lit = '\b'; break;
                case 'f': lit = '\f'; break;
                case 'n': lit = '\n'; break;
                case 'r': lit = '\r'; break;
                case 't': lit = '\t'; break;
                case 'u': {
                    if (c->pos + 4 > c->n) { c->failed = true; return false; }
                    uint32_t cp = 0;
                    if (!parse_hex4(c->s + c->pos, &cp)) { c->failed = true; return false; }
                    c->pos += 4;
                    if (cp >= 0xD800u && cp <= 0xDBFFu) {
                        if (c->pos + 6 > c->n || c->s[c->pos] != '\\' || c->s[c->pos + 1] != 'u') {
                            c->failed = true;
                            return false;
                        }
                        uint32_t lo = 0;
                        if (!parse_hex4(c->s + c->pos + 2, &lo)) { c->failed = true; return false; }
                        if (lo < 0xDC00u || lo > 0xDFFFu) { c->failed = true; return false; }
                        c->pos += 6;
                        cp = 0x10000u + ((cp - 0xD800u) << 10) + (lo - 0xDC00u);
                    } else if (cp >= 0xDC00u && cp <= 0xDFFFu) {
                        c->failed = true;
                        return false;
                    }
                    if (cp < 0x80u) {
                        buf[w++] = (char)cp;
                    } else if (cp < 0x800u) {
                        buf[w++] = (char)(0xC0u | (cp >> 6));
                        buf[w++] = (char)(0x80u | (cp & 0x3Fu));
                    } else if (cp < 0x10000u) {
                        buf[w++] = (char)(0xE0u | (cp >> 12));
                        buf[w++] = (char)(0x80u | ((cp >> 6) & 0x3Fu));
                        buf[w++] = (char)(0x80u | (cp & 0x3Fu));
                    } else {
                        buf[w++] = (char)(0xF0u | (cp >> 18));
                        buf[w++] = (char)(0x80u | ((cp >> 12) & 0x3Fu));
                        buf[w++] = (char)(0x80u | ((cp >> 6) & 0x3Fu));
                        buf[w++] = (char)(0x80u | (cp & 0x3Fu));
                    }
                    continue;
                }
                default:
                    c->failed = true;
                    return false;
            }
            buf[w++] = lit;
            continue;
        }
        if ((unsigned char)ch < 0x20u) {
            c->failed = true;
            return false;
        }
        buf[w++] = ch;
        c->pos++;
    }
    c->failed = true;
    return false;
}

static bool add_member(WdCur *c, uint32_t parent, uint32_t child) {
    WdJson *pn = &c->p->pool[parent];
    if (pn->first_child == WD_NONE) {
        pn->first_child = child;
    } else {
        uint32_t it = pn->first_child;
        for (;;) {
            WdJson *n = &c->p->pool[it];
            if (n->next == WD_NONE) {
                n->next = child;
                break;
            }
            it = n->next;
        }
    }
    pn->n++;
    return true;
}

static bool parse_value(WdCur *c, uint32_t *out) {
    skip_ws(c);
    if (c->pos >= c->n) {
        c->failed = true;
        return false;
    }
    if (c->depth >= WD_MAX_DEPTH) {
        c->failed = true;
        return false;
    }
    char ch = c->s[c->pos];
    if (ch == '{' || ch == '[') {
        bool is_obj = ch == '{';
        c->pos++;
        uint32_t node = alloc_node(c->p, is_obj ? WD_T_OBJ : WD_T_ARR);
        if (node == WD_NONE) {
            c->failed = true;
            return false;
        }
        c->depth++;
        skip_ws(c);
        if (c->pos < c->n && c->s[c->pos] == (is_obj ? '}' : ']')) {
            c->pos++;
            c->depth--;
            *out = node;
            return true;
        }
        for (;;) {
            char *key = NULL;
            size_t key_len = 0;
            if (is_obj) {
                skip_ws(c);
                if (!parse_string_raw(c, &key, &key_len)) {
                    c->failed = true;
                    return false;
                }
                skip_ws(c);
                if (c->pos >= c->n || c->s[c->pos] != ':') {
                    c->failed = true;
                    return false;
                }
                c->pos++;
            }
            uint32_t child = WD_NONE;
            if (!parse_value(c, &child)) {
                c->failed = true;
                return false;
            }
            WdJson *cn = &c->p->pool[child];
            if (is_obj) {
                for (uint32_t it = c->p->pool[node].first_child; it != WD_NONE;
                     it = c->p->pool[it].next) {
                    WdJson *ex = &c->p->pool[it];
                    if (ex->key_len == key_len && memcmp(ex->key, key, key_len) == 0) {
                        c->p->duplicate = true;
                    }
                }
                cn->key = key;
                cn->key_len = key_len;
            }
            add_member(c, node, child);
            skip_ws(c);
            if (c->pos < c->n && c->s[c->pos] == ',') {
                c->pos++;
                continue;
            }
            if (c->pos < c->n && c->s[c->pos] == (is_obj ? '}' : ']')) {
                c->pos++;
                break;
            }
            c->failed = true;
            return false;
        }
        c->depth--;
        *out = node;
        return true;
    }
    if (ch == '"') {
        uint32_t node = alloc_node(c->p, WD_T_STR);
        if (node == WD_NONE) {
            c->failed = true;
            return false;
        }
        char *s = NULL;
        size_t len = 0;
        if (!parse_string_raw(c, &s, &len)) {
            c->failed = true;
            return false;
        }
        WdJson *n = &c->p->pool[node];
        n->s = s;
        n->len = len;
        *out = node;
        return true;
    }
    if (ch == 't') {
        if (c->pos + 4 > c->n || memcmp(c->s + c->pos, "true", 4) != 0) {
            c->failed = true;
            return false;
        }
        c->pos += 4;
        uint32_t node = alloc_node(c->p, WD_T_BOOL);
        if (node == WD_NONE) {
            c->failed = true;
            return false;
        }
        c->p->pool[node].i = 1;
        *out = node;
        return true;
    }
    if (ch == 'f') {
        if (c->pos + 5 > c->n || memcmp(c->s + c->pos, "false", 5) != 0) {
            c->failed = true;
            return false;
        }
        c->pos += 5;
        uint32_t node = alloc_node(c->p, WD_T_BOOL);
        if (node == WD_NONE) {
            c->failed = true;
            return false;
        }
        *out = node;
        return true;
    }
    if (ch == 'n') {
        if (c->pos + 4 > c->n || memcmp(c->s + c->pos, "null", 4) != 0) {
            c->failed = true;
            return false;
        }
        c->pos += 4;
        uint32_t node = alloc_node(c->p, WD_T_NULL);
        if (node == WD_NONE) {
            c->failed = true;
            return false;
        }
        *out = node;
        return true;
    }
    if (ch == '-' || (ch >= '0' && ch <= '9')) {
        size_t start = c->pos;
        bool is_dbl = false;
        if (ch == '-') c->pos++;
        while (c->pos < c->n) {
            char d = c->s[c->pos];
            if (d >= '0' && d <= '9') {
                c->pos++;
            } else if (d == '.' || d == 'e' || d == 'E' || d == '+' || d == '-') {
                is_dbl = is_dbl || d == '.' || d == 'e' || d == 'E';
                c->pos++;
            } else {
                break;
            }
        }
        uint32_t node = alloc_node(c->p, is_dbl ? WD_T_DBL : WD_T_INT);
        if (node == WD_NONE) {
            c->failed = true;
            return false;
        }
        size_t tlen = c->pos - start;
        if (tlen == 0 || tlen >= 48) {
            c->failed = true;
            return false;
        }
        char tmp[48];
        memcpy(tmp, c->s + start, tlen);
        tmp[tlen] = '\0';
        WdJson *n = &c->p->pool[node];
        if (is_dbl) {
            n->d = strtod(tmp, NULL);
            n->i = (int64_t)n->d;
        } else {
            errno = 0;
            long long v = strtoll(tmp, NULL, 10);
            if (errno == ERANGE) {
                n->type = WD_T_DBL;
                n->d = strtod(tmp, NULL);
                n->i = (int64_t)n->d;
            } else {
                n->i = (int64_t)v;
            }
        }
        *out = node;
        return true;
    }
    c->failed = true;
    return false;
}

static const WdJson *jobj(const WdParser *p, const WdJson *obj, const char *key) {
    if (obj == NULL || obj->type != WD_T_OBJ) return NULL;
    size_t klen = strlen(key);
    for (uint32_t it = obj->first_child; it != WD_NONE; it = p->pool[it].next) {
        const WdJson *n = &p->pool[it];
        if (n->key_len == klen && memcmp(n->key, key, klen) == 0) return n;
    }
    return NULL;
}

static bool jstr(const WdParser *p, const WdJson *obj, const char *key, char *dst, size_t cap,
                 size_t max_len) {
    const WdJson *n = jobj(p, obj, key);
    if (n == NULL) {
        if (cap > 0) dst[0] = '\0';
        return true;
    }
    if (n->type != WD_T_STR || n->len > max_len) return false;
    size_t len = n->len;
    if (len >= cap) len = cap - 1;
    memcpy(dst, n->s, len);
    dst[len] = '\0';
    return true;
}

static bool jint(const WdParser *p, const WdJson *obj, const char *key, int64_t *val, bool *present) {
    const WdJson *n = jobj(p, obj, key);
    *present = n != NULL;
    if (n == NULL) return true;
    if (n->type != WD_T_INT) return false;
    *val = n->i;
    return true;
}

static bool jbool(const WdParser *p, const WdJson *obj, const char *key, bool *val, bool *present) {
    const WdJson *n = jobj(p, obj, key);
    *present = n != NULL;
    if (n == NULL) return true;
    if (n->type != WD_T_BOOL) return false;
    *val = n->i != 0;
    return true;
}

typedef struct {
    char *buf;
    size_t cap;
    size_t len;
    bool over;
} WdOut;

static void out_char(WdOut *o, char ch) {
    if (o->len + 1 >= o->cap) {
        o->over = true;
        return;
    }
    o->buf[o->len++] = ch;
}

static void out_lit(WdOut *o, const char *s) {
    while (*s != '\0') out_char(o, *s++);
}

static void out_esc(WdOut *o, const char *s) {
    out_char(o, '"');
    while (*s != '\0') {
        unsigned char ch = (unsigned char)*s++;
        if (ch == '"' || ch == '\\') {
            out_char(o, '\\');
            out_char(o, (char)ch);
        } else if (ch == '\n') {
            out_lit(o, "\\n");
        } else if (ch == '\r') {
            out_lit(o, "\\r");
        } else if (ch == '\t') {
            out_lit(o, "\\t");
        } else if (ch < 0x20u) {
            out_lit(o, "\\u0000");
            o->len -= 2;
            out_char(o, (char)('0' + (ch >> 4)));
            out_char(o, (char)('0' + (ch & 0xF)));
        } else {
            out_char(o, (char)ch);
        }
    }
    out_char(o, '"');
}

static void out_kv_str(WdOut *o, const char *k, const char *v) {
    out_char(o, ',');
    out_lit(o, k);
    out_char(o, ':');
    out_esc(o, v);
}

static void out_kv_int(WdOut *o, const char *k, long long v) {
    char num[24];
    int n = snprintf(num, sizeof(num), "%lld", v);
    out_char(o, ',');
    out_lit(o, k);
    out_char(o, ':');
    for (int i = 0; i < n; i++) out_char(o, num[i]);
}

static void out_kv_bool(WdOut *o, const char *k, bool v) {
    out_char(o, ',');
    out_lit(o, k);
    out_char(o, ':');
    out_lit(o, v ? "true" : "false");
}

static void out_open(WdOut *o, int channel) {
    o->len = 0;
    o->over = false;
    out_char(o, '{');
    if (channel == WD_CHANNEL_HMI) {
        out_lit(o, "\"schema\":2,\"service\":\"hmi\"");
    } else {
        out_lit(o, "\"schema\":1,\"service\":\"widgets\",\"build\":\"widgets-8\"");
    }
}

static size_t out_close(WdOut *o) {
    if (o->over) return 0;
    out_char(o, '}');
    out_char(o, '\n');
    o->buf[o->len] = '\0';
    return o->over ? 0 : o->len;
}

static size_t wd_error(int channel, const char *code, char *reply, size_t cap) {
    WdOut o = {reply, cap, 0, false};
    out_open(&o, channel);
    out_kv_bool(&o, "\"ok\"", false);
    out_char(&o, ',');
    out_lit(&o, "\"error\":");
    out_esc(&o, code);
    return out_close(&o);
}

static void wd_ok_head(WdOut *o, int channel) {
    out_open(o, channel);
    out_kv_bool(o, "\"ok\"", true);
    out_char(o, ',');
    out_lit(o, "\"result\":\"ok\"");
}

static bool is_color(const char *s) {
    if (strlen(s) != 9 || s[0] != '#') return false;
    for (int i = 1; i < 9; i++) {
        char ch = s[i];
        if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'))) {
            return false;
        }
    }
    return true;
}

static bool is_theme(const char *s) {
    return strcmp(s, "auto") == 0 || strcmp(s, "day") == 0 || strcmp(s, "night") == 0;
}

static void nav_cfg_defaults(WdNavCfg *g) {
    memset(g, 0, sizeof(*g));
    g->x = 180;
    g->y = 220;
    g->width = 500;
    g->height = 400;
    g->opacity = 255;
    strcpy(g->theme, "auto");
    g->ttl_ms = 20000;
    g->show_lanes = true;
    g->show_lights = true;
    g->show_alerts = true;
    g->lights_on_top = true;
    g->lanes_ttl_ms = 30000;
    g->lights_ttl_ms = 10000;
    g->alerts_ttl_ms = 10000;
    g->guidance_x = 790;
    g->guidance_y = 120;
    g->guidance_width = 340;
    g->guidance_height = 160;
    strcpy(g->guidance_bg_color, "#00000000");
}

static void music_cfg_defaults(WdMusicCfg *m) {
    memset(m, 0, sizeof(*m));
    m->x = 1080;
    m->y = 200;
    m->width = 560;
    m->height = 400;
    m->opacity = 255;
    strcpy(m->theme, "auto");
    m->ttl_ms = 20000;
    m->lyric_lines = 3;
    m->font_size = 26;
    m->secondary_size = 20;
    m->title_size = 22;
    m->artist_size = 20;
    m->stroke_width = 2;
    strcpy(m->stroke_color, "#000000FF");
    m->show_cover = true;
    m->show_progress = true;
    m->show_metadata = true;
    m->lyric_animation = false;
    m->music_layout = 2;
    m->cover_size = 200;
    m->cover_radius = 13;
    m->line_gap = 6;
    m->before_lines = 1;
    m->after_lines = 1;
    m->cover_gap = 12;
    m->transition_ms = 260;
    strcpy(m->background_color, "#00000000");
    strcpy(m->day_color, "#0D47A1FF");
    strcpy(m->day_prev_color, "#1E88E5FF");
    strcpy(m->day_next_color, "#1E88E5FF");
    strcpy(m->night_color, "#00E676FF");
    strcpy(m->night_prev_color, "#81C784FF");
    strcpy(m->night_next_color, "#81C784FF");
}

static void emit_json(const WdParser *p, uint32_t node, WdOut *o) {
    const WdJson *n = &p->pool[node];
    switch (n->type) {
        case WD_T_NULL: out_lit(o, "null"); break;
        case WD_T_BOOL: out_lit(o, n->i != 0 ? "true" : "false"); break;
        case WD_T_INT: {
            char num[24];
            int nn = snprintf(num, sizeof(num), "%lld", (long long)n->i);
            for (int i = 0; i < nn; i++) out_char(o, num[i]);
            break;
        }
        case WD_T_DBL: {
            char num[32];
            int nn = snprintf(num, sizeof(num), "%.17g", n->d);
            for (int i = 0; i < nn; i++) out_char(o, num[i]);
            break;
        }
        case WD_T_STR: out_esc(o, n->s); break;
        case WD_T_ARR: {
            out_char(o, '[');
            for (uint32_t it = n->first_child; it != WD_NONE; it = p->pool[it].next) {
                if (it != n->first_child) out_char(o, ',');
                emit_json(p, it, o);
            }
            out_char(o, ']');
            break;
        }
        case WD_T_OBJ: {
            out_char(o, '{');
            for (uint32_t it = n->first_child; it != WD_NONE; it = p->pool[it].next) {
                if (it != n->first_child) out_char(o, ',');
                out_char(o, '"');
                for (size_t i = 0; i < p->pool[it].key_len; i++) out_char(o, p->pool[it].key[i]);
                out_lit(o, "\":");
                emit_json(p, it, o);
            }
            out_char(o, '}');
            break;
        }
    }
}

static bool emit_to_buf(const WdParser *p, uint32_t node, char *dst, size_t cap) {
    WdOut o = {dst, cap, 0, false};
    emit_json(p, node, &o);
    if (o.over || o.len + 1 >= cap) return false;
    dst[o.len] = '\0';
    return true;
}

static void out_appearance(WdOut *o, const WdCore *core, const char *theme) {
    int day = -1;
    bool available = core->port.theme_day != NULL && core->port.theme_day(core->port.context, &day);
    if (!available) day = -1;
    const char *effective = "day";
    if (strcmp(theme, "night") == 0) effective = "night";
    else if (strcmp(theme, "day") == 0) effective = "day";
    else if (day == 0) effective = "night";
    out_char(o, ',');
    out_lit(o, "\"appearance\":{\"requested\":");
    out_esc(o, theme);
    out_char(o, ',');
    out_lit(o, "\"effective\":");
    out_esc(o, effective);
    out_lit(o, ",\"source\":\"hmi_root_state2\"");
    out_kv_bool(o, "\"source_available\"", available);
    out_char(o, '}');
}

static void out_revision(WdOut *o, const WdCore *core) {
    out_kv_int(o, "\"epoch\"", (long long)core->config.epoch);
    out_kv_int(o, "\"revision\"", (long long)core->revision);
}

static void out_nav_cfg_obj(WdOut *o, char lead, const char *k, const WdNavCfg *g) {
    out_char(o, lead);
    out_lit(o, k);
    out_lit(o, ":{\"x\":");
    {
        char num[16];
        snprintf(num, sizeof(num), "%d", g->x);
        out_lit(o, num);
    }
    out_kv_int(o, "\"y\"", g->y);
    out_kv_int(o, "\"width\"", g->width);
    out_kv_int(o, "\"height\"", g->height);
    out_kv_int(o, "\"opacity\"", g->opacity);
    out_kv_str(o, "\"theme\"", g->theme);
    out_kv_int(o, "\"ttl_ms\"", (long long)g->ttl_ms);
    out_kv_bool(o, "\"show_lanes\"", g->show_lanes);
    out_kv_bool(o, "\"show_lights\"", g->show_lights);
    out_kv_bool(o, "\"show_alerts\"", g->show_alerts);
    out_kv_bool(o, "\"lights_on_top\"", g->lights_on_top);
    out_kv_int(o, "\"lanes_ttl_ms\"", (long long)g->lanes_ttl_ms);
    out_kv_int(o, "\"lights_ttl_ms\"", (long long)g->lights_ttl_ms);
    out_kv_int(o, "\"alerts_ttl_ms\"", (long long)g->alerts_ttl_ms);
    out_kv_int(o, "\"guidance_x\"", g->guidance_x);
    out_kv_int(o, "\"guidance_y\"", g->guidance_y);
    out_kv_int(o, "\"guidance_width\"", g->guidance_width);
    out_kv_int(o, "\"guidance_height\"", g->guidance_height);
    out_kv_str(o, "\"guidance_background_color\"", g->guidance_bg_color);
    out_kv_int(o, "\"guidance_background_radius\"", g->guidance_bg_radius);
    out_kv_int(o, "\"guidance_padding_top\"", g->guidance_pad_top);
    out_kv_int(o, "\"guidance_padding_bottom\"", g->guidance_pad_bottom);
    out_char(o, '}');
}

static void out_music_cfg_obj(WdOut *o, char lead, const char *k, const WdMusicCfg *m) {
    out_char(o, lead);
    out_lit(o, k);
    out_lit(o, ":{\"x\":");
    {
        char num[16];
        snprintf(num, sizeof(num), "%d", m->x);
        out_lit(o, num);
    }
    out_kv_int(o, "\"y\"", m->y);
    out_kv_int(o, "\"width\"", m->width);
    out_kv_int(o, "\"height\"", m->height);
    out_kv_int(o, "\"opacity\"", m->opacity);
    out_kv_str(o, "\"theme\"", m->theme);
    out_kv_int(o, "\"ttl_ms\"", (long long)m->ttl_ms);
    out_lit(o, ",\"day_colors\":{\"color\":");
    out_esc(o, m->day_color);
    out_lit(o, ",\"previous_color\":");
    out_esc(o, m->day_prev_color);
    out_lit(o, ",\"next_color\":");
    out_esc(o, m->day_next_color);
    out_lit(o, "},\"night_colors\":{\"color\":");
    out_esc(o, m->night_color);
    out_lit(o, ",\"previous_color\":");
    out_esc(o, m->night_prev_color);
    out_lit(o, ",\"next_color\":");
    out_esc(o, m->night_next_color);
    out_lit(o, "}");
    out_kv_bool(o, "\"show_cover\"", m->show_cover);
    out_kv_bool(o, "\"show_progress\"", m->show_progress);
    out_kv_bool(o, "\"show_metadata\"", m->show_metadata);
    out_kv_int(o, "\"music_layout\"", m->music_layout);
    out_kv_int(o, "\"cover_size\"", m->cover_size);
    out_kv_int(o, "\"cover_radius\"", m->cover_radius);
    out_kv_int(o, "\"progress_width\"", m->progress_width);
    out_kv_int(o, "\"progress_gap\"", m->progress_gap);
    out_kv_str(o, "\"background_color\"", m->background_color);
    out_kv_int(o, "\"background_radius\"", m->background_radius);
    out_kv_int(o, "\"background_padding_top\"", m->background_padding_top);
    out_kv_int(o, "\"background_padding_bottom\"", m->background_padding_bottom);
    out_kv_int(o, "\"lyric_lines\"", m->lyric_lines);
    out_kv_int(o, "\"font_size\"", m->font_size);
    out_kv_int(o, "\"secondary_size\"", m->secondary_size);
    out_kv_int(o, "\"title_size\"", m->title_size);
    out_kv_int(o, "\"artist_size\"", m->artist_size);
    out_kv_int(o, "\"line_gap\"", m->line_gap);
    out_kv_int(o, "\"before_lines\"", m->before_lines);
    out_kv_int(o, "\"after_lines\"", m->after_lines);
    out_kv_int(o, "\"cover_gap\"", m->cover_gap);
    out_kv_int(o, "\"anim\"", m->anim);
    out_kv_int(o, "\"transition_ms\"", m->transition_ms);
    out_kv_bool(o, "\"lyric_animation\"", m->lyric_animation);
    out_kv_int(o, "\"stroke_width\"", m->stroke_width);
    out_kv_str(o, "\"stroke_color\"", m->stroke_color);
    out_char(o, '}');
}

static bool cfg_path(const WdCore *core, int channel, char *path, size_t cap) {
    if (core->config.config_dir[0] == '\0') return false;
    int n = snprintf(path, cap, "%s/%s", core->config.config_dir,
                     channel == WD_CHANNEL_NAV ? "navigation.json" : "music.json");
    return n > 0 && (size_t)n < cap;
}

static bool config_save(const WdCore *core, int channel, const char *json) {
    char path[320];
    char tmp[320];
    if (!cfg_path(core, channel, path, sizeof(path))) return false;
    int n = snprintf(tmp, sizeof(tmp), "%s.tmp", path);
    if (n <= 0 || (size_t)n >= sizeof(tmp)) return false;
    FILE *f = fopen(tmp, "wb");
    if (f == NULL) return false;
    size_t len = strlen(json);
    bool ok = fwrite(json, 1, len, f) == len;
    ok = ok && fclose(f) == 0;
    if (!ok) {
        remove(tmp);
        return false;
    }
    if (rename(tmp, path) != 0) {
        remove(tmp);
        return false;
    }
    return true;
}

static void apply_nav_cfg(WdNavCfg *g, const WdParser *p, const WdJson *obj) {
    int64_t v;
    bool present;
    char theme[8];
    if (jstr(p, obj, "theme", theme, sizeof(theme), 7)) {
        if (theme[0] != '\0') strcpy(g->theme, theme);
    }
    if (jint(p, obj, "x", &v, &present) && present) g->x = (int)v;
    if (jint(p, obj, "y", &v, &present) && present) g->y = (int)v;
    if (jint(p, obj, "width", &v, &present) && present) g->width = (int)v;
    if (jint(p, obj, "height", &v, &present) && present) g->height = (int)v;
    if (jint(p, obj, "opacity", &v, &present) && present) g->opacity = (int)v;
    if (jint(p, obj, "ttl_ms", &v, &present) && present) g->ttl_ms = v;
    bool b;
    if (jbool(p, obj, "show_lanes", &b, &present) && present) g->show_lanes = b;
    if (jbool(p, obj, "show_lights", &b, &present) && present) g->show_lights = b;
    if (jbool(p, obj, "show_alerts", &b, &present) && present) g->show_alerts = b;
    if (jbool(p, obj, "lights_on_top", &b, &present) && present) g->lights_on_top = b;
    if (jint(p, obj, "lanes_ttl_ms", &v, &present) && present) g->lanes_ttl_ms = v;
    if (jint(p, obj, "lights_ttl_ms", &v, &present) && present) g->lights_ttl_ms = v;
    if (jint(p, obj, "alerts_ttl_ms", &v, &present) && present) g->alerts_ttl_ms = v;
    if (jint(p, obj, "guidance_x", &v, &present) && present) g->guidance_x = (int)v;
    if (jint(p, obj, "guidance_y", &v, &present) && present) g->guidance_y = (int)v;
    if (jint(p, obj, "guidance_width", &v, &present) && present) g->guidance_width = (int)v;
    if (jint(p, obj, "guidance_height", &v, &present) && present) g->guidance_height = (int)v;
    char color[16];
    if (jstr(p, obj, "guidance_background_color", color, sizeof(color), 15)) {
        if (color[0] != '\0') strcpy(g->guidance_bg_color, color);
    }
    if (jint(p, obj, "guidance_background_radius", &v, &present) && present) {
        g->guidance_bg_radius = (int)v;
    }
    if (jint(p, obj, "guidance_padding_top", &v, &present) && present) g->guidance_pad_top = (int)v;
    if (jint(p, obj, "guidance_padding_bottom", &v, &present) && present) {
        g->guidance_pad_bottom = (int)v;
    }
}

static void apply_music_cfg(WdMusicCfg *m, const WdParser *p, const WdJson *obj) {
    int64_t v;
    bool present;
    bool b;
    char theme[8];
    if (jstr(p, obj, "theme", theme, sizeof(theme), 7)) {
        if (theme[0] != '\0') strcpy(m->theme, theme);
    }
    if (jint(p, obj, "x", &v, &present) && present) m->x = (int)v;
    if (jint(p, obj, "y", &v, &present) && present) m->y = (int)v;
    if (jint(p, obj, "width", &v, &present) && present) m->width = (int)v;
    if (jint(p, obj, "height", &v, &present) && present) m->height = (int)v;
    if (jint(p, obj, "opacity", &v, &present) && present) m->opacity = (int)v;
    if (jint(p, obj, "ttl_ms", &v, &present) && present) m->ttl_ms = v;
    if (jint(p, obj, "lyric_lines", &v, &present) && present) m->lyric_lines = (int)v;
    if (jint(p, obj, "font_size", &v, &present) && present) m->font_size = (int)v;
    if (jint(p, obj, "secondary_size", &v, &present) && present) m->secondary_size = (int)v;
    if (jint(p, obj, "title_size", &v, &present) && present) m->title_size = (int)v;
    if (jint(p, obj, "artist_size", &v, &present) && present) m->artist_size = (int)v;
    if (jint(p, obj, "stroke_width", &v, &present) && present) m->stroke_width = (int)v;
    if (jint(p, obj, "music_layout", &v, &present) && present) m->music_layout = (int)v;
    if (jint(p, obj, "cover_size", &v, &present) && present) m->cover_size = (int)v;
    if (jint(p, obj, "cover_radius", &v, &present) && present) m->cover_radius = (int)v;
    if (jint(p, obj, "progress_width", &v, &present) && present) m->progress_width = (int)v;
    if (jint(p, obj, "progress_gap", &v, &present) && present) m->progress_gap = (int)v;
    if (jint(p, obj, "line_gap", &v, &present) && present) m->line_gap = (int)v;
    if (jint(p, obj, "before_lines", &v, &present) && present) m->before_lines = (int)v;
    if (jint(p, obj, "after_lines", &v, &present) && present) m->after_lines = (int)v;
    if (jint(p, obj, "cover_gap", &v, &present) && present) m->cover_gap = (int)v;
    if (jint(p, obj, "anim", &v, &present) && present) m->anim = (int)v;
    if (jint(p, obj, "transition_ms", &v, &present) && present) m->transition_ms = (int)v;
    if (jbool(p, obj, "show_cover", &b, &present) && present) m->show_cover = b;
    if (jbool(p, obj, "show_progress", &b, &present) && present) m->show_progress = b;
    if (jbool(p, obj, "show_metadata", &b, &present) && present) m->show_metadata = b;
    if (jbool(p, obj, "lyric_animation", &b, &present) && present) m->lyric_animation = b;
    char color[16];
    if (jstr(p, obj, "stroke_color", color, sizeof(color), 15)) {
        if (color[0] != '\0') strcpy(m->stroke_color, color);
    }
    if (jstr(p, obj, "background_color", color, sizeof(color), 15)) {
        if (color[0] != '\0') strcpy(m->background_color, color);
    }
    const WdJson *day = jobj(p, obj, "day_colors");
    if (day != NULL) {
        if (jstr(p, day, "color", color, sizeof(color), 15) && color[0] != '\0') {
            strcpy(m->day_color, color);
        }
        if (jstr(p, day, "previous_color", color, sizeof(color), 15) && color[0] != '\0') {
            strcpy(m->day_prev_color, color);
        }
        if (jstr(p, day, "next_color", color, sizeof(color), 15) && color[0] != '\0') {
            strcpy(m->day_next_color, color);
        }
    }
    const WdJson *night = jobj(p, obj, "night_colors");
    if (night != NULL) {
        if (jstr(p, night, "color", color, sizeof(color), 15) && color[0] != '\0') {
            strcpy(m->night_color, color);
        }
        if (jstr(p, night, "previous_color", color, sizeof(color), 15) && color[0] != '\0') {
            strcpy(m->night_prev_color, color);
        }
        if (jstr(p, night, "next_color", color, sizeof(color), 15) && color[0] != '\0') {
            strcpy(m->night_next_color, color);
        }
    }
}

static bool config_load(WdCore *core, int channel) {
    char path[320];
    if (!cfg_path(core, channel, path, sizeof(path))) return false;
    FILE *f = fopen(path, "rb");
    if (f == NULL) return false;
    static char raw[8192];
    size_t len = fread(raw, 1, sizeof(raw) - 1, f);
    fclose(f);
    if (len == 0) return false;
    raw[len] = '\0';
    core->parser.used = 0;
    core->parser.str_used = 0;
    core->parser.overflow = false;
    core->parser.duplicate = false;
    WdCur c = {raw, len, 0, &core->parser, 0, false};
    uint32_t root = WD_NONE;
    if (!parse_value(&c, &root) || c.pos != len || root == WD_NONE) return false;
    const WdJson *obj = &core->parser.pool[root];
    if (obj->type != WD_T_OBJ) return false;
    const WdJson *inner = jobj(&core->parser, obj, "config");
    const WdJson *src = (inner != NULL && inner->type == WD_T_OBJ) ? inner : obj;
    if (channel == WD_CHANNEL_NAV) {
        apply_nav_cfg(&core->nav_cfg, &core->parser, src);
        core->nav_cfg_saved = true;
    } else {
        apply_music_cfg(&core->music_cfg, &core->parser, src);
        core->music_cfg_saved = true;
    }
    return true;
}

static const signed char *b64_table(void) {
    static signed char tbl[256];
    static bool ready;
    if (!ready) {
        for (int i = 0; i < 256; i++) tbl[i] = -1;
        const char *alpha =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        for (int i = 0; i < 64; i++) tbl[(unsigned char)alpha[i]] = (signed char)i;
        ready = true;
    }
    return tbl;
}

static bool b64_decode(const char *in, size_t len, size_t *out_bytes) {
    const signed char *tbl = b64_table();
    if (len % 4 != 0) return false;
    size_t pad = 0;
    if (len >= 1 && in[len - 1] == '=') pad++;
    if (len >= 2 && in[len - 2] == '=') pad++;
    if (pad > 2) return false;
    size_t body = len - pad;
    for (size_t i = 0; i < body; i++) {
        if (tbl[(unsigned char)in[i]] < 0) return false;
    }
    size_t rem = body % 4;
    if (rem == 1) return false;
    *out_bytes = body / 4 * 3 + (rem == 2 ? 1 : (rem == 3 ? 2 : 0));
    return true;
}

static bool b64_decode_into(const char *in, size_t len, uint8_t *out, size_t out_cap,
                            size_t *out_bytes) {
    size_t bytes = 0;
    if (!b64_decode(in, len, &bytes)) return false;
    if (bytes > out_cap) return false;
    const signed char *tbl = b64_table();
    size_t body = len;
    while (body > 0 && in[body - 1u] == '=') body--;
    size_t o = 0;
    size_t i = 0;
    for (; i + 4u <= body; i += 4u) {
        uint32_t v = ((uint32_t)(uint8_t)tbl[(unsigned char)in[i]] << 18)
            | ((uint32_t)(uint8_t)tbl[(unsigned char)in[i + 1u]] << 12)
            | ((uint32_t)(uint8_t)tbl[(unsigned char)in[i + 2u]] << 6)
            | (uint32_t)(uint8_t)tbl[(unsigned char)in[i + 3u]];
        out[o++] = (uint8_t)(v >> 16);
        out[o++] = (uint8_t)(v >> 8);
        out[o++] = (uint8_t)v;
    }
    size_t rem = body - i;
    if (rem == 2u) {
        uint32_t v = ((uint32_t)(uint8_t)tbl[(unsigned char)in[i]] << 2)
            | ((uint32_t)(uint8_t)tbl[(unsigned char)in[i + 1u]] >> 4);
        out[o++] = (uint8_t)v;
    } else if (rem == 3u) {
        uint32_t v = ((uint32_t)(uint8_t)tbl[(unsigned char)in[i]] << 10)
            | ((uint32_t)(uint8_t)tbl[(unsigned char)in[i + 1u]] << 4)
            | ((uint32_t)(uint8_t)tbl[(unsigned char)in[i + 2u]] >> 2);
        out[o++] = (uint8_t)(v >> 8);
        out[o++] = (uint8_t)v;
    }
    *out_bytes = o;
    return o == bytes;
}

static bool check_whitelist(const WdParser *p, const WdJson *obj, const char *const *keys,
                            size_t n, const char **bad) {
    if (obj == NULL || obj->type != WD_T_OBJ) {
        *bad = NULL;
        return true;
    }
    for (uint32_t it = obj->first_child; it != WD_NONE; it = p->pool[it].next) {
        const WdJson *m = &p->pool[it];
        if (m->key_len == 2 && memcmp(m->key, "op", 2) == 0) continue;
        bool found = false;
        for (size_t i = 0; i < n; i++) {
            if (strlen(keys[i]) == m->key_len && memcmp(keys[i], m->key, m->key_len) == 0) {
                found = true;
                break;
            }
        }
        if (!found) {
            *bad = m->key;
            return false;
        }
    }
    *bad = NULL;
    return true;
}

static const char *const NAV_UPDATE_KEYS[] = {
    "active", "turn", "distance_m", "distance_text", "current_road", "destination", "eta",
    "remaining_m", "remaining_s", "limit_kph", "ttl_ms", "show_next", "show_alerts", "show_limit",
    "lights", "lanes", "service_area", "traffic_text", "exit_name", "speed_kph", "next_ttl_ms",
    "alerts_ttl_ms"
};

static const char *const MUSIC_TRACK_KEYS[] = {
    "track_id", "title", "artist", "playing", "duration_ms", "position_ms", "lyrics",
    "line_index", "cover_rgba", "cover_width", "cover_height"
};

static const char *const MUSIC_SYNC_KEYS[] = {"track_id", "line_index"};

static const char *const NAV_CONFIG_KEYS[] = {
    "x", "y", "width", "height", "opacity", "theme", "ttl_ms", "show_lanes", "show_lights",
    "show_alerts", "lights_on_top", "lanes_ttl_ms", "lights_ttl_ms", "alerts_ttl_ms",
    "guidance_x", "guidance_y", "guidance_width", "guidance_height", "guidance_background_color",
    "guidance_background_radius", "guidance_padding_top", "guidance_padding_bottom"
};

static const char *const MUSIC_CONFIG_KEYS[] = {
    "x", "y", "width", "height", "opacity", "theme", "ttl_ms", "day_colors", "night_colors",
    "show_cover", "show_progress", "show_metadata", "music_layout", "cover_size", "cover_radius",
    "progress_width", "progress_gap", "background_color", "background_radius",
    "background_padding_top", "background_padding_bottom", "lyric_lines", "font_size",
    "secondary_size", "title_size", "artist_size", "line_gap", "before_lines", "after_lines",
    "cover_gap", "anim", "transition_ms", "lyric_animation", "stroke_width", "stroke_color"
};

static const char *const WIDGETS_SET_KEYS[] = {"navigation", "music"};

#define WD_KEY_COUNT(a) (sizeof(a) / sizeof((a)[0]))

static bool validate_lights(const WdParser *p, const WdJson *arr, char *raw, size_t cap,
                            const char **code) {
    if (arr->type != WD_T_ARR) {
        *code = "invalid_lights";
        return false;
    }
    bool any_with = false;
    bool any_without = false;
    for (uint32_t it = arr->first_child; it != WD_NONE; it = p->pool[it].next) {
        const WdJson *e = &p->pool[it];
        if (e->type != WD_T_OBJ) {
            *code = "invalid_lights";
            return false;
        }
        const WdJson *color = jobj(p, e, "color");
        const WdJson *seconds = jobj(p, e, "seconds");
        if (color != NULL && seconds == NULL) {
            *code = "light_color_and_seconds_required";
            return false;
        }
        if (color != NULL) {
            any_with = true;
            if (color->type != WD_T_INT || color->i < 0 || color->i > 9) {
                *code = "invalid_lights";
                return false;
            }
            if (seconds->type != WD_T_INT || seconds->i < 0 || seconds->i > 600) {
                *code = "invalid_lights";
                return false;
            }
        } else {
            any_without = true;
        }
    }
    if (any_with && any_without) {
        *code = "mixed_light_formats";
        return false;
    }
    if (arr->first_child == WD_NONE) {
        raw[0] = '\0';
        *code = NULL;
        return true;
    }
    uint32_t arr_idx = (uint32_t)(arr - (const WdJson *)p->pool);
    if (!emit_to_buf(p, arr_idx, raw, cap)) {
        *code = "invalid_lights";
        return false;
    }
    *code = NULL;
    return true;
}

static bool validate_lanes(const WdParser *p, const WdJson *arr, char *raw, size_t cap,
                           const char **code) {
    if (arr->type != WD_T_ARR) {
        *code = "invalid_lanes";
        return false;
    }
    for (uint32_t it = arr->first_child; it != WD_NONE; it = p->pool[it].next) {
        if (p->pool[it].type != WD_T_OBJ) {
            *code = "invalid_lanes";
            return false;
        }
    }
    if (arr->first_child == WD_NONE) {
        raw[0] = '\0';
        *code = NULL;
        return true;
    }
    uint32_t arr_idx = (uint32_t)(arr - (const WdJson *)p->pool);
    if (!emit_to_buf(p, arr_idx, raw, cap)) {
        *code = "invalid_lanes";
        return false;
    }
    *code = NULL;
    return true;
}

static void out_track_obj(WdOut *o, const WdMusicWidget *w) {
    out_lit(o, ",\"track\":{\"track_id\":");
    out_esc(o, w->frame.track_id);
    out_kv_int(o, "\"line_count\"", w->frame.line_count);
    out_kv_int(o, "\"line_index\"", w->frame.line_index);
    out_kv_int(o, "\"generation\"", (long long)w->generation);
    out_char(o, '}');
}

static void out_guidance_obj(WdOut *o, const WdCore *core) {
    bool lights_visible = core->nav_cfg.show_lights && core->nav.frame.lights_json[0] != '\0'
        && core->nav.visible;
    bool lanes_visible = core->nav_cfg.show_lanes && core->nav.frame.lanes_json[0] != '\0'
        && core->nav.visible;
    out_lit(o, ",\"guidance\":{\"lights_visible\":");
    out_lit(o, lights_visible ? "true" : "false");
    out_lit(o, ",\"lanes_visible\":");
    out_lit(o, lanes_visible ? "true" : "false");
    out_kv_int(o, "\"lights_ttl_ms\"", (long long)core->nav_cfg.lights_ttl_ms);
    out_kv_int(o, "\"lanes_ttl_ms\"", (long long)core->nav_cfg.lanes_ttl_ms);
    out_kv_int(o, "\"next_ttl_ms\"", (long long)core->nav.frame.next_ttl_ms);
    out_kv_int(o, "\"alerts_ttl_ms\"", (long long)core->nav_cfg.alerts_ttl_ms);
    out_char(o, '}');
}

static void out_style_obj(WdOut *o, const WdCore *core) {
    out_lit(o, ",\"style\":{\"lyric_lines\":");
    {
        char num[16];
        snprintf(num, sizeof(num), "%d", core->music_cfg.lyric_lines);
        out_lit(o, num);
    }
    out_kv_int(o, "\"font_size\"", core->music_cfg.font_size);
    out_kv_int(o, "\"cover_size\"", core->music_cfg.cover_size);
    out_kv_int(o, "\"music_layout\"", core->music_cfg.music_layout);
    out_kv_bool(o, "\"lyric_animation\"", core->music_cfg.lyric_animation);
    out_char(o, '}');
}

static void out_visibility(WdOut *o, const WdCore *core, int channel) {
    bool rendered = channel == WD_CHANNEL_NAV ? core->nav.rendered : core->music.rendered;
    bool visible = channel == WD_CHANNEL_NAV ? core->nav.visible : core->music.visible;
    out_kv_bool(o, "\"rendered\"", rendered);
    out_kv_bool(o, "\"effective_visible\"", visible);
}

static void out_ttl(WdOut *o, const WdCore *core, int channel) {
    int64_t ttl = channel == WD_CHANNEL_NAV ? core->nav.ttl_ms : core->music.ttl_ms;
    out_kv_int(o, "\"ttl_ms\"", (long long)ttl);
    out_kv_bool(o, "\"expired\"", false);
}

static bool nav_render(WdCore *core) {
    if (!core->nav.selected || !core->nav_lease_live) {
        core->nav.rendered = false;
        core->nav.visible = false;
        return false;
    }
    bool ok = core->port.nav_render != NULL
        && core->port.nav_render(core->port.context, &core->nav.frame);
    core->nav.rendered = ok;
    core->nav.visible = ok;
    return ok;
}

static bool music_render(WdCore *core) {
    if (!core->music.selected || !core->music_lease_live) {
        core->music.rendered = false;
        core->music.visible = false;
        return false;
    }
    bool ok = core->port.music_render != NULL
        && core->port.music_render(core->port.context, &core->music.frame);
    core->music.rendered = ok;
    core->music.visible = ok;
    return ok;
}

static void handle_show(WdCore *core, int channel, WdOut *o) {
    if (channel == WD_CHANNEL_NAV) {
        core->nav.selected = true;
        if (core->nav_lease_live) {
            nav_render(core);
        } else {
            core->nav.rendered = false;
            core->nav.visible = false;
        }
    } else {
        core->music.selected = true;
        if (core->music_lease_live) {
            music_render(core);
        } else {
            core->music.rendered = false;
            core->music.visible = false;
        }
    }
    core->revision++;
    out_visibility(o, core, channel);
    out_ttl(o, core, channel);
    out_appearance(o, core, channel == WD_CHANNEL_NAV ? core->nav_cfg.theme : core->music_cfg.theme);
    out_revision(o, core);
}

static void handle_hide(WdCore *core, int channel, WdOut *o) {
    if (channel == WD_CHANNEL_NAV) {
        core->nav.selected = false;
        core->nav_lease_live = false;
        core->nav.rendered = false;
        core->nav.visible = false;
        if (core->port.nav_hide != NULL) core->port.nav_hide(core->port.context);
    } else {
        core->music.selected = false;
        core->music_lease_live = false;
        core->music.rendered = false;
        core->music.visible = false;
        if (core->port.music_hide != NULL) core->port.music_hide(core->port.context);
    }
    core->revision++;
    out_kv_bool(o, "\"rendered\"", false);
    out_kv_bool(o, "\"effective_visible\"", false);
    out_appearance(o, core, channel == WD_CHANNEL_NAV ? core->nav_cfg.theme : core->music_cfg.theme);
    out_revision(o, core);
}

static const char *handle_update(WdCore *core, const WdJson *root, WdOut *o) {
    const char *bad = NULL;
    if (!check_whitelist(&core->parser, root, NAV_UPDATE_KEYS, WD_KEY_COUNT(NAV_UPDATE_KEYS), &bad)) {
        return "unknown_field_for_port";
    }
    WdParser *p = &core->parser;
    WdNavFrame f;
    memset(&f, 0, sizeof(f));
    int64_t v = 0;
    bool present = false;
    bool b = false;
    if (!jstr(p, root, "turn", f.turn, sizeof(f.turn), 63)) return "invalid_turn";
    if (!jstr(p, root, "current_road", f.current_road, sizeof(f.current_road), 255)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jstr(p, root, "destination", f.destination, sizeof(f.destination), 511)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jstr(p, root, "eta", f.eta, sizeof(f.eta), 63)) return "invalid_text_or_allocation_failed";
    if (!jstr(p, root, "distance_text", f.distance_text, sizeof(f.distance_text), 63)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jstr(p, root, "exit_name", f.exit_name, sizeof(f.exit_name), 63)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jstr(p, root, "service_area", f.service_area, sizeof(f.service_area), 63)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jstr(p, root, "traffic_text", f.traffic_text, sizeof(f.traffic_text), 63)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jbool(p, root, "active", &b, &present)) return "invalid_range_or_type";
    f.active = b;
    if (!jbool(p, root, "show_next", &b, &present)) return "invalid_range_or_type";
    f.show_next = b;
    if (!jbool(p, root, "show_alerts", &b, &present)) return "invalid_range_or_type";
    f.show_alerts = b;
    if (!jbool(p, root, "show_limit", &b, &present)) return "invalid_range_or_type";
    f.show_limit = b;
    if (!jint(p, root, "distance_m", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.distance_m = v;
    }
    if (!jint(p, root, "remaining_m", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.remaining_m = v;
    }
    if (!jint(p, root, "remaining_s", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.remaining_s = v;
    }
    if (!jint(p, root, "limit_kph", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.limit_kph = v;
    }
    if (!jint(p, root, "speed_kph", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.speed_kph = v;
    }
    int64_t ttl = 20000;
    if (!jint(p, root, "ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v <= 0) return "invalid_range_or_type";
        ttl = v;
    }
    if (!jint(p, root, "next_ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.next_ttl_ms = v;
    }
    if (!jint(p, root, "alerts_ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.alerts_ttl_ms = v;
    }
    const WdJson *lights = jobj(p, root, "lights");
    if (lights != NULL) {
        const char *code = NULL;
        if (!validate_lights(p, lights, f.lights_json, sizeof(f.lights_json), &code)) return code;
    } else if (f.active) {
        f.lights_json[0] = '\0';
    }
    const WdJson *lanes = jobj(p, root, "lanes");
    if (lanes != NULL) {
        const char *code = NULL;
        if (!validate_lanes(p, lanes, f.lanes_json, sizeof(f.lanes_json), &code)) return code;
    } else if (f.active) {
        f.lanes_json[0] = '\0';
    }
    core->nav.frame = f;
    core->nav.ttl_ms = ttl;
    core->nav_lease_live = true;
    core->nav.lease_until = core->last_now + (uint64_t)ttl;
    core->cycle_index++;
    bool rendered = nav_render(core);
    core->revision++;
    out_visibility(o, core, WD_CHANNEL_NAV);
    out_ttl(o, core, WD_CHANNEL_NAV);
    out_guidance_obj(o, core);
    out_appearance(o, core, core->nav_cfg.theme);
    out_revision(o, core);
    out_kv_int(o, "\"cycle_index\"", (long long)core->cycle_index);
    if (!rendered && core->nav.selected && core->nav_lease_live) return "display_write_failed";
    return NULL;
}

static const char *handle_track_load(WdCore *core, const WdJson *root, WdOut *o) {
    const char *bad = NULL;
    if (!check_whitelist(&core->parser, root, MUSIC_TRACK_KEYS, WD_KEY_COUNT(MUSIC_TRACK_KEYS),
                         &bad)) {
        return "unknown_field_for_port";
    }
    WdParser *p = &core->parser;
    WdMusicFrame f;
    memset(&f, 0, sizeof(f));
    int64_t v = 0;
    bool present = false;
    bool b = false;
    const WdJson *tid = jobj(p, root, "track_id");
    if (tid == NULL || tid->type != WD_T_STR || tid->len == 0 || tid->len >= 128) {
        return "invalid_track_request";
    }
    memcpy(f.track_id, tid->s, tid->len);
    f.track_id[tid->len] = '\0';
    if (!jstr(p, root, "title", f.title, sizeof(f.title), 255)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jstr(p, root, "artist", f.artist, sizeof(f.artist), 255)) {
        return "invalid_text_or_allocation_failed";
    }
    if (!jbool(p, root, "playing", &b, &present)) return "invalid_range_or_type";
    f.playing = b;
    if (!jint(p, root, "duration_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0) return "invalid_range_or_type";
        f.duration_ms = v;
    }
    if (!jint(p, root, "position_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (v < 0 || v > f.duration_ms) return "invalid_range_or_type";
        f.position_ms = v;
    }
    const WdJson *lyrics = jobj(p, root, "lyrics");
    if (lyrics == NULL) return "lyrics_required";
    if (lyrics->type != WD_T_ARR) return "invalid_lyrics";
    if (lyrics->n == 0) return "invalid_lyrics";
    if (lyrics->n > WD_LYRIC_LINES_MAX) return "lyric_lines_must_be_1_to_5";
    int idx = 0;
    for (uint32_t it = lyrics->first_child; it != WD_NONE; it = p->pool[it].next) {
        const WdJson *e = &p->pool[it];
        if (e->type != WD_T_STR || e->len > 255) return "invalid_lyrics";
        memcpy(f.lines[idx], e->s, e->len);
        f.lines[idx][e->len] = '\0';
        idx++;
    }
    f.line_count = idx;
    const WdJson *li = jobj(p, root, "line_index");
    if (li == NULL) return "line_index_required";
    if (li->type != WD_T_INT) return "invalid_range_or_type";
    if (li->i < -1 || li->i >= (int64_t)f.line_count) return "line_index_out_of_range";
    f.line_index = (int)li->i;
    const WdJson *cover = jobj(p, root, "cover_rgba");
    if (cover != NULL) {
        if (cover->type != WD_T_STR) return "invalid_cover_or_allocation_failed";
        size_t bytes = 0;
        if (!b64_decode(cover->s, cover->len, &bytes)) {
            return "invalid_cover_or_allocation_failed";
        }
        if (bytes > 8388608u) return "invalid_cover_or_allocation_failed";
        f.cover_bytes = (int64_t)bytes;
        const WdJson *cw = jobj(p, root, "cover_width");
        if (cw != NULL) {
            if (cw->type != WD_T_INT || cw->i < 0) return "invalid_range_or_type";
        }
        const WdJson *ch = jobj(p, root, "cover_height");
        if (ch != NULL) {
            if (ch->type != WD_T_INT || ch->i < 0) return "invalid_range_or_type";
        }
        int pw = 0;
        int ph = 0;
        if (cw != NULL && ch != NULL && cw->i > 0 && ch->i > 0 && cw->i <= 4096 && ch->i <= 4096
            && (uint64_t)cw->i * (uint64_t)ch->i * 4u == (uint64_t)bytes) {
            if (core->cover_cap < bytes) {
                uint8_t *nb = realloc(core->cover_px, bytes);
                if (nb != NULL) {
                    core->cover_px = nb;
                    core->cover_cap = bytes;
                }
            }
            size_t got = 0;
            if (core->cover_cap >= bytes
                && b64_decode_into(cover->s, cover->len, core->cover_px, core->cover_cap, &got)
                && got == bytes) {
                pw = (int)cw->i;
                ph = (int)ch->i;
            }
        }
        core->cover_w = pw;
        core->cover_h = ph;
    } else {
        core->cover_w = 0;
        core->cover_h = 0;
    }
    int64_t ttl = core->music_cfg.ttl_ms;
    if (ttl <= 0) ttl = 20000;
    core->music.frame = f;
    core->music.ttl_ms = ttl;
    core->music_lease_live = true;
    core->music.lease_until = core->last_now + (uint64_t)ttl;
    memcpy(core->music.loaded_track_id, f.track_id, strlen(f.track_id) + 1);
    core->music.generation++;
    core->cycle_index++;
    bool rendered = music_render(core);
    core->revision++;
    out_visibility(o, core, WD_CHANNEL_MUSIC);
    out_ttl(o, core, WD_CHANNEL_MUSIC);
    out_track_obj(o, &core->music);
    out_kv_int(o, "\"cover_bytes\"", (long long)f.cover_bytes);
    out_appearance(o, core, core->music_cfg.theme);
    out_revision(o, core);
    out_kv_int(o, "\"cycle_index\"", (long long)core->cycle_index);
    if (!rendered && core->music.selected && core->music_lease_live) return "display_write_failed";
    return NULL;
}

static const char *handle_track_sync(WdCore *core, const WdJson *root, WdOut *o) {
    const char *bad = NULL;
    if (!check_whitelist(&core->parser, root, MUSIC_SYNC_KEYS, WD_KEY_COUNT(MUSIC_SYNC_KEYS),
                         &bad)) {
        return "sync_fields_only";
    }
    WdParser *p = &core->parser;
    const WdJson *tid = jobj(p, root, "track_id");
    if (tid == NULL || tid->type != WD_T_STR) return "invalid_track_request";
    const WdJson *li = jobj(p, root, "line_index");
    if (li == NULL) return "line_index_required";
    if (li->type != WD_T_INT) return "invalid_range_or_type";
    if (strcmp(core->music.loaded_track_id, "") == 0) return "track_mismatch";
    if (tid->len != strlen(core->music.loaded_track_id)
        || memcmp(tid->s, core->music.loaded_track_id, tid->len) != 0) {
        return "track_mismatch";
    }
    if (li->i < -1 || li->i >= (int64_t)core->music.frame.line_count) {
        return "line_index_out_of_range";
    }
    core->music.frame.line_index = (int)li->i;
    core->revision++;
    out_visibility(o, core, WD_CHANNEL_MUSIC);
    out_track_obj(o, &core->music);
    out_appearance(o, core, core->music_cfg.theme);
    out_revision(o, core);
    if (core->port.music_line != NULL && core->music.visible) {
        core->port.music_line(core->port.context, core->music.frame.line_index);
    }
    return NULL;
}

static void handle_widgets_status(WdCore *core, WdOut *o) {
    out_kv_bool(o, "\"managed\"", true);
    out_lit(o, ",\"selected\":{\"music\":");
    out_lit(o, core->music.selected ? "true" : "false");
    out_lit(o, ",\"navigation\":");
    out_lit(o, core->nav.selected ? "true" : "false");
    out_char(o, '}');
    out_lit(o, ",\"effective\":{\"music\":");
    out_lit(o, core->music.visible ? "true" : "false");
    out_lit(o, ",\"navigation\":");
    out_lit(o, core->nav.visible ? "true" : "false");
    out_char(o, '}');
    out_guidance_obj(o, core);
    out_revision(o, core);
    out_kv_int(o, "\"accepted_revision\"", (long long)core->revision);
    out_kv_int(o, "\"cycle_index\"", (long long)core->cycle_index);
}

static const char *handle_widgets_set(WdCore *core, const WdJson *root, WdOut *o) {
    const char *bad = NULL;
    if (!check_whitelist(&core->parser, root, WIDGETS_SET_KEYS, WD_KEY_COUNT(WIDGETS_SET_KEYS),
                         &bad)) {
        return "unknown_field_for_port";
    }
    WdParser *p = &core->parser;
    bool b = false;
    bool present = false;
    if (!jbool(p, root, "navigation", &b, &present)) return "invalid_range_or_type";
    if (present) {
        core->nav.selected = b;
        if (!b) {
            core->nav_lease_live = false;
            core->nav.rendered = false;
            core->nav.visible = false;
            if (core->port.nav_hide != NULL) core->port.nav_hide(core->port.context);
        } else if (core->nav_lease_live) {
            nav_render(core);
        } else {
            core->nav.rendered = false;
            core->nav.visible = false;
        }
    }
    if (!jbool(p, root, "music", &b, &present)) return "invalid_range_or_type";
    if (present) {
        core->music.selected = b;
        if (!b) {
            core->music_lease_live = false;
            core->music.rendered = false;
            core->music.visible = false;
            if (core->port.music_hide != NULL) core->port.music_hide(core->port.context);
        } else if (core->music_lease_live) {
            music_render(core);
        } else {
            core->music.rendered = false;
            core->music.visible = false;
        }
    }
    core->revision++;
    handle_widgets_status(core, o);
    return NULL;
}

static void handle_config_get(WdCore *core, int channel, WdOut *o) {
    if (channel == WD_CHANNEL_NAV) {
        out_nav_cfg_obj(o, ',', "\"config\"", &core->nav_cfg);
        out_nav_cfg_obj(o, ',', "\"saved_config\"", &core->nav_cfg);
    } else {
        out_music_cfg_obj(o, ',', "\"config\"", &core->music_cfg);
        out_music_cfg_obj(o, ',', "\"saved_config\"", &core->music_cfg);
    }
    bool persistent = core->config.config_dir[0] != '\0';
    out_kv_bool(o, "\"persistent\"", persistent);
    out_kv_bool(o, "\"dirty\"", false);
    out_char(o, ',');
    out_lit(o, "\"storage_error\":null");
    if (channel == WD_CHANNEL_MUSIC) out_style_obj(o, core);
    out_guidance_obj(o, core);
    out_revision(o, core);
}

static bool range_i64(int64_t v, int64_t lo, int64_t hi) {
    return v >= lo && v <= hi;
}

static const char *validate_nav_config(const WdParser *p, const WdJson *root, WdNavCfg *next) {
    int64_t v = 0;
    bool present = false;
    bool b = false;
    char theme[8];
    char color[16];
    if (jstr(p, root, "theme", theme, sizeof(theme), 7)) {
        if (theme[0] != '\0') {
            if (!is_theme(theme)) return "theme_must_be_auto_day_or_night";
            strcpy(next->theme, theme);
        }
    } else {
        return "invalid_range_or_type";
    }
    if (!jint(p, root, "x", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, WD_NAV_W)) return "window_out_of_bounds";
        next->x = (int)v;
    }
    if (!jint(p, root, "y", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, WD_NAV_H)) return "window_out_of_bounds";
        next->y = (int)v;
    }
    if (!jint(p, root, "width", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, WD_NAV_W)) return "window_out_of_bounds";
        next->width = (int)v;
    }
    if (!jint(p, root, "height", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, WD_NAV_H)) return "window_out_of_bounds";
        next->height = (int)v;
    }
    if ((int64_t)next->x + (int64_t)next->width > WD_NAV_W) return "window_out_of_bounds";
    if ((int64_t)next->y + (int64_t)next->height > WD_NAV_H) return "window_out_of_bounds";
    if (!jint(p, root, "opacity", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 255)) return "invalid_range_or_type";
        next->opacity = (int)v;
    }
    if (!jint(p, root, "ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1000, 120000)) return "invalid_range_or_type";
        next->ttl_ms = v;
    }
    if (!jbool(p, root, "show_lanes", &b, &present)) return "invalid_range_or_type";
    if (present) next->show_lanes = b;
    if (!jbool(p, root, "show_lights", &b, &present)) return "invalid_range_or_type";
    if (present) next->show_lights = b;
    if (!jbool(p, root, "show_alerts", &b, &present)) return "invalid_range_or_type";
    if (present) next->show_alerts = b;
    if (!jbool(p, root, "lights_on_top", &b, &present)) return "invalid_range_or_type";
    if (present) next->lights_on_top = b;
    if (!jint(p, root, "lanes_ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1000, 60000)) return "invalid_range_or_type";
        next->lanes_ttl_ms = v;
    }
    if (!jint(p, root, "lights_ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1000, 15000)) return "invalid_range_or_type";
        next->lights_ttl_ms = v;
    }
    if (!jint(p, root, "alerts_ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1000, 60000)) return "invalid_range_or_type";
        next->alerts_ttl_ms = v;
    }
    if (!jint(p, root, "guidance_x", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, WD_NAV_W)) return "guidance_window_out_of_bounds";
        next->guidance_x = (int)v;
    }
    if (!jint(p, root, "guidance_y", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, WD_NAV_H)) return "guidance_window_out_of_bounds";
        next->guidance_y = (int)v;
    }
    if (!jint(p, root, "guidance_width", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, WD_NAV_W)) return "guidance_window_out_of_bounds";
        next->guidance_width = (int)v;
    }
    if (!jint(p, root, "guidance_height", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, WD_NAV_H)) return "guidance_window_out_of_bounds";
        next->guidance_height = (int)v;
    }
    if ((int64_t)next->guidance_x + (int64_t)next->guidance_width > WD_NAV_W) {
        return "guidance_window_out_of_bounds";
    }
    if ((int64_t)next->guidance_y + (int64_t)next->guidance_height > WD_NAV_H) {
        return "guidance_window_out_of_bounds";
    }
    if (jstr(p, root, "guidance_background_color", color, sizeof(color), 15)) {
        if (color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->guidance_bg_color, color);
        }
    } else {
        return "invalid_palette";
    }
    if (!jint(p, root, "guidance_background_radius", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 40)) return "invalid_range_or_type";
        next->guidance_bg_radius = (int)v;
    }
    if (!jint(p, root, "guidance_padding_top", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 60)) return "invalid_range_or_type";
        next->guidance_pad_top = (int)v;
    }
    if (!jint(p, root, "guidance_padding_bottom", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 60)) return "invalid_range_or_type";
        next->guidance_pad_bottom = (int)v;
    }
    return NULL;
}

static const char *validate_music_config(const WdParser *p, const WdJson *root, WdMusicCfg *next) {
    int64_t v = 0;
    bool present = false;
    bool b = false;
    char theme[8];
    char color[16];
    if (jstr(p, root, "theme", theme, sizeof(theme), 7)) {
        if (theme[0] != '\0') {
            if (!is_theme(theme)) return "theme_must_be_auto_day_or_night";
            strcpy(next->theme, theme);
        }
    } else {
        return "invalid_range_or_type";
    }
    if (!jint(p, root, "x", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, WD_NAV_W)) return "window_out_of_bounds";
        next->x = (int)v;
    }
    if (!jint(p, root, "y", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, WD_NAV_H)) return "window_out_of_bounds";
        next->y = (int)v;
    }
    if (!jint(p, root, "width", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, WD_NAV_W)) return "window_out_of_bounds";
        next->width = (int)v;
    }
    if (!jint(p, root, "height", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, WD_NAV_H)) return "window_out_of_bounds";
        next->height = (int)v;
    }
    if ((int64_t)next->x + (int64_t)next->width > WD_NAV_W) return "window_out_of_bounds";
    if ((int64_t)next->y + (int64_t)next->height > WD_NAV_H) return "window_out_of_bounds";
    if (!jint(p, root, "opacity", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 255)) return "invalid_range_or_type";
        next->opacity = (int)v;
    }
    if (!jint(p, root, "ttl_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1000, 120000)) return "invalid_range_or_type";
        next->ttl_ms = v;
    }
    const WdJson *day = jobj(p, root, "day_colors");
    if (day != NULL) {
        if (day->type != WD_T_OBJ) return "invalid_palette";
        if (jstr(p, day, "color", color, sizeof(color), 15) && color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->day_color, color);
        }
        if (jstr(p, day, "previous_color", color, sizeof(color), 15) && color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->day_prev_color, color);
        }
        if (jstr(p, day, "next_color", color, sizeof(color), 15) && color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->day_next_color, color);
        }
    }
    const WdJson *night = jobj(p, root, "night_colors");
    if (night != NULL) {
        if (night->type != WD_T_OBJ) return "invalid_palette";
        if (jstr(p, night, "color", color, sizeof(color), 15) && color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->night_color, color);
        }
        if (jstr(p, night, "previous_color", color, sizeof(color), 15) && color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->night_prev_color, color);
        }
        if (jstr(p, night, "next_color", color, sizeof(color), 15) && color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->night_next_color, color);
        }
    }
    if (!jbool(p, root, "show_cover", &b, &present)) return "invalid_range_or_type";
    if (present) next->show_cover = b;
    if (!jbool(p, root, "show_progress", &b, &present)) return "invalid_range_or_type";
    if (present) next->show_progress = b;
    if (!jbool(p, root, "show_metadata", &b, &present)) return "invalid_range_or_type";
    if (present) next->show_metadata = b;
    if (!jbool(p, root, "lyric_animation", &b, &present)) return "invalid_range_or_type";
    if (present) next->lyric_animation = b;
    if (!jint(p, root, "music_layout", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, 3)) return "invalid_range_or_type";
        next->music_layout = (int)v;
    }
    if (!jint(p, root, "cover_size", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 400)) return "invalid_range_or_type";
        next->cover_size = (int)v;
    }
    if (!jint(p, root, "cover_radius", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 200)) return "invalid_range_or_type";
        next->cover_radius = (int)v;
    }
    if (!jint(p, root, "progress_width", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 1920)) return "invalid_range_or_type";
        next->progress_width = (int)v;
    }
    if (!jint(p, root, "progress_gap", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 40)) return "invalid_range_or_type";
        next->progress_gap = (int)v;
    }
    if (jstr(p, root, "background_color", color, sizeof(color), 15)) {
        if (color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->background_color, color);
        }
    } else {
        return "invalid_palette";
    }
    if (!jint(p, root, "background_radius", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 40)) return "invalid_range_or_type";
        next->background_radius = (int)v;
    }
    if (!jint(p, root, "background_padding_top", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 60)) return "invalid_range_or_type";
        next->background_padding_top = (int)v;
    }
    if (!jint(p, root, "background_padding_bottom", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 60)) return "invalid_range_or_type";
        next->background_padding_bottom = (int)v;
    }
    if (!jint(p, root, "lyric_lines", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 1, 5)) return "lyric_lines_must_be_1_to_5";
        next->lyric_lines = (int)v;
    }
    if (!jint(p, root, "font_size", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 10, 64)) return "invalid_range_or_type";
        next->font_size = (int)v;
    }
    if (!jint(p, root, "secondary_size", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 10, 40)) return "invalid_range_or_type";
        next->secondary_size = (int)v;
    }
    if (!jint(p, root, "title_size", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 40)) return "invalid_range_or_type";
        next->title_size = (int)v;
    }
    if (!jint(p, root, "artist_size", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 32)) return "invalid_range_or_type";
        next->artist_size = (int)v;
    }
    if (!jint(p, root, "line_gap", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 40)) return "invalid_range_or_type";
        next->line_gap = (int)v;
    }
    if (!jint(p, root, "before_lines", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 5)) return "invalid_range_or_type";
        next->before_lines = (int)v;
    }
    if (!jint(p, root, "after_lines", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 5)) return "invalid_range_or_type";
        next->after_lines = (int)v;
    }
    if (!jint(p, root, "cover_gap", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 60)) return "invalid_range_or_type";
        next->cover_gap = (int)v;
    }
    if (!jint(p, root, "anim", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 1)) return "invalid_range_or_type";
        next->anim = (int)v;
    }
    if (!jint(p, root, "transition_ms", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 2000)) return "invalid_range_or_type";
        next->transition_ms = (int)v;
    }
    if (jstr(p, root, "stroke_color", color, sizeof(color), 15)) {
        if (color[0] != '\0') {
            if (!is_color(color)) return "invalid_palette";
            strcpy(next->stroke_color, color);
        }
    } else {
        return "invalid_palette";
    }
    if (!jint(p, root, "stroke_width", &v, &present)) return "invalid_range_or_type";
    if (present) {
        if (!range_i64(v, 0, 8)) return "invalid_range_or_type";
        next->stroke_width = (int)v;
    }
    return NULL;
}

static const char *handle_config_set(WdCore *core, int channel, const WdJson *root, WdOut *o) {
    const char *bad = NULL;
    if (channel == WD_CHANNEL_NAV) {
        if (!check_whitelist(&core->parser, root, NAV_CONFIG_KEYS, WD_KEY_COUNT(NAV_CONFIG_KEYS),
                             &bad)) {
            return "configuration_fields_only";
        }
    } else {
        if (!check_whitelist(&core->parser, root, MUSIC_CONFIG_KEYS,
                             WD_KEY_COUNT(MUSIC_CONFIG_KEYS), &bad)) {
            return "configuration_fields_only";
        }
    }
    char file[4096];
    WdOut fo = {file, sizeof(file), 0, false};
    const char *err = NULL;
    if (channel == WD_CHANNEL_NAV) {
        WdNavCfg next = core->nav_cfg;
        err = validate_nav_config(&core->parser, root, &next);
        if (err == NULL) {
            out_nav_cfg_obj(&fo, '{', "\"config\"", &next);
            if (fo.over) return "config_save_failed";
            out_char(&fo, '}');
            fo.buf[fo.len] = '\0';
            if (core->config.config_dir[0] != '\0' && !config_save(core, channel, file)) {
                return "config_save_failed_state_unchanged";
            }
            core->nav_cfg = next;
            core->nav_cfg_saved = true;
        }
    } else {
        WdMusicCfg next = core->music_cfg;
        err = validate_music_config(&core->parser, root, &next);
        if (err == NULL) {
            out_music_cfg_obj(&fo, '{', "\"config\"", &next);
            if (fo.over) return "config_save_failed";
            out_char(&fo, '}');
            fo.buf[fo.len] = '\0';
            if (core->config.config_dir[0] != '\0' && !config_save(core, channel, file)) {
                return "config_save_failed_state_unchanged";
            }
            core->music_cfg = next;
            core->music_cfg_saved = true;
        }
    }
    if (err != NULL) return err;
    core->revision++;
    handle_config_get(core, channel, o);
    out_appearance(o, core, channel == WD_CHANNEL_NAV ? core->nav_cfg.theme : core->music_cfg.theme);
    return NULL;
}

static void handle_theme_get(WdCore *core, WdOut *o) {
    int day = -1;
    bool available = core->port.theme_day != NULL && core->port.theme_day(core->port.context, &day);
    if (!available) day = -1;
    out_kv_bool(o, "\"day\"", day == 1);
    out_lit(o, ",\"details\":{\"appearance_valid\":");
    out_lit(o, day >= 0 ? "true" : "false");
    out_lit(o, ",\"appearance_source\":");
    out_esc(o, "hmi_root_state2");
    out_kv_int(o, "\"appearance_day\"", day);
    out_lit(o, ",\"theme_id\":");
    out_esc(o, "default");
    out_char(o, '}');
}

enum {
    WD_OP_THEME_GET, WD_OP_SHOW, WD_OP_HIDE, WD_OP_UPDATE, WD_OP_TRACK_LOAD, WD_OP_TRACK_SYNC,
    WD_OP_CONFIG_GET, WD_OP_CONFIG_SET, WD_OP_WIDGETS_STATUS, WD_OP_WIDGETS_SET
};

static bool op_no_fields(int op) {
    return op == WD_OP_SHOW || op == WD_OP_HIDE || op == WD_OP_CONFIG_GET
        || op == WD_OP_WIDGETS_STATUS || op == WD_OP_THEME_GET;
}

static int route_op(int channel, const char *op, size_t len) {
    struct Entry {
        const char *name;
        int op;
    };
    static const struct Entry HMI[] = {{"theme.get", WD_OP_THEME_GET}};
    static const struct Entry NAV[] = {
        {"navigation.show", WD_OP_SHOW}, {"navigation.hide", WD_OP_HIDE},
        {"update", WD_OP_UPDATE}, {"config.get", WD_OP_CONFIG_GET},
        {"config.set", WD_OP_CONFIG_SET}, {"widgets.status", WD_OP_WIDGETS_STATUS},
        {"widgets.set", WD_OP_WIDGETS_SET}
    };
    static const struct Entry MUSIC[] = {
        {"music.show", WD_OP_SHOW}, {"music.hide", WD_OP_HIDE},
        {"track.load", WD_OP_TRACK_LOAD}, {"track.sync", WD_OP_TRACK_SYNC},
        {"config.get", WD_OP_CONFIG_GET}, {"config.set", WD_OP_CONFIG_SET},
        {"widgets.status", WD_OP_WIDGETS_STATUS}, {"widgets.set", WD_OP_WIDGETS_SET}
    };
    const struct Entry *table;
    size_t n;
    if (channel == WD_CHANNEL_HMI) {
        table = HMI;
        n = sizeof(HMI) / sizeof(HMI[0]);
    } else if (channel == WD_CHANNEL_NAV) {
        table = NAV;
        n = sizeof(NAV) / sizeof(NAV[0]);
    } else {
        table = MUSIC;
        n = sizeof(MUSIC) / sizeof(MUSIC[0]);
    }
    for (size_t i = 0; i < n; i++) {
        if (strlen(table[i].name) == len && memcmp(table[i].name, op, len) == 0) {
            return table[i].op;
        }
    }
    return -1;
}

size_t wd_handle_line(WdCore *core, int channel, const char *line, size_t len, char *reply,
                      size_t reply_cap) {
    if (reply_cap < 64) return 0;
    if (len == 0) return wd_error(channel, "invalid_json", reply, reply_cap);
    if (len > WD_LINE_CAP) return wd_error(channel, "request_too_large", reply, reply_cap);
    core->parser.used = 0;
    core->parser.str_used = 0;
    core->parser.overflow = false;
    core->parser.duplicate = false;
    WdCur c = {line, len, 0, &core->parser, 0, false};
    uint32_t root = WD_NONE;
    if (!parse_value(&c, &root)) {
        return wd_error(channel, core->parser.overflow ? "request_too_large" : "invalid_json",
                        reply, reply_cap);
    }
    skip_ws(&c);
    if (c.pos != len || root == WD_NONE) {
        return wd_error(channel, "invalid_json", reply, reply_cap);
    }
    if (core->parser.duplicate) return wd_error(channel, "duplicate_field", reply, reply_cap);
    WdJson *obj = &core->parser.pool[root];
    if (obj->type != WD_T_OBJ) return wd_error(channel, "invalid_request", reply, reply_cap);
    const WdJson *op = jobj(&core->parser, obj, "op");
    if (op == NULL || op->type != WD_T_STR || op->len == 0) {
        return wd_error(channel, "invalid_request", reply, reply_cap);
    }
    int routed = route_op(channel, op->s, op->len);
    if (routed < 0) return wd_error(channel, "invalid_operation", reply, reply_cap);
    if (op_no_fields(routed)) {
        for (uint32_t it = obj->first_child; it != WD_NONE; it = core->parser.pool[it].next) {
            const WdJson *m = &core->parser.pool[it];
            if (m->key_len == 2 && memcmp(m->key, "op", 2) == 0) continue;
            return wd_error(channel, "fields_not_allowed_for_operation", reply, reply_cap);
        }
    }
    WdOut o = {reply, reply_cap, 0, false};
    wd_ok_head(&o, channel);
    const char *err = NULL;
    switch (routed) {
        case WD_OP_THEME_GET: handle_theme_get(core, &o); break;
        case WD_OP_SHOW: handle_show(core, channel, &o); break;
        case WD_OP_HIDE: handle_hide(core, channel, &o); break;
        case WD_OP_UPDATE: err = handle_update(core, obj, &o); break;
        case WD_OP_TRACK_LOAD: err = handle_track_load(core, obj, &o); break;
        case WD_OP_TRACK_SYNC: err = handle_track_sync(core, obj, &o); break;
        case WD_OP_CONFIG_GET: handle_config_get(core, channel, &o); break;
        case WD_OP_CONFIG_SET: err = handle_config_set(core, channel, obj, &o); break;
        case WD_OP_WIDGETS_STATUS: handle_widgets_status(core, &o); break;
        case WD_OP_WIDGETS_SET: err = handle_widgets_set(core, obj, &o); break;
        default: err = "invalid_operation"; break;
    }
    if (err != NULL) return wd_error(channel, err, reply, reply_cap);
    return out_close(&o);
}

void wd_tick(WdCore *core, uint64_t monotonic_ms) {
    core->last_now = monotonic_ms;
    if (core->nav_lease_live && monotonic_ms >= core->nav.lease_until) {
        core->nav_lease_live = false;
        if (core->nav.visible || core->nav.rendered) {
            core->nav.visible = false;
            core->nav.rendered = false;
            if (core->port.nav_hide != NULL) core->port.nav_hide(core->port.context);
        }
    }
    if (core->music_lease_live && monotonic_ms >= core->music.lease_until) {
        core->music_lease_live = false;
        if (core->music.visible || core->music.rendered) {
            core->music.visible = false;
            core->music.rendered = false;
            if (core->port.music_hide != NULL) core->port.music_hide(core->port.context);
        }
    }
}

bool wd_init(WdCore *core, const WdConfig *config, const WdPort *port) {
    if (core == NULL || config == NULL || port == NULL) return false;
    memset(core, 0, sizeof(*core));
    core->config = *config;
    core->port = *port;
    core->parser.pool = calloc(WD_JSON_POOL, sizeof(WdJson));
    core->parser.str_pool = malloc(WD_STR_POOL);
    if (core->parser.pool == NULL || core->parser.str_pool == NULL) {
        wd_shutdown(core);
        return false;
    }
    nav_cfg_defaults(&core->nav_cfg);
    music_cfg_defaults(&core->music_cfg);
    config_load(core, WD_CHANNEL_NAV);
    config_load(core, WD_CHANNEL_MUSIC);
    core->revision = 0;
    core->cycle_index = 0;
    return true;
}

void wd_shutdown(WdCore *core) {
    if (core == NULL) return;
    free(core->parser.pool);
    free(core->parser.str_pool);
    core->parser.pool = NULL;
    core->parser.str_pool = NULL;
    free(core->cover_px);
    core->cover_px = NULL;
    core->cover_cap = 0;
    core->cover_w = 0;
    core->cover_h = 0;
}
