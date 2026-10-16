
import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

interface ListenerState {
  ports: number[]
  error: { port: number | string; message: string } | null
}

const initialState: ListenerState = {
  ports: [],
  error: null,
}

const listenerSlice = createSlice({
  name: 'listener',
  initialState,
  reducers: {
    setPorts(state, action: PayloadAction<number[]>) {
      state.ports = action.payload.map((p) => Number(p)).filter((n) => !Number.isNaN(n))
    },
    addPort(state, action: PayloadAction<number>) {
      const p = Number(action.payload)
      if (!Number.isNaN(p) && !state.ports.includes(p)) state.ports.push(p)
    },
    removePort(state, action: PayloadAction<number>) {
      const p = Number(action.payload)
      state.ports = state.ports.filter((x) => x !== p)
    },
    setError(state, action: PayloadAction<{ port: number | string; message: string } | null>) {
      state.error = action.payload
    },
  },
})

export const { setPorts, addPort, removePort, setError } = listenerSlice.actions
export default listenerSlice.reducer
