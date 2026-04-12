import request from './request'

export const mediaSourceApi = {
  list: () => request.get('/media-sources'),
  create: data => request.post('/media-sources', data),
  update: (id, data) => request.patch(`/media-sources/${id}`, data),
  remove: id => request.delete(`/media-sources/${id}`),
  browse: (id, params) => request.get(`/media-sources/${id}/browse`, { params }),
  browseDraft: data => request.post('/media-sources/browse', data)
}
