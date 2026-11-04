import ACCESS_ENUM from './accessEnum'
import type { AccessType } from './accessEnum'

/**
 * 检查当前登录用户是否具备指定权限
 * @param loginUser 当前登录用户
 * @param needAccess 需要的权限
 * @returns 是否有权限
 */
export default function checkAccess(
  loginUser: API.LoginUserVO | null | undefined,
  needAccess: AccessType = ACCESS_ENUM.NOT_LOGIN,
): boolean {
  // 页面不需要登录，直接放行
  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true
  }

  // 需要登录但没有登录用户
  if (!loginUser?.id) {
    return false
  }

  // 登录即可访问
  if (needAccess === ACCESS_ENUM.USER) {
    return true
  }

  // 需要管理员权限
  if (needAccess === ACCESS_ENUM.ADMIN) {
    return loginUser.userRole === ACCESS_ENUM.ADMIN
  }

  return false
}
