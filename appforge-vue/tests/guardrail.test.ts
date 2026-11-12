import { describe, expect, it } from 'vitest'
import { streamErrorMessage } from '../src/utils/guardrail'
import { updateProgress } from '../src/utils/chatHistory'

describe('guardrail stream feedback', () => {
  it('shows a safe audit reference while preserving ordinary error messages', () => {
    const id = '94c77088-6e78-4d61-a636-55302b55b59b'
    expect(
      streamErrorMessage({ guardrail: true, message: '请调整输入', requestId: id }, '错误'),
    ).toBe(`请调整输入\n\n参考编号：${id}`)
    expect(streamErrorMessage({ message: '生成失败' }, '错误')).toBe('生成失败')
    expect(streamErrorMessage({ guardrail: true, requestId: '<script>' }, '错误')).toBe('错误')
    expect(streamErrorMessage(null, '错误')).toBe('错误')
  })

  it('accepts the rewrite notice as a bounded generation progress event', () => {
    const notice = {
      id: 'review-1',
      operation: 'guardrail',
      path: '',
      state: 'SUCCEEDED',
      message: '已隐藏敏感信息，将按安全版本生成',
    }
    expect(updateProgress([], notice)).toEqual([notice])
    expect(updateProgress([], { ...notice, message: 'x'.repeat(201) })).toEqual([])
  })
})
