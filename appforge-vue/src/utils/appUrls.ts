import { CodeGenTypeEnum } from '@/config/codeGenType'
import { API_BASE_URL, DEPLOY_DOMAIN, STATIC_BASE_URL } from '@/config/env'

/** 已部署作品与生成预览使用不同的后端路由。 */
export function getAppDeployUrl(deployKey: string) {
  return new URL(`${DEPLOY_DOMAIN}/${encodeURIComponent(deployKey)}/`, window.location.origin).href
}

/**
 * 获取生成页面的静态预览地址。
 *
 * <p>当前后端 StaticResourceController 的预览路由是 /static/{appId}/，
 * 并且会自动识别 Vue 工程、从 dist 目录提供 index.html。因此这里不能再
 * 拼接 dist/index.html，否则会变成 /static/{appId}/dist/index.html 而 404。</p>
 *
 * @param codeGenType 代码生成类型，保留参数用于区分不同生成模式
 * @param appId 应用 id
 */
export function getStaticPreviewUrl(codeGenType: string, appId: string) {
  // codeGenType 目前不参与路径；Vue 工程的 dist 由后端自动映射。
  void codeGenType
  return `${STATIC_BASE_URL}/${encodeURIComponent(appId)}/`
}

export function getAppPreviewUrl(appId: string, codeGenType?: string) {
  return getStaticPreviewUrl(codeGenType ?? CodeGenTypeEnum.HTML, appId)
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
