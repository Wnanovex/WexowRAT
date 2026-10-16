// Backend connection configuration.
//
// This is a PURE frontend. The backend is the user's existing `server.js`,
// which (a) serves the built SPA at `/app`, (b) exposes REST `/api/*` with
// cookie auth, and (c) runs socket.io on the same origin at the default path
// `/socket.io`.

import { io } from 'socket.io-client'

const isDev = (import.meta as any).env?.DEV === true

export const socket = io(
  isDev ? 'http://localhost:3000' : '/',
  {
    transports: isDev ? ['polling'] : ['polling', 'websocket'],
    reconnection: true,
    reconnectionAttempts: Infinity,
    reconnectionDelay: 1000,
    reconnectionDelayMax: 5000,
    timeout: 20000,
    autoConnect: false,
  },
)

export function apiUrl(path: string): string {
  return path
}

// Matches server.js's default ACCESS_PASSWORD — shown on the login screen as a
// hint in dev only. Replace/remove in your deployment.
export const DEMO_PASSWORD = 'change_me_1337'
