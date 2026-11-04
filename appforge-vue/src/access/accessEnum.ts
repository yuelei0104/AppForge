/**
 * 全局权限枚举。
 *
 * notLogin：无需登录即可访问
 * user：登录用户即可访问
 * admin：仅管理员可访问
 */
const ACCESS_ENUM = {
  NOT_LOGIN: 'notLogin',
  USER: 'user',
  ADMIN: 'admin',
} as const

export type AccessType = (typeof ACCESS_ENUM)[keyof typeof ACCESS_ENUM]

export default ACCESS_ENUM
