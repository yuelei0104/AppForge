<template>
  <div class="artifact" aria-hidden="true">
    <span class="artifact__index">LIVE ARTIFACT / 01</span>
    <span class="artifact__sun" />

    <div class="artifact__browser">
      <div class="artifact__chrome">
        <i /><i /><i />
        <span>appforge.site/acme</span>
        <b>↗</b>
      </div>
      <div class="artifact__page">
        <div class="artifact__nav">
          <strong>ACME</strong>
          <span>产品</span><span>案例</span><span>关于</span>
          <em>预约演示</em>
        </div>
        <div class="artifact__hero">
          <span class="artifact__kicker">INFRASTRUCTURE FOR MODERN TEAMS</span>
          <h3>让复杂的技术，<br /><b>变得简单。</b></h3>
          <p>从第一天就保持敏捷，为成长型团队提供稳定、可扩展的基础设施与支持。</p>
          <span class="artifact__cta">开始构建 <i>→</i></span>
        </div>
        <div class="artifact__trust">
          <span>TRUSTED BY AMBITIOUS TEAMS</span>
          <b>NORTHWIND</b><b>LAYER</b><b>ARC</b><b>MONO</b>
        </div>
        <div class="artifact__tiles">
          <div><small>01 / PLATFORM</small><span class="tile-shape tile-shape--sand" /></div>
          <div><small>02 / STORIES</small><span class="tile-shape tile-shape--lime" /></div>
          <div><small>03 / DOCS</small><span class="tile-shape tile-shape--cobalt" /></div>
          <div><small>04 / CONTACT</small><span class="tile-shape tile-shape--clay" /></div>
        </div>
      </div>
    </div>

    <div class="artifact__result">
      <span class="artifact__result-dot" />
      <div>
        <strong>第一版已生成</strong>
        <small>3 个页面 · 12 个组件 · 刚刚</small>
      </div>
      <span class="artifact__result-bar"><i /></span>
    </div>

    <pre class="artifact__code">export default function App() {
  return &lt;Hero /&gt;;
}</pre>
  </div>
</template>

<style scoped>
.artifact {
  position: relative;
  min-height: 500px;
  padding: 30px 0 0 18px;
  isolation: isolate;
}

