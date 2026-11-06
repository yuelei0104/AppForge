import { describe, it, expect } from 'vitest'
import { buildDiffRows } from '@/utils/versionDiff'
import { generationLabel, featureLabel, isOnline } from '@/utils/appStatus'

describe('side-by-side code diff', () => {
  it('aligns replacements and keeps unchanged line numbers', () => {
    const rows = buildDiffRows('old\nsame\n', 'new\nsame\n')
    expect(rows[0]).toMatchObject({
      left: 'old',
      right: 'new',
      leftLine: 1,
      rightLine: 1,
      added: true,
      removed: true,
    })
    expect(rows[1]).toMatchObject({ left: 'same', right: 'same', leftLine: 2, rightLine: 2 })
  })
  it('handles inserted lines without shifting old-side numbers', () => {
    const rows = buildDiffRows('a\nb\n', 'a\ninsert\nb\n')
    expect(rows[1]).toMatchObject({ left: '', right: 'insert', rightLine: 2, added: true })
    expect(rows[2]).toMatchObject({ leftLine: 2, rightLine: 3 })
  })
  it('handles deleted files and empty files', () => {
    expect(buildDiffRows('', '')).toEqual([])
    expect(buildDiffRows('a\n', '')[0]).toMatchObject({ left: 'a', right: '', removed: true })
    expect(buildDiffRows('', 'b\n')[0]).toMatchObject({ left: '', right: 'b', added: true })
  })
  it('preserves Unicode and displays code as plain strings', () => {
    expect(buildDiffRows('<script>旧代码</script>', '<script>新代码</script>')[0]?.right).toBe(
      '<script>新代码</script>',
    )
  })
})
describe('persisted app states', () => {
  it('maps generation states, including cancellation and failure', () => {
    expect(generationLabel('GENERATING')).toBe('生成中')
    expect(generationLabel('STOPPED')).toBe('已停止')
    expect(generationLabel('FAILED')).toBe('生成失败')
  })
  it('does not display an offline deployment as a live work link', () => {
    expect(isOnline({ deployKey: 'Abc123', deploymentStatus: 'OFFLINE' })).toBe(false)
    expect(isOnline({ deployKey: 'Abc123', deploymentStatus: 'ONLINE' })).toBe(true)
    expect(isOnline({ deploymentStatus: 'ONLINE' })).toBe(false)
  })
  it('retains legacy deployment display until metadata is migrated', () => {
    expect(isOnline({ deployKey: 'Legacy123' })).toBe(true)
  })
  it('distinguishes pending and rejected featured requests', () => {
    expect(featureLabel('PENDING')).toBe('审核中')
    expect(featureLabel('REJECTED')).toBe('未通过')
  })
})
