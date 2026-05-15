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

      <div class="settings-card">
        <div class="section-title">屏幕方向</div>
        <a-select
          class="setting-control"
          :value="playbackRotation"
          :options="rotationOptions"
          @change="value => $emit('change-rotation', value)"
        />
      </div>

      <div class="settings-card">
        <div class="section-title">亮度调节</div>
        <div class="mute-row">
          <div>
            <div class="distribution-name">定时变暗</div>
            <div class="device-meta">{{ brightnessScheduleEnabled ? '时段外自动降低画面亮度' : '未启用定时亮度' }}</div>
          </div>
          <a-switch :checked="brightnessScheduleEnabled" @change="value => $emit('toggle-brightness-schedule', value)" />
        </div>
        <div class="brightness-grid">
          <label class="setting-label">
            <span>开始</span>
            <a-select
              :value="brightnessStartHour"
              :options="hourOptions"
              @change="value => emitBrightnessHours(value, brightnessEndHour)"
            />
          </label>
          <label class="setting-label">
            <span>结束</span>
            <a-select
              :value="brightnessEndHour"
              :options="hourOptions"
              @change="value => emitBrightnessHours(brightnessStartHour, value)"
            />
          </label>
        </div>
        <label class="setting-label dim-label">
          <span>变暗比例</span>
          <a-select
            :value="brightnessDimPercent"
            :options="dimOptions"
            @change="value => $emit('change-brightness-dim', value)"
          />
        </label>
      </div>

      <a-space direction="vertical" style="width: 100%; margin-top: 16px">
        <a-button block type="primary" @click="$emit('refresh')">立即同步</a-button>
        <a-button block @click="$emit('check-update')">检查更新</a-button>
        <a-button block @click="$emit('show-system-info')">系统能力</a-button>
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
  playbackRotation: {
    type: String,
    default: 'auto'
  },
  brightnessScheduleEnabled: {
    type: Boolean,
    default: false
  },
  brightnessStartHour: {
    type: Number,
    default: 8
  },
  brightnessEndHour: {
    type: Number,
    default: 22
  },
  brightnessDimPercent: {
    type: Number,
    default: 55
  },
  disabledDistributionIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits([
  'select-distribution',
  'toggle-distribution',
  'toggle-mute',
  'change-rotation',
  'toggle-brightness-schedule',
  'change-brightness-hours',
  'change-brightness-dim',
  'check-update',
  'show-system-info',
  'refresh',
  'open-setup'
])

const rotationOptions = [
  { label: '自动', value: 'auto' },
  { label: '不旋转', value: '0' },
  { label: '旋转 90 度', value: '90' },
  { label: '旋转 180 度', value: '180' },
  { label: '旋转 270 度', value: '270' }
]
const hourOptions = Array.from({ length: 24 }, (_, hour) => ({
  label: `${String(hour).padStart(2, '0')}:00`,
  value: hour
}))
const dimOptions = [30, 45, 55, 70, 85].map(value => ({
  label: `${value}%`,
  value
}))

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

function emitBrightnessHours(startHour, endHour) {
  emit('change-brightness-hours', {
    startHour,
    endHour
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
  overflow-y: auto;
  padding-right: 6px;
  scrollbar-width: thin;
  scrollbar-color: rgba(148, 163, 184, 0.28) transparent;
}

.sidebar-wrap:hover {
  scrollbar-color: rgba(148, 163, 184, 0.48) transparent;
}

.sidebar-wrap::-webkit-scrollbar {
  width: 8px;
}

.sidebar-wrap::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-wrap::-webkit-scrollbar-thumb {
  min-height: 48px;
  border: 2px solid transparent;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.24);
  background-clip: content-box;
}

.sidebar-wrap:hover::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.44);
  background-clip: content-box;
}

.sidebar-wrap::-webkit-scrollbar-thumb:hover {
  background: rgba(203, 213, 225, 0.62);
  background-clip: content-box;
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

.mute-card,
.settings-card {
  margin-top: 16px;
}

.mute-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.setting-control {
  width: 100%;
  margin-top: 10px;
}

.brightness-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.setting-label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: rgba(255, 255, 255, 0.62);
  font-size: 12px;
}

.dim-label {
  margin-top: 10px;
}
</style>
