
import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export type LogType = 'info' | 'ok' | 'warn' | 'err'
export interface LogEntry {
  time: string
  msg: string
  type: LogType
}

const MAX_LOGS = 400

interface LogsState {
  items: LogEntry[]
}

const initialState: LogsState = {
  items: [],
}

const logsSlice = createSlice({
  name: 'logs',
  initialState,
  reducers: {
    addLog(state, action: PayloadAction<LogEntry>) {
      state.items.push(action.payload)
      if (state.items.length > MAX_LOGS) {
        state.items.splice(0, state.items.length - MAX_LOGS)
      }
    },
    clearLogs(state) {
      state.items = []
    },
  },
})

export const { addLog, clearLogs } = logsSlice.actions
export default logsSlice.reducer
