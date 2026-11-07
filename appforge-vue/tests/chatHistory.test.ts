import { describe, it, expect } from 'vitest'
import {
  historyMessages,
  prependHistory,
  oldestCursor,
  type ChatMessage,
} from '@/utils/chatHistory'

const record = (
  id: string,
  createTime = '2026-11-07T12:00:00',
  messageType = 'user',
): API.ChatHistory => ({ id, createTime, messageType, message: id })

describe('chat history chronology and cursors', () => {
  it('sorts descending or unsorted responses into ascending message order without changing the response', () => {
    const records = [
      record('3', '2026-11-07T12:00:03', 'ai'),
      record('1'),
      record('2', '2026-11-07T12:00:02'),
    ]
    expect(historyMessages(records).map((item) => item.content)).toEqual(['1', '2', '3'])
    expect(records[0]?.id).toBe('3')
    expect(historyMessages(records)[2]?.role).toBe('ai')
  })
  it('orders same-second snowflake IDs without losing the final digit', () => {
    const records = [record('475952860203356169'), record('475952860203356168')]
    expect(historyMessages(records)[0]?.id).toBe('history-475952860203356168')
    expect(oldestCursor(records)).toEqual({
      lastId: '475952860203356168',
      lastCreateTime: '2026-11-07T12:00:00',
    })
  })
  it('prepends older messages and deduplicates overlapping pages by ID', () => {
    const current = historyMessages([record('3')])
    expect(
      prependHistory(current, [record('3'), record('2'), record('2'), record('1')]).map(
        (item) => item.content,
      ),
    ).toEqual(['1', '2', '3'])
  })
  it('keeps live message references stable when older pages arrive', () => {
    const live: ChatMessage = {
      id: 'live-ai',
      content: 'partial',
      role: 'ai',
      time: '',
      status: 'streaming',
    }
    const next = prependHistory([live], [record('1')])
    live.content += ' response'
    expect(next.at(-1)).toBe(live)
    expect(next.at(-1)?.content).toBe('partial response')
  })
  it('does not treat empty or malformed cursors as another page', () => {
    expect(oldestCursor([])).toBeUndefined()
    expect(oldestCursor([{ id: '1' }])).toBeUndefined()
    expect(historyMessages([record('1', undefined, 'unexpected')])).toEqual([])
  })
})
