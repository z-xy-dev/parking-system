<template>
  <div class="home">
    <el-header class="header">
      <h3>共享停车位</h3>
      <div class="header-right">
        <span v-if="username">{{ username }}</span>
        <el-tag :type="role==='OWNER'?'danger':'primary'" size="small" effect="dark">
          {{ role === 'OWNER' ? '管理员' : '普通用户' }}
        </el-tag>
        <el-button link type="primary" @click="$router.push('/my-orders')">我的订单</el-button>
        <el-button v-if="role==='OWNER'" link type="primary" @click="$router.push('/my-parking')">车位管理</el-button>
        <el-button link type="danger" @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <el-alert v-if="role==='OWNER'" title="管理员模式 — 您可以发布和管理车位" type="warning" :closable="false" show-icon style="margin-bottom:16px" />
      <el-alert v-else title="普通用户模式 — 浏览并预约停车位" type="info" :closable="false" show-icon style="margin-bottom:16px" />
      <div class="search-bar">
        <el-input v-model="keyword" placeholder="搜索车位地址或名称" size="large" style="width:400px" clearable @clear="loadList" />
        <el-button type="primary" size="large" style="margin-left:12px" @click="loadList">搜索</el-button>
      </div>
      <el-row :gutter="20">
        <el-col :span="8" v-for="item in list" :key="item.id" style="margin-bottom:20px">
          <el-card :body-style="{ padding: '16px' }" @click="$router.push('/detail/'+item.id)" style="cursor:pointer">
            <h4>{{ item.title }}</h4>
            <p class="addr"><el-icon><Location /></el-icon> {{ item.address }}</p>
            <p>
              <span class="price">{{ item.pricePerHour }} 元/小时</span>
              <span class="spot">剩余 {{ item.availableSpots }}/{{ item.totalSpots }} 个</span>
            </p>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="list.length === 0" description="暂无车位" />
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import { Location } from '@element-plus/icons-vue'

const router = useRouter()
const username = ref(localStorage.getItem('username'))
const role = ref(localStorage.getItem('role'))
const keyword = ref('')
const list = ref([])

const loadList = async () => {
  const res = await request.get('/parking/list', { params: { keyword: keyword.value, page: 1, size: 50 } })
  list.value = res.data.records
}

const logout = () => {
  localStorage.clear()
  router.push('/login')
}

onMounted(loadList)
</script>

<style scoped>
.home { min-height:100vh; background:#f5f7fa; }
.header { display:flex; justify-content:space-between; align-items:center; background:#409eff; color:#fff; padding:0 24px; height:56px; }
.header-right { display:flex; gap:12px; align-items:center; }
.header-right * { color:#fff !important; }
.search-bar { margin:20px auto; text-align:center; }
.addr { color:#909399; font-size:13px; margin:8px 0; }
.price { color:#f56c6c; font-size:16px; font-weight:bold; }
.spot { float:right; color:#67c23a; }
</style>
