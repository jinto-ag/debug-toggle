# DebugToggle

[![CI](https://github.com/jinto-ag/debug-toggle/actions/workflows/ci.yml/badge.svg)](https://github.com/jinto-ag/debug-toggle/actions/workflows/ci.yml)
[![Semgrep](https://github.com/jinto-ag/debug-toggle/actions/workflows/semgrep.yml/badge.svg)](https://github.com/jinto-ag/debug-toggle/actions/workflows/semgrep.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Security Policy](https://img.shields.io/badge/Security-Policy-blue.svg)](SECURITY.md)

DebugToggle is an open-source Android utility and Quick Settings controller that manages USB Debugging, Wireless Debugging, and Developer Options. If Developer Options has been turned off, toggling any switch reactivates Developer Options automatically in the background.

## Features

- **Automatic Developer Options Restoration:** Reactivates developer options silently when toggling any debugging mode.
- **USB Debugging Toggle:** Cable ADB interface control from the dashboard or Quick Settings.
- **Wireless Debugging Toggle:** Network ADB bridge toggle with local IP and port detection.
- **Authorization Timeout Control:** Prevents automatic 7-day ADB key revocation (`adb_allowed_connection_time`).
- **Audit Logging Engine:** In-app ring-buffer log viewer with search, log-level filtering (`ALL`, `INFO`, `WARN`, `ERROR`), and PII-redacted export (`ACTION_SEND`).
- **Direct GitHub Issue Reporting:** In-app diagnostic pre-filling for bug reports and security disclosures.
- **Dedicated Quick Settings Tiles:** Two standalone tiles for `USB Debug` and `Wireless ADB`.
- **Zero Third-Party Runtime Dependencies:** Under 50 KB production APK compiled against native Android APIs.

## Installation

### Releases & Obtainium

Download `DebugToggle-release.apk` from the [Releases](https://github.com/jinto-ag/debug-toggle/releases) page, or add to [Obtainium](https://github.com/ImranR98/Obtainium):

- **App Source:** GitHub
- **Repository URL:** `https://github.com/jinto-ag/debug-toggle`

### Permission Setup

Grant the `WRITE_SECURE_SETTINGS` permission once via ADB:

```bash
adb shell pm grant com.jintoag.debugtoggle android.permission.WRITE_SECURE_SETTINGS
```

This permission persists across device reboots and updates.

## Quick Settings Setup

1. Open the Quick Settings shade on your device.
2. Tap the edit pencil icon.
3. Drag **USB Debug** and **Wireless ADB** into the active tiles drawer.

## Building from Source

### Prerequisites

- Android SDK (API 34, Build-Tools 35.0.0)
- JDK 17 or JDK 21
- [Bun](https://bun.sh) (v1.2+)

### Commands

```bash
bun install

# Build release APK
bun run build

# Run unit tests
bun run test

# Run E2E test suite (auto-detects connected phone or headless emulator)
bun run test:e2e

# Code style and secret scanning
bun run format:check
bun run lint:secrets
```

## Security & Governance

- [Security Policy](SECURITY.md)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
