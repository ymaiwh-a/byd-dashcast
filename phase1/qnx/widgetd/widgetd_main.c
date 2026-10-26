#include "widgetd.h"

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

static bool stub_nav_render(void *context, const WdNavFrame *frame) {
    (void)context;
    fprintf(stderr, "WD_NAV_RENDER road=%s turn=%s lights=%s lanes=%s\n", frame->current_road,
            frame->turn, frame->lights_json, frame->lanes_json);
    return true;
}

static void stub_nav_hide(void *context) {
    (void)context;
    fprintf(stderr, "WD_NAV_HIDE\n");
}

static bool stub_music_render(void *context, const WdMusicFrame *frame) {
    (void)context;
    fprintf(stderr, "WD_MUSIC_RENDER track=%s title=%s lines=%d index=%d\n", frame->track_id,
            frame->title, frame->line_count, frame->line_index);
    return true;
}

static void stub_music_hide(void *context) {
    (void)context;
    fprintf(stderr, "WD_MUSIC_HIDE\n");
}

static void stub_music_line(void *context, int line_index) {
    (void)context;
    fprintf(stderr, "WD_MUSIC_LINE %d\n", line_index);
}

static bool stub_theme_day(void *context, int *day) {
    (void)context;
    (void)day;
    return false;
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
    const WdPort port = {NULL, stub_nav_render, stub_nav_hide, stub_music_render, stub_music_hide,
                         stub_music_line, stub_theme_day};
    WdCore core;
    if (!wd_init(&core, &config, &port)) {
        fprintf(stderr, "WD_INIT_FAILED\n");
        return 1;
    }
    int listen_fd[3];
    listen_fd[WD_CHANNEL_HMI] = wd_make_listener(WD_PORT_HMI);
    listen_fd[WD_CHANNEL_NAV] = wd_make_listener(WD_PORT_NAV);
    listen_fd[WD_CHANNEL_MUSIC] = wd_make_listener(WD_PORT_MUSIC);
    for (int i = 0; i < 3; i++) {
        if (listen_fd[i] < 0) {
            fprintf(stderr, "WD_LISTEN_FAILED port=%d errno=%d\n", 31770 + i, errno);
            for (int j = 0; j < i; j++) close(listen_fd[j]);
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
    }
}
