<template>
  <a-layout-footer class="global-footer">
    <p class="global-footer__text">AppForge · 用一句话生成应用</p>
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

@media (max-width: 767px) {
  .global-footer {
    padding: 16px;
  }
}
</style>