.artifact__index {
  position: absolute;
  top: 0;
  right: 18px;
  color: var(--forge-muted-2);
  font-family: var(--forge-font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
}

.artifact__sun {
  position: absolute;
  z-index: -1;
  top: 10px;
  right: -30px;
  width: 370px;
  height: 370px;
  background: radial-gradient(circle, rgb(242 91 53 / 20%), transparent 68%);
  filter: blur(4px);
}

.artifact__browser {
  position: relative;
  z-index: 1;
  overflow: hidden;
  background: #17151a;
  border: 1px solid rgb(255 253 249 / 12%);
  border-radius: 14px;
  box-shadow: 0 30px 80px rgb(23 21 26 / 22%);
  transform: rotate(-1deg);
}

.artifact__chrome {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 11px;
  background: #242127;
  border-bottom: 1px solid rgb(255 255 255 / 8%);
}

.artifact__chrome i {
  width: 6px;
  height: 6px;
  background: #5b5550;
  border-radius: 50%;
}

.artifact__chrome span {
  margin-left: 7px;
  padding: 3px 10px;
  color: #9d958b;
  font-family: var(--forge-font-mono);
  font-size: 7px;
  background: #17151a;
  border-radius: 4px;
}

.artifact__chrome b {
  margin-left: auto;
  color: #9d958b;
  font-size: 10px;
  font-weight: 400;
}

.artifact__page {
  position: relative;
  min-height: 390px;
  padding: 20px 26px 26px;
  color: #fffdf9;
  background:
    radial-gradient(ellipse at 78% 22%, rgb(242 91 53 / 50%), transparent 32%),
    radial-gradient(ellipse at 86% 70%, rgb(120 42 27 / 40%), transparent 42%), #17151a;
}

.artifact__nav {
  display: flex;
  align-items: center;
  gap: 18px;
  color: #b9b1a7;
  font-size: 8px;
}

.artifact__nav strong {
  margin-right: 8px;
  color: #fffdf9;
  font-family: var(--forge-font-display);
  font-size: 14px;
  letter-spacing: 0.08em;
}

.artifact__nav em {
  margin-left: auto;
  padding: 4px 8px;
  color: #fffdf9;
  font-size: 7px;
  font-style: normal;
  border: 1px solid rgb(255 255 255 / 24%);
  border-radius: 999px;
}

.artifact__hero {
  max-width: 72%;
  padding: 44px 0 34px;
}

.artifact__kicker {
  color: #d8ef7a;
  font-family: var(--forge-font-mono);
  font-size: 6.5px;
  letter-spacing: 0.14em;
}

.artifact__hero h3 {
  margin: 11px 0 10px;
  font-family: var(--forge-font-display);
  font-size: clamp(26px, 2.8vw, 38px);
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -0.02em;
}

.artifact__hero h3 b {
  color: var(--forge-orange);
  font-weight: 700;
}

.artifact__hero p {
  max-width: 310px;
  margin: 0 0 16px;
  color: #b9b1a7;
  font-size: 8.5px;
  line-height: 1.7;
}

.artifact__cta {
  display: inline-flex;
  gap: 10px;
  align-items: center;
  padding: 7px 10px;
  color: #17151a;
  font-size: 8px;
  font-weight: 700;
  background: var(--forge-orange);
  border-radius: 3px;
}

.artifact__cta i {
  font-style: normal;
}

.artifact__trust {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-top: 1px solid rgb(255 255 255 / 12%);
  border-bottom: 1px solid rgb(255 255 255 / 12%);
}

.artifact__trust span {
  margin-right: auto;
  color: #817970;
  font-family: var(--forge-font-mono);
  font-size: 6px;
  letter-spacing: 0.1em;
}

.artifact__trust b {
  color: #b9b1a7;
  font-family: var(--forge-font-mono);
  font-size: 7px;
  font-weight: 500;
  letter-spacing: 0.12em;
}

.artifact__tiles {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  width: 67%;
  margin: 14px 0 0 auto;
}

.artifact__tiles > div {
  position: relative;
  min-height: 66px;
  overflow: hidden;
  padding: 8px;
  background: rgb(255 253 249 / 5%);
  border: 1px solid rgb(255 255 255 / 13%);
  border-radius: 4px;
}

.artifact__tiles small {
  position: relative;
  z-index: 1;
  color: #8f877b;
  font-family: var(--forge-font-mono);
  font-size: 6px;
}

.tile-shape {
  position: absolute;
  right: -9px;
  bottom: -15px;
  width: 50px;
  height: 50px;
  border-radius: 50% 50% 8px 50%;
  transform: rotate(18deg);
}

.tile-shape--sand {
  background: #cbb79e;
}
.tile-shape--lime {
  background: #d8ef7a;
}
.tile-shape--cobalt {
  background: #4350c9;
}
.tile-shape--clay {
  background: #de8c75;
}

.artifact__result {
  position: absolute;
  z-index: 2;
  right: 13%;
  bottom: 66px;
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 186px;
  padding: 9px 11px;
  background: #fffdf9;
  border: 1px solid var(--forge-line);
  border-radius: 7px;
  box-shadow: 0 14px 30px rgb(23 21 26 / 18%);
}

.artifact__result-dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--forge-green);
  border-radius: 50%;
}

.artifact__result div {
  display: grid;
  gap: 3px;
}

.artifact__result strong {
  color: var(--forge-ink);
  font-size: 9px;
  line-height: 1;
}

.artifact__result small {
  color: var(--forge-muted);
  font-size: 7px;
}

.artifact__result-bar {
  position: absolute;
  right: 11px;
  bottom: 5px;
  left: 25px;
  height: 2px;
  overflow: hidden;
  background: #e9e1d5;
  border-radius: 2px;
}

.artifact__result-bar i {
  display: block;
  width: 84%;
  height: 100%;
  background: var(--forge-orange);
}

.artifact__code {
  position: absolute;
  z-index: 2;
  right: -6px;
  bottom: 2px;
  width: 184px;
  margin: 0;
  padding: 9px 10px;
  color: #b9b1a7;
  font-family: var(--forge-font-mono);
  font-size: 6.5px;
  line-height: 1.6;
  background: #242127;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 5px;
  box-shadow: 0 16px 30px rgb(23 21 26 / 22%);
  transform: rotate(2deg);
}

@media (max-width: 1000px) {
  .artifact {
    min-height: 430px;
    padding-left: 0;
  }
  .artifact__page {
    min-height: 340px;
  }
  .artifact__result {
    right: 4%;
    bottom: 54px;
  }
  .artifact__code {
    right: 0;
  }
}

@media (max-width: 700px) {
  .artifact {
    min-height: 380px;
    margin-top: 18px;
  }
  .artifact__page {
    min-height: 300px;
    padding: 16px;
  }
  .artifact__hero {
    padding: 30px 0 22px;
  }
  .artifact__hero h3 {
    font-size: 25px;
  }
  .artifact__trust {
    gap: 8px;
  }
  .artifact__trust b:nth-of-type(3),
  .artifact__trust b:nth-of-type(4) {
    display: none;
  }
  .artifact__tiles {
    width: 72%;
  }
  .artifact__result {
    right: 2%;
    bottom: 48px;
  }
  .artifact__code {
    display: none;
  }
}
</style>
