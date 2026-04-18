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
  updateBgm: (id, data) => request.patch(`/albums/${id}/bgm`, data),

  listBgms: id => request.get(`/albums/${id}/bgms`),
  addBgm: (id, data) => request.post(`/albums/${id}/bgms`, data),
  addBgms: (id, data) => request.post(`/albums/${id}/bgms/batch`, data),
  updateBgmItem: (id, bgmId, data) => request.patch(`/albums/${id}/bgms/${bgmId}`, data),
  removeBgm: (id, bgmId) => request.delete(`/albums/${id}/bgms/${bgmId}`),
  reorderBgms: (id, data) => request.put(`/albums/${id}/bgms/order`, data)
}
