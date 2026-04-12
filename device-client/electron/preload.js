import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('deviceBridge', {
  getDeviceIdentity: () => ipcRenderer.invoke('device:get-identity')
})
