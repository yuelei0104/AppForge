import { diffLines } from 'diff'
export interface DiffRow {
  left: string
  right: string
  leftLine?: number
  rightLine?: number
  removed?: boolean
  added?: boolean
}
const lines = (value: string) => value.replace(/\n$/, '').split('\n')
export function buildDiffRows(before: string, after: string): DiffRow[] {
  const parts = diffLines(before, after)
  const rows: DiffRow[] = []
  let left = 1,
    right = 1
  for (let i = 0; i < parts.length; i++) {
    const part = parts[i]!
    if (part.removed && parts[i + 1]?.added) {
      const oldLines = lines(part.value),
        newLines = lines(parts[++i]!.value)
      for (let j = 0; j < Math.max(oldLines.length, newLines.length); j++)
        rows.push({
          left: oldLines[j] ?? '',
          right: newLines[j] ?? '',
          leftLine: j < oldLines.length ? left++ : undefined,
          rightLine: j < newLines.length ? right++ : undefined,
          removed: j < oldLines.length,
          added: j < newLines.length,
        })
    } else {
      for (const text of lines(part.value))
        rows.push({
          left: part.added ? '' : text,
          right: part.removed ? '' : text,
          leftLine: part.added ? undefined : left++,
          rightLine: part.removed ? undefined : right++,
          removed: part.removed,
          added: part.added,
        })
    }
  }
  return rows
}
