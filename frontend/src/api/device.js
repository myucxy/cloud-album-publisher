import request from './request'

export const deviceApi = {
  list: () => request.get('/devices'),
  listUnbound: () => request.get('/devices/unbound'),
  bind: data => request.post('/devices', data),
  bindUnbound: (id, data) => request.patch(`/devices/${id}/bind`, data),
  selfRegister: data => request.post('/devices/self/register', data),
  selfToken: data => request.post('/devices/self/token', data),
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
