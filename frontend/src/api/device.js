import request from './request'

export const deviceApi = {
  list: () => request.get('/devices'),
  bind: data => request.post('/devices', data),
  unbind: id => request.delete(`/devices/${id}`),
  rename: (id, data) => request.patch(`/devices/${id}/name`, data),
  updateStatus: (id, data) => request.patch(`/devices/${id}/status`, data),
  listGroups: () => request.get('/devices/groups'),
  createGroup: data => request.post('/devices/groups', data),
  updateGroup: (id, data) => request.patch(`/devices/groups/${id}`, data),
  deleteGroup: id => request.delete(`/devices/groups/${id}`),
  addGroupMember: (id, data) => request.post(`/devices/groups/${id}/members`, data),
  removeGroupMember: (id, deviceId) => request.delete(`/devices/groups/${id}/members/${deviceId}`)
}
