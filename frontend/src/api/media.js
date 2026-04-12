import request from './request'

export const mediaApi = {
  list: params => request.get('/media', { params }),
  groups: params => request.get('/media/groups', { params }),
  get: id => request.get(`/media/${id}`),
  getStatus: id => request.get(`/media/${id}/status`),
  process: id => request.post(`/media/${id}/process`),
  remove: id => request.delete(`/media/${id}`),

  initUpload: data => request.post('/media/upload/init', data),
  uploadPart: (uploadId, partNumber, file, fileName) => {
    const formData = new FormData()
    formData.append('file', file, fileName)
    return request.put(`/media/upload/${uploadId}/${partNumber}`, formData)
  },
  completeUpload: (uploadId, data) => request.post(`/media/upload/${uploadId}/complete`, data),
  getUploadStatus: uploadId => request.get(`/media/upload-status/${uploadId}`)
}
