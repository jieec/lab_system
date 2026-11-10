import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  profile: () => request.get('/auth/profile'),
  updateProfile: (data) => request.put('/auth/profile', data),
  changePassword: (data) => request.put('/auth/password', data)
}

export const userApi = {
  page: (params) => request.get('/users', { params }),
  options: (role) => request.get('/users/options', { params: { role } }),
  save: (data) => request.post('/users', data),
  update: (data) => request.put('/users', data),
  remove: (id) => request.delete(`/users/${id}`),
  resetPassword: (id) => request.put(`/users/${id}/reset-password`),
  toggleStatus: (id, status) => request.put(`/users/${id}/status`, null, { params: { status } })
}

export const labApi = {
  page: (params) => request.get('/labs', { params }),
  all: () => request.get('/labs/all'),
  buildings: () => request.get('/labs/buildings'),
  detail: (id) => request.get(`/labs/${id}`),
  equipments: (id) => request.get(`/labs/${id}/equipments`),
  save: (data) => request.post('/labs', data),
  update: (data) => request.put('/labs', data),
  remove: (id) => request.delete(`/labs/${id}`)
}

export const equipmentApi = {
  page: (params) => request.get('/equipments', { params }),
  categories: () => request.get('/equipments/categories'),
  byLab: (labId) => request.get(`/equipments/by-lab/${labId}`),
  detail: (id) => request.get(`/equipments/${id}`),
  save: (data) => request.post('/equipments', data),
  update: (data) => request.put('/equipments', data),
  remove: (id) => request.delete(`/equipments/${id}`),
  changeStatus: (id, status) => request.put(`/equipments/${id}/status`, null, { params: { status } })
}

export const reservationApi = {
  create: (data) => request.post('/reservations', data),
  my: (params) => request.get('/reservations/my', { params }),
  page: (params) => request.get('/reservations', { params }),
  timeline: (params) => request.get('/reservations/timeline', { params }),
  detail: (id) => request.get(`/reservations/${id}`),
  approve: (id, data) => request.put(`/reservations/${id}/approve`, data),
  cancel: (id) => request.put(`/reservations/${id}/cancel`),
  checkin: (id) => request.put(`/reservations/${id}/checkin`),
  reschedule: (id, data) => request.put(`/reservations/${id}/reschedule`, data)
}

export const messageApi = {
  unreadCount: () => request.get('/messages/unread-count'),
  page: (params) => request.get('/messages', { params }),
  markRead: (id) => request.put(`/messages/${id}/read`),
  markAllRead: () => request.put('/messages/read-all')
}

export const closureApi = {
  list: (params) => request.get('/closures', { params }),
  add: (data) => request.post('/closures', data),
  remove: (id) => request.delete(`/closures/${id}`)
}

export const logApi = {
  page: (params) => request.get('/logs', { params })
}

export const repairApi = {
  create: (data) => request.post('/repairs', data),
  page: (params) => request.get('/repairs', { params }),
  detail: (id) => request.get(`/repairs/${id}`),
  logs: (id) => request.get(`/repairs/${id}/logs`),
  assign: (id, repairerId) => request.put(`/repairs/${id}/assign`, null, { params: { repairerId } }),
  start: (id) => request.put(`/repairs/${id}/start`),
  finish: (id, data) => request.put(`/repairs/${id}/finish`, data),
  verify: (id, data) => request.put(`/repairs/${id}/verify`, data)
}

export const noticeApi = {
  page: (params) => request.get('/notices', { params }),
  latest: (limit = 5) => request.get('/notices/latest', { params: { limit } }),
  save: (data) => request.post('/notices', data),
  update: (data) => request.put('/notices', data),
  remove: (id) => request.delete(`/notices/${id}`)
}

export const statsApi = {
  dashboard: () => request.get('/stats/dashboard')
}
