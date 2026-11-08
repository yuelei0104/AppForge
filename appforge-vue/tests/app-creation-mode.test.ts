import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppWithMode } from '@/api/workspace'
import { generationModeLabel, type GenerationMode } from '@/utils/generationMode'

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('@/request', () => ({ default: request }))
const id = '477028605545680896'
const response = (data: unknown, code = 0, message = 'ok') => ({ data: { code, data, message } })

beforeEach(() => request.mockReset())

describe('创建应用时保留生成模式', () => {
  it.each<GenerationMode>(['vue_project', 'multi_file', 'html'])(
    '明确传递并确认 %s 模式',
    async (mode) => {
      request
        .mockResolvedValueOnce(response(id))
        .mockResolvedValueOnce(response({ id, codeGenType: mode }))
      await expect(createAppWithMode('创建博客', mode)).resolves.toBe(id)
      expect(request).toHaveBeenNthCalledWith(1, '/app/add', {
        method: 'POST',
        data: { initPrompt: '创建博客', codeGenType: mode },
      })
      expect(request).toHaveBeenNthCalledWith(2, '/app/get/vo', { params: { id } })
    },
  )
  it('服务保存成 HTML 时拒绝进入自动生成', async () => {
    request
      .mockResolvedValueOnce(response(id))
      .mockResolvedValueOnce(response({ id, codeGenType: 'html' }))
    await expect(createAppWithMode('创建博客', 'vue_project')).rejects.toThrow(
      '所选为完整网站工程，服务返回单页网站',
    )
    expect(request).toHaveBeenCalledTimes(2)
  })
  it('服务漏掉生成模式时不假定工程创建成功', async () => {
    request.mockResolvedValueOnce(response(id)).mockResolvedValueOnce(response({ id }))
    await expect(createAppWithMode('创建博客', 'vue_project')).rejects.toThrow('未知模式')
  })
  it('创建失败时不查询不存在的应用', async () => {
    request.mockResolvedValueOnce(response(null, 50000, '创建失败'))
    await expect(createAppWithMode('创建博客', 'vue_project')).rejects.toThrow('创建失败')
    expect(request).toHaveBeenCalledTimes(1)
  })
  it('查询失败时不再次创建应用', async () => {
    request.mockResolvedValueOnce(response(id)).mockResolvedValueOnce(response(null, 40400))
    await expect(createAppWithMode('创建博客', 'vue_project')).rejects.toThrow('应用已创建')
    expect(request).toHaveBeenCalledTimes(2)
  })
  it('展示三种实际模式和未知模式', () => {
    expect(['vue_project', 'multi_file', 'html', undefined].map(generationModeLabel)).toEqual([
      '完整网站工程',
      '轻量网站',
      '单页网站',
      '未知模式',
    ])
  })
  it('确认模式时网络失败也不重复创建应用', async () => {
    request.mockResolvedValueOnce(response(id)).mockRejectedValueOnce(new Error('Network Error'))
    await expect(createAppWithMode('创建博客', 'vue_project')).rejects.toThrow(
      '应用已创建，但暂时无法确认生成模式',
    )
    expect(request).toHaveBeenCalledTimes(2)
  })
})
