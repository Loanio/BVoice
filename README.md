<div align="right">

**English** | 中文

</div>

```
██████╗ ██████╗ ███████╗███████╗███╗   ██╗ ██████╗
██╔══██╗██╔══██╗██╔════╝██╔════╝████╗  ██║██╔═══██╗
██████╔╝██████╔╝█████╗  █████╗  ██╔██╗ ██║██║   ██║
██╔══██╗██╔══██╗██╔══╝  ██╔══╝  ██║╚██╗██║██║   ██║
██████╔╝██║  ██║███████╗███████╗██║ ╚████║╚██████╔╝
╚═════╝ ╚═╝  ╚═╝╚══════╝╚══════╝╚═╝  ╚═══╝ ╚═════╝
```

<p align="center">
  <a href="https://github.com/Loanio/BreenoTTSHook"><img src="https://img.shields.io/github/stars/Loanio/BreenoTTSHook?label=stars" alt="stars"/></a>
  <a href="https://github.com/Loanio/BreenoTTSHook/releases/latest"><img src="https://img.shields.io/github/v/release/Loanio/BreenoTTSHook?include_prereleases&label=release" alt="release"/></a>
  <a href="https://github.com/Loanio/BreenoTTSHook/releases/latest"><img src="https://img.shields.io/github/downloads/Loanio/BreenoTTSHook/total?label=downloads" alt="downloads"/></a>
</p>

<p align="center">
  Android · LSPosed · YukiHookAPI · GPT-SoVITS · Breeno Assistant
</p>

# BreenoTTSHook

An Android LSPosed module that connects Breeno Assistant to a self-hosted GPT-SoVITS service.

BreenoTTSHook provides both a standalone module app and an in-app Breeno settings entry. They share the same local configuration for voice selection, connection checks, previews, and speech synthesis. The module does not modify the Breeno APK and does not include a default service address or credentials.

> [!IMPORTANT]
> **Beta software.** Features, compatibility, and stability are still being validated and may change. The module currently supports only the Breeno Assistant versions listed in [Compatibility](#compatibility).

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Compatibility](#compatibility)
- [Download](#download)
- [Installation and configuration](#installation-and-configuration)
- [Settings](#settings)
- [Build from source](#build-from-source)
- [Project layout](#project-layout)
- [Diagnostics and feedback](#diagnostics-and-feedback)

## Features

- Adds GPT-SoVITS speech synthesis to Breeno Assistant's TTS pipeline.
- Loads character and emotion catalogs; supports manual voice settings, connection checks, and voice previews.
- Supports streaming responses and WAV, PCM, MP3, and OGG audio formats.
- Plays generated audio through `AudioTrack`, with cancellation and session management.
- Can fall back to Breeno's original TTS while third-party audio has not started, when enabled in the configuration.
- Shares versioned configuration between the module app and Breeno's embedded settings page.
- Includes Chinese and English settings UI, copyable diagnostic logs, and privacy-aware utterance identifiers.

## Architecture

```text
Breeno Assistant
    |
    v
LSPosed / YukiHookAPI
    |
    +-- 11.8.3 WebSocket TTS route
    +-- 12.9.9 Engine / streaming TTS route
            |
            v
      GptSovitsClient
            |
            +-- GET /character_list
            +-- POST /tts
                    |
                    v
              AudioTrack playback
```

Configuration is read and written through the module's `ContentProvider` and stored in its own `SharedPreferences`. The Breeno process only reads this shared configuration.

## Compatibility

| Component | Supported configuration |
| --- | --- |
| Android | minSdk 31, targetSdk 35 |
| Root environment | Magisk or KernelSU with LSPosed |
| Breeno Assistant 11.8.3 | WebSocket TTS route |
| Breeno Assistant 12.9.9 | Engine and streaming TTS routes |
| GPT-SoVITS service | `GET /character_list` and `POST /tts` |

Other Breeno versions are not matched heuristically. See the [compatibility notes](docs/COMPATIBILITY.md) for the supported-version boundaries.

## Download

- [Debug APK](releases/BreenoTTSHook-0.1.0-debug.apk) — signed with the Android debug key.
- [Release APK](releases/BreenoTTSHook-0.1.0-release-unsigned.apk) — built with the release variant and not signed; sign it with your own release key before installing.

## Installation and configuration

1. Install the module APK.
2. Enable the module in LSPosed and set its scope to `com.heytap.speechassist`.
3. Restart Breeno Assistant.
4. Open the BreenoTTSHook app, or open **Third-party voice** in Breeno settings.
5. Enter the GPT-SoVITS service address, load the character and emotion catalog, and run a preview.
6. Enable **Use third-party TTS**.

You provide the service address. When it is empty, the module does not make service requests.

For the full procedure, see [installation and usage](docs/INSTALL.md).

## Settings

| Category | Available settings |
| --- | --- |
| Basic | Enable state, service address, failure fallback |
| Voice | Character, emotion, text language, manual voice |
| Synthesis | Audio format, streaming response, speech rate, and generation parameters |
| Network | Connection and read timeouts |
| Diagnostics | Strict mode, module player, log level, and preview text |

The default log level is `ERROR`. Diagnostic records do not contain the complete utterance text, cookies, tokens, or complete request bodies.

## Build from source

Requirements: JDK 17 and Android SDK 35.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to [`app/build/outputs/apk/debug/app-debug.apk`](app/build/outputs/apk/debug/app-debug.apk).

To build the release variant:

```powershell
.\gradlew.bat :app:assembleRelease
```

The release APK must be signed with your own release key before it can be installed. Run the project verification script with:

```powershell
.\scripts\verify.ps1
```

## Project layout

```text
app/src/main/java/dev/breenottshook/
├── api/       GPT-SoVITS client, character catalog, and diagnostics
├── audio/     Audio decoding and format models
├── config/    Shared configuration, validation, encoding, and persistence
├── hook/      LSPosed injection, version routing, and host adapters
├── playback/  AudioTrack playback and streaming writes
├── session/   TTS sessions and cancellation control
└── ui/        Module app and embedded settings UI
```

Key entry points: [hook implementation](app/src/main/java/dev/breenottshook/hook), [configuration](app/src/main/java/dev/breenottshook/config), [settings UI](app/src/main/java/dev/breenottshook/ui), and the [Android manifest](app/src/main/AndroidManifest.xml).

## Diagnostics and feedback

Copy the diagnostic log from the bottom of the settings page. When reporting an issue, include the device model, Android/ColorOS version, Breeno version, LSPosed version, reproduction steps, and a redacted diagnostic log.

Do not include real utterance text, service addresses, API keys, cookies, or tokens.

See [diagnostics](docs/DIAGNOSTICS.md) for diagnostic details.
