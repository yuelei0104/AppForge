export interface ChatMessage {
  id: string
  role: 'user' | 'ai'
  content: string
  time: string
  status?: 'streaming' | 'done' | 'error' | 'stopped'
}

/** 同一时间的雪花 ID 按整数比较，避免 Number 转换损失精度。 */
export function compareHistory(a: API.ChatHistory, b: API.ChatHistory) {
  const time = (a.createTime ?? '')
    .replace(' ', 'T')
    .localeCompare((b.createTime ?? '').replace(' ', 'T'))
  if (time) return time
  const left = String(a.id ?? ''),
    right = String(b.id ?? '')
  if (/^\d+$/.test(left) && /^\d+$/.test(right)) {
    return left.length - right.length || left.localeCompare(right)
  }
  return left.localeCompare(right)
}

export function historyMessages(records: API.ChatHistory[]): ChatMessage[] {
  return [...records]
    .sort(compareHistory)
    .filter(
      (record) =>
        record.id != null && (record.messageType === 'user' || record.messageType === 'ai'),
    )
    .map((record) => ({
      id: `history-${String(record.id)}`,
      role: record.messageType === 'user' ? 'user' : 'ai',
      content: record.message ?? '',
      time: record.createTime?.replace('T', ' ') ?? '',
      status: 'done',
    }))
}

/** 不替换已有对象，流式消息始终保持原引用。 */
export function prependHistory(current: ChatMessage[], records: API.ChatHistory[]) {
  const known = new Set(current.map((item) => item.id))
  return historyMessages(records)
    .filter((item) => {
      if (known.has(item.id)) return false
      known.add(item.id)
      return true
    })
    .concat(current)
}

export function oldestCursor(records: API.ChatHistory[]) {
  const oldest = [...records].sort(compareHistory)[0]
  return oldest?.createTime && oldest.id != null
    ? { lastCreateTime: oldest.createTime, lastId: String(oldest.id) }
    : undefined
}
