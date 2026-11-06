import request from '@/request'

export type AppInfo = API.AppVO & {
  description?: string
  tags?: string[]
  visibility?: 'PRIVATE' | 'PUBLIC'
  generationStatus?: 'IDLE' | 'GENERATING' | 'SUCCEEDED' | 'FAILED' | 'STOPPED'
  generationError?: string
  currentVersion?: number
  deployedVersion?: number
  deploymentStatus?: 'ONLINE' | 'OFFLINE'
  featureStatus?: 'NONE' | 'PENDING' | 'APPROVED' | 'REJECTED'
  featureReason?: string
}
export type AppFilters = API.AppQueryRequest & {
  searchText?: string
  tag?: string
  featureStatus?: string
  visibility?: string
  generationStatus?: string
}
export interface AppVersion {
  version: number
  kind: string
  sourceVersion?: number
  summary: string
  createTime: string
}
interface Response<T> {
  code: number
  data: T
  message: string
}
export const listPublicApps = (body: AppFilters) =>
  request<Response<API.PageAppVO>>('/app/public/list/page/vo', { method: 'POST', data: body })
export const listVersions = (appId: string) =>
  request<Response<AppVersion[]>>('/app/version/list', { params: { appId } })
export const getVersionFiles = (appId: string, version: number) =>
  request<Response<Record<string, string>>>('/app/version/files', { params: { appId, version } })
export const rollbackVersion = (appId: string, version: number) =>
  request<Response<number>>('/app/version/rollback', { method: 'POST', data: { appId, version } })
export const stopGeneration = (appId: string) =>
  request<Response<boolean>>('/app/generation/stop', { method: 'POST', data: { appId } })
export const undeployApp = (appId: string) =>
  request<Response<boolean>>('/app/undeploy', { method: 'POST', data: { appId } })
export const applyFeatured = (appId: string) =>
  request<Response<boolean>>('/app/feature/apply', { method: 'POST', data: { appId } })
export const reviewFeatured = (appId: string, approved: boolean, reason: string) =>
  request<Response<boolean>>('/app/admin/feature/review', {
    method: 'POST',
    data: { appId, approved, reason },
  })
