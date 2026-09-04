<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2>用户注册</h2>
      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input v-model="form.phone" placeholder="手机号" size="large" />
        </el-form-item>
        <el-form-item prop="realName">
          <el-input v-model="form.realName" placeholder="真实姓名" size="large" />
        </el-form-item>
        <el-form-item prop="carPlate">
          <el-input v-model="form.carPlate" placeholder="车牌号（如粤A12345）" size="large" />
        </el-form-item>
        <el-form-item>
          <el-radio-group v-model="form.role">
            <el-radio value="USER">普通用户</el-radio>
            <el-radio value="OWNER">车位业主</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width:100%" @click="register" :loading="loading">注册</el-button>
        </el-form-item>
      </el-form>
      <p class="tip">已有账号？<router-link to="/login">去登录</router-link></p>
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
const form = reactive({ username: '', password: '', phone: '', realName: '', carPlate: '', role: 'USER' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  carPlate: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
}

const register = () => {
  formRef.value.validate(async valid => {
    if (!valid) return
    loading.value = true
    try {
      await request.post('/user/register', form)
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } finally {
      loading.value = false
    }
  })
}
</script>

<style scoped>
.login-container { display:flex; justify-content:center; align-items:center; height:100vh; background:#f0f2f5; }
.login-card { width:420px; }
.login-card h2 { text-align:center; margin-bottom:24px; }
.tip { text-align:center; color:#999; }
</style>
