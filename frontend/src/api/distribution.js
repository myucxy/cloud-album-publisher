import request from './request'

export const distributionApi = {
  list: params => request.get('/distributions', { params }),
  get: id => request.get(`/distributions/${id}`),
  create: data => request.post('/distributions', data),
  update: (id, data) => request.put(`/distributions/${id}`, data),
  remove: id => request.delete(`/distributions/${id}`),
  activate: id => request.patch(`/distributions/${id}/activate`),
  disable: id => request.patch(`/distributions/${id}/disable`)
}

export const adminApi = {
  listUsers: params => request.get('/users', { params }),
  updateUser: (id, data) => request.put(`/users/${id}`, data),
  deleteUser: id => request.delete(`/users/${id}`),
  stats: () => request.get('/admin/stats'),
  listAuditLogs: params => request.get('/admin/audit-logs', { params }),
  listReviews: params => request.get('/admin/reviews', { params }),
  getReviewSettings: () => request.get('/admin/reviews/settings'),
  updateReviewSettings: data => request.put('/admin/reviews/settings', data),
  approveReview: id => request.post(`/admin/reviews/${id}/approve`),
  rejectReview: (id, data) => request.post(`/admin/reviews/${id}/reject`, data)
}
