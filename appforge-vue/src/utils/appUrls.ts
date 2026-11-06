import { API_BASE_URL, DEPLOY_DOMAIN, STATIC_BASE_URL } from '@/config/env'

/** 已部署作品与生成预览使用不同的后端路由。 */
export function getAppDeployUrl(deployKey: string) {
  return `${DEPLOY_DOMAIN}/${encodeURIComponent(deployKey)}/`
}

export function getAppPreviewUrl(appId: string) {
  return `${STATIC_BASE_URL}/${encodeURIComponent(appId)}/`
}

/** 后端返回完整部署地址，提取标识后应用前端配置的部署域名。 */
export function getAppDeployUrlFromResponse(value: string) {
  const url = new URL(value, new URL(`${API_BASE_URL}/`, window.location.origin))
  const key = url.pathname.match(/\/deploy\/([A-Za-z0-9]+)\/?$/)?.[1]
  if (!key) {
    throw new Error('部署接口未返回有效的部署地址')
  }
  return getAppDeployUrl(key)
}

export function isValidCoverUrl(value: string) {
  if (!value.trim()) {
    return true
  }
  try {
    const url = new URL(value.trim())
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}
