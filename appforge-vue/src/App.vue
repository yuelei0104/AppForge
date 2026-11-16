<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
</script>

<template>
  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#17151a',
        colorInfo: '#2f3fa8',
        colorSuccess: '#2f7d5a',
        colorWarning: '#b7791f',
        colorError: '#b23a2c',
        colorText: '#17151a',
        colorTextSecondary: '#6b645b',
        colorBorder: '#ddd3c4',
        colorBgBase: '#fffdf9',
        borderRadius: 10,
        fontFamily:
          'Inter, -apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei, Noto Sans SC, sans-serif',
      },
    }"
  >
    <BasicLayout />
  </a-config-provider>
</template>
