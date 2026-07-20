<template>
  <div class="login-container">
    <div class="login-card">
      <h1>城市级多源海量数据智能挖掘与可视化决策平台</h1>
      <form @submit.prevent="doLogin" style="width:100%;">
        <div class="login-field">
          <input v-model="loginForm.username" type="text" placeholder="用户名" autocomplete="username" class="login-input" required>
        </div>
        <div class="login-field">
          <input v-model="loginForm.password" type="password" placeholder="密码" autocomplete="current-password" class="login-input" required>
        </div>
        <button type="submit" :disabled="loading" class="login-btn">{{ loading ? '登录中...' : '登 录' }}</button>
        <p style="text-align:center;color:#999;margin-top:16px;font-size:13px;">管理员: admin/123456 | 用户: user/123456</p>
      </form>
    </div>
  </div>
</template>

<script>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authAPI } from '../api'

export default {
  name: 'LoginView',
  setup() {
    const router = useRouter()
    const loginForm = reactive({ username: '', password: '' })
    const loading = ref(false)

    // 已有token直接跳转
    if (localStorage.getItem('token')) {
      router.replace('/')
    }

    async function doLogin() {
      if (!loginForm.username || !loginForm.password) {
        ElMessage.warning('请输入用户名和密码')
        return
      }
      loading.value = true
      try {
        const res = await authAPI.login(loginForm)
        if (res.code === 200) {
          localStorage.setItem('token', res.data.token)
          localStorage.setItem('username', res.data.username || loginForm.username)
          localStorage.setItem('role', res.data.role || 'user')
          ElMessage.success('登录成功')
          router.replace('/')
        } else {
          ElMessage.error(res.message || '登录失败')
        }
      } catch (e) {
        ElMessage.error('登录失败: ' + (e.response?.data?.message || e.message))
      }
      loading.value = false
    }

    return { loginForm, loading, doLogin }
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #0a1929 0%, #1a2332 50%, #16213e 100%);
}
.login-card {
  background: rgba(20, 30, 48, 0.95);
  border: 1px solid rgba(0, 212, 255, 0.3);
  border-radius: 16px;
  padding: 48px 40px;
  width: 460px;
  max-width: 90vw;
  text-align: center;
  box-shadow: 0 8px 48px rgba(0, 0, 0, 0.5);
}
.login-card h1 {
  font-size: 20px;
  font-weight: 600;
  color: #00d4ff;
  margin-bottom: 32px;
  line-height: 1.6;
}
.login-field { margin-bottom: 20px; }
.login-input {
  width: 100%;
  padding: 14px 16px;
  font-size: 15px;
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 8px;
  background: rgba(255,255,255,0.06);
  color: #fff;
  outline: none;
  transition: border-color 0.3s;
}
.login-input:focus { border-color: #00d4ff; }
.login-btn {
  width: 100%;
  padding: 14px;
  font-size: 16px;
  font-weight: 600;
  border: none;
  border-radius: 8px;
  background: linear-gradient(90deg, #00d4ff, #409eff);
  color: #fff;
  cursor: pointer;
  margin-top: 12px;
  transition: opacity 0.3s;
}
.login-btn:hover { opacity: 0.9; }
.login-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
