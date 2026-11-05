import { API_BASE_URL } from '@/request'

/** 已部署作品与生成预览使用不同的后端路由。 */
export function getAppDeployUrl(deployKey: string) {
  return `${API_BASE_URL}/deploy/${encodeURIComponent(deployKey)}/`
}

export function getAppPreviewUrl(appId: string) {
  return `${API_BASE_URL}/static/${encodeURIComponent(appId)}/`
}
