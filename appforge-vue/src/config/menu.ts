/**
 * 全局导航菜单配置。
 *
 * 新增菜单项时，只需在 menuItems 中追加一条配置：
 * - key：唯一标识，同时作为点击后跳转的路由路径
 * - label：菜单显示文本
 * - disabled：可选，是否禁用该菜单项
 *
 * 组件会依据当前路由自动高亮对应菜单项。
 */
export interface MenuItemConfig {
  key: string
  label: string
  disabled?: boolean
}

export const menuItems: MenuItemConfig[] = [
  {
    key: '/',
    label: '首页',
  },
  {
    key: '/about',
    label: '关于',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
  },
  {
    key: '/admin/chatManage',
    label: '对话管理',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
  },
]
