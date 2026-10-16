
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Terminal, Lock, Loader2, ShieldCheck } from 'lucide-react'
import { useLoginMutation } from '@/store/api/backend-api'
import { DEMO_PASSWORD } from '@/lib/config'

export function LoginForm() {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [login, { isLoading }] = useLoginMutation()

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      const res = await login({ password }).unwrap()
      if (res.success) {
        window.location.href = '/'
      } else {
        setError(res.error || 'Access denied')
      }
    } catch (err: any) {
      setError(err?.data?.error || 'Connection error')
    }
  }

  return (
    <div className="relative min-h-screen flex items-center justify-center px-4 py-10 overflow-hidden">
      {/* ambient backdrop */}
      <div className="absolute inset-0 bg-grid opacity-60" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_rgba(16,185,129,0.12),_transparent_60%)]" />
      <div className="absolute -top-32 -left-32 h-96 w-96 rounded-full bg-emerald-500/10 blur-3xl" />
      <div className="absolute -bottom-32 -right-32 h-96 w-96 rounded-full bg-teal-500/10 blur-3xl" />

      <div className="relative w-full max-w-md">
        {/* brand */}
        <div className="flex flex-col items-center mb-8">
          <div className="flex items-center gap-3">
            <div className="grid h-12 w-12 place-items-center rounded-xl bg-emerald-500/15 ring-1 ring-emerald-500/30 glow-emerald">
              <Terminal className="h-6 w-6 text-emerald-400" />
            </div>
            <div className="text-left">
              <div className="text-2xl font-semibold tracking-tight text-zinc-50">
                WexowRAT<span className="text-emerald-400">C2</span>
              </div>
              <div className="text-[11px] uppercase tracking-[0.18em] text-zinc-500">
                Command Center
              </div>
            </div>
          </div>
        </div>

        <div className="rounded-2xl border border-white/10 bg-zinc-900/70 backdrop-blur-xl p-7 shadow-2xl shadow-black/40">
          <div className="flex items-center gap-2 mb-1">
            <Lock className="h-4 w-4 text-emerald-400" />
            <h1 className="text-base font-semibold text-zinc-100">
              Restricted Access
            </h1>
          </div>
          <p className="text-sm text-zinc-500 mb-6">
            Authenticate to enter the command center.
          </p>

          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="password" className="text-zinc-400 text-xs uppercase tracking-wider">
                Access Code
              </Label>
              <Input
                id="password"
                type="password"
                autoFocus
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter access code"
                className="bg-black/40 border-white/10 focus-visible:ring-emerald-500/40 font-mono"
              />
            </div>

            {error && (
              <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-300">
                {error}
              </div>
            )}

            <Button
              type="submit"
              disabled={isLoading || !password}
              className="w-full bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-semibold glow-emerald"
            >
              {isLoading ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" /> Authenticating…
                </>
              ) : (
                <>
                  <ShieldCheck className="h-4 w-4" /> Authenticate
                </>
              )}
            </Button>
          </form>

          <div className="mt-6 flex items-center gap-2 rounded-lg border border-emerald-500/15 bg-emerald-500/5 px-3 py-2 text-[11px] text-emerald-300/80">
            <span className="font-mono">demo code:</span>
            <code className="font-mono text-emerald-300">{DEMO_PASSWORD}</code>
          </div>
        </div>

        <p className="mt-6 text-center text-[11px] text-zinc-600">
          React · TypeScript · Redux Toolkit · RTK Query · Tailwind CSS
        </p>
      </div>
    </div>
  )
}
