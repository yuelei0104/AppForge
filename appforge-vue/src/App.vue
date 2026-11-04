<script setup lang="ts">
import { onMounted } from 'vue'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from '@/stores/loginUser'

const loginUserStore = useLoginUserStore()

// 应用启动时尝试获取当前登录用户，未登录时后端会返回 40100
onMounted(() => {
  void loginUserStore.fetchLoginUser()
})
</script>

<template>
  <BasicLayout />
</template>
