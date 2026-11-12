/** Preserve ordinary stream errors; attach only a validated audit reference for guardrail failures. */
export function streamErrorMessage(value: unknown, fallback: string): string {
  if (!value || typeof value !== 'object') return fallback
  const data = value as Record<string, unknown>
  const rawMessage = typeof data.message === 'string' ? data.message : data.m
  const message =
    typeof rawMessage === 'string' && rawMessage.trim() ? rawMessage.slice(0, 300) : fallback
  if (
    data.guardrail !== true ||
    typeof data.requestId !== 'string' ||
    !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(data.requestId)
  )
    return message
  return `${message}\n\n参考编号：${data.requestId}`
}
