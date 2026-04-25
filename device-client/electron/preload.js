import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('deviceBridge', {
  getDeviceIdentity: () => ipcRenderer.invoke('device:get-identity'),
  getAppVersion: () => ipcRenderer.invoke('app:get-version'),
  openExternal: url => ipcRenderer.invoke('app:open-external', url),
  downloadUpdate: update => ipcRenderer.invoke('app:download-update', update)
})
