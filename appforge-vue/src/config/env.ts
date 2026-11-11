/** Vite 环境变量均为公开配置，不要在这里放 API 密钥。 */
function normalizeBase(value: string | undefined, fallback: string) {
  const base = value?.trim() || fallback
  return base.replace(/\/+$/, '')
}

export const API_BASE_URL = normalizeBase(import.meta.env.VITE_API_BASE_URL, '/api')
export const DEPLOY_DOMAIN = normalizeBase(
  import.meta.env.VITE_DEPLOY_DOMAIN,
  `${API_BASE_URL}/deploy`,
)
export const STATIC_BASE_URL = normalizeBase(
  import.meta.env.VITE_STATIC_BASE_URL,
  `${API_BASE_URL}/static`,
)
