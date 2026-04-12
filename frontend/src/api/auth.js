import request from './request'

export const authApi = {
  register: data => request.post('/auth/register', data),
  login: data => request.post('/auth/login', data),
  logout: () => request.post('/auth/logout'),
  refresh: data => request.post('/auth/refresh', data),
  changePassword: data => request.post('/auth/change-password', data)
}
