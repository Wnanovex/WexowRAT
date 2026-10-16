
import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface ClientDetails {
  man?: string
  model?: string
  release?: string
  country?: string
  battery?: number
  ip?: string
  [k: string]: any
}

export interface Client {
  id: string
  details: ClientDetails
}

interface ClientsState {
  items: Client[]
}

const initialState: ClientsState = {
  items: [],
}

const clientsSlice = createSlice({
  name: 'clients',
  initialState,
  reducers: {
    setClients(state, action: PayloadAction<Client[]>) {
      // dedupe by id, preserve order
      const seen = new Set<string>()
      state.items = action.payload.filter((c) => {
        if (seen.has(c.id)) return false
        seen.add(c.id)
        return true
      })
    },
    addClient(state, action: PayloadAction<Client>) {
      const idx = state.items.findIndex((c) => c.id === action.payload.id)
      if (idx >= 0) {
        state.items[idx] = action.payload
      } else {
        state.items.push(action.payload)
      }
    },
    removeClient(state, action: PayloadAction<string>) {
      state.items = state.items.filter((c) => c.id !== action.payload)
    },
    updateClientDetails(
      state,
      action: PayloadAction<{ id: string; details: Partial<ClientDetails> }>,
    ) {
      const c = state.items.find((x) => x.id === action.payload.id)
      if (c) c.details = { ...c.details, ...action.payload.details }
    },
  },
})

export const { setClients, addClient, removeClient, updateClientDetails } =
  clientsSlice.actions
export default clientsSlice.reducer
