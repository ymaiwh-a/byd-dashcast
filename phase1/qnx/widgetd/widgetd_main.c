#include "widgetd.h"

#include "wd_canvas.h"
#include "wd_font.h"
#include "wd_render.h"
#include "wd_screen.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#define WD_PORT_HMI 31770
#define WD_PORT_NAV 31771
#define WD_PORT_MUSIC 31772
#define WD_MAX_CLIENTS 8
#define WD_CLIENT_CAP (WD_LINE_CAP + 2u)

typedef struct {
    int fd;
    int channel;
    char *buf;
    size_t len;
} WdClient;

static uint64_t wd_now_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) return 0;
    return (uint64_t)ts.tv_sec * 1000u + (uint64_t)(ts.tv_nsec / 1000000L);
}

static bool main_theme_day(void *context, int *day) {
    return wd_screen_theme_day(context, day);
}

static void main_font_destroy(WdFont *font, bool is_ft) {
    if (is_ft) wd_font_freetype_destroy(font);
    else wd_font_fake_destroy(font);
}

static int wd_make_listener(int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    int on = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons((uint16_t)port);
    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) != 0) {
        close(fd);
        return -1;
    }
    if (listen(fd, 4) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static void wd_accept(int listen_fd, int channel, WdClient *clients) {
    int fd = accept(listen_fd, NULL, NULL);
    if (fd < 0) return;
    int on = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &on, sizeof(on));
    for (int i = 0; i < WD_MAX_CLIENTS; i++) {
        if (clients[i].fd < 0) {
            clients[i].buf = malloc(WD_CLIENT_CAP);
            if (clients[i].buf == NULL) {
                close(fd);
                return;
            }
            clients[i].fd = fd;
            clients[i].channel = channel;
            clients[i].len = 0;
            return;
        }
    }
    close(fd);
}

static void wd_send_all(int fd, const char *data, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t n = send(fd, data + off, len - off, 0);
        if (n <= 0) return;
        off += (size_t)n;
    }
}

static void wd_close_client(WdClient *client) {
    if (client->fd >= 0) close(client->fd);
    free(client->buf);
    client->buf = NULL;
    client->fd = -1;
    client->len = 0;
}

static void wd_readable(WdClient *client, WdCore *core, char *reply) {
    size_t room = WD_CLIENT_CAP - 1u - client->len;
    if (room == 0) room = 1;
    ssize_t n = recv(client->fd, client->buf + client->len, room, 0);
    if (n <= 0) {
        wd_close_client(client);
        return;
    }
    client->len += (size_t)n;
    for (;;) {
        char *nl = memchr(client->buf, '\n', client->len);
        if (nl == NULL) {
            if (client->len >= WD_LINE_CAP + 1u) {
                size_t r = wd_handle_line(core, client->channel, client->buf, client->len, reply,
                                          WD_REPLY_CAP);
                if (r > 0) wd_send_all(client->fd, reply, r);
                client->len = 0;
            }
            return;
        }
        size_t used = (size_t)(nl - client->buf) + 1u;
        size_t line_len = (size_t)(nl - client->buf);
        if (line_len > 0 && client->buf[line_len - 1u] == '\r') line_len--;
        size_t r = wd_handle_line(core, client->channel, client->buf, line_len, reply,
                                  WD_REPLY_CAP);
        if (r > 0) wd_send_all(client->fd, reply, r);
        memmove(client->buf, client->buf + used, client->len - used);
        client->len -= used;
    }
}

