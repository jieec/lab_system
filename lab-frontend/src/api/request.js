import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: '/api',
  timeout: 20000
})

service.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data.code === 200) {
      return data
    }
    if (data.code === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.error(data.msg || '登录已失效')
      setTimeout(() => {
        window.location.href = '/login'
      }, 600)
      return Promise.reject(new Error(data.msg))
    }
    ElMessage.error(data.msg || '操作失败')
    return Promise.reject(new Error(data.msg || '操作失败'))
  },
  (error) => {
    const msg = error.response ? `请求失败（${error.response.status}）` : '网络异常，请检查后端服务是否启动'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service
