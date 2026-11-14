/** 仅保留站内路径，认证页面往返时沿用同一个目标。 */
export function safeAuthRedirect(value: unknown): string {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    /[\\\u0000-\u001f\u007f]/.test(value)
  )
    return '/'
  return value
}

export function validatePasswordReset(values: {
  userAccount: string
  resetCode: string
  newPassword: string
  checkPassword: string
}) {
  if (!values.userAccount.trim()) throw new Error('请输入登录账号')
  if (!/^[A-Za-z0-9_-]{32}$/.test(values.resetCode.trim()))
    throw new Error('请输入管理员提供的 32 位重置码')
  if (!values.newPassword.trim() || values.newPassword.length < 8 || values.newPassword.length > 64)
    throw new Error('新密码需为 8～64 位')
  if (values.newPassword !== values.checkPassword) throw new Error('两次输入的新密码不一致')
}

/** 网络和 HTTP 异常显示可操作的提示，保留服务端业务错误。 */
export function authErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'isAxiosError' in error) return fallback
  return error instanceof Error ? error.message : fallback
}
