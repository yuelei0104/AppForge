export interface ToolProgress {
  id: string
  operation: string
  path: string
  state: 'RUNNING' | 'SUCCEEDED' | 'FAILED'
  message: string
}

export function updateProgress(current: ToolProgress[], value: unknown): ToolProgress[] {
  if (!value || typeof value !== 'object') return current
  const raw = value as Record<string, unknown>
  const id = typeof raw.id === 'string' ? raw.id : raw.i
  const operation = typeof raw.operation === 'string' ? raw.operation : raw.o
  const path = typeof raw.path === 'string' ? raw.path : raw.p
  const state = typeof raw.state === 'string' ? raw.state : raw.s
  const progressMessage = typeof raw.message === 'string' ? raw.message : raw.m
  if (
    typeof id !== 'string' ||
    typeof operation !== 'string' ||
    typeof path !== 'string' ||
    typeof progressMessage !== 'string' ||
    typeof state !== 'string' ||
    !['RUNNING', 'SUCCEEDED', 'FAILED'].includes(state || '')
  )
    return current
  if (
    !id ||
    id.length > 64 ||
    path.length > 160 ||
    progressMessage.length > 200 ||
    ![
      'read_directory',
      'read_file',
      'write_file',
      'modify_file',
      'delete_file',
      'prepare',
      'build',
      'guardrail',
    ].includes(operation)
  )
    return current
  const safe: ToolProgress = {
    id,
    operation,
    path,
    state: state as ToolProgress['state'],
    message: progressMessage,
  }
  const found = current.findIndex((row) => row.id === id)
  if (found < 0) return [...current.slice(-199), safe]
  return current.map((row, index) => (index === found ? safe : row))
}

export interface ChatAttachment {
  name: string
  kind: string
  url: string
}

export interface ChatMessage {
  progress?: ToolProgress[]
  attachments?: ChatAttachment[]
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
      attachments: record.attachments
        ?.filter((item) => item.name && item.url)
        .map((item) => ({
          name: item.name as string,
          kind: item.kind || 'document',
          url: item.url as string,
        })),
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
