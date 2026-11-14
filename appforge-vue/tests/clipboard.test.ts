import { afterEach, describe, expect, it, vi } from 'vitest'
import { copyText } from '@/utils/clipboard'
afterEach(() => vi.unstubAllGlobals())

function legacyBrowser(result: boolean | Error = true) {
  const field = {
    value: '',
    readOnly: false,
    tabIndex: 0,
    style: {},
    setAttribute: vi.fn(),
    select: vi.fn(),
    setSelectionRange: vi.fn(),
    remove: vi.fn(),
  }
  const focused = { value: '尚未发送的草稿', focus: vi.fn() }
  const range = {}
  const selection = {
    rangeCount: 1,
    getRangeAt: () => ({ cloneRange: () => range }),
    removeAllRanges: vi.fn(),
    addRange: vi.fn(),
  }
  const execCommand = vi.fn(() => {
    if (result instanceof Error) throw result
    return result
  })
  const document = {
    activeElement: focused,
    getSelection: () => selection,
    createElement: vi.fn(() => field),
    body: { appendChild: vi.fn() },
    execCommand,
  }
  vi.stubGlobal('document', document)
  return { field, focused, range, selection, document }
}

describe('复制完整对话原文', () => {
  it('保留中文、换行、缩进与代码围栏，不复制渲染后的摘要', async () => {
    const text = '  修改库存为 10 本\r\n\n```html\n  <p>保留完整代码</p>\n```\n'
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await copyText(text)
    expect(writeText).toHaveBeenCalledWith(text)
  })
  it('不截断长回复', async () => {
    const text = '完整代码\n'.repeat(10000)
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await copyText(text)
    expect(writeText).toHaveBeenCalledWith(text)
  })
  it('浏览器未提供 Clipboard API 时使用兼容复制并清理临时元素', async () => {
    vi.stubGlobal('navigator', {})
    const f = legacyBrowser()
    await copyText('  原始文字\n')
    expect(f.field.value).toBe('  原始文字\n')
    expect(f.document.execCommand).toHaveBeenCalledWith('copy')
    expect(f.field.remove).toHaveBeenCalledOnce()
    expect(f.focused.focus).toHaveBeenCalledWith({ preventScroll: true })
    expect(f.selection.addRange).toHaveBeenCalledWith(f.range)
    expect(f.focused.value).toBe('尚未发送的草稿')
  })
  it('原生复制权限被拒绝时回退复制', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('permission denied'))
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    const f = legacyBrowser()
    await copyText('失败后仍可复制的消息')
    expect(writeText).toHaveBeenCalledOnce()
    expect(f.document.execCommand).toHaveBeenCalledOnce()
  })
  it.each([false, new Error('blocked')])(
    '两种方式均不可用时报告失败且移除临时元素',
    async (result) => {
      vi.stubGlobal('navigator', {})
      const f = legacyBrowser(result)
      await expect(copyText('原文')).rejects.toThrow('手动复制')
      expect(f.field.remove).toHaveBeenCalledOnce()
      expect(f.focused.value).toBe('尚未发送的草稿')
    },
  )
  it('没有任何复制能力时明确提示，不伪造成功', async () => {
    vi.stubGlobal('navigator', {})
    vi.stubGlobal('document', undefined)
    await expect(copyText('消息')).rejects.toThrow('手动复制')
  })
  it('焦点恢复失败不影响成功复制', async () => {
    vi.stubGlobal('navigator', {})
    const f = legacyBrowser()
    f.focused.focus.mockImplementation(() => {
      throw new Error('removed')
    })
    await expect(copyText('消息')).resolves.toBeUndefined()
  })
  it.each(['', ' \n\t'])('空白消息不写入剪贴板', async (text) => {
    const writeText = vi.fn()
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    await expect(copyText(text)).rejects.toThrow('没有可复制')
    expect(writeText).not.toHaveBeenCalled()
  })
})
