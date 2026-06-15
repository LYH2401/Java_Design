import api from './index'
import { ElMessage } from 'element-plus'

const USE_MOCK = false

// ==================== Mock 数据 ====================
const mockOrders = [
  {
    id: 1, orderNo: 'REP2026061510455284', userId: 1, title: '图书馆三楼空调不制冷',
    location: '图书馆三楼自习区', description: '图书馆三楼自习区中央空调出风口无冷风，室温超过32度，影响学生自习。',
    urgencyLevel: 'HIGH', status: 'COMPLETED', imageUrls: null, createdBy: 'admin',
    assignedTo: 1, assignedTime: '2026-06-10 09:30:00', completedTime: '2026-06-10 14:00:00',
    createTime: '2026-06-10 09:15:00', updateTime: '2026-06-10 14:00:00',
    maintainerName: '张师傅', reviewRating: 5, reviewComment: '维修技术专业，制冷效果很好！'
  },
  {
    id: 2, orderNo: 'REP2026061510451937', userId: 1, title: '宿舍1号楼502室水龙头漏水',
    location: '学生宿舍1号楼502室', description: '洗手台水龙头持续漏水，地面积水严重，存在安全隐患。',
    urgencyLevel: 'URGENT', status: 'PENDING', imageUrls: null, createdBy: 'admin',
    assignedTo: null, assignedTime: null, completedTime: null,
    createTime: '2026-06-12 08:30:00', updateTime: null,
    maintainerName: null, reviewRating: null, reviewComment: null
  },
  {
    id: 3, orderNo: 'REP2026061510456723', userId: 1, title: '教学楼B301投影仪故障',
    location: '教学楼B区301教室', description: '投影仪无法正常显示，画面模糊且有闪烁。',
    urgencyLevel: 'HIGH', status: 'ASSIGNED', imageUrls: null, createdBy: 'admin',
    assignedTo: 2, assignedTime: '2026-06-13 10:00:00', completedTime: null,
    createTime: '2026-06-13 09:30:00', updateTime: '2026-06-13 10:00:00',
    maintainerName: '李师傅', reviewRating: null, reviewComment: null
  }
]

const mockMaintainers = [
  { id: 1, name: '张师傅', phone: '13800138001', skillCategory: '水电', status: 'AVAILABLE' },
  { id: 2, name: '李师傅', phone: '13800138002', skillCategory: '网络', status: 'BUSY' },
  { id: 3, name: '王师傅', phone: '13800138003', skillCategory: '木工', status: 'BUSY' }
]

const mockStats = {
  totalOrders: 3, pendingCount: 2, completedCount: 1,
  avgRating: 5.0, avgResponseTimeMinutes: 15, completionRate: 33
}

let mockOrderSeq = 10

// ==================== API 封装 ====================

export const createRepair = async (data) => {
  if (USE_MOCK) return mockCreateRepair(data)
  return api.post('/repair/orders', data)
}

export const getRepairOrders = async (params) => {
  if (USE_MOCK) return mockGetOrders(params)
  return api.get('/repair/orders', { params })
}

export const getRepairDetail = async (id) => {
  if (USE_MOCK) return mockGetDetail(id)
  return api.get(`/repair/orders/${id}`)
}

export const assignOrder = async (id, maintainerId) => {
  if (USE_MOCK) {
    ElMessage.success('派单成功（Mock）')
    return { success: true }
  }
  return api.put(`/repair/orders/${id}/assign`, null, { params: { maintainerId } })
}

export const acceptOrder = async (id, maintainerId) => {
  if (USE_MOCK) {
    ElMessage.success('已接单（Mock）')
    return { success: true }
  }
  return api.put(`/repair/orders/${id}/accept`, null, { params: { maintainerId } })
}

export const completeOrder = async (id, maintainerId) => {
  if (USE_MOCK) {
    ElMessage.success('维修已完成（Mock）')
    return { success: true }
  }
  return api.put(`/repair/orders/${id}/complete`, null, { params: { maintainerId } })
}

export const cancelOrder = async (id, userId) => {
  if (USE_MOCK) {
    ElMessage.success('已取消（Mock）')
    return { success: true }
  }
  return api.put(`/repair/orders/${id}/cancel`, null, { params: { userId } })
}

export const submitReview = async (id, data) => {
  if (USE_MOCK) {
    ElMessage.success('评价成功（Mock）')
    return { success: true }
  }
  return api.post(`/repair/orders/${id}/review`, data)
}

/** @deprecated 使用 submitReview */
export const reviewOrder = submitReview

export const getRepairStats = async () => {
  if (USE_MOCK) return { data: mockStats }
  return api.get('/repair/stats')
}

export const getRepairTrend = (days = 30) => api.get('/eval/repair-stats/trend', { params: { days } })

export const getStatusDistribution = () => api.get('/eval/repair-stats/status-distribution')

export const getUrgencyDistribution = () => api.get('/eval/repair-stats/urgency-distribution')

export const getAvailableMaintainers = async (skillCategory) => {
  if (USE_MOCK) return mockGetMaintainers(skillCategory)
  return api.get('/repair/maintainers', { params: { skillCategory } })
}

/** @deprecated 使用 getAvailableMaintainers */
export const getMaintainers = getAvailableMaintainers

// ==================== Mock 实现 ====================

function mockCreateRepair(data) {
  const now = new Date()
  const timePart = now.getFullYear().toString() +
    String(now.getMonth() + 1).padStart(2, '0') +
    String(now.getDate()).padStart(2, '0') +
    String(now.getHours()).padStart(2, '0') +
    String(now.getMinutes()).padStart(2, '0')
  const random = Math.floor(1000 + Math.random() * 9000)
  const orderNo = 'REP' + timePart + random

  const order = {
    id: ++mockOrderSeq,
    orderNo,
    userId: 1,
    title: data.title,
    description: data.description || '',
    location: data.location || '',
    urgencyLevel: data.urgencyLevel || 'NORMAL',
    status: 'PENDING',
    createTime: now.toISOString().replace('T', ' ').substring(0, 19),
    imageUrls: null,
    createdBy: 'admin',
    assignedTo: null,
    assignedTime: null,
    completedTime: null,
    updateTime: null,
    maintainerName: null,
    reviewRating: null,
    reviewComment: null
  }
  mockOrders.unshift(order)
  return { data: order }
}

function mockGetOrders(params) {
  let list = [...mockOrders]
  if (params.userId) {
    list = list.filter(o => o.userId === params.userId)
  }
  if (params.status) {
    list = list.filter(o => o.status === params.status)
  }
  list.sort((a, b) => b.id - a.id)
  const page = params.page || 1
  const pageSize = params.pageSize || 10
  const total = list.length
  const start = (page - 1) * pageSize
  const records = list.slice(start, start + pageSize)
  return { data: { records, total, page, pageSize } }
}

function mockGetDetail(id) {
  const order = mockOrders.find(o => o.id === id)
  if (!order) throw new Error('报修单不存在')
  return { data: { ...order } }
}

function mockGetMaintainers(skillCategory) {
  let list = mockMaintainers.filter(m => m.status === 'AVAILABLE')
  if (skillCategory) {
    list = list.filter(m => m.skillCategory === skillCategory)
  }
  return { data: list }
}
