import request from './request'

export const deviceApi = {
  selfRegister: data => request.post('/devices/self/register', data, { authType: 'none' }),
  createSelfAccessToken: (deviceUid) => request.post('/devices/self/token', { deviceUid }, { authType: 'none' }),
  pullCurrent: () => request.get('/devices/pull/current', { authType: 'device' })
}
