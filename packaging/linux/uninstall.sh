#!/usr/bin/env bash
set -euo pipefail

data_root="${XDG_DATA_HOME:-$HOME/.local/share}"
app_root="$HOME/.local/opt/aria"

rm -f -- "$HOME/.local/bin/aria" "$data_root/applications/ru.muz.Aria.desktop" "$data_root/icons/hicolor/256x256/apps/ru.muz.Aria.png"
rm -rf -- "$app_root"

if [[ "${1:-}" == "--purge" ]]; then
    rm -rf -- "${XDG_CONFIG_HOME:-$HOME/.config}/aria" "${XDG_CACHE_HOME:-$HOME/.cache}/aria"
    printf 'Aria удалена вместе с настройками и токеном\n'
else
    printf 'Aria удалена. Настройки и вход остались в %s, удалить их: %s --purge\n' "${XDG_CONFIG_HOME:-$HOME/.config}/aria" "$0"
fi
