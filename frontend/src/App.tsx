import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Providers } from '@/components/providers'
import { useCheckSessionQuery } from '@/store/api/backend-api'
import { LoginForm } from '@/components/login-form'
import { DashboardShell } from '@/components/dashboard/dashboard-shell'
import { OverviewPanel } from '@/components/dashboard/overview-panel'
import { ClientsPanel } from '@/components/dashboard/clients-panel'
import { BuilderPanel } from '@/components/dashboard/builder-panel'
import { ExploitConsole } from '@/components/dashboard/exploit-console'
import { Loader2 } from 'lucide-react'

function Gate({ children }: { children: React.ReactNode }) {
  const { data, isLoading } = useCheckSessionQuery()

  if (isLoading) {
    return (
      <div className="min-h-screen grid place-items-center bg-background">
        <div className="flex items-center gap-3 text-zinc-500">
          <Loader2 className="h-5 w-5 animate-spin text-emerald-400" />
          <span className="text-sm">Establishing secure link…</span>
        </div>
      </div>
    )
  }

  if (!data?.authenticated) {
    return <LoginForm />
  }

  return <>{children}</>
}

export default function App() {
  return (
    <Providers>
      <Gate>
        <BrowserRouter>
          <Routes>
            <Route element={<DashboardShell />}>
              <Route path="/" element={<OverviewPanel />} />
              <Route path="/devices" element={<ClientsPanel />} />
              <Route path="/device/:id" element={<ExploitConsole />} />
              <Route path="/builder" element={<BuilderPanel />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </Gate>
    </Providers>
  )
}
