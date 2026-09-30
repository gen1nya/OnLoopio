#!/system/bin/sh
# root-only immutable firmware seed; once per image revision, never on every boot.
umask 077
revision=$(cat /system/etc/onloopio-seed.id) || exit 1
attempt=0
while [ "$attempt" -lt 120 ]; do
    app_uid=
    while read package uid rest; do
        if [ "$package" = io.onloopio ]; then app_uid=$uid; break; fi
    done < /data/system/packages.list
    if [ -n "$app_uid" ] && [ -d /data/data/io.onloopio ]; then break; fi
    sleep 1
    attempt=$((attempt + 1))
done
case "$app_uid" in ''|*[!0-9]*) exit 1;; esac
[ -d /data/data/io.onloopio ] || exit 1
private=/data/data/io.onloopio/files
mkdir -p "$private" || exit 1
chown "$app_uid:$app_uid" "$private" || exit 1
chmod 700 "$private" || exit 1
if [ -f "$private/onloopio-seed.id" ] && [ "$(cat "$private/onloopio-seed.id")" = "$revision" ]; then exit 0; fi
cp /system/etc/onloopio-config.json "$private/.onloopio-config.tmp" || exit 1
chown "$app_uid:$app_uid" "$private/.onloopio-config.tmp" || exit 1
chmod 600 "$private/.onloopio-config.tmp" || exit 1
mv "$private/.onloopio-config.tmp" "$private/onloopio-config.json" || exit 1
echo "$revision" > "$private/onloopio-seed.id" || exit 1
chown "$app_uid:$app_uid" "$private/onloopio-seed.id" || exit 1
# No credential is passed to am. HOME may already be running when the seed arrives.
/system/bin/am start -a io.onloopio.action.RELOAD -n io.onloopio/.ui.PlaylistActivity >/dev/null 2>&1
