<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2>共享停车位系统</h2>
      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width:100%" @click="login" :loading="loading">登录</el-button>
        </el-form-item>
      </el-form>
      <p class="tip">没有账号？<router-link to="/register">立即注册</router-link></p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import { ElMessage } from 'element-plus'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const login = () => {
  formRef.value.validate(async valid => {
    if (!valid) return
    loading.value = true
    try {
      const res = await request.post('/user/login', form)
      const data = res.data
      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('username', data.username)
      localStorage.setItem('role', data.role)
      localStorage.setItem('carPlate', data.carPlate || '')
      ElMessage.success('登录成功')
      router.push('/home')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container { display:flex; justify-content:center; align-items:center; height:100vh; background:#f0f2f5; }
.login-card { width:400px; }
.login-card h2 { text-align:center; margin-bottom:24px; }
.tip { text-align:center; color:#999; }
</style>
