import { CodeGenTypeEnum } from '@/config/codeGenType'

export const generationModeOptions = [
  { value: CodeGenTypeEnum.VUE_PROJECT, label: '完整网站工程' },
  { value: CodeGenTypeEnum.MULTI_FILE, label: '轻量网站' },
  { value: CodeGenTypeEnum.HTML, label: '单页网站' },
] as const

export type GenerationMode = (typeof generationModeOptions)[number]['value']

export function generationModeLabel(value: string | undefined) {
  return generationModeOptions.find((option) => option.value === value)?.label || '未知模式'
}
