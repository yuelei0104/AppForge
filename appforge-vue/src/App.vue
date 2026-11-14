<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
</script>

<template>
  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#6654d9',
        borderRadius: 10,
        colorText: '#242336',
        fontFamily:
          'Inter, -apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif',
      },
    }"
  >
    <BasicLayout />
  </a-config-provider>
</template>
