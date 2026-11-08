import { describe, expect, it } from 'vitest'
import { validateMarkdownExport } from '@/utils/chatExport'
describe('Markdown 导出响应', () => {
  it('保留 UTF-8 Markdown 原文', async () => {
    const blob = new Blob(['# 博客\n\n用户：保留完整代码'], { type: 'text/markdown' })
    expect(
      await (await validateMarkdownExport(blob, 'text/markdown;charset=UTF-8')).text(),
    ).toContain('保留完整代码')
  })
  it('把 JSON 业务错误显示给用户，避免下载错误文件', async () => {
    await expect(
      validateMarkdownExport(
        new Blob(['{"code":40101,"message":"无权查看该应用的对话历史"}']),
        'application/json',
      ),
    ).rejects.toThrow('无权查看该应用的对话历史')
  })
  it('拒绝 HTML 错误页和缺失的内容类型', async () => {
    await expect(
      validateMarkdownExport(new Blob(['<html>failed</html>']), 'text/html'),
    ).rejects.toThrow('导出失败')
    await expect(validateMarkdownExport(new Blob(['failed']), '')).rejects.toThrow('导出失败')
  })
})
