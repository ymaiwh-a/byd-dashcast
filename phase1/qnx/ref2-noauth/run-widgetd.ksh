#!/bin/sh
set -f
umask 077
RUN=/fs/tmpfs/dip-usb
PROGRAM=$RUN/widgetd
[ -x "$PROGRAM" ] || exit 1
for name in widgetd-owner widgetd-cleanup widgetd-cleanup.tmp widgetd-guard; do
    [ ! -L "$RUN/$name" ] || exit 1
done
mkdir "$RUN/widgetd-guard" || { echo WD_WRAPPER_CONFLICT; exit 1; }
mkdir -p "$RUN/widgetd-config" || { echo WD_CONFIG_DIR_FAILED; exit 1; }
trap 'echo WD_WRAPPER_INTERRUPTED; rmdir "$RUN/widgetd-guard"; exit 125' INT TERM
"$PROGRAM" --config-dir "$RUN/widgetd-config" &
native_pid=$!
printf 'WD %s\n' "$native_pid" > "$RUN/widgetd-owner"
wait "$native_pid"
native_rc=$?
printf 'WD %s %s\n' "$native_pid" "$native_rc" > "$RUN/widgetd-cleanup.tmp"
mv "$RUN/widgetd-cleanup.tmp" "$RUN/widgetd-cleanup" || exit 1
[ "$native_rc" = 0 ] || exit "$native_rc"
rmdir "$RUN/widgetd-guard" || exit 1
printf 'WD_NATIVE_EXIT pid=%s rc=%s\n' "$native_pid" "$native_rc"
exit "$native_rc"
