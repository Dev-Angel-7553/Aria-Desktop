#!/usr/bin/env bash
set -euo pipefail

source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
app_root="$HOME/.local/opt/aria"
bin_root="$HOME/.local/bin"
apps_root="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
icons_root="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/256x256/apps"

if [[ "$(realpath "$source_root")" != "$(realpath -m "$app_root")" ]]; then
    mkdir -p "$app_root"
    rm -rf -- "$app_root/bin" "$app_root/lib" "$app_root/runtime"
    cp -a "$source_root/." "$app_root/"
fi

mkdir -p "$bin_root" "$apps_root" "$icons_root"
ln -sfn "$app_root/bin/aria" "$bin_root/aria"
cp "$app_root/aria.png" "$icons_root/ru.muz.Aria.png"

cat > "$apps_root/ru.muz.Aria.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Aria
GenericName=Музыка
Comment=Клиент Яндекс Музыки с текстами песен
Exec=$app_root/bin/aria
Icon=ru.muz.Aria
Terminal=false
Categories=AudioVideo;Audio;Player;
Keywords=music;yandex;lyrics;музыка;яндекс;
StartupWMClass=Aria
DESKTOP

command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q "$apps_root" || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q -t "${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor" || true

printf 'Aria @VERSION@ установлена в %s\n' "$app_root"
printf 'Запуск из меню приложений или командой: aria\n'
case ":$PATH:" in
    *":$bin_root:"*) ;;
    *) printf 'Чтобы команда aria работала в терминале, добавьте %s в PATH\n' "$bin_root" ;;
esac
