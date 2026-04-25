import { app, BrowserWindow, ipcMain, shell } from 'electron'
import crypto from 'node:crypto'
import fs from 'node:fs'
import { promises as fsPromises } from 'node:fs'
import http from 'node:http'
import https from 'node:https'
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

async function openExternalUrl(url) {
  const target = new URL(String(url || ''))
  if (target.protocol !== 'http:' && target.protocol !== 'https:') {
    throw new Error('Unsupported external URL')
  }
  await shell.openExternal(target.toString())
}

async function downloadAndInstallUpdate(update) {
  const targetUrl = new URL(String(update?.downloadUrl || ''))
  if (targetUrl.protocol !== 'http:' && targetUrl.protocol !== 'https:') {
    throw new Error('仅支持 http/https 更新地址')
  }

  const fileName = sanitizeInstallerFileName(update?.fileName || path.basename(targetUrl.pathname))
  const updatesDir = path.join(app.getPath('userData'), 'updates')
  await fsPromises.mkdir(updatesDir, { recursive: true })

  const targetPath = path.join(updatesDir, fileName)
  const tempPath = `${targetPath}.part`
  await fsPromises.rm(tempPath, { force: true })
  await fsPromises.rm(targetPath, { force: true })

  await downloadFile(targetUrl, tempPath)
  await verifyDownloadedFile(tempPath, update)
  await fsPromises.rename(tempPath, targetPath)

  const openError = await shell.openPath(targetPath)
  if (openError) {
    throw new Error(openError)
  }

  return { filePath: targetPath }
}

function sanitizeInstallerFileName(value) {
  const fileName = path.basename(String(value || '')).replace(/[<>:"/\\|?*]/g, '_')
  const ext = path.extname(fileName).toLowerCase()
  if (ext !== '.exe' && ext !== '.msi') {
    throw new Error('不支持的安装包格式')
  }
  return fileName
}

function downloadFile(url, targetPath) {
  const client = url.protocol === 'https:' ? https : http
  return new Promise((resolve, reject) => {
    const request = client.get(url, response => {
      if ([301, 302, 303, 307, 308].includes(response.statusCode || 0)) {
        response.resume()
        reject(new Error('更新下载地址发生重定向，请使用最终下载地址'))
        return
      }
      if ((response.statusCode || 0) < 200 || (response.statusCode || 0) >= 300) {
        response.resume()
        reject(new Error(`更新下载失败：HTTP ${response.statusCode}`))
        return
      }

      const output = fs.createWriteStream(targetPath)
      response.pipe(output)
      output.on('finish', () => output.close(resolve))
      output.on('error', reject)
    })
    request.on('error', reject)
  })
}

async function verifyDownloadedFile(filePath, update) {
  const fileStat = await fsPromises.stat(filePath)
  if (update?.size && Number(update.size) > 0 && fileStat.size !== Number(update.size)) {
    await fsPromises.rm(filePath, { force: true })
    throw new Error('安装包大小校验失败')
  }

  if (update?.sha256) {
    const actualHash = await sha256File(filePath)
    if (actualHash.toLowerCase() !== String(update.sha256).toLowerCase()) {
      await fsPromises.rm(filePath, { force: true })
      throw new Error('安装包 SHA-256 校验失败')
    }
  }
}

function sha256File(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256')
    const input = fs.createReadStream(filePath)
    input.on('data', chunk => hash.update(chunk))
    input.on('error', reject)
    input.on('end', () => resolve(hash.digest('hex')))
  })
}

function createWindow() {
  const window = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1100,
    minHeight: 720,
    title: '云影客户端',
    backgroundColor: '#0b1220',
    icon: path.join(__dirname, '..', 'assets', 'icon.png'),
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
  ipcMain.handle('app:get-version', () => app.getVersion())
  ipcMain.handle('app:open-external', (_event, url) => openExternalUrl(url))
  ipcMain.handle('app:download-update', (_event, update) => downloadAndInstallUpdate(update))
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
