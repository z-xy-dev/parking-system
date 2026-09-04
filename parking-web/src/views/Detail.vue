<template>
 <div class="detail">
 <el-header class="header">
 <el-button link @click="$router.back()"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
 <h3>车位详情</h3>
 </el-header>
 <el-main v-if="space">
 <el-descriptions :column="2" border>
 <el-descriptions-item label="名称">{{ space.title }}</el-descriptions-item>
 <el-descriptions-item label="地址">{{ space.address }}</el-descriptions-item>
 <el-descriptions-item label="价格">{{ space.pricePerHour }} 元/小时</el-descriptions-item>
 <el-descriptions-item label="剩余车位">
 <span style="color:#67c23a;font-weight:bold">{{ availableCount }}</span>
 / {{ space.totalSpots }}
 </el-descriptions-item>
 <el-descriptions-item label="状态">
 <el-tag :type="space.status==='AVAILABLE'?'success':'danger'">
 {{ space.status === 'AVAILABLE' ? '可预订' : '已满' }}
 </el-tag>
 </el-descriptions-item>
 </el-descriptions>
 <p v-if="space.description" style="margin-top:16px;color:#666;">{{ space.description }}</p>

 <!-- 车位选择网格 -->
 <el-card style="margin-top:24px" v-if="space.status==='AVAILABLE'">
 <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
 <h4 style="margin:0">选择车位编号</h4>
 <el-tooltip content="每5秒自动刷新车位状态" placement="left">
 <el-tag size="small" type="info">
 <el-icon style="margin-right:4px"><Refresh /></el-icon>
 实时刷新
 </el-tag>
 </el-tooltip>
 </div>

 <el-alert title="温馨提示：同一时间只能有一个进行中的订单；超时离场按1.5倍单价收取超时费" type="warning" :closable="false" show-icon style="margin-bottom:16px" />

 <!-- 车位网格 -->
 <div class="spot-grid" v-loading="spotsLoading">
 <div
 v-for="spot in spots"
 :key="spot.spotNumber"
 class="spot-item"
 :class="spotClass(spot)"
 @click="selectSpot(spot)"
 >
 <div class="spot-num">{{ spot.spotNumber }}</div>
 <div class="spot-status">{{ spotStatusText(spot) }}</div>
 </div>
 </div>

 <!-- 图例 -->
 <div class="legend">
 <span class="legend-item"><span class="dot dot-available"></span>空闲可选</span>
 <span class="legend-item"><span class="dot dot-selected"></span>已选中</span>
 <span class="legend-item"><span class="dot dot-occupied"></span>已占用</span>
 <span class="legend-item"><span class="dot dot-disabled"></span>不可用</span>
 </div>

 <!-- 预订表单 -->
 <el-divider />
 <h4>预订信息</h4>
 <el-form :model="orderForm">
 <el-form-item label="车牌号">
 <el-input v-model="orderForm.carPlate" placeholder="请输入车牌号" :disabled="!!userCarPlate" />
 <div v-if="userCarPlate" style="color:#909399;font-size:12px;margin-top:4px">已绑定车牌，不可修改</div>
 </el-form-item>
 <el-form-item label="已选车位">
 <el-tag v-if="selectedSpot" type="primary" size="large" closable @close="selectedSpot=null">
 编号 {{ selectedSpot }}
 </el-tag>
 <span v-else style="color:#c0c4cc">请上方选择车位</span>
 </el-form-item>
 <el-form-item label="开始时间">
 <el-date-picker v-model="orderForm.startTime" type="datetime" placeholder="选择开始时间" value-format="YYYY-MM-DDTHH:mm:ss" :disabled-date="disabledPast" />
 </el-form-item>
 <el-form-item label="结束时间">
 <el-date-picker v-model="orderForm.endTime" type="datetime" placeholder="选择结束时间" value-format="YYYY-MM-DDTHH:mm:ss" :disabled-date="disabledPast" />
 </el-form-item>
 <el-form-item>
 <el-button type="primary" size="large" @click="book" :loading="booking" :disabled="!selectedSpot">
 立即预订
 </el-button>
 <span v-if="!selectedSpot" style="color:#e6a23c;font-size:12px;margin-left:12px">请先选择车位</span>
 </el-form-item>
 </el-form>
 </el-card>
 </el-main>
 </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const space = ref(null)
const spots = ref([])
const spotsLoading = ref(false)
const booking = ref(false)
const selectedSpot = ref(null)
const userCarPlate = ref(localStorage.getItem('carPlate') || '')
const orderForm = ref({ carPlate: localStorage.getItem('carPlate') || '', startTime: '', endTime: '' })
const disabledPast = time => time.getTime() < Date.now() - 86400000
let refreshTimer = null

const availableCount = computed(() => spots.value.filter(s => s.status === 'AVAILABLE').length)

const loadDetail = async () => {
 const res = await request.get('/parking/detail/' + route.params.id)
 space.value = res.data
}

