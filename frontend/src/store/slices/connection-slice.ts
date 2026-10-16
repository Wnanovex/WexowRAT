
import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

interface ConnectionState {
  connected: boolean
  error: string | null
}

const initialState: ConnectionState = {
  connected: false,
  error: null,
}

const connectionSlice = createSlice({
  name: 'connection',
  initialState,
  reducers: {
    socketConnected(state) {
      state.connected = true
      state.error = null
    },
    socketDisconnected(state, action: PayloadAction<string | undefined>) {
      state.connected = false
      state.error = action.payload ?? null
    },
    socketError(state, action: PayloadAction<string>) {
      state.error = action.payload
    },
  },
})

export const { socketConnected, socketDisconnected, socketError } =
  connectionSlice.actions
export default connectionSlice.reducer
