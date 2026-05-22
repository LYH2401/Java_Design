<template>
  <div id="app-container">
    <nav class="nav-bar">
      <router-link to="/" class="nav-item" active-class="nav-active">
        <span>💬 校园助手</span>
      </router-link>
      <router-link to="/solver" class="nav-item" active-class="nav-active">
        <span>📝 问题求解</span>
      </router-link>
      <router-link to="/eval" class="nav-item" active-class="nav-active">
        <span>📊 评估对比</span>
      </router-link>
    </nav>
    <router-view />
  </div>
</template>

<script setup>
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app, #app-container {
  height: 100%;
  width: 100%;
  overflow: hidden;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}

.nav-bar {
  display: flex;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 16px;
  height: 44px;
  align-items: center;
  gap: 4px;
  z-index: 100;
  position: relative;
}

.nav-item {
  padding: 8px 16px;
  border-radius: 8px;
  text-decoration: none;
  color: #606266;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-item:hover {
  background: #f0f2f5;
  color: #303133;
}

.nav-active {
  background: #ecf5ff;
  color: #409eff;
}
</style>
