
import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export type BuildStatus = 'idle' | 'building' | 'success' | 'failed'

interface BuildState {
  status: BuildStatus
  logs: string[]
  filename: string | null
  error: string | null
}

const initialState: BuildState = {
  status: 'idle',
  logs: [],
  filename: null,
  error: null,
}

const buildSlice = createSlice({
  name: 'build',
  initialState,
  reducers: {
    buildStart(state) {
      state.status = 'building'
      state.logs = []
      state.filename = null
      state.error = null
    },
    buildLog(state, action: PayloadAction<string>) {
      state.logs.push(action.payload)
    },
    buildSuccess(state, action: PayloadAction<string>) {
      state.status = 'success'
      state.filename = action.payload
    },
    buildFailed(state, action: PayloadAction<string>) {
      state.status = 'failed'
      state.error = action.payload
    },
    buildReset(state) {
      Object.assign(state, initialState)
    },
  },
})

export const { buildStart, buildLog, buildSuccess, buildFailed, buildReset } =
  buildSlice.actions
export default buildSlice.reducer
