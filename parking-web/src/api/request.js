import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(res)
    }
    return res
  },
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
      return Promise.reject(error)
    }
    const msg = error.response?.data?.msg
      || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试'
        : error.response ? `服务器错误 (${error.response.status})` : '网络连接失败，请检查服务是否启动')
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
