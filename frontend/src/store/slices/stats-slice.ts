
import { createSlice, type PayloadAction } from '@reduxjs/toolkit'

export interface TrafficSide {
  bytes: number
  packets: number
  rate: number
}
export interface Stats {
  sent: TrafficSide
  recv: TrafficSide
}
export interface SysInfo {
  cpu: string
  ram: string
  uptime: string
  platform: string
}

interface StatsState {
  stats: Stats
  sysInfo: SysInfo
}

const initialState: StatsState = {
  stats: { sent: { bytes: 0, packets: 0, rate: 0 }, recv: { bytes: 0, packets: 0, rate: 0 } },
  sysInfo: { cpu: '0%', ram: '0%', uptime: '0h 0m', platform: '—' },
}

const statsSlice = createSlice({
  name: 'stats',
  initialState,
  reducers: {
    setStats(
      state,
      action: PayloadAction<{ stats: Stats; sysInfo: SysInfo }>,
    ) {
      state.stats = action.payload.stats
      state.sysInfo = action.payload.sysInfo
    },
  },
})

export const { setStats } = statsSlice.actions
export default statsSlice.reducer
