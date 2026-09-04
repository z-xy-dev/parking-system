<template>
  <div class="page">
    <el-header class="header">
      <el-button link @click="$router.push('/home')"><el-icon><ArrowLeft /></el-icon> 首页</el-button>
      <h3>我的订单</h3>
    </el-header>
    <el-main>
      <el-table :data="orders" border v-loading="loading">
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column label="停车场" min-width="140">
          <template #default="{row}">{{ spaceMap[row.spaceId] || ('车位#' + row.spaceId) }}</template>
        </el-table-column>
        <el-table-column label="车位编号" width="100">
          <template #default="{row}">
            <el-tag type="warning" size="small">{{ row.spotNumber || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="carPlate" label="车牌号" width="100" />
        <el-table-column label="开始时间"><template #default="{row}">{{ format(row.startTime) }}</template></el-table-column>
        <el-table-column label="结束时间"><template #default="{row}">{{ format(row.endTime) }}</template></el-table-column>
        <el-table-column prop="hours" label="时长(h)" width="80" />
        <el-table-column label="金额(元)" width="120">
          <template #default="{row}">
            <div>{{ row.totalAmount }}</div>
            <div v-if="row.overdueHours > 0" style="color:#f56c6c;font-size:12px">
              含超时费{{ row.overdueFee }}（{{ row.overdueHours }}h）
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{row}">
            <el-tag :type="statusType(row)">{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status==='RESERVED'" link type="danger" :loading="row._loading" @click="cancelOrder(row)">取消</el-button>
            <el-button v-if="row.status==='RESERVED'" link type="primary" :loading="row._loading" @click="completeOrder(row)">完成</el-button>
            <el-button v-if="row.status==='COMPLETED' || row.status==='CANCELED'" link type="info" :loading="row._loading" @click="deleteOrder(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="orders.length===0" description="暂无订单" />
    </el-main>

    <el-dialog v-model="detailVisible" title="订单详情" width="680px" top="6vh">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border title="订单信息">
            <el-descriptions-item label="订单号" :span="2">{{ detail.order.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="statusType(detail.order)" size="small">{{ statusText(detail.order) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="预订时长">{{ detail.order.hours }} 小时</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ format(detail.order.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ format(detail.order.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="订单金额">￥{{ detail.order.totalAmount }}</el-descriptions-item>
            <el-descriptions-item label="超时情况">
              <span v-if="detail.order.overdueHours > 0" style="color:#f56c6c">
                超时 {{ detail.order.overdueHours }} 小时，加收 ￥{{ detail.order.overdueFee }}
              </span>
              <span v-else>无超时</span>
            </el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ format(detail.order.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="车牌号">
              <el-tag type="warning" size="small">{{ detail.order.carPlate || '-' }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-descriptions :column="2" border :title="detail.viewerIsOwner ? '租客信息' : '下单用户信息'" style="margin-top:20px">
            <el-descriptions-item label="用户名">{{ detail.user.username || '-' }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ detail.user.realName || '未填写' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ maskedPhone }}</el-descriptions-item>
            <el-descriptions-item label="账号角色">{{ roleText(detail.user.role) }}</el-descriptions-item>
            <el-descriptions-item label="绑定车牌">{{ detail.user.carPlate || '未绑定' }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ format(detail.user.createdAt) }}</el-descriptions-item>
          </el-descriptions>
          <el-alert v-if="detail.user.degraded" type="warning" :closable="false" show-icon
                    title="用户服务暂时不可用，用户信息未能加载" style="margin-top:12px" />

          <el-descriptions :column="2" border title="车位信息" style="margin-top:20px">
            <el-descriptions-item label="停车场" :span="2">{{ detail.space.title || ('车位#' + detail.order.spaceId) }}</el-descriptions-item>
            <el-descriptions-item label="车位编号">{{ detail.order.spotNumber || '-' }}</el-descriptions-item>
            <el-descriptions-item label="单价">￥{{ detail.space.pricePerHour ?? '-' }} / 小时</el-descriptions-item>
            <el-descriptions-item label="地址" :span="2">{{ detail.space.address || '-' }}</el-descriptions-item>
          </el-descriptions>

          <el-descriptions :column="1" border title="支付信息" style="margin-top:20px">
            <el-descriptions-item label="支付状态">
              <el-tag :type="detail.order.payStatus === 'PAID' ? 'success' : 'info'" size="small">
                {{ detail.order.payStatus === 'PAID' ? '已支付' : (detail.order.status === 'COMPLETED' ? '未支付' : '待出场结算') }}
              </el-tag>
            </el-descriptions-item>
            <template v-if="detail.payments && detail.payments.length">
              <el-descriptions-item label="交易流水号">{{ detail.payments[0].tradeNo }}</el-descriptions-item>
              <el-descriptions-item label="支付方式">{{ methodText(detail.payments[0].method) }}</el-descriptions-item>
              <el-descriptions-item label="实付金额">￥{{ detail.payments[0].amount }}</el-descriptions-item>
              <el-descriptions-item label="支付时间">{{ format(detail.payments[0].paidAt) }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.order.overdueFee > 0" label="金额构成">
                基础费 ￥{{ detail.order.totalAmount - detail.order.overdueFee }} + 超时费 ￥{{ detail.order.overdueFee }}
              </el-descriptions-item>
            </template>
            <el-descriptions-item v-else label="说明">
              <span style="color:#909399">订单在出场「完成」时一次性结算（基础费 + 超时费），当前尚未扣款。</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else-if="!detailLoading" description="未获取到订单详情" />
      </div>
      <template #footer>
        <el-button @click="detailVisible=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const orders = ref([])
const spaceMap = ref({})
const loading = ref(false)
const format = t => t ? new Date(t).toLocaleString() : ''

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const currentUserId = Number(localStorage.getItem('userId') || 0)

const roleText = role => ({ OWNER: '车位业主', USER: '普通用户' }[role] || role || '-')

const methodText = method => ({ WECHAT: '微信支付（模拟）', ALIPAY: '支付宝（模拟）', CASH: '现金' }[method] || method || '-')

// 非本人手机号做脱敏，避免订单详情泄露完整号码
const maskedPhone = computed(() => {
  const phone = detail.value?.user?.phone
  if (!phone) return '未填写'
  const isSelf = Number(detail.value?.user?.id) === currentUserId
  if (isSelf || phone.length < 7) return phone
  return phone.slice(0, 3) + '****' + phone.slice(-4)
})

const openDetail = async row => {
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await request.get('/order/detail/' + row.id)
    detail.value = {
      order: res.data.order || {},
      space: res.data.space || {},
      user: res.data.user || {},
      payments: res.data.payments || [],
      viewerIsOwner: !!res.data.viewerIsOwner
    }
  } catch {
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const statusText = row => {
  if (row.status === 'COMPLETED' && row.overdueHours > 0) return '已完成(超时)'
  const map = { RESERVED: '已预订', USING: '使用中', COMPLETED: '已完成', CANCELED: '已取消' }
  return map[row.status] || row.status
}

const statusType = row => {
  if (row.status === 'COMPLETED' && row.overdueHours > 0) return 'danger'
  const map = { RESERVED: 'warning', USING: 'primary', COMPLETED: 'success', CANCELED: 'info' }
  return map[row.status] || 'info'
}

const loadOrders = async () => {
  loading.value = true
  try {
    const res = await request.get('/order/my')
    orders.value = res.data.map(o => ({ ...o, _loading: false }))
    const spaceIds = [...new Set(orders.value.map(o => o.spaceId))]
    await Promise.all(spaceIds.map(async id => {
      if (!spaceMap.value[id]) {
        try {
          const r = await request.get('/parking/detail/' + id)
          spaceMap.value[id] = r.data.title
        } catch {
          spaceMap.value[id] = '车位#' + id
        }
      }
    }))
  } finally {
    loading.value = false
  }
}

const cancelOrder = async row => {
  await ElMessageBox.confirm('确定取消该订单？', '提示', { type: 'warning' })
  row._loading = true
  try {
    await request.put('/order/cancel/' + row.id)
    ElMessage.success('已取消')
    await loadOrders()
  } finally {
    row._loading = false
  }
}

const completeOrder = async row => {
  // 完成即出场结算：先预览一次性应付金额（基础费 + 超时费），让用户确认
  let preview
  try {
    const res = await request.get('/order/settle/preview/' + row.id)
    preview = res.data
  } catch {
    preview = null
  }
  const parts = []
  if (preview) {
    if (preview.overdueHours > 0) {
      parts.push(`基础费 ￥${preview.baseFee} + 超时费 ￥${preview.overdueFee}（超时 ${preview.overdueHours} 小时，1.5倍）`)
    } else {
      parts.push(`基础费 ￥${preview.baseFee}`)
    }
  }
  const settleText = preview ? `本次将一次性结算：${parts.join('，')}，合计 ￥${preview.totalAmount}。` : ''
  const msg = (isOverdue(row) ? '检测到已超时。' : '') + (settleText || '确定完成该订单？')
  await ElMessageBox.confirm(msg, '完成订单（出场结算）', { type: isOverdue(row) ? 'warning' : 'info' })
  row._loading = true
  try {
    await request.put('/order/complete/' + row.id)
    ElMessage.success(preview ? `已完成，已结算 ￥${preview.totalAmount}` : '已完成')
    await loadOrders()
  } finally {
    row._loading = false
  }
}

const isOverdue = row => new Date() > new Date(row.endTime)

const deleteOrder = async row => {
  await ElMessageBox.confirm('确定删除该订单记录？', '提示', { type: 'warning' })
  row._loading = true
  try {
    await request.delete('/order/' + row.id)
    ElMessage.success('已删除')
    await loadOrders()
  } finally {
    row._loading = false
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.header { display:flex; align-items:center; gap:16px; background:#409eff; color:#fff; height:56px; padding:0 24px; }
.header * { color:#fff; }
.page { min-height:100vh; background:#f5f7fa; }
</style>
