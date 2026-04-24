<template>
  <div class="sidebar-wrap">
    <div>
      <div class="device-card">
        <div class="device-title">{{ device.deviceName || device.hostname || device.deviceUid }}</div>
        <div class="device-meta">UID: {{ device.deviceUid || '-' }}</div>
        <div class="device-meta">服务器: {{ device.serverBaseUrl }}</div>
        <div class="device-meta">设备类型: {{ device.deviceType }}</div>
        <a-tag :color="syncColor">{{ syncLabel }}</a-tag>
      </div>

      <div class="section-header">
        <div class="section-title">播放分组</div>
        <div class="section-summary">已启用 {{ enabledDistributionCount }} / {{ playableDistributions.length }}</div>
      </div>

      <a-list :data-source="playableDistributions" size="small" :locale="{ emptyText: '暂无可播放分组' }">
        <template #renderItem="{ item }">
          <a-list-item class="distribution-item" :class="{ active: item.id === currentDistributionId }" @click="$emit('select-distribution', item.id)">
            <div class="distribution-row">
              <a-checkbox
                :checked="isDistributionEnabled(item.id)"
                @click.stop
                @change="event => handleDistributionToggle(item.id, event)"
              />
              <div class="distribution-body">
                <div class="distribution-name">{{ item.name || '未命名分组' }}</div>
                <div class="distribution-meta">
                  {{ item.mediaList?.length || 0 }} 个媒体 · {{ item.shuffle ? '随机' : '顺序' }} · {{ item.loopPlay === false ? '单次' : '循环' }}
                </div>
              </div>
            </div>
          </a-list-item>
        </template>
      </a-list>
    </div>

    <div>
      <div class="section-title">当前播放</div>
      <div class="now-card">
        <div class="now-name">{{ currentMedia?.fileName || '暂无媒体' }}</div>
        <div class="device-meta">分组: {{ currentDistribution?.name || '-' }}</div>
        <div class="device-meta">相册: {{ currentDistribution?.album?.title || '-' }}</div>
        <div class="device-meta">最近同步: {{ pulledAt || '-' }}</div>
        <div v-if="errorMessage" class="error-text">{{ errorMessage }}</div>
      </div>

      <div class="mute-card">
        <div class="section-title">声音设置</div>
        <div class="mute-row">
          <div>
            <div class="distribution-name">静音播放</div>
            <div class="device-meta">{{ playbackMuted ? '当前已静音' : '当前已开启声音' }}</div>
          </div>
          <a-switch :checked="playbackMuted" @change="value => $emit('toggle-mute', value)" />
        </div>
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
  currentDistributionId: {
    type: [Number, String],
    default: null
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
  },
  playbackMuted: {
    type: Boolean,
    default: false
  },
  disabledDistributionIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['select-distribution', 'toggle-distribution', 'toggle-mute', 'refresh', 'open-setup'])

const playableDistributions = computed(() => {
  return props.distributions.filter(item => Array.isArray(item?.mediaList) && item.mediaList.length > 0)
})

const enabledDistributionCount = computed(() => {
  return playableDistributions.value.filter(item => !props.disabledDistributionIds.includes(String(item.id))).length
})

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

function isDistributionEnabled(distributionId) {
  return !props.disabledDistributionIds.includes(String(distributionId))
}

function handleDistributionToggle(distributionId, event) {
  emit('toggle-distribution', {
    distributionId,
    enabled: Boolean(event?.target?.checked)
  })
}
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
.now-card,
.mute-card {
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
.distribution-meta,
.section-summary {
  margin-top: 6px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 13px;
}

.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin: 18px 0 10px;
}

.section-title {
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

.distribution-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.distribution-body {
  min-width: 0;
  flex: 1;
}

.error-text {
  margin-top: 10px;
  color: #fda4af;
  font-size: 13px;
}

.mute-card {
  margin-top: 16px;
}

.mute-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
