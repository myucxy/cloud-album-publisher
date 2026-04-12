<template>
  <div class="sidebar-wrap">
    <div>
      <div class="device-card">
        <div class="device-title">{{ device.deviceName || device.hostname || device.deviceUid }}</div>
        <div class="device-meta">UID：{{ device.deviceUid || '-' }}</div>
        <div class="device-meta">服务器：{{ device.serverBaseUrl }}</div>
        <div class="device-meta">设备类型：{{ device.deviceType }}</div>
        <a-tag :color="syncColor">{{ syncLabel }}</a-tag>
      </div>

      <div class="section-title">分发队列</div>
      <a-list :data-source="distributions" size="small" :locale="{ emptyText: '暂无分发内容' }">
        <template #renderItem="{ item, index }">
          <a-list-item class="distribution-item" :class="{ active: index === currentDistributionIndex }" @click="$emit('select-distribution', index)">
            <div class="distribution-name">{{ item.name }}</div>
            <div class="distribution-meta">
              {{ item.mediaList?.length || 0 }} 项 · {{ item.shuffle ? '随机' : '顺序' }} · {{ item.loopPlay === false ? '单次' : '循环' }}
            </div>
          </a-list-item>
        </template>
      </a-list>
    </div>

    <div>
      <div class="section-title">当前播放</div>
      <div class="now-card">
        <div class="now-name">{{ currentMedia?.fileName || '暂无媒体' }}</div>
        <div class="device-meta">分发：{{ currentDistribution?.name || '-' }}</div>
        <div class="device-meta">相册：{{ currentDistribution?.album?.title || '-' }}</div>
        <div class="device-meta">最近同步：{{ pulledAt || '-' }}</div>
        <div v-if="errorMessage" class="error-text">{{ errorMessage }}</div>
      </div>

      <a-space direction="vertical" style="width: 100%; margin-top: 16px">
        <a-button block type="primary" @click="$emit('refresh')">立即同步</a-button>
        <a-button block @click="$emit('open-setup')">设备设置</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  device: {
    type: Object,
    required: true
  },
  distributions: {
    type: Array,
    default: () => []
  },
  currentDistributionIndex: {
    type: Number,
    default: 0
  },
  currentDistribution: {
    type: Object,
    default: null
  },
  currentMedia: {
    type: Object,
    default: null
  },
  pulledAt: {
    type: String,
    default: ''
  },
  syncStatus: {
    type: String,
    default: 'idle'
  },
  errorMessage: {
    type: String,
    default: ''
  }
})

defineEmits(['select-distribution', 'refresh', 'open-setup'])

const syncColor = computed(() => {
  if (props.syncStatus === 'ready') return 'green'
  if (props.syncStatus === 'loading') return 'processing'
  if (props.syncStatus === 'error') return 'red'
  return 'default'
})

const syncLabel = computed(() => {
  if (props.syncStatus === 'ready') return '已同步'
  if (props.syncStatus === 'loading') return '同步中'
  if (props.syncStatus === 'error') return '同步失败'
  return '未开始'
})
</script>

<style scoped>
.sidebar-wrap {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 20px;
}

.device-card,
.now-card {
  padding: 16px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.9);
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.device-title,
.now-name,
.distribution-name {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.device-meta,
.distribution-meta {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 13px;
}

.section-title {
  margin: 18px 0 10px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.56);
}

.distribution-item {
  display: block;
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 14px;
  cursor: pointer;
  background: rgba(15, 23, 42, 0.72);
  border: 1px solid transparent;
}

.distribution-item.active {
  border-color: rgba(96, 165, 250, 0.72);
  background: rgba(30, 41, 59, 0.9);
}

.error-text {
  margin-top: 10px;
  color: #fda4af;
  font-size: 13px;
}
</style>