int main(int argc, char **argv) {
    WdConfig config;
    memset(&config, 0, sizeof(config));
    strcpy(config.config_dir, "/fs/tmpfs/widgetd");
    config.epoch = (uint64_t)time(NULL);
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--config-dir") == 0 && i + 1 < argc) {
            i++;
            size_t len = strlen(argv[i]);
            if (len >= sizeof(config.config_dir)) {
                fprintf(stderr, "WD_CONFIG_DIR_TOO_LONG\n");
                return 2;
            }
            memcpy(config.config_dir, argv[i], len + 1u);
        } else {
            fprintf(stderr, "WD_USAGE widgetd [--config-dir DIR]\n");
            return 2;
        }
    }
    signal(SIGPIPE, SIG_IGN);
    WdCore core;
    WdPort empty_port;
    memset(&empty_port, 0, sizeof(empty_port));
    if (!wd_init(&core, &config, &empty_port)) {
        fprintf(stderr, "WD_INIT_FAILED\n");
        return 1;
    }
    static const char *const FONT_PATHS[] = {
        "/base/usr/share/fonts/msyh.ttf",
        "/base/app/rc_hmi/Resource_Font/msyh.ttf",
        "msyhbd.ttf"
    };
    WdFont *font = wd_font_freetype_create(FONT_PATHS, 3);
    bool font_is_ft = font != NULL;
    if (font == NULL) font = wd_font_fake_create();
    if (font == NULL) {
        fprintf(stderr, "WD_FONT_INIT_FAILED\n");
        wd_shutdown(&core);
        return 1;
    }
    WdScreen *nav_screen = wd_screen_create("nav", WD_NAV_W, WD_NAV_H);
    WdScreen *music_screen = wd_screen_create("music", WD_NAV_W, WD_NAV_H);
    WdSurface nav_surface;
    WdSurface music_surface;
    bool nav_ok = nav_screen != NULL && wd_screen_surface(nav_screen, &nav_surface);
    bool music_ok = music_screen != NULL && wd_screen_surface(music_screen, &music_surface);
    if (!nav_ok) memset(&nav_surface, 0, sizeof(nav_surface));
    if (!music_ok) memset(&music_surface, 0, sizeof(music_surface));
    WdRender *render = wd_render_create(&core, &nav_surface, &music_surface, font,
                                        main_theme_day, nav_screen);
    if (render == NULL) {
        fprintf(stderr, "WD_RENDER_INIT_FAILED\n");
        wd_screen_destroy(nav_screen);
        wd_screen_destroy(music_screen);
        main_font_destroy(font, font_is_ft);
        wd_shutdown(&core);
        return 1;
    }
    core.port = wd_render_port(render);
    int last_nav_x = -1;
    int last_nav_y = -1;
    int last_nav_op = -1;
    int last_music_x = -1;
    int last_music_y = -1;
    int last_music_op = -1;
    int listen_fd[3];
    listen_fd[WD_CHANNEL_HMI] = wd_make_listener(WD_PORT_HMI);
    listen_fd[WD_CHANNEL_NAV] = wd_make_listener(WD_PORT_NAV);
    listen_fd[WD_CHANNEL_MUSIC] = wd_make_listener(WD_PORT_MUSIC);
    for (int i = 0; i < 3; i++) {
        if (listen_fd[i] < 0) {
            fprintf(stderr, "WD_LISTEN_FAILED port=%d errno=%d\n", 31770 + i, errno);
            for (int j = 0; j < i; j++) close(listen_fd[j]);
            wd_render_destroy(render);
            wd_screen_destroy(nav_screen);
            wd_screen_destroy(music_screen);
            main_font_destroy(font, font_is_ft);
            wd_shutdown(&core);
            return 1;
        }
    }
    WdClient clients[WD_MAX_CLIENTS];
    for (int i = 0; i < WD_MAX_CLIENTS; i++) {
        clients[i].fd = -1;
        clients[i].channel = -1;
        clients[i].buf = NULL;
        clients[i].len = 0;
    }
    char *reply = malloc(WD_REPLY_CAP);
    if (reply == NULL) {
        fprintf(stderr, "WD_REPLY_ALLOC_FAILED\n");
        for (int i = 0; i < 3; i++) close(listen_fd[i]);
        wd_render_destroy(render);
        wd_screen_destroy(nav_screen);
        wd_screen_destroy(music_screen);
        main_font_destroy(font, font_is_ft);
        wd_shutdown(&core);
        return 1;
    }
    fprintf(stderr, "WD_WIDGETD_READY epoch=%llu\n", (unsigned long long)config.epoch);
    for (;;) {
        fd_set rfds;
        FD_ZERO(&rfds);
        int max_fd = 0;
        for (int i = 0; i < 3; i++) {
            FD_SET(listen_fd[i], &rfds);
            if (listen_fd[i] > max_fd) max_fd = listen_fd[i];
        }
        for (int i = 0; i < WD_MAX_CLIENTS; i++) {
            if (clients[i].fd >= 0) {
                FD_SET(clients[i].fd, &rfds);
                if (clients[i].fd > max_fd) max_fd = clients[i].fd;
            }
        }
        struct timeval tv;
        tv.tv_sec = 0;
        tv.tv_usec = 250000;
        int rc = select(max_fd + 1, &rfds, NULL, NULL, &tv);
        if (rc > 0) {
            for (int i = 0; i < 3; i++) {
                if (FD_ISSET(listen_fd[i], &rfds)) wd_accept(listen_fd[i], i, clients);
            }
            for (int i = 0; i < WD_MAX_CLIENTS; i++) {
                if (clients[i].fd >= 0 && FD_ISSET(clients[i].fd, &rfds)) {
                    wd_readable(&clients[i], &core, reply);
                }
            }
        }
        wd_tick(&core, wd_now_ms());
        if (nav_screen != NULL
            && (core.nav_cfg.x != last_nav_x || core.nav_cfg.y != last_nav_y
                || core.nav_cfg.opacity != last_nav_op)) {
            wd_screen_set_geometry(nav_screen, core.nav_cfg.x, core.nav_cfg.y,
                                   core.nav_cfg.opacity);
            last_nav_x = core.nav_cfg.x;
            last_nav_y = core.nav_cfg.y;
            last_nav_op = core.nav_cfg.opacity;
        }
        if (music_screen != NULL
            && (core.music_cfg.x != last_music_x || core.music_cfg.y != last_music_y
                || core.music_cfg.opacity != last_music_op)) {
            wd_screen_set_geometry(music_screen, core.music_cfg.x, core.music_cfg.y,
                                   core.music_cfg.opacity);
            last_music_x = core.music_cfg.x;
            last_music_y = core.music_cfg.y;
            last_music_op = core.music_cfg.opacity;
        }
    }
}
