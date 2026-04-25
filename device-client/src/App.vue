<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { deviceApi } from '@/api/device'

const UPDATE_PROMPT_STORAGE_KEY = 'device_update_prompted_version'

function formatUpdateContent(update) {
  const lines = []
  if (update.latestVersion) {
    lines.push(`最新版本：${update.latestVersion}`)
  }
  if (update.releaseNotes) {
    lines.push(update.releaseNotes)
  }
  return lines.join('\n') || '检测到新版本，是否前往下载？'
}

async function getCurrentVersion() {
  if (window.deviceBridge?.getAppVersion) {
    return await window.deviceBridge.getAppVersion()
  }
  return import.meta.env.VITE_APP_VERSION || '0.0.0'
}

async function openDownloadUrl(url) {
  if (!url) {
    return
  }
  if (window.deviceBridge?.openExternal) {
    await window.deviceBridge.openExternal(url)
    return
  }
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function downloadAndInstallUpdate(update) {
  if (!window.deviceBridge?.downloadUpdate) {
    await openDownloadUrl(update.downloadUrl)
    return
  }

  const hide = message.loading('正在下载更新安装包...', 0)
  try {
    await window.deviceBridge.downloadUpdate(update)
    message.success('安装包已下载，正在打开安装程序')
  } catch (error) {
    message.error(error?.message || '更新下载失败，已打开浏览器下载')
    await openDownloadUrl(update.downloadUrl)
  } finally {
    hide()
  }
}

function showUpdatePrompt(update) {
  const storageKey = `${update.platform || 'pc'}:${update.channel || 'stable'}:${update.latestVersion || update.latestVersionCode || ''}`
  if (!update.forceUpdate && sessionStorage.getItem(UPDATE_PROMPT_STORAGE_KEY) === storageKey) {
    return
  }

  const options = {
    title: update.forceUpdate ? '发现必须更新的客户端版本' : '发现客户端新版本',
    content: formatUpdateContent(update),
    okText: '下载并安装',
    maskClosable: !update.forceUpdate,
    keyboard: !update.forceUpdate,
    async onOk() {
      await downloadAndInstallUpdate(update)
    }
  }

  if (!update.forceUpdate) {
    options.cancelText = '取消'
    options.onCancel = () => {
      sessionStorage.setItem(UPDATE_PROMPT_STORAGE_KEY, storageKey)
    }
  } else {
    options.cancelButtonProps = { style: { display: 'none' } }
  }

  Modal.confirm(options)
}

async function checkForUpdates() {
  try {
    const currentVersion = await getCurrentVersion()
    const res = await deviceApi.checkUpdate({
      platform: 'pc',
      currentVersion,
      channel: 'stable'
    })
    const update = res.data
    if (update?.hasUpdate) {
      showUpdatePrompt(update)
    }
  } catch {
    // 更新检查不影响播放主流程。
  }
}

onMounted(() => {
  checkForUpdates()
})
</script>
