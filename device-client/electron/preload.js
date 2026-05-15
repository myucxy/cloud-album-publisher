import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('deviceBridge', {
  getDeviceIdentity: () => ipcRenderer.invoke('device:get-identity'),
  getAppVersion: () => ipcRenderer.invoke('app:get-version'),
  getSystemInfo: () => ipcRenderer.invoke('app:get-system-info'),
  openExternal: url => ipcRenderer.invoke('app:open-external', url),
  downloadUpdate: update => ipcRenderer.invoke('app:download-update', update),
  toggleFullscreen: () => ipcRenderer.invoke('app:set-fullscreen'),
  isFullscreen: () => ipcRenderer.invoke('app:is-fullscreen'),
  onFullscreenChanged: callback => {
    const listener = (_event, value) => callback(Boolean(value))
    ipcRenderer.on('app:fullscreen-changed', listener)
    return () => ipcRenderer.removeListener('app:fullscreen-changed', listener)
  }
})
