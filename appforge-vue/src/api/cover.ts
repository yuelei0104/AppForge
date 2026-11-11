import request from '@/request'
interface Response<T> {
  code: number
  data: T
  message: string
}
export interface CoverStatus {
  enabled: boolean
  mode?: 'AUTO' | 'MANUAL'
  status: string
  version?: number
  targetVersion?: number
  revision?: string
  errorCode?: string
  errorMessage?: string
  queuePaused?: boolean
}
export const getCoverStatus = (appId: string, signal?: AbortSignal) =>
  request<Response<CoverStatus>>('/app/cover/status', { params: { appId }, signal })
export const regenerateCover = (appId: string, expectedVersion: number, requestId: string) =>
  request<Response<{ taskId: string; status: string }>>('/app/cover/regenerate', {
    method: 'POST',
    data: { appId, expectedVersion, requestId },
  })
export const setCoverMode = (appId: string, mode: 'AUTO' | 'MANUAL', url?: string) =>
  request<Response<boolean>>('/app/cover/mode', { method: 'POST', data: { appId, mode, url } })
