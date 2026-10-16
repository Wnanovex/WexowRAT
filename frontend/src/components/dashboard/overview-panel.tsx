
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/components/ui/card'
import {
  Activity,
  Cpu,
  MemoryStick,
  Clock,
  Server,
  Radio,
  TerminalSquare,
  ArrowDown,
  ArrowUp,
  Play,
  Square,
  Trash2,
} from 'lucide-react'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { clearLogs } from '@/store/slices/logs-slice'
import { emitStartListener, emitStopListener } from '@/lib/socket'
import { formatBytes, formatRate } from '@/lib/format'

export function OverviewPanel() {
  const dispatch = useAppDispatch()
  const [port, setPort] = useState('4444')
  const { stats, sysInfo } = useAppSelector((s) => s.stats)
  const logs = useAppSelector((s) => s.logs.items)
  const listenerPorts = useAppSelector((s) => s.listener.ports)
  const listenerError = useAppSelector((s) => s.listener.error)

  const activePort = listenerPorts[0]
  const isListening = listenerPorts.length > 0

  const handleToggle = () => {
    if (isListening && activePort != null) {
      emitStopListener(activePort)
    } else {
      const p = parseInt(port, 10)
      if (!Number.isNaN(p) && p > 0 && p < 65536) emitStartListener(p)
    }
  }

  const statCards = [
    {
      label: 'CPU Load',
      value: sysInfo.cpu,
      icon: Cpu,
      hint: sysInfo.platform,
    },
    {
      label: 'Memory',
      value: sysInfo.ram,
      icon: MemoryStick,
      hint: 'RAM usage',
    },
    {
      label: 'Uptime',
      value: sysInfo.uptime,
      icon: Clock,
      hint: 'Server uptime',
    },
    {
      label: 'Active Listeners',
      value: String(listenerPorts.length),
      icon: Server,
      hint: listenerPorts.join(', ') || '—',
    },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-50">
          Overview
        </h1>
        <p className="text-sm text-zinc-500">
          Listener control, live system telemetry and the realtime event console.
        </p>
      </div>

      {/* Stat cards */}
      <div className="grid gap-4 grid-cols-2 lg:grid-cols-4">
        {statCards.map((c) => (
          <Card
            key={c.label}
            className="bg-zinc-900/60 border-white/10 overflow-hidden relative"
          >
            <CardContent className="p-4">
              <div className="flex items-start justify-between">
                <div>
                  <div className="text-xs uppercase tracking-wider text-zinc-500">
                    {c.label}
                  </div>
                  <div className="mt-1 text-2xl font-semibold text-zinc-50 tabular-nums">
                    {c.value}
                  </div>
                  <div className="mt-0.5 text-[11px] text-zinc-600 truncate">
                    {c.hint}
                  </div>
                </div>
                <div className="grid h-9 w-9 place-items-center rounded-lg bg-emerald-500/10 ring-1 ring-emerald-500/20">
                  <c.icon className="h-4 w-4 text-emerald-400" />
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Listener control */}
        <Card className="bg-zinc-900/60 border-white/10 lg:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-zinc-100">
              <Radio className="h-4 w-4 text-emerald-400" /> Listener
            </CardTitle>
            <CardDescription>
              Open a TCP port that infected devices connect back to.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label className="text-xs uppercase tracking-wider text-zinc-500">
                Port
              </Label>
              <Input
                value={port}
                onChange={(e) => setPort(e.target.value.replace(/[^0-9]/g, ''))}
                disabled={isListening}
                inputMode="numeric"
                placeholder="4444"
                className="bg-black/40 border-white/10 font-mono"
              />
            </div>

            <div
              className={`flex items-center justify-between rounded-lg border px-3 py-2 ${
                isListening
                  ? 'border-emerald-500/30 bg-emerald-500/5'
                  : 'border-white/10 bg-black/20'
              }`}
            >
              <span className="text-xs text-zinc-400">Status</span>
              <span
                className={`inline-flex items-center gap-1.5 text-xs font-medium ${
                  isListening ? 'text-emerald-400' : 'text-zinc-500'
                }`}
              >
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    isListening ? 'bg-emerald-400 pulse-emerald' : 'bg-zinc-600'
                  }`}
                />
                {isListening ? `Listening :${activePort}` : 'Offline'}
              </span>
            </div>

            <Button
              onClick={handleToggle}
              className={`w-full font-semibold ${
                isListening
                  ? 'bg-rose-500/90 hover:bg-rose-500 text-white'
                  : 'bg-emerald-500 hover:bg-emerald-400 text-zinc-950 glow-emerald'
              }`}
            >
              {isListening ? (
                <>
                  <Square className="h-4 w-4" /> Stop Listener
                </>
              ) : (
                <>
                  <Play className="h-4 w-4" /> Start Listener
                </>
              )}
            </Button>

            {listenerError && (
              <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-300">
                Port {listenerError.port}: {listenerError.message}
              </div>
            )}
          </CardContent>
        </Card>

        {/* Traffic */}
        <Card className="bg-zinc-900/60 border-white/10 lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-zinc-100">
              <Activity className="h-4 w-4 text-emerald-400" /> Live Traffic
            </CardTitle>
            <CardDescription>
              Aggregate bytes / packets exchanged with connected devices.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4 sm:grid-cols-2">
              <TrafficSide
                label="Received"
                icon={ArrowDown}
                side={stats.recv}
                accent="emerald"
              />
              <TrafficSide
                label="Sent"
                icon={ArrowUp}
                side={stats.sent}
                accent="teal"
              />
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Console */}
      <Card className="bg-zinc-900/60 border-white/10">
        <CardHeader className="flex-row items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-zinc-100">
              <TerminalSquare className="h-4 w-4 text-emerald-400" /> Event
              Console
            </CardTitle>
            <CardDescription>Live system log feed.</CardDescription>
          </div>
          <Button
            onClick={() => dispatch(clearLogs())}
            variant="ghost"
            size="sm"
            className="text-zinc-400 hover:text-rose-300"
          >
            <Trash2 className="h-3.5 w-3.5" /> Clear
          </Button>
        </CardHeader>
        <CardContent>
          <div className="h-80 overflow-y-auto scrollbar-emerald rounded-lg border border-white/10 bg-black/40 p-3 font-mono text-[12px] leading-relaxed">
            {logs.length === 0 ? (
              <div className="text-zinc-600 italic">
                No events yet. Start the listener and wait for devices…
              </div>
            ) : (
              logs.map((l, i) => {
                const color =
                  l.type === 'ok'
                    ? 'text-emerald-400'
                    : l.type === 'err'
                      ? 'text-rose-400'
                      : l.type === 'warn'
                        ? 'text-amber-400'
                        : 'text-zinc-300'
                return (
                  <div key={i} className="flex gap-2 hover:bg-white/5 -mx-1 px-1">
                    <span className="text-zinc-600 shrink-0">[{l.time}]</span>
                    <span className={color}>{l.msg}</span>
                  </div>
                )
              })
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function TrafficSide({
  label,
  icon: Icon,
  side,
  accent,
}: {
  label: string
  icon: typeof ArrowDown
  side: { bytes: number; packets: number; rate: number }
  accent: 'emerald' | 'teal'
}) {
  const color =
    accent === 'emerald' ? 'text-emerald-400' : 'text-teal-300'
  const bg =
    accent === 'emerald'
      ? 'bg-emerald-500/10 ring-emerald-500/20'
      : 'bg-teal-500/10 ring-teal-500/20'
  return (
    <div className={`rounded-xl border border-white/10 ${bg} p-4 ring-1`}>
      <div className="flex items-center gap-2">
        <Icon className={`h-4 w-4 ${color}`} />
        <span className="text-xs uppercase tracking-wider text-zinc-400">
          {label}
        </span>
      </div>
      <div className="mt-2 text-3xl font-semibold tabular-nums text-zinc-50">
        {formatBytes(side.bytes)}
      </div>
      <div className="mt-1 flex items-center justify-between text-[11px] text-zinc-500">
        <span>{side.packets.toLocaleString()} pkts</span>
        <span className={`font-medium ${color}`}>
          {formatRate(side.rate)}
        </span>
      </div>
    </div>
  )
}
