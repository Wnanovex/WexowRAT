
import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react'
import { apiUrl } from '@/lib/config'

// ─── Types ──────────────────────────────────────────────────────────────────

export interface LoginResponse {
  success: boolean
  error?: string
}
export interface SessionResponse {
  authenticated: boolean
}
export interface UploadResponse {
  success: boolean
  error?: string
}
export interface UnzipResponse {
  success: boolean
  error?: string
}

export interface DownloadEntry {
  name: string
  url: string
  isDir: boolean
  size: number
  mtime?: number
}
export interface ListResponse {
  path: string
  entries: DownloadEntry[]
}

// ─── RTK Query API ───────────────────────────────────────────────────────────

const baseQuery = fetchBaseQuery({
  // Always relative paths; Caddy routes ?XTransformPort=<port> to the backend.
  baseUrl: '/',
  credentials: 'include', // send + accept the session cookie
})

export const backendApi = createApi({
  reducerPath: 'backendApi',
  baseQuery,
  tagTypes: ['Session'],
  endpoints: (builder) => ({
    // Session check. Works whether or not server.js exposes /api/session:
    //   • 401                  → requireAuth rejected the request → NOT authed
    //   • JSON {authenticated} → server.js has the route → trust the body
    //   • anything else (404)  → requireAuth passed (cookie valid) → authed
    // Add `app.get('/api/session', (req,res)=>res.json({authenticated:
    // verifySession(req.cookies.session)}))` to server.js for the clean path.
    checkSession: builder.query<SessionResponse, void>({
      async queryFn() {
        try {
          const res = await fetch(apiUrl('/api/session'), {
            credentials: 'include',
            headers: { Accept: 'application/json' },
          })
          if (res.status === 401) return { data: { authenticated: false } }
          const ct = res.headers.get('content-type') || ''
          if (ct.includes('application/json')) {
            const body = await res.json().catch(() => null)
            if (body && typeof body.authenticated === 'boolean') {
              return { data: { authenticated: body.authenticated } }
            }
          }
          return { data: { authenticated: true } }
        } catch {
          return { data: { authenticated: false } }
        }
      },
      providesTags: ['Session'],
    }),

    // POST /api/login
    login: builder.mutation<LoginResponse, { password: string }>({
      query: (body) => ({
        url: apiUrl('/api/login'),
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Session'],
    }),

    // GET /api/logout
    logout: builder.mutation<UploadResponse, void>({
      query: () => ({ url: apiUrl('/api/logout'), method: 'GET' }),
      invalidatesTags: ['Session'],
    }),

    // POST /api/upload  (whole file in one request)
    uploadFile: builder.mutation<
      UploadResponse,
      { clientId: string; path: string; data: string }
    >({
      query: (body) => ({
        url: apiUrl('/api/upload'),
        method: 'POST',
        body,
      }),
    }),

    // POST /api/upload-chunked  (action: start | chunk | end)
    uploadChunked: builder.mutation<
      UploadResponse,
      {
        clientId: string
        path: string
        action: 'start' | 'chunk' | 'end'
        chunkIndex?: number
        totalChunks?: number
        totalSize?: number
        data?: string
      }
    >({
      query: (body) => ({
        url: apiUrl('/api/upload-chunked'),
        method: 'POST',
        body,
      }),
    }),

    // POST /api/unzip  (tell the device to extract a zip)
    unzipOnDevice: builder.mutation<
      UnzipResponse,
      { clientId: string; zipPath: string; destDir: string }
    >({
      query: (body) => ({
        url: apiUrl('/api/unzip'),
        method: 'POST',
        body,
      }),
    }),

    // GET /api/list?path=<rel>  — list a folder under Downloads/ (server-side)
    // Used by the "Downloaded" tab to browse Downloads/<clientId>/...
    listDownloads: builder.query<ListResponse, string>({
      query: (p) => apiUrl(`/api/list?path=${encodeURIComponent(p)}`),
    }),
  }),
})

export const {
  useCheckSessionQuery,
  useLoginMutation,
  useLogoutMutation,
  useUploadFileMutation,
  useUploadChunkedMutation,
  useUnzipOnDeviceMutation,
  useListDownloadsQuery,
} = backendApi
