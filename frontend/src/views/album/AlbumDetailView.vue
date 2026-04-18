<template>
  <div>
    <a-page-header :title="album?.title" @back="router.push('/albums')" style="background:#fff; margin-bottom:16px; padding:16px">
      <template #extra>
        <a-space>
          <a-button @click="openCoverModal"><picture-outlined /> 更新封面</a-button>
          <a-button @click="bgmModalOpen = true"><customer-service-outlined /> 设置 BGM</a-button>
          <a-button type="primary" @click="openAddMediaModal"><plus-outlined /> 添加媒体</a-button>
        </a-space>
      </template>
      <a-descriptions :column="3" size="small">
        <a-descriptions-item label="状态">
          <a-tag :color="album?.status === 'PUBLISHED' ? 'green' : 'default'">{{ album?.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="可见性">{{ album?.visibility }}</a-descriptions-item>
        <a-descriptions-item label="描述">{{ album?.description || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-page-header>

    <a-table :data-source="contents" :columns="columns" row-key="id" :loading="loading"
             :pagination="{ total, current: page, pageSize: 12, onChange: p => { page = p; loadContents() } }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'preview'">
          <div style="width:64px; height:64px; display:flex; align-items:center; justify-content:center; background:#fafafa; border-radius:8px; overflow:hidden">
            <SecureImage
              v-if="getContentMedia(record)?.thumbnailUrl"
              :src="getContentMedia(record).thumbnailUrl"
              alt="thumbnail"
              img-style="width:100%; height:100%; object-fit:cover"
            />
            <SecureImage
              v-else-if="getContentMedia(record)?.mediaType === 'IMAGE' && getContentMedia(record)?.url"
              :src="getContentMedia(record).url"
              alt="image"
              img-style="width:100%; height:100%; object-fit:cover"
            />
            <video-camera-outlined v-else-if="getContentMedia(record)?.mediaType === 'VIDEO'" style="font-size:30px; color:#8c8c8c" />
            <customer-service-outlined v-else-if="getContentMedia(record)?.mediaType === 'AUDIO'" style="font-size:30px; color:#8c8c8c" />
            <file-outlined v-else style="font-size:28px; color:#8c8c8c" />
          </div>
        </template>
        <template v-if="column.key === 'media'">
          <div>{{ getContentMedia(record)?.fileName || `媒体 #${record.mediaId}` }}</div>
          <a-space size="small" style="margin-top:4px" wrap>
            <a-tag>{{ getContentMedia(record)?.mediaType || '未知类型' }}</a-tag>
            <a-tag v-if="getContentMedia(record)?.sourceName">{{ getContentMedia(record).sourceName }}</a-tag>
            <a-tag v-if="getContentMedia(record)?.status" :color="statusColor(getContentMedia(record).status)">
              {{ statusLabel(getContentMedia(record).status) }}
            </a-tag>
          </a-space>
          <div v-if="getContentMedia(record)?.folderPath" style="color:#8c8c8c; font-size:12px; margin-top:4px">
            {{ getContentMedia(record).folderPath }}
          </div>
        </template>
        <template v-if="column.key === 'duration'">
          {{ record.duration ? `${record.duration}s` : '-' }}
        </template>
        <template v-if="column.key === 'action'">
          <a-popconfirm title="从相册中移除？" @confirm="removeContent(record.id)">
            <a-button type="link" danger size="small">移除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="coverModalOpen" title="从媒体库选择相册封面" @ok="submitCover" :confirm-loading="saving" :width="860"
             ok-text="保存" cancel-text="取消"
             :ok-button-props="{ disabled: coverSubmitDisabled }"
             :body-style="{ maxHeight: 'calc(100vh - 220px)', overflowY: 'auto', padding: '16px 20px' }">
      <div class="album-picker-modal-layout">
        <div class="album-picker-modal-sidebar">
          <a-card size="small" title="来源 / 目录">
            <template #extra>
              <a-button type="link" size="small" @click="reloadCoverPicker" :loading="coverPickerLoading || coverGroupLoading">刷新</a-button>
            </template>

            <a-input-search
              v-model:value="coverPickerKeywordInput"
              placeholder="搜索媒体"
              allow-clear
              @search="applyCoverKeyword"
            />

            <div style="margin-top:12px">
              <div :style="pickerAllCardStyle(!coverPickerSourceType && !coverPickerFolderPath)" @click="selectAllCoverSource">
                <div style="display:flex; justify-content:space-between; gap:8px">
                  <span>全部来源</span>
                  <span style="color:#8c8c8c">{{ coverTotalMediaCount }}</span>
                </div>
              </div>

              <div style="display:flex; flex-direction:column; gap:8px; margin-top:8px">
                <template v-for="source in coverSourceGroups" :key="pickerSourceKey(source)">
                  <div :style="pickerSourceCardStyle(source, coverPickerSourceType, coverPickerSourceId, coverPickerFolderPath)">
                    <div style="display:flex; justify-content:space-between; gap:8px; cursor:pointer" @click="selectCoverSource(source)">
                      <div style="min-width:0">
                        <div style="font-weight:500; word-break:break-all">{{ source.sourceName || sourceTypeLabel(source.sourceType) }}</div>
                        <div style="color:#8c8c8c; font-size:12px; margin-top:4px">{{ sourceTypeLabel(source.sourceType) }}</div>
                      </div>
                      <a-tag color="blue" style="margin-inline-end:0">{{ source.mediaCount }}</a-tag>
                    </div>

                    <div v-if="source.folders?.length" style="margin-top:10px; display:flex; flex-direction:column; gap:6px">
                      <div
                        v-for="folder in source.folders"
                        :key="`${pickerSourceKey(source)}#${folder.folderPath}`"
                        :style="pickerFolderItemStyle(source, folder, coverPickerSourceType, coverPickerSourceId, coverPickerFolderPath)"
                        @click="selectCoverFolder(source, folder)"
                      >
                        <span style="min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap">{{ folder.title || folder.folderPath }}</span>
                        <span style="color:inherit">{{ folder.mediaCount }}</span>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </a-card>
        </div>

        <div class="album-picker-modal-main">
          <div style="display:flex; flex-direction:column; gap:12px; margin-bottom:16px">
            <a-space wrap>
              <a-select v-model:value="coverPickerFilterType" style="width:120px" allow-clear placeholder="媒体类型" @change="reloadCoverPicker">
                <a-select-option value="IMAGE">图片</a-select-option>
                <a-select-option v-if="!isExternalCoverSelection" value="VIDEO">视频</a-select-option>
              </a-select>
              <a-tag color="blue">{{ coverPickerTitle }}</a-tag>
              <a-tag v-if="coverPickerKeyword">搜索：{{ coverPickerKeyword }}</a-tag>
            </a-space>
            <span style="color:#8c8c8c; font-size:12px">{{ coverPickerHintText }}</span>
          </div>

          <a-spin :spinning="coverPickerLoading || coverGroupLoading">
            <div v-if="coverSelectableMedia.length" style="display:grid; grid-template-columns:1fr; gap:12px">
              <a-card v-for="item in coverSelectableMedia" :key="resolvePickerItemKey(item)" hoverable :body-style="{ padding: '12px' }" :style="coverMediaCardStyle(item)" @click="selectCoverMedia(item)">
                <div style="display:flex; gap:12px; align-items:flex-start">
                  <div style="width:88px; height:88px; display:flex; align-items:center; justify-content:center; background:#fafafa; border-radius:8px; overflow:hidden; flex-shrink:0">
                    <SecureImage
                      v-if="item.thumbnailUrl"
                      :src="item.thumbnailUrl"
                      alt="cover thumbnail"
                      img-style="width:100%; height:100%; object-fit:cover"
                    />
                    <SecureImage
                      v-else-if="item.mediaType === 'IMAGE' && item.url"
                      :src="item.url"
                      alt="cover image"
                      img-style="width:100%; height:100%; object-fit:cover"
                    />
                    <video-camera-outlined v-else-if="item.mediaType === 'VIDEO'" style="font-size:34px; color:#8c8c8c" />
                    <file-outlined v-else style="font-size:30px; color:#8c8c8c" />
                  </div>
                  <div class="album-picker-modal-main">
                    <div style="font-weight:500; word-break:break-all">{{ item.fileName }}</div>
                    <a-space size="small" wrap style="margin-top:8px">
                      <a-tag>{{ item.mediaType }}</a-tag>
                      <a-tag>{{ item.sourceName || sourceTypeLabel(item.sourceType) }}</a-tag>
                      <a-tag v-if="item.folderPath">{{ item.folderPath }}</a-tag>
                      <a-tag :color="statusColor(item.status)">{{ statusLabel(item.status) }}</a-tag>
                      <a-tag :color="reviewStatusColor(item.reviewStatus, item.status)">
                        {{ reviewStatusLabel(item.reviewStatus, item.status) }}
                      </a-tag>
                    </a-space>
                    <div style="margin-top:8px; color:#8c8c8c; font-size:12px">
                      {{ formatSize(item.fileSize) }}
                      <span v-if="item.durationSec"> · {{ formatDuration(item.durationSec) }}</span>
                      <span v-if="item.width && item.height"> · {{ item.width }} × {{ item.height }}</span>
                    </div>
                  </div>
                </div>
              </a-card>
            </div>
            <a-empty v-else description="暂无可选封面媒体" />
          </a-spin>

          <div style="margin-top:16px; text-align:right">
            <a-pagination
              :current="coverPickerPage"
              :total="coverPickerTotal"
              :page-size="coverPickerPageSize"
              @change="onCoverPickerPageChange"
              show-less-items
            />
          </div>

          <div style="margin-top:16px; border:1px solid #f0f0f0; border-radius:8px; padding:16px; background:#fafafa">
            <div style="font-weight:500; margin-bottom:12px">封面预览</div>
            <template v-if="selectedCoverMediaRecord">
              <div style="margin-bottom:16px; text-align:center; background:#fff; border-radius:8px; padding:12px">
                <SecureImage
                  v-if="selectedCoverMediaRecord.thumbnailUrl"
                  :src="selectedCoverMediaRecord.thumbnailUrl"
                  alt="selected cover"
                  img-style="max-width:100%; max-height:220px; object-fit:contain"
                />
                <SecureImage
                  v-else-if="selectedCoverMediaRecord.mediaType === 'IMAGE' && selectedCoverMediaRecord.url"
                  :src="selectedCoverMediaRecord.url"
                  alt="selected cover"
                  img-style="max-width:100%; max-height:220px; object-fit:contain"
                />
                <video-camera-outlined v-else-if="selectedCoverMediaRecord.mediaType === 'VIDEO'" style="font-size:36px; color:#8c8c8c" />
                <file-outlined v-else style="font-size:36px; color:#8c8c8c" />
              </div>
              <div style="font-weight:500; word-break:break-all">{{ selectedCoverMediaRecord.fileName }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:8px">{{ formatSize(selectedCoverMediaRecord.fileSize) }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:4px">来源：{{ selectedCoverMediaRecord.sourceName || sourceTypeLabel(selectedCoverMediaRecord.sourceType) }}</div>
              <div v-if="selectedCoverMediaRecord.folderPath" style="color:#8c8c8c; font-size:12px; margin-top:4px">目录：{{ selectedCoverMediaRecord.folderPath }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:4px">
                {{ isExternalPickerItem(selectedCoverMediaRecord)
                  ? '将通过服务端代理使用此外部图片作为相册封面'
                  : (selectedCoverMediaRecord.mediaType === 'VIDEO' && !selectedCoverMediaRecord.thumbnailUrl
                    ? '当前视频暂无可用缩略图，请先等待封面生成后再设置为相册封面'
                    : '将使用当前媒体的缩略图作为相册封面') }}
              </div>
            </template>
            <template v-else-if="coverUrl">
              <div style="margin-bottom:16px; text-align:center; background:#fff; border-radius:8px; padding:12px">
                <SecureImage
                  :src="coverUrl"
                  alt="current cover"
                  img-style="max-width:100%; max-height:220px; object-fit:contain"
                />
              </div>
              <div style="color:#8c8c8c; font-size:12px">当前相册封面</div>
            </template>
            <a-empty v-else description="请选择封面媒体" />
          </div>
        </div>
      </div>
    </a-modal>

    <a-modal v-model:open="bgmModalOpen" title="设置 BGM" :width="480" @ok="submitBgm" :confirm-loading="saving" ok-text="保存" cancel-text="取消">
      <a-form layout="vertical">
        <a-form-item label="BGM URL">
          <a-input v-model:value="bgmUrl" placeholder="https://..." />
        </a-form-item>
        <a-form-item label="音量 (0-100)">
          <a-slider v-model:value="bgmVolume" :min="0" :max="100" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="addMediaModalOpen" title="添加媒体到相册" @ok="submitAddMedia" :confirm-loading="saving" :width="860"
             ok-text="保存" cancel-text="取消"
             :ok-button-props="{ disabled: addMediaSubmitDisabled }"
             :body-style="{ maxHeight: 'calc(100vh - 220px)', overflowY: 'auto', padding: '16px 20px' }">
      <div class="album-picker-modal-layout">
        <div class="album-picker-modal-sidebar">
          <a-card size="small" title="来源 / 目录">
            <template #extra>
              <a-button type="link" size="small" @click="reloadMediaPicker" :loading="mediaPickerLoading || mediaGroupLoading">刷新</a-button>
            </template>

            <a-input-search
              v-model:value="mediaPickerKeywordInput"
              placeholder="搜索媒体"
              allow-clear
              @search="applyMediaKeyword"
            />

            <div style="margin-top:12px">
              <div :style="pickerAllCardStyle(!mediaPickerSourceType && !mediaPickerFolderPath)" @click="selectAllMediaSource">
                <div style="display:flex; justify-content:space-between; gap:8px">
                  <span>全部来源</span>
                  <span style="color:#8c8c8c">{{ mediaTotalMediaCount }}</span>
                </div>
              </div>

              <div style="display:flex; flex-direction:column; gap:8px; margin-top:8px">
                <template v-for="source in mediaSourceGroups" :key="pickerSourceKey(source)">
                  <div :style="pickerSourceCardStyle(source, mediaPickerSourceType, mediaPickerSourceId, mediaPickerFolderPath)">
                    <div style="display:flex; justify-content:space-between; gap:8px; cursor:pointer" @click="selectMediaSource(source)">
                      <div style="min-width:0">
                        <div style="font-weight:500; word-break:break-all">{{ source.sourceName || sourceTypeLabel(source.sourceType) }}</div>
                        <div style="color:#8c8c8c; font-size:12px; margin-top:4px">{{ sourceTypeLabel(source.sourceType) }}</div>
                      </div>
                      <a-tag color="blue" style="margin-inline-end:0">{{ source.mediaCount }}</a-tag>
                    </div>

                    <div v-if="source.folders?.length" style="margin-top:10px; display:flex; flex-direction:column; gap:6px">
                      <div
                        v-for="folder in source.folders"
                        :key="`${pickerSourceKey(source)}#${folder.folderPath}`"
                        :style="pickerFolderItemStyle(source, folder, mediaPickerSourceType, mediaPickerSourceId, mediaPickerFolderPath)"
                        @click="selectMediaFolder(source, folder)"
                      >
                        <span style="min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap">{{ folder.title || folder.folderPath }}</span>
                        <span style="color:inherit">{{ folder.mediaCount }}</span>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </a-card>
        </div>

        <div class="album-picker-modal-main">
          <div style="display:flex; flex-direction:column; gap:12px; margin-bottom:16px">
            <a-space wrap>
              <a-select v-model:value="mediaPickerFilterType" style="width:120px" allow-clear placeholder="媒体类型" @change="reloadMediaPicker">
                <a-select-option value="IMAGE">图片</a-select-option>
                <a-select-option value="VIDEO">视频</a-select-option>
                <a-select-option value="AUDIO">音频</a-select-option>
              </a-select>
              <a-tag color="blue">{{ mediaPickerTitle }}</a-tag>
              <a-tag v-if="mediaPickerKeyword">搜索：{{ mediaPickerKeyword }}</a-tag>
            </a-space>
            <span style="color:#8c8c8c; font-size:12px">{{ mediaPickerHintText }}</span>
          </div>

          <a-spin :spinning="mediaPickerLoading || mediaGroupLoading">
            <div v-if="selectableMedia.length" style="display:grid; grid-template-columns:1fr; gap:12px">
              <a-card v-for="item in selectableMedia" :key="resolvePickerItemKey(item)" hoverable :body-style="{ padding: '12px' }" :style="mediaCardStyle(item)" @click="selectMedia(item)">
                <div style="display:flex; gap:12px; align-items:flex-start">
                  <div style="width:88px; height:88px; display:flex; align-items:center; justify-content:center; background:#fafafa; border-radius:8px; overflow:hidden; flex-shrink:0">
                    <SecureImage
                      v-if="item.thumbnailUrl"
                      :src="item.thumbnailUrl"
                      alt="thumbnail"
                      img-style="width:100%; height:100%; object-fit:cover"
                    />
                    <SecureImage
                      v-else-if="item.mediaType === 'IMAGE' && item.url"
                      :src="item.url"
                      alt="image"
                      img-style="width:100%; height:100%; object-fit:cover"
                    />
                    <video-camera-outlined v-else-if="item.mediaType === 'VIDEO'" style="font-size:34px; color:#8c8c8c" />
                    <customer-service-outlined v-else-if="item.mediaType === 'AUDIO'" style="font-size:34px; color:#8c8c8c" />
                    <file-outlined v-else style="font-size:30px; color:#8c8c8c" />
                  </div>
                  <div class="album-picker-modal-main">
                    <div style="font-weight:500; word-break:break-all">{{ item.fileName }}</div>
                    <a-space size="small" wrap style="margin-top:8px">
                      <a-tag>{{ item.mediaType }}</a-tag>
                      <a-tag>{{ item.sourceName || sourceTypeLabel(item.sourceType) }}</a-tag>
                      <a-tag v-if="item.folderPath">{{ item.folderPath }}</a-tag>
                      <a-tag :color="statusColor(item.status)">{{ statusLabel(item.status) }}</a-tag>
                      <a-tag :color="reviewStatusColor(item.reviewStatus, item.status)">
                        {{ reviewStatusLabel(item.reviewStatus, item.status) }}
                      </a-tag>
                    </a-space>
                    <div style="margin-top:8px; color:#8c8c8c; font-size:12px">
                      {{ formatSize(item.fileSize) }}
                      <span v-if="item.durationSec"> · {{ formatDuration(item.durationSec) }}</span>
                      <span v-if="item.width && item.height"> · {{ item.width }} × {{ item.height }}</span>
                    </div>
                  </div>
                </div>
              </a-card>
            </div>
            <a-empty v-else description="暂无可选媒体" />
          </a-spin>

          <div style="margin-top:16px; text-align:right">
            <a-pagination
              :current="mediaPickerPage"
              :total="mediaPickerTotal"
              :page-size="mediaPickerPageSize"
              @change="onMediaPickerPageChange"
              show-less-items
            />
          </div>

          <div style="margin-top:16px; border:1px solid #f0f0f0; border-radius:8px; padding:16px; background:#fafafa">
            <div style="font-weight:500; margin-bottom:12px">已选媒体</div>
            <template v-if="selectedMediaRecord">
              <div style="margin-bottom:16px; text-align:center; background:#fff; border-radius:8px; padding:12px">
                <SecureImage
                  v-if="selectedMediaRecord.thumbnailUrl"
                  :src="selectedMediaRecord.thumbnailUrl"
                  alt="selected thumbnail"
                  img-style="max-width:100%; max-height:180px; object-fit:contain"
                />
                <SecureImage
                  v-else-if="selectedMediaRecord.mediaType === 'IMAGE' && selectedMediaRecord.url"
                  :src="selectedMediaRecord.url"
                  alt="selected image"
                  img-style="max-width:100%; max-height:180px; object-fit:contain"
                />
                <video v-else-if="selectedMediaRecord.mediaType === 'VIDEO' && selectedMediaRecordResolvedUrl" :src="selectedMediaRecordResolvedUrl" controls style="width:100%; max-height:180px" />
                <audio v-else-if="selectedMediaRecord.mediaType === 'AUDIO' && selectedMediaRecordResolvedUrl" :src="selectedMediaRecordResolvedUrl" controls style="width:100%" />
                <file-outlined v-else style="font-size:36px; color:#8c8c8c" />
              </div>
              <div style="font-weight:500; word-break:break-all">{{ selectedMediaRecord.fileName }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:8px">{{ formatSize(selectedMediaRecord.fileSize) }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:4px">来源：{{ selectedMediaRecord.sourceName || sourceTypeLabel(selectedMediaRecord.sourceType) }}</div>
              <div v-if="selectedMediaRecord.folderPath" style="color:#8c8c8c; font-size:12px; margin-top:4px">目录：{{ selectedMediaRecord.folderPath }}</div>
              <div style="color:#8c8c8c; font-size:12px; margin-top:4px">
                {{ isExternalPickerItem(selectedMediaRecord)
                  ? '当前将直接绑定此外部媒体到相册，播放时通过服务端代理访问'
                  : '当前将添加该媒体到相册播放列表' }}
              </div>
            </template>
            <a-empty v-else description="请先从上方选择媒体" />

            <a-form layout="vertical" style="margin-top:16px">
              <a-form-item label="排序权重">
                <a-input-number v-model:value="addMediaForm.sortOrder" :min="0" style="width:100%" />
              </a-form-item>
              <a-form-item label="展示时长（秒）">
                <a-input-number v-model:value="addMediaForm.duration" :min="1" style="width:100%" />
              </a-form-item>
            </a-form>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  PictureOutlined,
  CustomerServiceOutlined,
  VideoCameraOutlined,
  FileOutlined
} from '@ant-design/icons-vue'
import { albumApi } from '@/api/album'
import { mediaApi } from '@/api/media'
import { mediaSourceApi } from '@/api/media-source'
import SecureImage from '@/components/SecureImage.vue'
import { useSecureObjectUrl } from '@/components/useSecureObjectUrl'

const SOURCE_TYPE_ORDER = ['UPLOAD', 'SMB', 'FTP', 'SFTP', 'WEBDAV']

const route = useRoute()
const router = useRouter()
const albumId = route.params.id

const album = ref(null)
const contents = ref([])
const contentMediaMap = ref({})
const total = ref(0)
let page = ref(1)
const loading = ref(false)
const saving = ref(false)

const coverModalOpen = ref(false)
const bgmModalOpen = ref(false)
const addMediaModalOpen = ref(false)

const coverUrl = ref('')
const bgmUrl = ref(80)
const bgmVolume = ref(80)
const addMediaForm = reactive({ mediaId: null, sortOrder: 0, duration: 5 })
const selectableMedia = ref([])
const selectedMediaRecord = ref(null)
const mediaPickerLoading = ref(false)
const mediaPickerFilterType = ref(undefined)
const mediaPickerPage = ref(1)
const mediaPickerPageSize = 12
const mediaPickerTotal = ref(0)
const mediaPickerSourceType = ref(undefined)
const mediaPickerSourceId = ref(undefined)
const mediaPickerFolderPath = ref(undefined)
const mediaPickerKeywordInput = ref('')
const mediaPickerKeyword = ref(undefined)
const mediaPickerGroups = ref({ sourceGroups: [], mediaTypeGroups: [] })
const mediaGroupLoading = ref(false)
const mediaSources = ref([])

const coverSelectableMedia = ref([])
const selectedCoverMediaRecord = ref(null)
const coverPickerLoading = ref(false)
const coverPickerFilterType = ref(undefined)
const coverPickerPage = ref(1)
const coverPickerPageSize = 12
const coverPickerTotal = ref(0)
const coverPickerSourceType = ref(undefined)
const coverPickerSourceId = ref(undefined)
const coverPickerFolderPath = ref(undefined)
const coverPickerKeywordInput = ref('')
const coverPickerKeyword = ref(undefined)
const coverPickerGroups = ref({ sourceGroups: [], mediaTypeGroups: [] })
const coverGroupLoading = ref(false)

const selectedMediaRecordUrl = computed(() => selectedMediaRecord.value?.url || '')
const { resolvedSrc: selectedMediaRecordResolvedUrl } = useSecureObjectUrl(selectedMediaRecordUrl)

const mediaSourceGroups = computed(() => mergePickerSourceGroups(mediaPickerGroups.value?.sourceGroups || [], mediaSources.value || []))
const coverSourceGroups = computed(() => mergePickerSourceGroups(coverPickerGroups.value?.sourceGroups || [], mediaSources.value || []))
const mediaTotalMediaCount = computed(() => mediaSourceGroups.value.reduce((sum, item) => sum + (item.mediaCount || 0), 0))
const coverTotalMediaCount = computed(() => coverSourceGroups.value.reduce((sum, item) => sum + (item.mediaCount || 0), 0))
const mediaPickerTitle = computed(() => buildPickerTitle(mediaSourceGroups.value, mediaPickerSourceType.value, mediaPickerSourceId.value, mediaPickerFolderPath.value))
const coverPickerTitle = computed(() => buildPickerTitle(coverSourceGroups.value, coverPickerSourceType.value, coverPickerSourceId.value, coverPickerFolderPath.value))
const isExternalCoverSelection = computed(() => isExternalPickerSelection(coverPickerSourceType.value, coverPickerSourceId.value))
const mediaPickerHintText = computed(() => {
  if (isExternalPickerSelection(mediaPickerSourceType.value, mediaPickerSourceId.value)) {
    return '当前展示的是外部媒体源目录，可直接绑定到相册，播放时仍通过服务端代理访问。'
  }
  return '仅展示已处理完成的媒体'
})
const coverPickerHintText = computed(() => {
  if (isExternalPickerSelection(coverPickerSourceType.value, coverPickerSourceId.value)) {
    return '当前展示的是外部媒体源目录，仅支持选择图片作为相册封面，访问时仍通过服务端代理。'
  }
  return '可直接选择图片或视频缩略图作为相册封面'
})

const addMediaSubmitDisabled = computed(() => !selectedMediaRecord.value)
const coverSubmitDisabled = computed(() => !selectedCoverMediaRecord.value)

const columns = [
  { title: '预览', key: 'preview', width: 92 },
  { title: '媒体信息', key: 'media' },
  { title: '排序', dataIndex: 'sortOrder', width: 90 },
  { title: '时长', key: 'duration', width: 100 },
  { title: '操作', key: 'action', width: 100 }
]

onMounted(async () => {
  await Promise.all([loadAlbum(), loadContents(), loadMediaSources()])
})

async function loadAlbum() {
  const res = await albumApi.get(albumId)
  album.value = res.data
  coverUrl.value = album.value.coverUrl || ''
  bgmUrl.value = album.value.bgmUrl || ''
  bgmVolume.value = album.value.bgmVolume ?? 80
}

async function loadContents() {
  loading.value = true
  try {
    const res = await albumApi.listContents(albumId, { page: page.value, size: 12 })
    const list = res.data.list || []
    contents.value = list
    total.value = res.data.total
    await loadContentMedia(list)
  } finally {
    loading.value = false
  }
}

async function loadContentMedia(list) {
  const nextMap = {}
  ;(list || []).forEach(item => {
    if (item?.id) {
      nextMap[item.id] = { ...item }
    }
  })

  const mediaIds = [...new Set((list || []).map(item => item.mediaId).filter(Boolean))]
  if (!mediaIds.length) {
    contentMediaMap.value = nextMap
    return
  }

  const responses = await Promise.all(
    mediaIds.map(async id => {
      try {
        const res = await mediaApi.get(id)
        return res.data
      } catch {
        return null
      }
    })
  )

  responses.forEach(item => {
    if (item?.id) {
      const record = (list || []).find(content => content.mediaId === item.id)
      nextMap[record?.id || item.id] = {
        ...(record || {}),
        ...item,
        sourceId: item.sourceId ?? record?.sourceId,
        sourceType: item.sourceType || record?.sourceType,
        sourceName: item.sourceName || record?.sourceName,
        path: record?.path,
        url: record?.url || item.url,
        thumbnailUrl: record?.thumbnailUrl || item.thumbnailUrl
      }
    }
  })

  contentMediaMap.value = nextMap
}

function getContentMedia(record) {
  return contentMediaMap.value[record.id] || record
}

async function removeContent(id) {
  await albumApi.removeContent(albumId, id)
  message.success('已移除')
  await loadContents()
}

async function submitCover() {
  if (!selectedCoverMediaRecord.value) {
    message.warning('请选择封面媒体')
    return
  }

  const payload = buildCoverPayload(selectedCoverMediaRecord.value)
  if (!payload) {
    message.warning('请选择封面媒体')
    return
  }

  saving.value = true
  try {
    await albumApi.updateCover(albumId, payload)
    message.success('封面已更新')
    coverModalOpen.value = false
    selectedCoverMediaRecord.value = null
    await loadAlbum()
  } finally {
    saving.value = false
  }
}

async function submitBgm() {
  saving.value = true
  try {
    await albumApi.updateBgm(albumId, { bgmUrl: bgmUrl.value, bgmVolume: bgmVolume.value })
    message.success('BGM 已更新')
    bgmModalOpen.value = false
    await loadAlbum()
  } finally {
    saving.value = false
  }
}

async function openCoverModal() {
  selectedCoverMediaRecord.value = null
  sanitizeCoverFilterType()
  coverPickerPage.value = 1
  coverModalOpen.value = true
  await Promise.all([loadCoverGroups(), loadCoverSelectableMedia()])
}

async function reloadCoverPicker() {
  sanitizeCoverFilterType()
  coverPickerPage.value = 1
  await Promise.all([loadCoverGroups(), loadCoverSelectableMedia()])
}

async function applyCoverKeyword() {
  coverPickerKeyword.value = coverPickerKeywordInput.value?.trim() || undefined
  coverPickerPage.value = 1
  await Promise.all([loadCoverGroups(), loadCoverSelectableMedia()])
}

async function loadCoverGroups() {
  coverGroupLoading.value = true
  try {
    const res = await mediaApi.groups({ keyword: coverPickerKeyword.value || undefined })
    coverPickerGroups.value = {
      sourceGroups: res.data?.sourceGroups || [],
      mediaTypeGroups: res.data?.mediaTypeGroups || []
    }
  } finally {
    coverGroupLoading.value = false
  }
}

async function loadMediaSources() {
  const res = await mediaSourceApi.list()
  mediaSources.value = Array.isArray(res.data) ? res.data : []
}

async function loadExternalMediaBrowseItems(sourceId, folderPath) {
  if (!sourceId) {
    return []
  }
  const res = await mediaSourceApi.browse(sourceId, {
    path: folderPath ? normalizePath(folderPath) : undefined
  })
  const items = Array.isArray(res.data?.items) ? res.data.items : []
  return items.filter(item => !item?.directory)
}

function toExternalPickerMediaItem(item) {
  return {
    id: null,
    externalMediaKey: item.externalMediaKey,
    sourceId: item.sourceId,
    sourceType: item.sourceType,
    sourceName: item.sourceName,
    path: item.path,
    folderPath: item.folderPath,
    fileName: item.fileName || item.name,
    mediaType: item.mediaType,
    contentType: item.contentType,
    fileSize: item.fileSize ?? item.size,
    durationSec: null,
    width: null,
    height: null,
    status: item.status || 'READY',
    reviewStatus: null,
    thumbnailUrl: item.thumbnailUrl,
    url: item.url,
    ingestMode: item.ingestMode || 'LINKED'
  }
}

function mediaSourceGroupKey(source) {
  return `${source?.sourceType || 'UNKNOWN'}#${source?.sourceId ?? 'default'}`
}

function sourceTypeOrderIndex(sourceType) {
  const index = SOURCE_TYPE_ORDER.indexOf(sourceType)
  return index === -1 ? SOURCE_TYPE_ORDER.length : index
}

function normalizePath(path) {
  if (!path) {
    return '/'
  }
  let normalized = String(path).trim().replace(/\\/g, '/')
  if (!normalized.startsWith('/')) {
    normalized = '/' + normalized
  }
  normalized = normalized.replace(/\/+/g, '/')
  if (normalized.length > 1 && normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized || '/'
}

function mergePickerSourceGroups(baseGroups, sourceList) {
  const sourceMap = new Map()

  const ensureSource = source => {
    const normalizedType = source?.sourceType || 'UPLOAD'
    const key = mediaSourceGroupKey({ sourceType: normalizedType, sourceId: source?.sourceId ?? source?.id ?? null })
    if (!sourceMap.has(key)) {
      sourceMap.set(key, {
        sourceType: normalizedType,
        sourceId: source?.sourceId ?? source?.id ?? null,
        sourceName: source?.sourceName || source?.name || (normalizedType === 'UPLOAD' ? '自行上传' : sourceTypeLabel(normalizedType)),
        mediaCount: 0,
        folders: []
      })
    }
    const target = sourceMap.get(key)
    if (source?.sourceName || source?.name) {
      target.sourceName = source.sourceName || source.name
    }
    if (Array.isArray(source?.folders) && source.folders.length) {
      target.folders = source.folders
    }
    if (source?.mediaCount !== undefined && source?.mediaCount !== null) {
      target.mediaCount = source.mediaCount
    }
    return target
  }

  ensureSource({ sourceType: 'UPLOAD', sourceId: null, sourceName: '自行上传', mediaCount: 0, folders: [] })

  ;(baseGroups || []).forEach(group => ensureSource(group))

  ;(sourceList || []).forEach(source => {
    if (source?.enabled === false) {
      return
    }
    ensureSource({
      sourceType: source.sourceType,
      sourceId: source.id,
      sourceName: source.name,
      mediaCount: 0,
      folders: source.boundPath
        ? [{ folderPath: normalizePath(source.boundPath), title: source.boundPathName || normalizePath(source.boundPath), mediaCount: 0 }]
        : []
    })
  })

  return Array.from(sourceMap.values()).sort((a, b) => {
    const typeDiff = sourceTypeOrderIndex(a.sourceType) - sourceTypeOrderIndex(b.sourceType)
    if (typeDiff !== 0) {
      return typeDiff
    }
    if ((a.sourceId ?? null) === (b.sourceId ?? null)) {
      return 0
    }
    return String(a.sourceName || '').localeCompare(String(b.sourceName || ''), 'zh-CN')
  })
}

function isExternalPickerSelection(sourceType, sourceId) {
  return !!sourceType && sourceType !== 'UPLOAD' && sourceId !== undefined && sourceId !== null
}

function resolvePickerItemKey(item) {
  if (!item) {
    return ''
  }
  return item.id ?? item.externalMediaKey ?? `${item.sourceId || 'draft'}:${item.fileName || item.url || ''}`
}

function filterExternalPickerItems(items, { mediaType, keyword, coverOnly = false } = {}) {
  return items
    .map(toExternalPickerMediaItem)
    .filter(item => !mediaType || item.mediaType === mediaType)
    .filter(item => !coverOnly || item.mediaType === 'IMAGE')
    .filter(item => {
      if (!keyword) {
        return true
      }
      const text = `${item.fileName || ''} ${item.sourceName || ''} ${item.folderPath || ''}`.toLowerCase()
      return text.includes(String(keyword).toLowerCase())
    })
}

function sanitizeCoverFilterType() {
  if (isExternalCoverSelection.value && coverPickerFilterType.value === 'VIDEO') {
    coverPickerFilterType.value = undefined
  }
}

function paginatePickerItems(items, pageNo, pageSize) {
  const start = Math.max(0, (pageNo - 1) * pageSize)
  return items.slice(start, start + pageSize)
}

async function loadCoverSelectableMedia() {
  coverPickerLoading.value = true
  try {
    if (isExternalPickerSelection(coverPickerSourceType.value, coverPickerSourceId.value)) {
      const rawItems = await loadExternalMediaBrowseItems(coverPickerSourceId.value, coverPickerFolderPath.value)
      const filtered = filterExternalPickerItems(rawItems, {
        mediaType: coverPickerFilterType.value || undefined,
        keyword: coverPickerKeyword.value || undefined,
        coverOnly: true
      })
      coverPickerTotal.value = filtered.length
      coverSelectableMedia.value = paginatePickerItems(filtered, coverPickerPage.value, coverPickerPageSize)
      return
    }

    const res = await mediaApi.list({
      page: coverPickerPage.value,
      size: coverPickerPageSize,
      status: 'READY',
      mediaType: coverPickerFilterType.value || undefined,
      sourceType: coverPickerSourceType.value || undefined,
      sourceId: coverPickerSourceId.value ?? undefined,
      folderPath: coverPickerFolderPath.value || undefined,
      keyword: coverPickerKeyword.value || undefined
    })
    const list = res.data.list || []
    coverSelectableMedia.value = list.filter(item => item.mediaType === 'IMAGE' || item.mediaType === 'VIDEO')
    coverPickerTotal.value = res.data.total
  } finally {
    coverPickerLoading.value = false
  }
}

async function selectAllCoverSource() {
  coverPickerSourceType.value = undefined
  coverPickerSourceId.value = undefined
  coverPickerFolderPath.value = undefined
  sanitizeCoverFilterType()
  coverPickerPage.value = 1
  await loadCoverSelectableMedia()
}

async function selectCoverSource(source) {
  coverPickerSourceType.value = source.sourceType || undefined
  coverPickerSourceId.value = source.sourceId ?? undefined
  coverPickerFolderPath.value = undefined
  sanitizeCoverFilterType()
  coverPickerPage.value = 1
  await loadCoverSelectableMedia()
}

async function selectCoverFolder(source, folder) {
  coverPickerSourceType.value = source.sourceType || undefined
  coverPickerSourceId.value = source.sourceId ?? undefined
  coverPickerFolderPath.value = folder.folderPath || undefined
  sanitizeCoverFilterType()
  coverPickerPage.value = 1
  await loadCoverSelectableMedia()
}

function onCoverPickerPageChange(nextPage) {
  coverPickerPage.value = nextPage
  loadCoverSelectableMedia()
}

function buildExternalSelectionPayload(item) {
  if (!item?.externalMediaKey || !item?.sourceId) {
    return null
  }
  const rawPath = item.path || (item.folderPath && item.fileName ? `${item.folderPath}/${item.fileName}` : null)
  if (!rawPath) {
    return null
  }
  return {
    sourceId: item.sourceId,
    sourceType: item.sourceType,
    sourceName: item.sourceName,
    externalMediaKey: item.externalMediaKey,
    path: normalizePath(rawPath),
    fileName: item.fileName,
    contentType: item.contentType,
    mediaType: item.mediaType
  }
}

function buildCoverPayload(item) {
  if (!item) {
    return null
  }
  if (isExternalPickerItem(item)) {
    return buildExternalSelectionPayload(item)
  }
  if (!item.id) {
    return null
  }
  return { mediaId: item.id }
}

function buildAddMediaPayload(item) {
  if (!item) {
    return null
  }
  const basePayload = isExternalPickerItem(item)
    ? buildExternalSelectionPayload(item)
    : item.id
      ? { mediaId: item.id }
      : null
  if (!basePayload) {
    return null
  }
  return {
    ...basePayload,
    sortOrder: addMediaForm.sortOrder,
    duration: addMediaForm.duration
  }
}

function isExternalPickerItem(item) {
  return !item?.id && !!item?.externalMediaKey
}

function selectCoverMedia(item) {
  selectedCoverMediaRecord.value = item
}

function coverMediaCardStyle(item) {
  const selected = resolvePickerItemKey(selectedCoverMediaRecord.value) === resolvePickerItemKey(item)
  return selected
    ? 'border:1px solid #1677ff; box-shadow:0 0 0 2px rgba(22,119,255,0.12)'
    : 'border:1px solid #f0f0f0'
}

async function openAddMediaModal() {
  resetAddMediaState()
  addMediaModalOpen.value = true
  mediaPickerPage.value = 1
  await Promise.all([loadMediaGroups(), loadSelectableMedia()])
}

function resetAddMediaState() {
  addMediaForm.mediaId = null
  addMediaForm.sortOrder = 0
  addMediaForm.duration = 5
  selectedMediaRecord.value = null
}

async function reloadMediaPicker() {
  mediaPickerPage.value = 1
  await Promise.all([loadMediaGroups(), loadSelectableMedia()])
}

async function applyMediaKeyword() {
  mediaPickerKeyword.value = mediaPickerKeywordInput.value?.trim() || undefined
  mediaPickerPage.value = 1
  await Promise.all([loadMediaGroups(), loadSelectableMedia()])
}

async function loadMediaGroups() {
  mediaGroupLoading.value = true
  try {
    const res = await mediaApi.groups({ keyword: mediaPickerKeyword.value || undefined })
    mediaPickerGroups.value = {
      sourceGroups: res.data?.sourceGroups || [],
      mediaTypeGroups: res.data?.mediaTypeGroups || []
    }
  } finally {
    mediaGroupLoading.value = false
  }
}

async function loadSelectableMedia() {
  mediaPickerLoading.value = true
  try {
    if (isExternalPickerSelection(mediaPickerSourceType.value, mediaPickerSourceId.value)) {
      const rawItems = await loadExternalMediaBrowseItems(mediaPickerSourceId.value, mediaPickerFolderPath.value)
      const filtered = filterExternalPickerItems(rawItems, {
        mediaType: mediaPickerFilterType.value || undefined,
        keyword: mediaPickerKeyword.value || undefined
      })
      mediaPickerTotal.value = filtered.length
      selectableMedia.value = paginatePickerItems(filtered, mediaPickerPage.value, mediaPickerPageSize)
      return
    }

    const res = await mediaApi.list({
      page: mediaPickerPage.value,
      size: mediaPickerPageSize,
      status: 'READY',
      mediaType: mediaPickerFilterType.value || undefined,
      sourceType: mediaPickerSourceType.value || undefined,
      sourceId: mediaPickerSourceId.value ?? undefined,
      folderPath: mediaPickerFolderPath.value || undefined,
      keyword: mediaPickerKeyword.value || undefined
    })
    selectableMedia.value = res.data.list || []
    mediaPickerTotal.value = res.data.total
  } finally {
    mediaPickerLoading.value = false
  }
}

async function selectAllMediaSource() {
  mediaPickerSourceType.value = undefined
  mediaPickerSourceId.value = undefined
  mediaPickerFolderPath.value = undefined
  mediaPickerPage.value = 1
  await loadSelectableMedia()
}

async function selectMediaSource(source) {
  mediaPickerSourceType.value = source.sourceType || undefined
  mediaPickerSourceId.value = source.sourceId ?? undefined
  mediaPickerFolderPath.value = undefined
  mediaPickerPage.value = 1
  await loadSelectableMedia()
}

async function selectMediaFolder(source, folder) {
  mediaPickerSourceType.value = source.sourceType || undefined
  mediaPickerSourceId.value = source.sourceId ?? undefined
  mediaPickerFolderPath.value = folder.folderPath || undefined
  mediaPickerPage.value = 1
  await loadSelectableMedia()
}

function onMediaPickerPageChange(nextPage) {
  mediaPickerPage.value = nextPage
  loadSelectableMedia()
}

function selectMedia(item) {
  selectedMediaRecord.value = item
  addMediaForm.mediaId = item?.id || null
}

function mediaCardStyle(item) {
  const selected = resolvePickerItemKey(selectedMediaRecord.value) === resolvePickerItemKey(item)
  return selected
    ? 'border:1px solid #1677ff; box-shadow:0 0 0 2px rgba(22,119,255,0.12)'
    : 'border:1px solid #f0f0f0'
}

async function submitAddMedia() {
  const payload = buildAddMediaPayload(selectedMediaRecord.value)
  if (!payload) {
    message.warning('请选择媒体')
    return
  }
  saving.value = true
  try {
    await albumApi.addContent(albumId, payload)
    message.success('已添加')
    addMediaModalOpen.value = false
    resetAddMediaState()
    await loadContents()
  } finally {
    saving.value = false
  }
}

function pickerSourceKey(source) {
  return `${source?.sourceType || 'UNKNOWN'}#${source?.sourceId ?? 'default'}`
}

function sourceMatches(source, sourceType, sourceId) {
  return (source?.sourceType || undefined) === (sourceType || undefined)
    && (source?.sourceId ?? undefined) === (sourceId ?? undefined)
}

function buildPickerTitle(sourceGroups, sourceType, sourceId, folderPath) {
  const source = (sourceGroups || []).find(item => sourceMatches(item, sourceType, sourceId))
  if (folderPath) {
    return `${source?.sourceName || sourceTypeLabel(sourceType)} / ${folderPath}`
  }
  if (source) {
    return source.sourceName || sourceTypeLabel(source.sourceType)
  }
  return '全部媒体'
}

function pickerAllCardStyle(selected) {
  return selected
    ? 'border:1px solid #1677ff; border-radius:8px; padding:10px 12px; background:#e6f4ff; cursor:pointer'
    : 'border:1px solid #f0f0f0; border-radius:8px; padding:10px 12px; background:#fff; cursor:pointer'
}

function pickerSourceCardStyle(source, sourceType, sourceId, folderPath) {
  const selected = sourceMatches(source, sourceType, sourceId) && !folderPath
  return selected
    ? 'border:1px solid #1677ff; border-radius:8px; padding:10px 12px; background:#e6f4ff'
    : 'border:1px solid #f0f0f0; border-radius:8px; padding:10px 12px; background:#fff'
}

function pickerFolderItemStyle(source, folder, sourceType, sourceId, folderPath) {
  const selected = sourceMatches(source, sourceType, sourceId) && folderPath === folder.folderPath
  return selected
    ? 'display:flex; justify-content:space-between; gap:8px; border-radius:6px; padding:6px 8px; background:#1677ff; color:#fff; cursor:pointer'
    : 'display:flex; justify-content:space-between; gap:8px; border-radius:6px; padding:6px 8px; background:#fafafa; cursor:pointer'
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function formatDuration(seconds) {
  if (seconds === null || seconds === undefined || seconds <= 0) return '-'
  const totalSeconds = Math.floor(seconds)
  const hrs = Math.floor(totalSeconds / 3600)
  const mins = Math.floor((totalSeconds % 3600) / 60)
  const secs = totalSeconds % 60
  if (hrs > 0) return `${hrs}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  return `${mins}:${String(secs).padStart(2, '0')}`
}

function statusColor(status) {
  const map = { UPLOADED: 'gold', PROCESSING: 'blue', READY: 'green', FAILED: 'red' }
  return map[status] || 'default'
}

function statusLabel(status) {
  const map = { UPLOADED: '已上传', PROCESSING: '处理中', READY: '可用', FAILED: '失败' }
  return map[status] || status
}

function reviewStatusColor(reviewStatus, mediaStatus) {
  if (reviewStatus === 'APPROVED') return 'green'
  if (reviewStatus === 'REJECTED') return 'red'
  if (reviewStatus === 'PENDING') return 'orange'
  if (mediaStatus === 'READY') return 'orange'
  return 'default'
}

function reviewStatusLabel(reviewStatus, mediaStatus) {
  if (reviewStatus === 'APPROVED') return '已通过'
  if (reviewStatus === 'REJECTED') return '已驳回'
  if (reviewStatus === 'PENDING') return '待审核'
  if (mediaStatus === 'READY') return '待提交'
  return '未开始'
}

function sourceTypeLabel(sourceType) {
  const map = {
    UPLOAD: '上传',
    SMB: 'SMB',
    FTP: 'FTP',
    SFTP: 'SFTP',
    WEBDAV: 'WebDAV',
    NAS: 'NAS'
  }
  return map[sourceType] || sourceType || '未知来源'
}
</script>

<style scoped>
.album-picker-modal-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  min-height: 420px;
}

.album-picker-modal-sidebar {
  width: 240px;
  flex-shrink: 0;
}

.album-picker-modal-main {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1200px) {
  .album-picker-modal-layout {
    flex-direction: column;
    min-height: auto;
  }

  .album-picker-modal-sidebar {
    width: 100%;
  }

  .album-picker-modal-main {
    width: 100%;
  }
}
</style>
