import request from './request'

export const focalPointApi = {
  updateAlbumSettings: (albumId, data) => request.patch(`/focal-point/albums/${albumId}/settings`, data),
  updateFocalPoint: (albumId, contentId, data) => request.put(`/focal-point/albums/${albumId}/contents/${contentId}/focal-point`, data),
  clearFocalPoint: (albumId, contentId) => request.delete(`/focal-point/albums/${albumId}/contents/${contentId}/focal-point`),
  batchProcess: (albumId, data) => request.post(`/focal-point/albums/${albumId}/process`, data),
  getProviders: () => request.get('/focal-point/providers'),

  listLlmConfigs: () => request.get('/focal-point/llm-configs'),
  getLlmConfig: id => request.get(`/focal-point/llm-configs/${id}`),
  createLlmConfig: data => request.post('/focal-point/llm-configs', data),
  updateLlmConfig: (id, data) => request.put(`/focal-point/llm-configs/${id}`, data),
  deleteLlmConfig: id => request.delete(`/focal-point/llm-configs/${id}`)
}
