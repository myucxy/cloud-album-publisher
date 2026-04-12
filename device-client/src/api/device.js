import request from './request'

export const deviceApi = {
  bind: (data) => request.post('/devices', data, { authType: 'owner' }),
  createAccessToken: (deviceUid) => request.post('/devices/token', { deviceUid }, { authType: 'owner' }),
  pullCurrent: () => request.get('/devices/pull/current', { authType: 'device' })
}
