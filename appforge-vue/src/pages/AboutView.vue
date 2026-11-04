<template>
  <a-card :bordered="false" class="about-view">
    <a-typography-title :level="3">关于</a-typography-title>
    <a-typography-paragraph>
      这是关于页面的内容区域，用于演示路由切换后全局布局保持不变。
    </a-typography-paragraph>
    <a-typography-paragraph type="secondary">
      页面内容由 RouterView 渲染，头部与底部由 BasicLayout 统一提供。
    </a-typography-paragraph>
  </a-card>
</template>

<style scoped>
.about-view {
  min-height: 60vh;
}
</style>
