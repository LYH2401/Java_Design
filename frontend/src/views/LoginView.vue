<template>
  <div class="auth-wrapper">
    <el-card class="auth-card" shadow="hover">
      <h2 class="auth-title">登录</h2>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="账号" prop="account">
          <el-input
            v-model="form.account"
            placeholder="请输入用户名 / 手机号 / 邮箱"
            prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="auth-btn"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="auth-link">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  account: '',
  password: ''
})

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await login({ account: form.account, password: form.password })
    const token = res && res.data
    if (!token) {
      ElMessage.error('登录失败：未获取到token')
      return
    }
    localStorage.setItem('campus_token', token)
    localStorage.removeItem('campus_user')
    ElMessage.success('登录成功')
    router.push('/')
  } catch (err) {
    ElMessage.error(err.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  height: calc(100% - 44px);
  padding: 20px;
}

.auth-card {
  width: 420px;
  max-width: 100%;
}

.auth-title {
  text-align: center;
  margin-bottom: 24px;
  font-size: 22px;
  color: var(--text-primary);
}

.auth-btn {
  width: 100%;
  margin-top: 8px;
}

.auth-link {
  text-align: center;
  font-size: 14px;
  color: var(--text-secondary);
}

.auth-link a {
  color: var(--brand-color);
  text-decoration: none;
}

.auth-link a:hover {
  text-decoration: underline;
}
</style>
