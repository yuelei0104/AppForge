import { describe, it, expect } from 'vitest'
import { updateProgress, type ToolProgress } from '@/utils/chatHistory'
const item: ToolProgress = {
  id: 'write-1',
  operation: 'write_file',
  path: 'src/pages/Home.vue',
  state: 'RUNNING',
  message: '正在处理',
}
describe('工程生成进度', () => {
  it('按操作 ID 更新完成状态，同时保留不同文件操作', () => {
    const first = updateProgress([], item)
    const second = updateProgress(first, { ...item, id: 'write-2', path: 'src/App.vue' })
    const done = updateProgress(second, { ...item, state: 'SUCCEEDED' })
    expect(done).toHaveLength(2)
    expect(done[0]?.state).toBe('SUCCEEDED')
    expect(done[1]?.path).toBe('src/App.vue')
    expect(first[0]?.state).toBe('RUNNING')
  })
  it('忽略不完整或非法事件，不影响已有消息', () => {
    const current = [item]
    for (const value of [
      null,
      {},
      'bad',
      { ...item, state: 'unexpected' },
      { ...item, path: 4 },
      { ...item, id: '' },
      { ...item, operation: 'execute_shell' },
    ])
      expect(updateProgress(current, value)).toBe(current)
  })
  it('只保留基本信息，不把工具参数和文件内容带入展示状态', () => {
    const result = updateProgress([], {
      ...item,
      arguments: { content: 'private source' },
      content: 'private source',
    })
    expect(result[0]).toEqual(item)
    expect(JSON.stringify(result)).not.toContain('private source')
  })
  it('限制长任务中的记录数量', () => {
    let progress: ToolProgress[] = []
    for (let i = 0; i < 240; i++) progress = updateProgress(progress, { ...item, id: `write-${i}` })
    expect(progress).toHaveLength(200)
    expect(progress.at(-1)?.id).toBe('write-239')
  })
})
