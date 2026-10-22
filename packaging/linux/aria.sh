#!/bin/sh
APP_HOME=$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)
exec "$APP_HOME/runtime/bin/java" \
    @JAVA_OPTIONS@ \
    --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED \
    -Dawt.useSystemAAFontSettings=on \
    -Daria.home="$APP_HOME" \
    -cp "$APP_HOME/lib/*" \
    @MAIN@ "$@"
