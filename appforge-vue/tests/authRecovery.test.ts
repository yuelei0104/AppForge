import { beforeEach, describe, expect, it, vi } from 'vitest'
import { authErrorMessage, safeAuthRedirect, validatePasswordReset } from '@/utils/auth'
import { issuePasswordResetCode, resetPassword } from '@/api/account'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/request', () => ({ default: request }))
beforeEach(() => {
  request.mockReset()
})

describe('认证跳转与找回密码', () => {
  it.each([
    undefined,
    null,
    ['/', '/admin'],
    'https://example.com',
    '//example.com',
    '/\\example.com',
    '/\nexample.com',
  ])('拒绝非站内目标：%s', (value) => {
    expect(safeAuthRedirect(value)).toBe('/')
  })
  it('保留站内作品路径和查询参数', () => {
    expect(safeAuthRedirect('/app/chat/123?view=1')).toBe('/app/chat/123?view=1')
  })
  const valid = {
    userAccount: 'fixture_user',
    resetCode: 'Test-only-reset-code-123456789ab',
    newPassword: 'Test-new-123!',
    checkPassword: 'Test-new-123!',
  }
  it('验证合法输入并允许粘贴时码两侧的空白', () => {
    expect(valid.resetCode).toHaveLength(32)
    expect(() =>
      validatePasswordReset({ ...valid, resetCode: ` ${valid.resetCode} ` }),
    ).not.toThrow()
  })
  it.each([
    { userAccount: '' },
    { resetCode: '' },
    { resetCode: 'invalid' },
    { newPassword: 'short', checkPassword: 'short' },
    { newPassword: 'x'.repeat(65), checkPassword: 'x'.repeat(65) },
    { newPassword: ' '.repeat(8), checkPassword: ' '.repeat(8) },
    { checkPassword: 'mismatch' },
  ])('拒绝缺失或不一致的找回字段：%j', (patch) => {
    expect(() => validatePasswordReset({ ...valid, ...patch })).toThrow()
  })
  it('重置凭证只进入 POST 请求体，不进入链接或查询参数', async () => {
    request.mockResolvedValue({ data: { code: 0, data: true } })
    await resetPassword(valid)
    expect(request).toHaveBeenCalledWith('/user/password/reset', { method: 'POST', data: valid })
  })
  it('管理员必须显式传递身份核验确认及完整用户 id', async () => {
    request.mockResolvedValue({
      data: { code: 0, data: { resetCode: valid.resetCode, expiresInSeconds: 600 } },
    })
    await issuePasswordResetCode('475494645992804352', true)
    expect(request).toHaveBeenCalledWith('/user/password/reset-code', {
      method: 'POST',
      data: { userId: '475494645992804352', identityVerified: true },
    })
  })
  it('网络异常显示中文提示并保留业务错误', () => {
    expect(authErrorMessage({ isAxiosError: true, message: 'Network Error' }, '请检查网络')).toBe(
      '请检查网络',
    )
    expect(authErrorMessage(new Error('重置码已过期'), '请检查网络')).toBe('重置码已过期')
  })
  it('网络失败不会在客户端伪造重置成功', async () => {
    request.mockRejectedValue(new Error('network'))
    await expect(resetPassword(valid)).rejects.toThrow('network')
  })
})
