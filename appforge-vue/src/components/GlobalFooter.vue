<script setup lang="ts">
const projectUrl = 'https://www.codefather.cn'
</script>

<template>
  <a-layout-footer class="global-footer">
    <p class="global-footer__text">
      编程导航原创项目 by
      <a :href="projectUrl" class="global-footer__link" target="_blank" rel="noopener noreferrer">
        程序员岳蕾
      </a>
    </p>
  </a-layout-footer>
</template>

<style scoped>
.global-footer {
  padding: 20px 24px;
  text-align: center;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}

.global-footer__text {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  color: rgba(0, 0, 0, 0.45);
}

.global-footer__link {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.65);
  text-decoration: none;
  transition: color 0.2s;
}

.global-footer__link:hover {
  color: #1677ff;
}

@media (max-width: 767px) {
  .global-footer {
    padding: 16px;
  }
}
</style>
