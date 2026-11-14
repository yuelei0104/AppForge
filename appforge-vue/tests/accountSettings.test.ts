import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useLoginUserStore } from '@/stores/loginUser'
import { useAccountSettings } from '@/composables/useAccountSettings'
import { validateNickname } from '@/utils/accountValidation'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/request', () => ({ default: request }))
const identity = { id: '100', userName: '无名', userRole: 'user', userAccount: 'test-only' }
const ok = (data: unknown) => ({ data: { code: 0, data } })
beforeEach(() => {
  setActivePinia(createPinia())
  request.mockReset()
  useLoginUserStore().setLoginUser(identity)
})

describe('个人设置与真实 API 合约', () => {
  it('只提交昵称并使用服务端返回值更新全局用户', async () => {
    request.mockResolvedValue(ok({ ...identity, userName: '岳蕾的创作室' }))
    await useAccountSettings().saveNickname('  岳蕾的创作室  ')
    expect(request).toHaveBeenCalledWith('/user/profile/update', {
      method: 'POST',
      data: { userName: '岳蕾的创作室' },
    })
    expect(useLoginUserStore().loginUser.userName).toBe('岳蕾的创作室')
  })
  it('保存失败保留现有昵称，可以重试', async () => {
    request.mockResolvedValue({ data: { code: 50000, message: '保存失败' } })
    await expect(useAccountSettings().saveNickname('新昵称')).rejects.toThrow('保存失败')
    expect(useLoginUserStore().loginUser.userName).toBe('无名')
  })
  it('网络失败不改用户或登录状态', async () => {
    request.mockRejectedValue(new Error('Network Error'))
    await expect(useAccountSettings().saveNickname('新昵称')).rejects.toThrow()
    expect(useLoginUserStore().isLoggedIn).toBe(true)
  })
  it.each(['', ' ', '名', '名'.repeat(25), '名\n字'])('无效昵称不发请求：%s', async (value) => {
    await expect(useAccountSettings().saveNickname(value)).rejects.toThrow('昵称')
    expect(request).not.toHaveBeenCalled()
  })
  it('表情按字符数计算', () => {
    expect(validateNickname('😀'.repeat(24))).toBe('😀'.repeat(24))
  })
  it('密码更新成功后清空身份，不保留密码到用户状态', async () => {
    request.mockResolvedValue(ok(true))
    const values = {
      oldPassword: 'Test-old-123!',
      newPassword: 'Test-new-456!',
      checkPassword: 'Test-new-456!',
    }
    await useAccountSettings().changePassword(values)
    expect(request).toHaveBeenCalledWith('/user/password/update', { method: 'POST', data: values })
    expect(useLoginUserStore().isLoggedIn).toBe(false)
  })
  it('原密码错误不退出账号', async () => {
    request.mockResolvedValue({ data: { code: 40000, message: '原密码不正确' } })
    await expect(
      useAccountSettings().changePassword({
        oldPassword: 'Test-old-123!',
        newPassword: 'Test-new-456!',
        checkPassword: 'Test-new-456!',
      }),
    ).rejects.toThrow('原密码不正确')
    expect(useLoginUserStore().isLoggedIn).toBe(true)
  })
  it.each([
    ['', 'Test-new-456!', 'Test-new-456!'],
    ['Test-old-123!', 'short', 'short'],
    ['Test-old-123!', 'x'.repeat(65), 'x'.repeat(65)],
    ['Test-old-123!', 'Test-new-456!', 'mismatch'],
    ['Test-old-123!', 'Test-old-123!', 'Test-old-123!'],
  ])('密码校验失败不发送请求', async (oldPassword, newPassword, checkPassword) => {
    await expect(
      useAccountSettings().changePassword({ oldPassword, newPassword, checkPassword }),
    ).rejects.toThrow()
    expect(request).not.toHaveBeenCalled()
  })
})
