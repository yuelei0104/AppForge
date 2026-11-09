/**
 * 代码生成类型枚举。
 *
 * <p>与后端 CodeGenTypeEnum 保持一致，避免前端散落字符串。</p>
 */
export enum CodeGenTypeEnum {
  HTML = 'html',
  MULTI_FILE = 'multi_file',
  VUE_PROJECT = 'vue_project',
}

/**
 * 代码生成类型配置。
 */
export const CODE_GEN_TYPE_CONFIG = {
  [CodeGenTypeEnum.HTML]: {
    label: '原生 HTML 模式',
    value: CodeGenTypeEnum.HTML,
  },
  [CodeGenTypeEnum.MULTI_FILE]: {
    label: '原生多文件模式',
    value: CodeGenTypeEnum.MULTI_FILE,
  },
  [CodeGenTypeEnum.VUE_PROJECT]: {
    label: 'Vue 项目模式',
    value: CodeGenTypeEnum.VUE_PROJECT,
  },
} as const

export function codeGenTypeLabel(value: string | undefined) {
  const config = Object.values(CODE_GEN_TYPE_CONFIG).find((item) => item.value === value)
  return config?.label ?? '未知模式'
}
