export const generationModeOptions = [
  { value: 'vue_project', label: '完整网站工程' },
  { value: 'multi_file', label: '轻量网站' },
  { value: 'html', label: '单页网站' },
] as const

export type GenerationMode = (typeof generationModeOptions)[number]['value']

export function generationModeLabel(value: string | undefined) {
  return generationModeOptions.find((option) => option.value === value)?.label || '未知模式'
}
