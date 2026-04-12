import { app, BrowserWindow, ipcMain } from 'electron'
import crypto from 'node:crypto'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import machineIdModule from 'node-machine-id'

const { machineId } = machineIdModule
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const rendererUrl = process.env.DEVICE_CLIENT_RENDERER_URL

app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required')

async function getDeviceIdentity() {
  let rawMachineId
  try {
    rawMachineId = await machineId(true)
  } catch {
    rawMachineId = `${os.hostname()}-${os.platform()}-${os.arch()}`
  }

  const fingerprint = crypto
    .createHash('sha256')
    .update(String(rawMachineId))
    .digest('hex')
    .slice(0, 24)

  return {
    deviceUid: `desktop-${os.platform()}-${fingerprint}`,
    platform: os.platform(),
    hostname: os.hostname()
  }
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1100,
    minHeight: 720,
    backgroundColor: '#0b1220',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  if (rendererUrl) {
    window.loadURL(rendererUrl)
    window.webContents.openDevTools({ mode: 'detach' })
    return
  }

  window.loadFile(path.join(app.getAppPath(), 'dist/index.html'))
}

app.whenReady().then(() => {
  ipcMain.handle('device:get-identity', getDeviceIdentity)
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
