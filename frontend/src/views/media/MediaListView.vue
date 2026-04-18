<template>
  <div style="display:flex; gap:16px; align-items:flex-start">
    <div style="width:300px; flex-shrink:0">
      <a-card title="媒体库分组" size="small">
        <template #extra>
          <a-button type="link" size="small" @click="reloadLibrary" :loading="groupLoading || loading">刷新</a-button>
        </template>

        <a-input-search
          v-model:value="keywordInput"
          placeholder="搜索文件名 / 来源 / 目录"
          allow-clear
          @search="applyKeyword"
        />

        <div style="margin-top:16px">
          <div style="font-size:12px; color:#8c8c8c; margin-bottom:8px">来源类型</div>
          <a-spin :spinning="groupLoading || mediaSourceLoading">
            <div style="display:flex; flex-direction:column; gap:8px">
              <div :style="allLibraryCardStyle()" @click="selectAllLibrary">
                <div style="display:flex; justify-content:space-between; gap:8px">
                  <span>全部媒体</span>
                  <span style="color:#8c8c8c">{{ totalMediaCount }}</span>
                </div>
              </div>

              <template v-for="typeGroup in sourceTypeSections" :key="typeGroup.sourceType">
                <div :style="sourceTypeCardStyle(typeGroup)">
                  <div
                    style="display:flex; justify-content:space-between; gap:8px; cursor:pointer"
                    @click="selectSourceTypeSection(typeGroup)"
                  >
                    <div style="min-width:0">
                      <div style="font-weight:600; word-break:break-all">{{ sourceTypeLabel(typeGroup.sourceType) }}</div>
                      <div style="color:#8c8c8c; font-size:12px; margin-top:4px">
                        {{ typeGroup.sourceCount }} 个来源
                      </div>
                    </div>
                    <a-tag color="blue" style="margin-inline-end:0">{{ typeGroup.mediaCount }}</a-tag>
                  </div>

                  <div style="margin-top:10px; display:flex; flex-direction:column; gap:8px">
                    <template v-for="source in typeGroup.sources" :key="sourceGroupKey(source)">
                      <div :style="sourceCardStyle(source)" @click="selectSource(source)">
                        <div style="display:flex; justify-content:space-between; gap:8px; align-items:flex-start">
                          <div style="min-width:0; flex:1">
                            <div style="font-weight:500; word-break:break-all">{{ source.sourceName || sourceTypeLabel(source.sourceType) }}</div>
                            <div style="color:#8c8c8c; font-size:12px; margin-top:4px">
                              {{ sourceSummaryText(source) }}
                            </div>
                          </div>
                          <a-tag style="margin-inline-end:0">{{ source.mediaCount || 0 }}</a-tag>
                        </div>

                        <div v-if="source.sourceType !== 'UPLOAD' && source.boundPath" style="margin-top:8px">
                          <div
                            :style="boundPathStyle(source)"
                            @click.stop="openBrowseSource(source, source.boundPath)"
                          >
                            <span style="min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap">
                              已绑定：{{ source.boundPathName || source.boundPath }}
                            </span>
                            <span>{{ source.enabled === false ? '已停用' : '查看' }}</span>
                          </div>
                        </div>

                        <div v-if="source.folders?.length" style="margin-top:8px; display:flex; flex-direction:column; gap:6px">
                          <div
                            v-for="folder in source.folders"
                            :key="`${sourceGroupKey(source)}#${folder.folderPath}`"
                            :style="folderItemStyle(source, folder)"
                            @click.stop="selectFolder(source, folder)"
                          >
                            <span style="min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap">
                              {{ folder.title || folder.folderPath }}
                            </span>
                            <span>{{ folder.mediaCount }}</span>
                          </div>
                        </div>
                      </div>
                    </template>

                    <a-empty
                      v-if="!typeGroup.sources.length"
                      :description="typeGroup.sourceType === 'UPLOAD' ? '暂无上传媒体' : '暂无已配置来源'"
                      :image="false"
                    />
                  </div>
                </div>
              </template>
            </div>
          </a-spin>
        </div>

        <div style="margin-top:20px">
          <div style="font-size:12px; color:#8c8c8c; margin-bottom:8px">类型分组</div>
          <div style="display:flex; flex-wrap:wrap; gap:8px">
            <a-tag
              :color="!filterType ? 'blue' : 'default'"
              style="cursor:pointer"
              @click="clearTypeFilter"
            >
              全部类型
            </a-tag>
            <a-tag
              v-for="item in mediaTypeGroups"
              :key="item.value"
              :color="filterType === item.value ? 'blue' : 'default'"
              style="cursor:pointer"
              @click="selectType(item)"
            >
              {{ item.label }} · {{ item.count }}
            </a-tag>
          </div>
        </div>
      </a-card>
    </div>

    <div style="flex:1; min-width:0">
      <a-card>
        <div style="display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; margin-bottom:16px">
          <div style="display:flex; flex-direction:column; gap:12px; flex:1; min-width:320px">
            <a-space wrap>
              <a-tag color="blue">{{ currentLibraryTitle }}</a-tag>
              <a-tag v-if="externalBrowseActive">路径：{{ externalBrowsePath }}</a-tag>
              <a-tag v-if="filterFolderPath">目录：{{ filterFolderPath }}</a-tag>
              <a-tag v-if="filterType">类型：{{ mediaTypeLabel(filterType) }}</a-tag>
              <a-tag v-if="filterStatus">状态：{{ statusLabel(filterStatus) }}</a-tag>
              <a-tag v-if="keyword">搜索：{{ keyword }}</a-tag>
            </a-space>
            <a-space wrap>
              <a-select v-model:value="filterStatus" style="width:140px" @change="onFilterChanged" allow-clear placeholder="状态筛选">
                <a-select-option value="UPLOADED">已上传</a-select-option>
                <a-select-option value="PROCESSING">处理中</a-select-option>
                <a-select-option value="READY">可用</a-select-option>
                <a-select-option value="FAILED">失败</a-select-option>
              </a-select>
              <a-select v-model:value="filterType" style="width:120px" @change="onFilterChanged" allow-clear placeholder="类型">
                <a-select-option value="IMAGE">图片</a-select-option>
                <a-select-option value="VIDEO">视频</a-select-option>
                <a-select-option value="AUDIO">音频</a-select-option>
              </a-select>
              <a-button @click="resetFilters">重置筛选</a-button>
            </a-space>
          </div>

          <a-space wrap>
            <a-button @click="openMediaSourceDrawer">外部媒体源</a-button>
            <a-button type="primary" @click="uploadDrawerOpen = true"><upload-outlined /> 上传到媒体库</a-button>
          </a-space>
        </div>

        <a-alert style="margin-bottom:16px" type="info" show-icon>
          <template #message>{{ externalBrowseActive ? '当前为外部媒体目录视图' : '当前为系统媒体库视图' }}</template>
          <template #description>
            {{ externalBrowseActive
              ? '外部媒体通过服务端代理预览，不默认导入系统内；相册和分发仍继续使用系统媒体库。'
              : '自行上传和已缓存到系统内的媒体会显示在这里，可继续处理、审核和加入相册。' }}
          </template>
        </a-alert>

        <a-table
          :data-source="mediaList"
          :columns="columns"
          :row-key="resolveMediaRowKey"
          :loading="loading"
          :pagination="externalBrowseActive
            ? { total, current: externalBrowsePage, pageSize: externalBrowsePageSize, onChange: onTablePageChange, showLessItems: true }
            : { total, current: page, pageSize, onChange: onTablePageChange }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'preview'">
              <div
                style="width:60px; height:60px; display:flex; align-items:center; justify-content:center; background:#fafafa; border-radius:8px; overflow:hidden; cursor:pointer; position:relative"
                @click="viewDetail(record)"
              >
                <template v-if="record.status === 'FAILED'">
                  <div style="font-size:12px; color:#ff4d4f; text-align:center; line-height:1.4">处理失败</div>
                </template>
                <template v-else-if="isProcessingPreview(record)">
                  <div style="text-align:center; line-height:1.4">
                    <a-spin size="small" />
                    <div style="font-size:12px; color:#8c8c8c; margin-top:6px">处理中</div>
                  </div>
                </template>
                <SecureImage
                  v-else-if="record.thumbnailUrl"
                  :src="record.thumbnailUrl"
                  alt="thumbnail"
                  img-style="width:100%; height:100%; object-fit:cover"
                />
                <SecureImage
                  v-else-if="record.mediaType === 'IMAGE' && record.url"
                  :src="record.url"
                  alt="image"
                  img-style="width:100%; height:100%; object-fit:cover"
                />
                <video-camera-outlined v-else-if="record.mediaType === 'VIDEO'" style="font-size:32px; color:#8c8c8c" />
                <customer-service-outlined v-else-if="record.mediaType === 'AUDIO'" style="font-size:32px; color:#8c8c8c" />
                <file-outlined v-else style="font-size:28px; color:#8c8c8c" />
              </div>
            </template>
            <template v-if="column.key === 'name'">
              <div>{{ record.fileName }}</div>
              <div style="color:#8c8c8c; font-size:12px">{{ formatSize(record.fileSize) }}</div>
              <div v-if="record.status === 'FAILED' && record.errorMessage" style="color:#ff4d4f; font-size:12px; margin-top:4px">
                {{ record.errorMessage }}
              </div>
              <div v-else-if="isProcessingPreview(record)" style="color:#8c8c8c; font-size:12px; margin-top:4px">
                正在生成预览和元数据，请稍后自动刷新
              </div>
            </template>
            <template v-if="column.key === 'source'">
              <div>{{ record.sourceName || sourceTypeLabel(record.sourceType) }}</div>
              <div style="color:#8c8c8c; font-size:12px">{{ sourceTypeLabel(record.sourceType) }}</div>
            </template>
            <template v-if="column.key === 'folderPath'">
              <div>{{ record.folderPath || '-' }}</div>
              <div style="color:#8c8c8c; font-size:12px">{{ ingestModeLabel(record.ingestMode) }}</div>
            </template>
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
            </template>
            <template v-if="column.key === 'reviewStatus'">
              <a-tag :color="reviewStatusColor(record.reviewStatus, record.status)">
                {{ reviewStatusLabel(record.reviewStatus, record.status) }}
              </a-tag>
            </template>
            <template v-if="column.key === 'createdAt'">
              {{ record.createdAt ? record.createdAt.slice(0, 16) : '-' }}
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="viewDetail(record)">详情</a-button>
                <a-popconfirm v-if="record.id" title="确认删除该媒体文件？" @confirm="deleteMedia(record.id)">
                  <a-button type="link" danger size="small">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>

    <a-drawer v-model:open="mediaSourceDrawerOpen" title="外部媒体源" width="760">
      <template #extra>
        <a-space>
          <a-button @click="loadMediaSources" :loading="mediaSourceLoading">刷新</a-button>
          <a-button type="primary" @click="openCreateMediaSource">新增媒体源</a-button>
        </a-space>
      </template>

      <a-alert style="margin-bottom:16px" type="info" show-icon>
        <template #message>统一来源入口</template>
        <template #description>
          上传媒体会作为“自行上传”来源展示；外部媒体源先选择类型，再配置连接并绑定目录。当前已打通 SMB，FTP / SFTP / WebDAV 入口已预留。
        </template>
      </a-alert>

      <a-spin :spinning="mediaSourceLoading">
        <div style="display:flex; flex-direction:column; gap:12px">
          <a-card v-for="source in mediaSources" :key="source.id" size="small">
            <div style="display:flex; justify-content:space-between; gap:16px; align-items:flex-start; flex-wrap:wrap">
              <div style="flex:1; min-width:280px">
                <div style="display:flex; align-items:center; gap:8px; flex-wrap:wrap">
                  <span style="font-size:16px; font-weight:600">{{ source.name }}</span>
                  <a-tag color="blue">{{ sourceTypeLabel(source.sourceType) }}</a-tag>
                  <a-tag :color="source.enabled ? 'green' : 'default'">{{ source.enabled ? '已启用' : '已停用' }}</a-tag>
                </div>
                <div style="margin-top:10px; color:#595959; line-height:1.8">
                  <div>{{ sourceConnectionSummary(source) }}</div>
                  <div>根目录：{{ normalizePath(source.config?.rootPath || source.configSummary?.rootPath || '/') }}</div>
                  <div>绑定目录：{{ source.boundPathName || source.boundPath || '-' }}</div>
                  <div>密码：{{ source.passwordConfigured ? '已配置' : '未配置' }}</div>
                  <div>更新时间：{{ formatDateTime(source.updatedAt) }}</div>
                </div>
              </div>
              <a-space wrap>
                <a-button @click="openBrowseSource(source)">查看目录</a-button>
                <a-button @click="openEditMediaSource(source)">编辑</a-button>
                <a-popconfirm title="确认删除该媒体源？" @confirm="removeMediaSource(source)">
                  <a-button danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </div>
          </a-card>

          <a-empty v-if="!mediaSources.length" description="暂无外部媒体源，先新增一个来源" />
        </div>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="mediaSourceModalOpen"
      :title="editingMediaSourceId ? '编辑媒体源' : '新增媒体源'"
      :confirm-loading="mediaSourceSaving"
      :ok-button-props="{ disabled: !currentSourceTypeImplemented }"
      destroy-on-close
      @ok="submitMediaSource"
      @cancel="closeMediaSourceModal"
    >
      <a-form layout="vertical">
        <a-form-item label="媒体源类型" required>
          <a-select v-model:value="mediaSourceForm.sourceType" :disabled="!!editingMediaSourceId" @change="onMediaSourceTypeChanged">
            <a-select-option v-for="item in externalSourceTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-alert v-if="!currentSourceTypeImplemented" type="warning" show-icon style="margin-bottom:16px">
          <template #message>{{ sourceTypeLabel(mediaSourceForm.sourceType) }} 入口已预留</template>
          <template #description>
            当前版本先完整打通 SMB；{{ sourceTypeLabel(mediaSourceForm.sourceType) }} 的连接校验、目录浏览和服务端代理预览链路将在同一套交互下继续接入。
          </template>
        </a-alert>

        <a-form-item label="媒体源名称" required>
          <a-input v-model:value="mediaSourceForm.name" placeholder="例如：家用 NAS" />
        </a-form-item>

        <template v-if="mediaSourceForm.sourceType === 'SMB'">
          <a-form-item label="主机地址" required>
            <a-input v-model:value="mediaSourceForm.config.host" placeholder="例如：192.168.1.10" />
          </a-form-item>
          <a-form-item label="端口">
            <a-input-number v-model:value="mediaSourceForm.config.port" :min="1" :max="65535" style="width:100%" />
          </a-form-item>
          <a-form-item label="共享名称" required>
            <a-input v-model:value="mediaSourceForm.config.shareName" placeholder="例如：media" />
          </a-form-item>
          <a-form-item label="根目录">
            <a-input v-model:value="mediaSourceForm.config.rootPath" placeholder="默认为 /" />
          </a-form-item>
          <a-form-item :label="editingMediaSourceId ? '用户名（留空则保持不变）' : '用户名'" :required="!editingMediaSourceId">
            <a-input v-model:value="mediaSourceForm.credentials.username" placeholder="请输入用户名" />
          </a-form-item>
          <a-form-item :label="editingMediaSourceId ? '密码（留空则保持不变）' : '密码'" :required="!editingMediaSourceId">
            <a-input-password v-model:value="mediaSourceForm.credentials.password" placeholder="请输入密码" />
          </a-form-item>
        </template>

        <a-form-item label="绑定目录" required>
          <div style="display:flex; flex-direction:column; gap:12px">
            <a-alert type="info" show-icon>
              <template #message>{{ mediaSourceForm.boundPathName || '未选择目录' }}</template>
              <template #description>{{ mediaSourceForm.boundPath || '-' }}</template>
            </a-alert>
            <a-space wrap>
              <a-button @click="openDraftBrowse" :disabled="!currentSourceTypeImplemented">浏览并选择目录</a-button>
              <a-button @click="resetBoundPath" :disabled="!currentSourceTypeImplemented">重置为根目录</a-button>
            </a-space>
            <a-input
              v-model:value="mediaSourceForm.boundPathName"
              placeholder="目录显示名称，留空则自动使用目录名"
            />
            <div v-if="editingMediaSourceId" style="font-size:12px; color:#8c8c8c">
              如需重新测试连接或改绑目录，请填写当前可用的账号密码后再浏览目录；直接保存则沿用已存储凭证。
            </div>
          </div>
        </a-form-item>

        <a-form-item label="启用状态">
          <a-switch v-model:checked="mediaSourceForm.enabled" checked-children="启用" un-checked-children="停用" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="browseModalOpen"
      :title="browseModalTitle"
      :footer="null"
      :width="960"
      destroy-on-close
      @cancel="closeBrowseModal"
    >
      <div style="display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap; margin-bottom:16px">
        <div>
          <div style="font-size:16px; font-weight:600">{{ browseDisplayName }}</div>
          <div style="color:#8c8c8c; margin-top:4px">{{ browseDisplaySummary }}</div>
        </div>
        <a-space wrap>
          <a-button @click="navigateBrowseUp" :disabled="!canBrowseUp">上级目录</a-button>
          <a-button @click="refreshBrowse" :loading="browseLoading">刷新</a-button>
          <a-button
            v-if="browseSelectionMode === 'bind'"
            type="primary"
            @click="applyBoundPathFromBrowse"
          >
            绑定当前目录
          </a-button>
          <a-button
            v-else
            type="primary"
            @click="applyExternalBrowseToLibrary"
          >
            在媒体管理中打开当前目录
          </a-button>
        </a-space>
      </div>

      <a-alert style="margin-bottom:12px" type="info" show-icon>
        <template #message>当前路径</template>
        <template #description>{{ browseCurrentPath }}</template>
      </a-alert>

      <a-alert v-if="browseSelectionMode !== 'bind'" style="margin-bottom:12px" type="info" show-icon>
        <template #message>外部内容访问方式</template>
        <template #description>当前目录中的媒体文件会通过服务端代理预览，不会默认导入系统媒体库。</template>
      </a-alert>

      <a-alert v-if="browseSelectionMode !== 'bind' && browseBoundPath" style="margin-bottom:16px" type="success" show-icon>
        <template #message>已绑定目录</template>
        <template #description>{{ browseBoundPathName || browseBoundPath }}</template>
      </a-alert>

      <a-table
        :data-source="browseItems"
        :columns="browseColumns"
        row-key="path"
        size="small"
        :loading="browseLoading"
        :pagination="false"
        :scroll="{ y: 420 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div style="display:flex; align-items:center; gap:8px; min-width:0">
              <a-tag :color="record.directory ? 'blue' : 'default'">{{ record.directory ? '目录' : mediaTypeLabel(record.mediaType) }}</a-tag>
              <a-button v-if="record.directory" type="link" size="small" style="padding:0" @click="enterBrowseDirectory(record)">
                {{ record.name }}
              </a-button>
              <span v-else>{{ record.name }}</span>
            </div>
          </template>
          <template v-if="column.key === 'type'">
            {{ record.directory ? '目录' : (record.contentType || '-') }}
          </template>
          <template v-if="column.key === 'size'">
            {{ record.directory ? '-' : formatSize(record.size) }}
          </template>
          <template v-if="column.key === 'modifiedAt'">
            {{ formatDateTime(record.modifiedAt) }}
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button v-if="record.directory" type="link" size="small" @click="enterBrowseDirectory(record)">进入</a-button>
              <a-button
                v-else-if="browseSelectionMode !== 'bind'"
                type="link"
                size="small"
                @click="viewDetail(record)"
              >
                预览
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-modal>

    <a-drawer v-model:open="uploadDrawerOpen" title="上传媒体文件" width="480" :destroy-on-close="true">
      <a-steps :current="uploadStep" size="small" style="margin-bottom:24px">
        <a-step title="选择文件" />
        <a-step title="上传中" />
        <a-step title="完成" />
      </a-steps>

      <div v-if="uploadStep === 0">
        <a-upload-dragger
          :before-upload="onFileSelected"
          :show-upload-list="false"
          accept="image/*,video/*,audio/*"
        >
          <p class="ant-upload-drag-icon"><inbox-outlined /></p>
          <p class="ant-upload-text">点击或拖拽文件到此区域</p>
          <p class="ant-upload-hint">支持图片、视频和音频文件</p>
        </a-upload-dragger>
        <div v-if="selectedFile" style="margin-top:16px">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="文件名">{{ selectedFile.name }}</a-descriptions-item>
            <a-descriptions-item label="大小">{{ formatSize(selectedFile.size) }}</a-descriptions-item>
            <a-descriptions-item label="类型">{{ selectedFile.type || '-' }}</a-descriptions-item>
            <a-descriptions-item label="来源">自行上传</a-descriptions-item>
          </a-descriptions>

          <div v-if="selectedPreviewUrl" style="margin-top:16px">
            <div style="margin-bottom:8px; color:#8c8c8c">本地预览</div>
            <div style="background:#fafafa; border-radius:8px; padding:12px; text-align:center">
              <img
                v-if="selectedPreviewType === 'IMAGE'"
                :src="selectedPreviewUrl"
                alt="preview"
                style="max-width:100%; max-height:220px; object-fit:contain"
              />
              <video
                v-else-if="selectedPreviewType === 'VIDEO'"
                :src="selectedPreviewUrl"
                controls
                style="width:100%; max-height:220px"
              />
              <audio
                v-else-if="selectedPreviewType === 'AUDIO'"
                :src="selectedPreviewUrl"
                controls
                style="width:100%"
              />
              <div v-else style="color:#8c8c8c">当前文件类型暂不支持预览</div>
            </div>
          </div>

          <a-button type="primary" block style="margin-top:16px" :loading="uploading" @click="startUpload">
            开始上传
          </a-button>
        </div>
      </div>

      <div v-if="uploadStep === 1" style="text-align:center; padding:40px 0">
        <a-progress type="circle" :percent="uploadProgress" />
        <div style="margin-top:16px; color:#8c8c8c">{{ uploadStatusText }}</div>
      </div>

      <div v-if="uploadStep === 2" style="text-align:center; padding:40px 0">
        <check-circle-outlined style="font-size:64px; color:#52c41a" />
        <div style="margin-top:16px; font-size:16px">上传成功！</div>
        <div style="color:#8c8c8c; margin-top:8px">文件已提交处理，处理成功后会进入“自行上传”来源</div>
        <a-button type="primary" style="margin-top:24px" @click="resetUpload">继续上传</a-button>
      </div>
    </a-drawer>

    <a-modal v-model:open="detailModalOpen" title="媒体详情" :footer="null" :width="620"
             :body-style="{ maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' }">
      <template v-if="selectedMedia">
        <div style="margin-bottom:16px; background:#fafafa; border-radius:8px; padding:16px; text-align:center">
          <SecureImage
            v-if="selectedMedia.mediaType === 'IMAGE' && selectedMedia.url"
            :src="selectedMedia.url"
            alt="media"
            img-style="max-width:100%; max-height:320px; object-fit:contain"
          />
          <div v-else-if="selectedMedia.mediaType === 'VIDEO' && selectedMediaResolvedUrl">
            <SecureImage
              v-if="selectedMedia.thumbnailUrl"
              :src="selectedMedia.thumbnailUrl"
              alt="video thumbnail"
              img-style="width:180px; max-width:100%; margin-bottom:16px"
            />
            <video
              :src="selectedMediaResolvedUrl"
              controls
              style="width:100%; max-height:320px"
            />
          </div>
          <div v-else-if="selectedMedia.mediaType === 'AUDIO' && selectedMediaResolvedUrl">
            <SecureImage
              v-if="selectedMedia.thumbnailUrl"
              :src="selectedMedia.thumbnailUrl"
              alt="audio thumbnail"
              img-style="width:180px; max-width:100%; margin-bottom:16px"
            />
            <audio :src="selectedMediaResolvedUrl" controls style="width:100%" />
          </div>
          <div v-else style="color:#8c8c8c">当前媒体暂无可在线预览内容</div>
        </div>

        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item v-if="!selectedMediaIsExternal" label="ID">{{ selectedMedia.id }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ selectedMedia.mediaType }}</a-descriptions-item>
          <a-descriptions-item label="来源">{{ selectedMedia.sourceName || sourceTypeLabel(selectedMedia.sourceType) }}</a-descriptions-item>
          <a-descriptions-item label="来源类型">{{ sourceTypeLabel(selectedMedia.sourceType) }}</a-descriptions-item>
          <a-descriptions-item label="目录">{{ selectedMedia.folderPath || '-' }}</a-descriptions-item>
          <a-descriptions-item label="导入方式">{{ ingestModeLabel(selectedMedia.ingestMode) }}</a-descriptions-item>
          <a-descriptions-item label="原始文件名" :span="2">{{ selectedMedia.fileName }}</a-descriptions-item>
          <a-descriptions-item label="文件大小">{{ formatSize(selectedMedia.fileSize) }}</a-descriptions-item>
          <a-descriptions-item label="处理状态">
            <a-tag :color="statusColor(selectedMedia.status)">{{ statusLabel(selectedMedia.status) }}</a-tag>
            <a-button
              v-if="selectedMedia.id && selectedMedia.status !== 'PROCESSING'"
              type="link"
              size="small"
              :loading="reprocessingMediaId === selectedMedia.id"
              @click="reprocessMedia(selectedMedia)"
            >
              重新处理
            </a-button>
          </a-descriptions-item>
          <a-descriptions-item v-if="!selectedMediaIsExternal" label="审核状态">
            <a-tag :color="reviewStatusColor(selectedMedia.reviewStatus, selectedMedia.status)">
              {{ reviewStatusLabel(selectedMedia.reviewStatus, selectedMedia.status) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item v-if="!selectedMediaIsExternal" label="审核时间">
            {{ selectedMedia.reviewedAt ? selectedMedia.reviewedAt.slice(0, 16) : '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="分辨率">
            {{ formatResolution(selectedMedia.width, selectedMedia.height) }}
          </a-descriptions-item>
          <a-descriptions-item label="时长">
            {{ formatDuration(selectedMedia.durationSec) }}
          </a-descriptions-item>
          <a-descriptions-item v-if="!selectedMediaIsExternal" label="驳回原因" :span="2">
            {{ selectedMedia.reviewRejectReason || '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="MIME 类型" :span="2">{{ selectedMedia.contentType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="来源定位" :span="2">{{ selectedMedia.originUri || '-' }}</a-descriptions-item>
          <a-descriptions-item label="文件 URL" :span="2">
            <a :href="selectedMediaResolvedUrl" target="_blank" v-if="selectedMediaResolvedUrl">查看文件</a>
            <span v-else>-</span>
          </a-descriptions-item>
          <a-descriptions-item v-if="!selectedMediaIsExternal" label="创建时间" :span="2">{{ selectedMedia.createdAt }}</a-descriptions-item>
          <a-descriptions-item v-else label="访问方式" :span="2">服务端代理访问</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import {
  UploadOutlined,
  InboxOutlined,
  CheckCircleOutlined,
  VideoCameraOutlined,
  CustomerServiceOutlined,
  FileOutlined
} from '@ant-design/icons-vue'
import { mediaApi } from '@/api/media'
import { mediaSourceApi } from '@/api/media-source'
import SecureImage from '@/components/SecureImage.vue'
import { useSecureObjectUrl } from '@/components/useSecureObjectUrl'

const EXTERNAL_SOURCE_TYPE_OPTIONS = [
  { value: 'SMB', label: 'SMB', implemented: true, defaultPort: 445 },
  { value: 'FTP', label: 'FTP', implemented: true, defaultPort: 21 },
  { value: 'SFTP', label: 'SFTP', implemented: true, defaultPort: 22 },
  { value: 'WEBDAV', label: 'WebDAV', implemented: true, defaultPort: 80 }
]

const SOURCE_TYPE_ORDER = ['UPLOAD', 'SMB', 'FTP', 'SFTP', 'WEBDAV']

const mediaList = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const loading = ref(false)
const groupLoading = ref(false)
const filterStatus = ref(undefined)
const filterType = ref(undefined)
const filterSourceType = ref(undefined)
const filterSourceId = ref(undefined)
const filterFolderPath = ref(undefined)
const keywordInput = ref('')
const keyword = ref(undefined)
const groups = ref({ sourceGroups: [], mediaTypeGroups: [] })

const INTERNAL_LIBRARY_MODE = 'internal'
const EXTERNAL_LIBRARY_MODE = 'external'
const libraryMode = ref(INTERNAL_LIBRARY_MODE)

const mediaSourceDrawerOpen = ref(false)
const mediaSourceLoading = ref(false)
const mediaSourceSaving = ref(false)
const mediaSources = ref([])
const mediaSourceModalOpen = ref(false)
const editingMediaSourceId = ref(null)
const mediaSourceForm = ref(createMediaSourceForm())

const browseModalOpen = ref(false)
const browseLoading = ref(false)
const browseMode = ref('saved')
const browseSelectionMode = ref('bind')
const browseMediaSource = ref(null)
const browseCurrentPath = ref('/')
const browseRootPath = ref('/')
const browseBoundPath = ref('')
const browseBoundPathName = ref('')
const browseItems = ref([])

const externalBrowseSource = ref(null)
const externalBrowsePath = ref('')
const externalBrowsePage = ref(1)
const externalBrowsePageSize = 12

const uploadDrawerOpen = ref(false)
const uploadStep = ref(0)
const selectedFile = ref(null)
const selectedPreviewUrl = ref('')
const selectedPreviewType = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatusText = ref('')

const detailModalOpen = ref(false)
const selectedMedia = ref(null)
const selectedMediaUrl = computed(() => selectedMedia.value?.url || '')
const { resolvedSrc: selectedMediaResolvedUrl } = useSecureObjectUrl(selectedMediaUrl)
const reprocessingMediaId = ref(null)
let statusRefreshTimer = null

const externalSourceTypeOptions = EXTERNAL_SOURCE_TYPE_OPTIONS
const sourceGroups = computed(() => groups.value?.sourceGroups || [])
const mediaTypeGroups = computed(() => groups.value?.mediaTypeGroups || [])
const totalMediaCount = computed(() => sourceGroups.value.reduce((sum, item) => sum + (item.mediaCount || 0), 0))
const currentSourceTypeImplemented = computed(() => isImplementedSourceType(mediaSourceForm.value.sourceType))
const selectedSourceRecord = computed(() => findSelectedSource())
const externalBrowseActive = computed(() => libraryMode.value === EXTERNAL_LIBRARY_MODE && !!(externalBrowseSource.value?.id ?? externalBrowseSource.value?.sourceId))
const currentLibraryTitle = computed(() => {
  if (externalBrowseActive.value) {
    return `${externalBrowseSource.value?.sourceName || sourceTypeLabel(externalBrowseSource.value?.sourceType)} / 外部目录`
  }
  if (filterFolderPath.value) {
    return `${selectedSourceRecord.value?.sourceName || sourceTypeLabel(filterSourceType.value)} / ${filterFolderPath.value}`
  }
  if (selectedSourceRecord.value?.sourceId !== undefined && selectedSourceRecord.value?.sourceId !== null) {
    return selectedSourceRecord.value.sourceName || sourceTypeLabel(selectedSourceRecord.value.sourceType)
  }
  if (filterSourceType.value) {
    return sourceTypeLabel(filterSourceType.value)
  }
  return '全部媒体'
})
const canBrowseUp = computed(() => normalizePath(browseCurrentPath.value) !== normalizePath(browseRootPath.value || '/'))
const browseModalTitle = computed(() => browseSelectionMode.value === 'bind' ? '浏览并绑定目录' : '浏览来源目录')
const selectedMediaIsExternal = computed(() => !selectedMedia.value?.id || selectedMedia.value?.ingestMode === 'LINKED')
const browseDisplayName = computed(() => {
  if (browseMode.value === 'draft') {
    return mediaSourceForm.value.name?.trim() || `未保存的${sourceTypeLabel(mediaSourceForm.value.sourceType)}来源`
  }
  return browseMediaSource.value?.name || '-'
})
const browseDisplaySummary = computed(() => {
  if (browseMode.value === 'draft') {
    return draftSourceSummary(mediaSourceForm.value)
  }
  return browseMediaSource.value ? sourceConnectionSummary(browseMediaSource.value) : '-'
})

const sourceTypeSections = computed(() => {
  const sectionMap = new Map()
  for (const type of SOURCE_TYPE_ORDER) {
    sectionMap.set(type, {
      sourceType: type,
      mediaCount: 0,
      sourceCount: 0,
      sources: []
    })
  }

  const sourceMap = new Map()

  const ensureSection = sourceType => {
    const normalizedType = sourceType || 'UPLOAD'
    if (!sectionMap.has(normalizedType)) {
      sectionMap.set(normalizedType, {
        sourceType: normalizedType,
        mediaCount: 0,
        sourceCount: 0,
        sources: []
      })
    }
    return sectionMap.get(normalizedType)
  }

  const ensureSource = source => {
    const normalizedType = source?.sourceType || 'UPLOAD'
    const normalizedSourceId = source?.sourceId ?? source?.id ?? null
    const key = sourceGroupKey({ sourceType: normalizedType, sourceId: normalizedSourceId, sourceName: source?.sourceName || source?.name })
    if (!sourceMap.has(key)) {
      const base = {
        id: source?.id ?? normalizedSourceId,
        sourceType: normalizedType,
        sourceId: normalizedSourceId,
        sourceName: source?.sourceName || source?.name || (normalizedType === 'UPLOAD' ? '自行上传' : sourceTypeLabel(normalizedType)),
        mediaCount: 0,
        folders: [],
        boundPath: source?.boundPath || source?.config?.rootPath || source?.configSummary?.rootPath || '/',
        boundPathName: source?.boundPathName || '',
        enabled: source?.enabled,
        config: source?.config || source?.configSummary || {},
        configSummary: source?.configSummary || source?.config || {},
        passwordConfigured: source?.passwordConfigured || false
      }
      sourceMap.set(key, base)
      ensureSection(normalizedType).sources.push(base)
    }
    const target = sourceMap.get(key)
    if (source?.id !== undefined && source?.id !== null) target.id = source.id
    if (source?.sourceId !== undefined && source?.sourceId !== null) target.sourceId = source.sourceId
    if (source?.name && !source?.sourceName) target.sourceName = source.name
    if (source?.sourceName) target.sourceName = source.sourceName
    if (source?.boundPath) target.boundPath = source.boundPath
    if (source?.boundPathName) target.boundPathName = source.boundPathName
    if (source?.config) target.config = source.config
    if (source?.configSummary) target.configSummary = source.configSummary
    if (source?.enabled !== undefined) target.enabled = source.enabled
    if (source?.passwordConfigured !== undefined) target.passwordConfigured = source.passwordConfigured
    return target
  }

  ensureSource({ sourceType: 'UPLOAD', sourceId: null, sourceName: '自行上传', boundPath: '/上传', boundPathName: '上传' })

  for (const source of mediaSources.value) {
    ensureSource(source)
  }

  for (const group of sourceGroups.value) {
    const target = ensureSource(group)
    target.mediaCount = group.mediaCount || 0
    target.folders = Array.isArray(group.folders) ? group.folders : []
    ensureSection(group.sourceType).mediaCount += group.mediaCount || 0
  }

  for (const section of sectionMap.values()) {
    if (section.sourceType !== 'UPLOAD' && section.mediaCount === 0) {
      section.mediaCount = section.sources.reduce((sum, source) => sum + (source.mediaCount || 0), 0)
    }
    section.sourceCount = section.sources.length
    section.sources.sort((a, b) => {
      if ((a.sourceId ?? null) === (b.sourceId ?? null) && (a.sourceName || '') === (b.sourceName || '')) {
        return 0
      }
      if ((a.sourceType || '') === 'UPLOAD') {
        return -1
      }
      if ((b.sourceType || '') === 'UPLOAD') {
        return 1
      }
      return String(a.sourceName || '').localeCompare(String(b.sourceName || ''), 'zh-CN')
    })
  }

  return SOURCE_TYPE_ORDER
    .map(type => sectionMap.get(type))
    .filter(section => section && (section.sourceType === 'UPLOAD' || section.sources.length || section.mediaCount > 0))
})

const columns = computed(() => {
  if (externalBrowseActive.value) {
    return [
      { title: '预览', key: 'preview', width: 80 },
      { title: '文件名', key: 'name' },
      { title: '来源', key: 'source', width: 140 },
      { title: '目录', key: 'folderPath', width: 180 },
      { title: '类型', dataIndex: 'mediaType', width: 80 },
      { title: '处理状态', key: 'status', width: 110 },
      { title: '操作', key: 'action', width: 100 }
    ]
  }

  return [
    { title: '预览', key: 'preview', width: 80 },
    { title: '文件名', key: 'name' },
    { title: '来源', key: 'source', width: 140 },
    { title: '目录', key: 'folderPath', width: 180 },
    { title: '类型', dataIndex: 'mediaType', width: 80 },
    { title: '处理状态', key: 'status', width: 110 },
    { title: '审核状态', key: 'reviewStatus', width: 110 },
    { title: '创建时间', key: 'createdAt', width: 140 },
    { title: '操作', key: 'action', width: 130 }
  ]
})

const browseColumns = [
  { title: '名称', key: 'name' },
  { title: '类型', key: 'type', width: 220 },
  { title: '大小', key: 'size', width: 120 },
  { title: '修改时间', key: 'modifiedAt', width: 180 },
  { title: '操作', key: 'action', width: 140 }
]

onMounted(async () => {
  await Promise.all([loadGroups(), load(), loadMediaSources()])
  statusRefreshTimer = window.setInterval(refreshPendingStatuses, 5000)
})

onBeforeUnmount(() => {
  clearSelectedPreview()
  if (statusRefreshTimer) {
    clearInterval(statusRefreshTimer)
    statusRefreshTimer = null
  }
})

function createMediaSourceForm(sourceType = 'SMB') {
  return {
    sourceType,
    name: '',
    config: createDefaultSourceConfig(sourceType),
    credentials: {
      username: '',
      password: ''
    },
    enabled: true,
    boundPath: '/',
    boundPathName: ''
  }
}

function createDefaultSourceConfig(sourceType) {
  const option = EXTERNAL_SOURCE_TYPE_OPTIONS.find(item => item.value === sourceType)
  return {
    host: '',
    port: option?.defaultPort || 445,
    shareName: '',
    rootPath: '/'
  }
}

function isImplementedSourceType(sourceType) {
  return EXTERNAL_SOURCE_TYPE_OPTIONS.some(item => item.value === sourceType && item.implemented)
}

async function refreshPendingStatuses() {
  const pendingRecords = mediaList.value.filter(item => item?.id && isProcessingPreview(item))
  if (!pendingRecords.length) {
    return
  }

  try {
    const responses = await Promise.all(pendingRecords.map(item => mediaApi.getStatus(item.id).catch(() => null)))
    const shouldReload = responses.some((res, index) => {
      const statusData = res?.data
      const record = pendingRecords[index]
      return statusData && (record.status !== statusData.mediaStatus || record.errorMessage !== statusData.errorMessage)
    })

    if (shouldReload) {
      await Promise.all([load(), loadGroups()])
    }
  } catch {
    // ignore background refresh failures
  }
}

function isProcessingPreview(record) {
  if (record?.ingestMode === 'LINKED') {
    return false
  }
  return record?.status === 'UPLOADED' || record?.status === 'PROCESSING'
}

async function reprocessMedia(record) {
  if (!record?.id) {
    return
  }

  reprocessingMediaId.value = record.id
  try {
    await mediaApi.process(record.id)
    message.success('已重新提交处理')
    await Promise.all([load(), loadGroups()])
    if (selectedMedia.value?.id === record.id) {
      selectedMedia.value = {
        ...selectedMedia.value,
        status: 'UPLOADED',
        errorMessage: null
      }
    }
  } finally {
    reprocessingMediaId.value = null
  }
}

async function load() {
  loading.value = true
  try {
    if (externalBrowseActive.value) {
      const sourceId = externalBrowseSource.value?.id ?? externalBrowseSource.value?.sourceId
      if (!sourceId) {
        mediaList.value = []
        total.value = 0
        return
      }
      const res = await mediaSourceApi.browse(sourceId, { path: externalBrowsePath.value || externalBrowseSource.value?.boundPath || '/' })
      const items = Array.isArray(res.data?.items) ? res.data.items : []
      const filteredItems = items
        .filter(item => !item.directory)
        .filter(item => !filterType.value || item.mediaType === filterType.value)
        .filter(item => !filterStatus.value || item.status === filterStatus.value)
        .filter(item => {
          if (!keyword.value) {
            return true
          }
          const text = `${item.fileName || ''} ${item.sourceName || ''} ${item.folderPath || ''}`.toLowerCase()
          return text.includes(String(keyword.value).toLowerCase())
        })
      total.value = filteredItems.length
      mediaList.value = paginateExternalItems(filteredItems)
      externalBrowsePath.value = normalizePath(res.data?.currentPath || externalBrowsePath.value || '/')
      return
    }

    const res = await mediaApi.list({
      page: page.value,
      size: pageSize,
      status: filterStatus.value || undefined,
      mediaType: filterType.value || undefined,
      sourceType: filterSourceType.value || undefined,
      sourceId: filterSourceId.value ?? undefined,
      folderPath: filterFolderPath.value || undefined,
      keyword: keyword.value || undefined
    })
    mediaList.value = res.data.list || []
    total.value = res.data.total || 0

    if (selectedMedia.value?.id) {
      const matched = mediaList.value.find(item => item.id === selectedMedia.value.id)
      if (matched) {
        selectedMedia.value = matched
      }
    }
  } finally {
    loading.value = false
  }
}

async function loadGroups() {
  groupLoading.value = true
  try {
    const res = await mediaApi.groups({ keyword: keyword.value || undefined })
    groups.value = {
      sourceGroups: res.data?.sourceGroups || [],
      mediaTypeGroups: res.data?.mediaTypeGroups || []
    }
  } finally {
    groupLoading.value = false
  }
}

async function loadMediaSources() {
  mediaSourceLoading.value = true
  try {
    const res = await mediaSourceApi.list()
    mediaSources.value = res.data || []
  } finally {
    mediaSourceLoading.value = false
  }
}

async function reloadLibrary() {
  await Promise.all([load(), loadGroups(), loadMediaSources()])
}

function exitExternalBrowseMode() {
  libraryMode.value = INTERNAL_LIBRARY_MODE
  externalBrowseSource.value = null
  externalBrowsePath.value = ''
  externalBrowsePage.value = 1
}

function applyExternalBrowseToLibrary() {
  if (!(browseMediaSource.value?.id ?? browseMediaSource.value?.sourceId)) {
    return
  }
  libraryMode.value = EXTERNAL_LIBRARY_MODE
  externalBrowseSource.value = browseMediaSource.value
  externalBrowsePath.value = normalizePath(browseCurrentPath.value)
  externalBrowsePage.value = 1
  page.value = 1
  closeBrowseModal()
  load()
}

function paginateExternalItems(items) {
  const start = Math.max(0, (externalBrowsePage.value - 1) * externalBrowsePageSize)
  return items.slice(start, start + externalBrowsePageSize)
}

async function openMediaSourceDrawer() {
  mediaSourceDrawerOpen.value = true
  if (!mediaSources.value.length) {
    await loadMediaSources()
  }
}

function openCreateMediaSource() {
  editingMediaSourceId.value = null
  mediaSourceForm.value = createMediaSourceForm('SMB')
  mediaSourceModalOpen.value = true
}

function openEditMediaSource(source) {
  const sourceType = source?.sourceType || 'SMB'
  editingMediaSourceId.value = source.id
  mediaSourceForm.value = {
    sourceType,
    name: source.name || '',
    config: {
      ...createDefaultSourceConfig(sourceType),
      ...(source.config || {})
    },
    credentials: {
      username: '',
      password: ''
    },
    enabled: !!source.enabled,
    boundPath: normalizePath(source.boundPath || source.config?.rootPath || '/'),
    boundPathName: source.boundPathName || ''
  }
  mediaSourceModalOpen.value = true
}

function closeMediaSourceModal() {
  mediaSourceModalOpen.value = false
  editingMediaSourceId.value = null
  mediaSourceForm.value = createMediaSourceForm('SMB')
}

function onMediaSourceTypeChanged(nextType) {
  mediaSourceForm.value = {
    ...createMediaSourceForm(nextType),
    name: mediaSourceForm.value.name?.trim() || ''
  }
}

function resetBoundPath() {
  mediaSourceForm.value.boundPath = normalizePath(mediaSourceForm.value.config?.rootPath || '/')
  mediaSourceForm.value.boundPathName = ''
}

function buildSourceConfigPayload() {
  if (mediaSourceForm.value.sourceType === 'SMB') {
    return {
      host: mediaSourceForm.value.config.host?.trim(),
      port: mediaSourceForm.value.config.port || 445,
      shareName: mediaSourceForm.value.config.shareName?.trim(),
      rootPath: normalizePath(mediaSourceForm.value.config.rootPath || '/')
    }
  }
  return {}
}

function buildSourceCredentialsPayload() {
  const credentials = {}
  const username = mediaSourceForm.value.credentials.username?.trim()
  const password = mediaSourceForm.value.credentials.password?.trim()
  if (username) {
    credentials.username = username
  }
  if (password) {
    credentials.password = password
  }
  return credentials
}

function validateMediaSourceForm({ requireCredentials, forBrowse = false } = {}) {
  if (!mediaSourceForm.value.name?.trim()) {
    message.warning('请填写媒体源名称')
    return false
  }

  if (!currentSourceTypeImplemented.value) {
    message.warning(`${sourceTypeLabel(mediaSourceForm.value.sourceType)} 暂未实现`)
    return false
  }

  const config = buildSourceConfigPayload()
  const credentials = buildSourceCredentialsPayload()

  if (mediaSourceForm.value.sourceType === 'SMB') {
    if (!config.host || !config.shareName) {
      message.warning('请完整填写 SMB 连接信息')
      return false
    }
    if (requireCredentials && (!credentials.username || !credentials.password)) {
      message.warning(forBrowse ? '浏览目录前请填写账号和密码' : '请填写用户名和密码')
      return false
    }
  }

  if (!forBrowse && !mediaSourceForm.value.boundPath) {
    message.warning('请先绑定目录')
    return false
  }

  return true
}

async function submitMediaSource() {
  const isEditing = !!editingMediaSourceId.value
  if (!validateMediaSourceForm({ requireCredentials: !isEditing })) {
    return
  }

  const payload = {
    name: mediaSourceForm.value.name?.trim(),
    sourceType: mediaSourceForm.value.sourceType,
    boundPath: normalizePath(mediaSourceForm.value.boundPath || mediaSourceForm.value.config?.rootPath || '/'),
    boundPathName: mediaSourceForm.value.boundPathName?.trim() || undefined,
    enabled: !!mediaSourceForm.value.enabled,
    config: buildSourceConfigPayload(),
    credentials: buildSourceCredentialsPayload()
  }

  mediaSourceSaving.value = true
  try {
    if (isEditing) {
      await mediaSourceApi.update(editingMediaSourceId.value, payload)
      message.success('媒体源已更新')
    } else {
      await mediaSourceApi.create(payload)
      message.success('媒体源已创建')
    }
    closeMediaSourceModal()
    await Promise.all([loadMediaSources(), loadGroups(), load()])
  } finally {
    mediaSourceSaving.value = false
  }
}

async function removeMediaSource(source) {
  await mediaSourceApi.remove(source.id)
  message.success('媒体源已删除')
  if (browseMediaSource.value?.id === source.id) {
    closeBrowseModal()
  }
  await Promise.all([loadMediaSources(), loadGroups(), load()])
}

async function openDraftBrowse() {
  const requireCredentials = !editingMediaSourceId.value || !!mediaSourceForm.value.credentials.username?.trim() || !!mediaSourceForm.value.credentials.password?.trim()
  if (!validateMediaSourceForm({ requireCredentials: true, forBrowse: true })) {
    return
  }

  browseMode.value = 'draft'
  browseSelectionMode.value = 'bind'
  browseMediaSource.value = null
  browseCurrentPath.value = normalizePath(mediaSourceForm.value.boundPath || mediaSourceForm.value.config?.rootPath || '/')
  browseRootPath.value = normalizePath(mediaSourceForm.value.config?.rootPath || '/')
  browseBoundPath.value = ''
  browseBoundPathName.value = ''
  browseModalOpen.value = true
  await loadBrowse(browseCurrentPath.value)
}

async function openBrowseSource(source, path = null) {
  if (!source?.id && !source?.sourceId) {
    return
  }
  browseMode.value = 'saved'
  browseSelectionMode.value = 'view'
  browseMediaSource.value = source
  browseCurrentPath.value = normalizePath(path || source.boundPath || source.config?.rootPath || '/')
  browseRootPath.value = normalizePath(source.boundPath || source.config?.rootPath || '/')
  browseBoundPath.value = normalizePath(source.boundPath || '')
  browseBoundPathName.value = source.boundPathName || ''
  browseModalOpen.value = true
  await loadBrowse(browseCurrentPath.value)
}

function closeBrowseModal() {
  browseModalOpen.value = false
  browseMode.value = 'saved'
  browseSelectionMode.value = 'view'
  browseMediaSource.value = null
  browseCurrentPath.value = '/'
  browseRootPath.value = '/'
  browseBoundPath.value = ''
  browseBoundPathName.value = ''
  browseItems.value = []
}

async function loadBrowse(path = browseCurrentPath.value) {
  browseLoading.value = true
  try {
    const targetPath = normalizePath(path)
    let res
    if (browseMode.value === 'draft') {
      res = await mediaSourceApi.browseDraft({
        sourceType: mediaSourceForm.value.sourceType,
        name: mediaSourceForm.value.name?.trim(),
        path: targetPath,
        config: buildSourceConfigPayload(),
        credentials: buildSourceCredentialsPayload()
      })
    } else if (browseMediaSource.value?.id || browseMediaSource.value?.sourceId) {
      res = await mediaSourceApi.browse(browseMediaSource.value.id ?? browseMediaSource.value.sourceId, { path: targetPath })
    } else {
      return
    }

    browseRootPath.value = normalizePath(res.data?.rootPath || browseRootPath.value || '/')
    browseCurrentPath.value = normalizePath(res.data?.currentPath || targetPath)
    browseBoundPath.value = res.data?.boundPath ? normalizePath(res.data.boundPath) : browseBoundPath.value
    browseBoundPathName.value = res.data?.boundPathName || browseBoundPathName.value
    browseItems.value = res.data?.items || []
  } finally {
    browseLoading.value = false
  }
}

async function refreshBrowse() {
  await loadBrowse(browseCurrentPath.value)
}

async function navigateBrowseUp() {
  const parentPath = resolveParentPath(browseCurrentPath.value, browseRootPath.value || '/')
  await loadBrowse(parentPath)
}

async function enterBrowseDirectory(record) {
  if (!record?.directory) {
    return
  }
  await loadBrowse(record.path)
}

function applyBoundPathFromBrowse() {
  mediaSourceForm.value.boundPath = normalizePath(browseCurrentPath.value)
  if (!mediaSourceForm.value.boundPathName?.trim()) {
    mediaSourceForm.value.boundPathName = pathDisplayName(browseCurrentPath.value)
  }
  message.success('已选择绑定目录')
  closeBrowseModal()
}

async function applyKeyword() {
  keyword.value = keywordInput.value?.trim() || undefined
  page.value = 1
  await Promise.all([load(), loadGroups(), loadMediaSources()])
}

async function onFilterChanged() {
  page.value = 1
  await load()
}

async function resetFilters() {
  filterStatus.value = undefined
  filterType.value = undefined
  filterSourceType.value = undefined
  filterSourceId.value = undefined
  filterFolderPath.value = undefined
  keywordInput.value = ''
  keyword.value = undefined
  page.value = 1
  exitExternalBrowseMode()
  await Promise.all([load(), loadGroups(), loadMediaSources()])
}

async function selectAllLibrary() {
  exitExternalBrowseMode()
  filterSourceType.value = undefined
  filterSourceId.value = undefined
  filterFolderPath.value = undefined
  page.value = 1
  await load()
}

async function selectSourceTypeSection(section) {
  exitExternalBrowseMode()
  filterSourceType.value = section?.sourceType || undefined
  filterSourceId.value = undefined
  filterFolderPath.value = undefined
  page.value = 1
  await load()
}

async function selectSource(source) {
  if (source?.sourceType !== 'UPLOAD' && source?.sourceId && source?.boundPath) {
    libraryMode.value = EXTERNAL_LIBRARY_MODE
    externalBrowseSource.value = source
    externalBrowsePath.value = normalizePath(source.boundPath)
    externalBrowsePage.value = 1
    filterSourceType.value = source.sourceType || undefined
    filterSourceId.value = source.sourceId ?? undefined
    filterFolderPath.value = undefined
    page.value = 1
    await load()
    return
  }

  exitExternalBrowseMode()
  filterSourceType.value = source.sourceType || undefined
  filterSourceId.value = source.sourceId ?? undefined
  filterFolderPath.value = undefined
  page.value = 1
  await load()
}

async function selectFolder(source, folder) {
  exitExternalBrowseMode()
  filterSourceType.value = source.sourceType || undefined
  filterSourceId.value = source.sourceId ?? undefined
  filterFolderPath.value = folder.folderPath || undefined
  page.value = 1
  await load()
}

async function selectType(item) {
  filterType.value = item.value || undefined
  page.value = 1
  await load()
}

async function clearTypeFilter() {
  filterType.value = undefined
  page.value = 1
  await load()
}

async function onTablePageChange(nextPage) {
  if (externalBrowseActive.value) {
    externalBrowsePage.value = nextPage
    await load()
    return
  }
  page.value = nextPage
  await load()
}

function findSelectedSource() {
  if (externalBrowseActive.value && externalBrowseSource.value) {
    return externalBrowseSource.value
  }
  if (!filterSourceType.value) {
    return null
  }
  for (const section of sourceTypeSections.value) {
    for (const source of section.sources || []) {
      if (sourceMatches(source, filterSourceType.value, filterSourceId.value)) {
        return source
      }
    }
  }
  return null
}

function sourceGroupKey(source) {
  return `${source?.sourceType || 'UNKNOWN'}#${source?.sourceId ?? 'default'}`
}

function sourceMatches(source, sourceType, sourceId) {
  return (source?.sourceType || undefined) === (sourceType || undefined)
    && (source?.sourceId ?? undefined) === (sourceId ?? undefined)
}

function allLibraryCardStyle() {
  const selected = !filterSourceType.value && !filterFolderPath.value
  return selected
    ? 'border:1px solid #1677ff; border-radius:8px; padding:10px 12px; background:#e6f4ff; cursor:pointer'
    : 'border:1px solid #f0f0f0; border-radius:8px; padding:10px 12px; background:#fff; cursor:pointer'
}

function sourceTypeCardStyle(typeGroup) {
  const selected = filterSourceType.value === typeGroup.sourceType && filterSourceId.value === undefined && !filterFolderPath.value
  return selected
    ? 'border:1px solid #1677ff; border-radius:8px; padding:10px 12px; background:#f0f8ff'
    : 'border:1px solid #f0f0f0; border-radius:8px; padding:10px 12px; background:#fff'
}

function sourceCardStyle(source) {
  const externalSelected = externalBrowseActive.value
    && source?.sourceId === externalBrowseSource.value?.id
    && source?.sourceType === externalBrowseSource.value?.sourceType
  const selected = externalSelected || (sourceMatches(source, filterSourceType.value, filterSourceId.value) && !filterFolderPath.value)
  return selected
    ? 'border:1px solid #91caff; border-radius:8px; padding:10px; background:#ffffff; cursor:pointer'
    : 'border:1px solid #f5f5f5; border-radius:8px; padding:10px; background:#fafafa; cursor:pointer'
}

function boundPathStyle(source) {
  return source.enabled === false
    ? 'display:flex; justify-content:space-between; gap:8px; border-radius:6px; padding:6px 8px; background:#fff7e6; color:#8c8c8c; cursor:pointer'
    : 'display:flex; justify-content:space-between; gap:8px; border-radius:6px; padding:6px 8px; background:#f6ffed; color:#389e0d; cursor:pointer'
}

function folderItemStyle(source, folder) {
  const selected = !externalBrowseActive.value
    && sourceMatches(source, filterSourceType.value, filterSourceId.value)
    && filterFolderPath.value === folder.folderPath
  return selected
    ? 'display:flex; justify-content:space-between; gap:8px; border-radius:6px; padding:6px 8px; background:#1677ff; color:#fff; cursor:pointer'
    : 'display:flex; justify-content:space-between; gap:8px; border-radius:6px; padding:6px 8px; background:#fff; cursor:pointer; border:1px solid #f0f0f0'
}

function sourceSummaryText(source) {
  if (source.sourceType === 'UPLOAD') {
    return '系统内上传来源'
  }
  const summary = source.configSummary || source.config || {}
  const host = summary.host || '-'
  const port = summary.port || '-'
  const shareName = summary.shareName || '-'
  return `${host}:${port} / ${shareName}`
}

function sourceConnectionSummary(source) {
  const summary = source.configSummary || source.config || {}
  const host = summary.host || '-'
  const port = summary.port || '-'
  const shareName = summary.shareName || '-'
  return `连接：${host}:${port} / ${shareName}`
}

function draftSourceSummary(form) {
  if (form.sourceType === 'SMB') {
    const host = form.config.host?.trim() || '-'
    const port = form.config.port || 445
    const shareName = form.config.shareName?.trim() || '-'
    return `SMB / ${host}:${port} / ${shareName}`
  }
  return sourceTypeLabel(form.sourceType)
}

function onFileSelected(file) {
  clearSelectedPreview()
  selectedFile.value = file
  selectedPreviewType.value = detectMediaType(file.type)
  if (selectedPreviewType.value === 'IMAGE' || selectedPreviewType.value === 'VIDEO' || selectedPreviewType.value === 'AUDIO') {
    selectedPreviewUrl.value = URL.createObjectURL(file)
  }
  return false
}

async function startUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  uploadStep.value = 1
  uploadProgress.value = 0
  uploadStatusText.value = '初始化上传...'

  try {
    const file = selectedFile.value
    const initRes = await mediaApi.initUpload({
      fileName: file.name,
      fileSize: file.size,
      contentType: file.type || 'application/octet-stream'
    })
    const { uploadId, partSize, totalParts } = initRes.data
    const parts = []

    uploadStatusText.value = `上传分片 (0/${totalParts})...`

    for (let i = 0; i < totalParts; i++) {
      const start = i * partSize
      const end = Math.min(start + partSize, file.size)
      const chunk = file.slice(start, end)
      const partNumber = i + 1
      const uploadRes = await mediaApi.uploadPart(uploadId, partNumber, chunk, file.name)
      parts.push({ partNumber, etag: uploadRes.data.etag })
      uploadProgress.value = Math.round((partNumber / totalParts) * 90)
      uploadStatusText.value = `上传分片 (${partNumber}/${totalParts})...`
    }

    uploadStatusText.value = '合并文件并触发处理...'
    await mediaApi.completeUpload(uploadId, { parts })
    uploadProgress.value = 100
    uploadStep.value = 2
    await Promise.all([load(), loadGroups(), loadMediaSources()])
  } catch (e) {
    message.error('上传失败：' + (e.response?.data?.message || e.message || '未知错误'))
    uploadStep.value = 0
  } finally {
    uploading.value = false
  }
}

function resetUpload() {
  uploadStep.value = 0
  selectedFile.value = null
  selectedPreviewType.value = ''
  clearSelectedPreview()
  uploadProgress.value = 0
  uploadStatusText.value = ''
}

function clearSelectedPreview() {
  if (selectedPreviewUrl.value) {
    URL.revokeObjectURL(selectedPreviewUrl.value)
    selectedPreviewUrl.value = ''
  }
}

function detectMediaType(contentType) {
  if (contentType?.startsWith('image/')) return 'IMAGE'
  if (contentType?.startsWith('video/')) return 'VIDEO'
  if (contentType?.startsWith('audio/')) return 'AUDIO'
  return 'OTHER'
}

async function deleteMedia(id) {
  await mediaApi.remove(id)
  message.success('已删除')
  await Promise.all([load(), loadGroups(), loadMediaSources()])
}

function viewDetail(record) {
  selectedMedia.value = {
    ...record,
    ingestMode: record?.ingestMode || (record?.id ? 'UPLOADED' : 'LINKED')
  }
  detailModalOpen.value = true
}

function normalizePath(path) {
  if (!path) {
    return '/'
  }
  let normalized = String(path).trim().replace(/\\/g, '/')
  if (!normalized.startsWith('/')) {
    normalized = `/${normalized}`
  }
  normalized = normalized.replace(/\/+/g, '/')
  if (normalized.length > 1 && normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized || '/'
}

function resolveParentPath(path, rootPath) {
  const normalizedPath = normalizePath(path)
  const normalizedRoot = normalizePath(rootPath)
  if (normalizedPath === normalizedRoot) {
    return normalizedRoot
  }
  const segments = normalizedPath.split('/').filter(Boolean)
  segments.pop()
  const parent = `/${segments.join('/')}`
  if (!parent.startsWith(normalizedRoot)) {
    return normalizedRoot
  }
  return normalizePath(parent)
}

function pathDisplayName(path) {
  const normalized = normalizePath(path)
  if (normalized === '/') {
    return '根目录'
  }
  const segments = normalized.split('/').filter(Boolean)
  return segments[segments.length - 1] || '根目录'
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

function formatResolution(width, height) {
  if (!width || !height) return '-'
  return `${width} × ${height}`
}

function formatDateTime(value) {
  return value ? String(value).slice(0, 16) : '-'
}

function statusColor(status) {
  const map = { UPLOADED: 'gold', PROCESSING: 'blue', READY: 'green', FAILED: 'red' }
  return map[status] || 'default'
}

function statusLabel(status) {
  const map = { UPLOADED: '已上传', PROCESSING: '处理中', READY: '可用', FAILED: '失败' }
  return map[status] || status || '-'
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
    UPLOAD: '自行上传',
    SMB: 'SMB',
    FTP: 'FTP',
    SFTP: 'SFTP',
    WEBDAV: 'WebDAV'
  }
  return map[sourceType] || sourceType || '未知来源'
}

function mediaTypeLabel(mediaType) {
  const map = {
    IMAGE: '图片',
    VIDEO: '视频',
    AUDIO: '音频',
    OTHER: '其他'
  }
  return map[mediaType] || mediaType || '其他'
}

function ingestModeLabel(ingestMode) {
  const map = {
    UPLOADED: '上传',
    LINKED: '链接',
    CACHED: '缓存'
  }
  return map[ingestMode] || ingestMode || '-'
}
</script>
