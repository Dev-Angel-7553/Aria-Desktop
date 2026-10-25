<p align="center">
  <img src="src/main/resources/aria.png" width="112" alt="Aria">
</p>

**English** · [Русский](README.ru.md)

# Aria Desktop

An unofficial Yandex Music client for Linux and Windows. This is the desktop version.
There is also [Aria for Android](https://github.com/Dev-Angel-7553/Aria-Android).

## Features

- home with My Wave, collections, playlists and the chart
- search for tracks, artists, albums and playlists
- collection: Liked, playlists, albums
- lyrics at the side or fullscreen, the line fills in word by word
- queue, shuffle, repeat, resuming from the same place after a restart
- listens are counted by Yandex, the wave learns from skips
- cache of played tracks, they replay without loading
- export of any list to CSV
- dark, black and light themes, accent color from the cover, interface scale
- hotkeys, see below

Java is not required, it is bundled in the archive. ffmpeg is not needed either.

## Hotkeys

| Keys | What they do |
| --- | --- |
| Space | play and pause |
| Ctrl + → / ← | next and previous track |
| Shift + → / ← | seek 10 seconds |
| Ctrl + ↑ / ↓ | volume |
| Ctrl + L | like the current track |
| Ctrl + F | search |
| Ctrl + Enter | open and close the lyrics screen |
| Alt + ← | back |
| F11 | fullscreen |
| Esc | leave fullscreen or close the lyrics |

## Installation

Archives are in [Releases](../../releases).

**Linux.** Download `Aria-<version>-linux-x64.tar.gz`:

```bash
tar -xzf Aria-*-linux-x64.tar.gz
./aria/install.sh
```

The app is installed to `~/.local/opt/aria` and appears in the menu and as the `aria` command.
Uninstall: `~/.local/opt/aria/uninstall.sh`, together with the settings: `--purge`.
It can also run without installing: `./aria/bin/aria`.

**Windows.** Download `Aria-<version>-windows-x64.zip`, unpack it anywhere and run `Aria.exe`.
If something fails to start, `aria-debug.cmd` opens a window with the log.

## Signing in

You need a Yandex Music OAuth token. The login screen explains step by step how to get it:
open the Yandex page, sign in, copy the address and paste it into the app.

The token is stored in `~/.config/aria/session` (on Windows `%APPDATA%\Aria\session`),
the file is readable only by your user. It is sent only to Yandex.

## Build

You need JDK 17 or newer.

```bash
./gradlew run                      # run
./gradlew dist                     # archive for the current system
./gradlew dist -Ptarget=linux      # archive for Linux
./gradlew dist -Ptarget=windows    # archive for Windows
```

On Windows the same works through `gradlew.bat`. Archives appear in `build/dist/`.

You can build for another system from anywhere: Gradle downloads the right Java
(Temurin 21) itself and puts it in the archive. The first Windows build from Linux also
downloads a Windows JDK once, to take the `Aria.exe` launcher from it.
Everything downloaded is cached in `~/.gradle/caches/aria-desktop`.

There is also `-Parch=arm64` for Linux on ARM.

Every push to `main` is built in GitHub Actions on Linux and Windows and published
as release `v<version>` from `gradle.properties`.

## Stack

Kotlin, Compose Multiplatform, Material 3, OkHttp, kotlinx.serialization, JLayer for MP3.

## Disclaimer

The project is not affiliated with Yandex. The Yandex Music API is unofficial and may change at any moment.
You need your own Yandex Plus subscription for the music.
