import request from './request'

export const albumApi = {
  list: params => request.get('/albums', { params }),
  get: id => request.get(`/albums/${id}`),
  create: data => request.post('/albums', data),
  update: (id, data) => request.put(`/albums/${id}`, data),
  remove: id => request.delete(`/albums/${id}`),

  listContents: (id, params) => request.get(`/albums/${id}/contents`, { params }),
  addContent: (id, data) => request.post(`/albums/${id}/contents`, data),
  addContents: (id, data) => request.post(`/albums/${id}/contents/batch`, data),
  removeContent: (id, contentId) => request.delete(`/albums/${id}/contents/${contentId}`),
  updateCover: (id, data) => request.patch(`/albums/${id}/cover`, data),
  updateBgm: (id, data) => request.patch(`/albums/${id}/bgm`, data)
}
