<template>
  <a-card :bordered="false" class="home-view">
    <a-typography-title :level="3">欢迎使用岳蕾 AI 零代码应用生成平台</a-typography-title>
    <a-typography-paragraph>
      这里是全局基础布局的内容区域，页面内容会随路由切换。
    </a-typography-paragraph>
    <a-typography-paragraph type="secondary">
      顶部为全局导航栏，底部为版权信息，移动端会自动切换为抽屉式导航。
    </a-typography-paragraph>
  </a-card>
</template>

<style scoped>
.home-view {
  min-height: 60vh;
}
</style>
