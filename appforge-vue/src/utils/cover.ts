import type { AppInfo } from '@/api/workspace'
export const coverPending = (status?: string) =>
  ['PENDING', 'RUNNING', 'RETRY_WAIT'].includes(status || '')
export const coverStatusLabel = (status?: string) =>
  ({
    NONE: '暂无自动封面',
    PENDING: '等待截图',
    RUNNING: '正在截图',
    RETRY_WAIT: '等待自动重试',
    SUCCEEDED: '封面已更新',
    FAILED: '封面生成失败',
    CANCELLED: '截图已取消',
  })[status || 'NONE'] || '暂无自动封面'
export function resolveCoverUrl(app: AppInfo, apiBase: string) {
  if (app.coverMode === 'AUTO' && app.cover) {
    try {
      const url = new URL(app.cover)
      if (url.pathname.endsWith('/app/cover/image'))
        return `${apiBase.replace(/\/+$/, '')}/app/cover/image${url.search}`
    } catch {
      /* Keep custom URLs unchanged. */
    }
  }
  return app.cover || ''
}
