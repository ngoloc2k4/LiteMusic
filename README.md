# LiteMusic 🎵

A lightweight, minimal music player for Android 7.0+ (API 24+) powered by Jetpack Compose and AndroidX Media3.

## Features

- **Multi-Source Free Streaming**: YouTube Music (Opus 160kbps audio-only), SoundCloud, and Spotify metadata bridge.
- **Background Playback**: Full `MediaSessionService` support with lockscreen controls, Bluetooth headset actions, and auto-pause on headphone disconnect.
- **Volume Normalization**: Hardware DSP volume normalizer via `LoudnessEnhancer` (-14 LUFS target).
- **Lightweight & Battery Efficient**: Under 12 MB APK size (R8 ProGuard) and minimal RAM footprint (< 60 MB).
- **Persistent Signing Key**: Built-in dev keystore (`keystore/dev-release.jks`) ensuring all CI/CD updates share the same signature for seamless app updates.
- **Automated Rolling Beta**: Every commit to `main` builds, signs, and updates the latest APK automatically via GitHub Actions under the [Beta Release](https://github.com/ngoloc2k4/LiteMusic/releases/tag/beta).

## Download

Get the latest pre-signed APK from [GitHub Releases (Beta)](https://github.com/ngoloc2k4/LiteMusic/releases/tag/beta).
