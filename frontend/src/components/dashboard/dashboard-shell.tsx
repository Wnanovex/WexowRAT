
import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  Terminal,
  LayoutDashboard,
  Smartphone,
  Hammer,
  LogOut,
  Menu,
  Wifi,
  ShieldCheck,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { useLogoutMutation, useCheckSessionQuery } from '@/store/api/backend-api'
import { useAppSelector } from '@/store/hooks'

const NAV: { to: string; label: string; icon: typeof LayoutDashboard; count?: boolean }[] = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/devices', label: 'Devices', icon: Smartphone, count: true },
  { to: '/builder', label: 'Builder', icon: Hammer },
]

const PAGE_TITLES: Record<string, string> = {
  '/': 'Overview',
  '/devices': 'Devices',
  '/builder': 'Builder',
}

export function DashboardShell() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [logout] = useLogoutMutation()
  const { refetch } = useCheckSessionQuery()
  const connected = useAppSelector((s) => s.connection.connected)
  const clientCount = useAppSelector((s) => s.clients.items.length)
  const listenerCount = useAppSelector((s) => s.listener.ports.length)
  const location = useLocation()

  const onLogout = async () => {
    await logout().unwrap().catch(() => {})
    await refetch()
  }

  const title = location.pathname.startsWith('/device')
    ? 'Device'
    : PAGE_TITLES[location.pathname] ?? 'WexowRATC2'

  const navItems = (
    <nav className="flex flex-col gap-1 p-3">
      {NAV.map((n) => (
        <NavLink
          key={n.to}
          to={n.to}
          end={n.to === '/'}
          onClick={() => setMobileOpen(false)}
          className={({ isActive }) =>
            `group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all ${
              isActive
                ? 'bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 glow-emerald-sm'
                : 'text-zinc-400 hover:bg-white/5 hover:text-zinc-100'
            }`
          }
        >
          <n.icon className="h-4 w-4" />
          {n.label}
          {n.count && clientCount > 0 && (
            <span className="ml-auto rounded-full bg-emerald-500/20 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-300">
              {clientCount}
            </span>
          )}
        </NavLink>
      ))}
    </nav>
  )

  const sidebarBody = (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 px-5 h-14 border-b border-white/10">
        <div className="grid h-8 w-8 place-items-center rounded-lg bg-emerald-500/15 ring-1 ring-emerald-500/30">
          <Terminal className="h-4 w-4 text-emerald-400" />
        </div>
        <div className="leading-tight">
          <div className="text-sm font-semibold text-zinc-50">
            WexowRAT<span className="text-emerald-400">C2</span>
          </div>
          <div className="text-[10px] uppercase tracking-[0.18em] text-zinc-600">
            Command Center
          </div>
        </div>
      </div>
      <div className="flex-1 overflow-y-auto scrollbar-emerald">{navItems}</div>
      <div className="border-t border-white/10 p-3">
        <div className="rounded-lg bg-zinc-900/60 border border-white/5 p-3 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-zinc-500">Listener</span>
            <span
              className={`inline-flex items-center gap-1.5 font-medium ${
                listenerCount > 0 ? 'text-emerald-400' : 'text-zinc-600'
              }`}
            >
              <span
                className={`h-1.5 w-1.5 rounded-full ${
                  listenerCount > 0 ? 'bg-emerald-400 pulse-emerald' : 'bg-zinc-700'
                }`}
              />
              {listenerCount > 0 ? 'Online' : 'Offline'}
            </span>
          </div>
          <div className="mt-1 flex items-center justify-between">
            <span className="text-zinc-500">Socket</span>
            <span
              className={`font-medium ${
                connected ? 'text-emerald-400' : 'text-zinc-600'
              }`}
            >
              {connected ? 'Connected' : 'Linking…'}
            </span>
          </div>
        </div>
        <Button
          onClick={onLogout}
          variant="ghost"
          className="mt-2 w-full justify-start text-zinc-400 hover:text-rose-300 hover:bg-rose-500/10"
        >
          <LogOut className="h-4 w-4" /> Sign out
        </Button>
      </div>
    </div>
  )

  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* Topbar */}
      <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-white/10 bg-zinc-950/80 backdrop-blur-xl px-4">
        <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
          <SheetTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden text-zinc-400"
              aria-label="Open navigation"
            >
              <Menu className="h-5 w-5" />
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-64 p-0 bg-zinc-950 border-white/10">
            <SheetTitle className="sr-only">Navigation</SheetTitle>
            {sidebarBody}
          </SheetContent>
        </Sheet>

        <div className="flex items-center gap-2">
          <Wifi
            className={`h-4 w-4 ${connected ? 'text-emerald-400' : 'text-zinc-600'}`}
          />
          <span className="text-sm font-medium text-zinc-200">{title}</span>
        </div>

        <div className="ml-auto flex items-center gap-3">
          <div className="hidden sm:flex items-center gap-2 rounded-full border border-emerald-500/20 bg-emerald-500/5 px-3 py-1 text-xs text-emerald-300">
            <ShieldCheck className="h-3.5 w-3.5" />
            <span>Linked to server.js</span>
          </div>
        </div>
      </header>

      {/* Body: sidebar + main */}
      <div className="flex flex-1">
        <aside className="hidden lg:block w-64 shrink-0 border-r border-white/10 bg-zinc-950/60">
          <div className="sticky top-14 h-[calc(100vh-3.5rem)]">{sidebarBody}</div>
        </aside>

        <main className="flex-1 flex flex-col min-w-0">
          <div className="flex-1 p-4 sm:p-6">
            <Outlet />
          </div>

          {/* Footer */}
          <footer className="mt-auto border-t border-white/10 bg-zinc-950/60 backdrop-blur-xl px-4 py-2.5">
            <div className="flex flex-wrap items-center gap-x-6 gap-y-1 text-[11px] text-zinc-500">
              <span className="flex items-center gap-1.5">
                <span
                  className={`h-1.5 w-1.5 rounded-full ${
                    connected ? 'bg-emerald-400 pulse-emerald' : 'bg-rose-500'
                  }`}
                />
                {connected ? 'Realtime link established' : 'Reconnecting…'}
              </span>
              <span>
                Devices: <span className="text-zinc-300">{clientCount}</span>
              </span>
              <span>
                Listeners: <span className="text-zinc-300">{listenerCount}</span>
              </span>
              <span className="ml-auto">WexowRATC2 · emerald command center</span>
            </div>
          </footer>
        </main>
      </div>
    </div>
  )
}
