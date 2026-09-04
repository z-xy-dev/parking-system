<template>
  <div class="page">
    <el-header class="header">
      <el-button link @click="$router.push('/home')"><el-icon><ArrowLeft /></el-icon> 首页</el-button>
      <h3>我的车位</h3>
    </el-header>
    <el-main>
      <el-button type="primary" @click="dialogVisible=true" style="margin-bottom:16px">发布新车位</el-button>
      <el-row :gutter="20">
        <el-col :span="8" v-for="item in spaces" :key="item.id" style="margin-bottom:20px">
          <el-card>
            <h4>{{ item.title }}</h4>
            <p>{{ item.address }}</p>
            <p>剩余 {{ item.availableSpots }}/{{ item.totalSpots }} | ￥{{ item.pricePerHour }}/h</p>
            <el-tag :type="item.status==='AVAILABLE'?'success':item.status==='UNPUBLISHED'?'warning':'danger'">
              {{ item.status==='AVAILABLE'?'可用':item.status==='UNPUBLISHED'?'已下架':'已满' }}
            </el-tag>
            <div style="margin-top:12px">
              <el-button v-if="item.status==='AVAILABLE'" type="danger" size="small" @click="unpublish(item.id)">下架</el-button>
              <el-button v-if="item.status==='UNPUBLISHED'" type="success" size="small" @click="republish(item.id)">上架</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="spaces.length===0" description="暂无车位" />
    </el-main>

    <el-dialog v-model="dialogVisible" title="发布新车位" width="500px">
      <el-form :model="form">
        <el-form-item label="名称"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="价格(元/小时)"><el-input-number v-model="form.pricePerHour" :min="1" :precision="2" /></el-form-item>
        <el-form-item label="总车位数"><el-input-number v-model="form.totalSpots" :min="1" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" @click="publish" :loading="pubLoading">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const spaces = ref([])
const dialogVisible = ref(false)
const pubLoading = ref(false)
const form = ref({ title: '', address: '', pricePerHour: 10, totalSpots: 1, description: '' })

const loadSpaces = async () => {
  const res = await request.get('/parking/owner')
  spaces.value = res.data
}

const publish = async () => {
  pubLoading.value = true
  try {
    await request.post('/parking/publish', form.value)
    ElMessage.success('发布成功')
    dialogVisible.value = false
    form.value = { title: '', address: '', pricePerHour: 10, totalSpots: 1, description: '' }
    loadSpaces()
  } finally {
    pubLoading.value = false
  }
}

const unpublish = async (id) => {
  await ElMessageBox.confirm('确定要下架该车位吗？下架后用户将无法搜索到该车位。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await request.put(`/parking/unpublish/${id}`)
  ElMessage.success('下架成功')
  loadSpaces()
}

const republish = async (id) => {
  await request.put(`/parking/republish/${id}`)
  ElMessage.success('上架成功')
  loadSpaces()
}

onMounted(loadSpaces)
</script>

<style scoped>
.header { display:flex; align-items:center; gap:16px; background:#409eff; color:#fff; height:56px; padding:0 24px; }
.header * { color:#fff; }
.page { min-height:100vh; background:#f5f7fa; }
</style>
