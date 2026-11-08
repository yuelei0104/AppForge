import request from '@/request'
import {
  generationModeLabel,
  generationModeOptions,
  type GenerationMode,
} from '@/utils/generationMode'

// 与生成的 API 类型保持一致，集中保留工作区请求入口。
export type AppInfo = API.AppVO
export type AppFilters = API.AppQueryRequest
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
/** 创建后确认持久化的模式，再允许页面自动发起生成。 */
export async function createAppWithMode(initPrompt: string, codeGenType: GenerationMode) {
  if (!generationModeOptions.some((option) => option.value === codeGenType))
    throw new Error('请选择有效的生成模式')
  const created = await request<Response<string | number>>('/app/add', {
    method: 'POST',
    data: { initPrompt, codeGenType },
  })
  if (created.data.code !== 0 || created.data.data == null)
    throw new Error(created.data.message || '创建应用失败')
  const id = String(created.data.data)
  const actual = await request<Response<AppInfo>>('/app/get/vo', { params: { id } }).catch(() => {
    throw new Error('应用已创建，但暂时无法确认生成模式，请刷新“我的作品”查看')
  })
  if (actual.data.code !== 0 || !actual.data.data)
    throw new Error('应用已创建，但无法确认生成模式，请刷新“我的作品”查看')
  if (actual.data.data.codeGenType !== codeGenType)
    throw new Error(
      `生成模式不一致：所选为${generationModeLabel(codeGenType)}，服务返回${generationModeLabel(actual.data.data.codeGenType)}。已暂停自动生成，请确认前后端已更新并重启服务。`,
    )
  return id
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
