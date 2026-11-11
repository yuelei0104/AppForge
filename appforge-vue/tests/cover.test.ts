import { describe, expect, it } from 'vitest'
import { coverPending, coverStatusLabel, resolveCoverUrl } from '../src/utils/cover'
describe('cover state and URL', () => {
  it('polls only live tasks', () => {
    for (const s of ['PENDING', 'RUNNING', 'RETRY_WAIT']) expect(coverPending(s)).toBe(true)
    for (const s of ['FAILED', 'SUCCEEDED', 'CANCELLED', undefined])
      expect(coverPending(s)).toBe(false)
  })
  it('distinguishes waiting, success and failure', () => {
    expect(coverStatusLabel('FAILED')).toBe('封面生成失败')
    expect(coverStatusLabel('SUCCEEDED')).toBe('封面已更新')
  })
  it('uses the configured API host for authenticated automatic images', () => {
    expect(
      resolveCoverUrl(
        {
          coverMode: 'AUTO',
          cover: 'http://127.0.0.1:8123/api/app/cover/image?appId=1&revision=abc',
        },
        'https://api.example.com/api',
      ),
    ).toBe('https://api.example.com/api/app/cover/image?appId=1&revision=abc')
  })
  it('preserves custom covers', () => {
    expect(
      resolveCoverUrl(
        { coverMode: 'MANUAL', cover: 'https://cdn.example.com/cover.jpg' },
        'http://localhost:8123/api',
      ),
    ).toBe('https://cdn.example.com/cover.jpg')
  })
})