const loadSpots = async () => {
 if (!space.value) return
 spotsLoading.value = true
 try {
 const res = await request.get('/parking/spots/' + route.params.id)
 spots.value = res.data || []
 // 如果已选车位在刷新后变为占用，取消选择
 if (selectedSpot.value !== null) {
 const spot = spots.value.find(s => s.spotNumber === selectedSpot.value)
 if (spot && spot.status !== 'AVAILABLE') {
 ElMessage.warning(`车位 ${selectedSpot.value} 刚被其他用户占用，请重新选择`)
 selectedSpot.value = null
 }
 }
 } finally {
 spotsLoading.value = false
 }
 }

const spotClass = (spot) => {
 if (spot.status === 'DISABLED') return 'spot-disabled'
 if (spot.status !== 'AVAILABLE') return 'spot-occupied'
 if (selectedSpot.value === spot.spotNumber) return 'spot-selected'
 return 'spot-available'
}

const spotStatusText = (spot) => {
 if (spot.status === 'DISABLED') return '不可用'
 if (spot.status === 'AVAILABLE') return '空闲'
 return '已占'
}

const selectSpot = (spot) => {
 if (spot.status === 'DISABLED') {
 ElMessage.warning('该车位不可用')
 return
 }
 if (spot.status !== 'AVAILABLE') {
 ElMessage.warning(`车位 ${spot.spotNumber} 已被占用`)
 return
 }
 if (selectedSpot.value === spot.spotNumber) {
 selectedSpot.value = null
 } else {
 selectedSpot.value = spot.spotNumber
 }
}

const book = async () => {
 if (!selectedSpot.value) {
 ElMessage.warning('请先选择车位')
 return
 }
 if (!orderForm.value.carPlate || !orderForm.value.startTime || !orderForm.value.endTime) {
 ElMessage.warning('请填写完整信息')
 return
 }
 if (new Date(orderForm.value.startTime) >= new Date(orderForm.value.endTime)) {
 ElMessage.warning('结束时间必须晚于开始时间')
 return
 }
 booking.value = true
 try {
 const res = await request.post('/order/create', {
 spaceId: space.value.id,
 spotNumber: String(selectedSpot.value),
 carPlate: orderForm.value.carPlate,
 startTime: orderForm.value.startTime,
 endTime: orderForm.value.endTime
 })
 ElMessage.success(`预订成功！车位编号：${selectedSpot.value}`)
 router.push('/my-orders')
 } catch (err) {
 // 预订失败，刷新车位状态（可能被其他人抢先）
 await loadSpots()
 selectedSpot.value = null
 } finally {
 booking.value = false
 }
}

onMounted(async () => {
 await loadDetail()
 await loadSpots()
 // 每5秒自动刷新车位状态
 refreshTimer = setInterval(loadSpots, 5000)
})

onUnmounted(() => {
 if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.header { display:flex; align-items:center; gap:16px; background:#409eff; color:#fff; height:56px; padding:0 24px; }
.header * { color:#fff; }
.detail { min-height:100vh; background:#f5f7fa; }

.spot-grid {
 display: grid;
 grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
 gap: 12px;
 margin-bottom: 16px;
}
.spot-item {
 border: 2px solid #dcdfe6;
 border-radius: 8px;
 padding: 12px 8px;
 text-align: center;
 cursor: pointer;
 transition: all 0.2s ease;
 user-select: none;
}
.spot-item:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.spot-num { font-size: 20px; font-weight: bold; margin-bottom: 4px; }
.spot-status { font-size: 11px; }

.spot-available {
 border-color: #67c23a;
 background: #f0f9eb;
 color: #67c23a;
}
.spot-available:hover { background: #e1f3d8; }

.spot-selected {
 border-color: #409eff;
 background: #ecf5ff;
 color: #409eff;
 box-shadow: 0 0 0 2px #409eff inset;
}

.spot-occupied {
 border-color: #dcdfe6;
 background: #f4f4f5;
 color: #909399;
 cursor: not-allowed;
}
.spot-occupied:hover { transform: none; }

.spot-disabled {
 border-color: #fde2e2;
 background: #fef0f0;
 color: #f56c6c;
 cursor: not-allowed;
}
.spot-disabled:hover { transform: none; }

.legend {
 display: flex;
 gap: 20px;
 margin-bottom: 16px;
 font-size: 13px;
 color: #606266;
}
.legend-item { display: flex; align-items: center; gap: 6px; }
.dot {
 display: inline-block;
 width: 12px;
 height: 12px;
 border-radius: 50%;
 border: 2px solid;
}
.dot-available { border-color: #67c23a; background: #f0f9eb; }
.dot-selected { border-color: #409eff; background: #ecf5ff; }
.dot-occupied { border-color: #dcdfe6; background: #f4f4f5; }
.dot-disabled { border-color: #f56c6c; background: #fef0f0; }
</style>
