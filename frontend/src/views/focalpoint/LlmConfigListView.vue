<template>
  <div>
    <div style="display:flex; justify-content:space-between; margin-bottom:16px">
      <h3>Vision LLM 配置管理</h3>
      <a-button type="primary" @click="openCreate"><plus-outlined /> 新建配置</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="configs"
      :loading="loading"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.enabled ? 'green' : 'red'">
            {{ record.enabled ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'apiKey'">
          <span>{{ record.apiKeyMasked || '***' }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确认删除该配置？" @confirm="deleteConfig(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- Create/Edit Modal -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingId ? '编辑 LLM 配置' : '新建 LLM 配置'"
      :width="600"
      @ok="submitForm"
      @cancel="closeModal"
      ok-text="保存"
      cancel-text="取消"
      :confirm-loading="saving"
    >
      <a-form :model="form" layout="vertical" ref="formRef">
        <a-form-item label="配置名称" name="name" :rules="[{required:true, message:'请输入配置名称'}]">
          <a-input v-model:value="form.name" placeholder="例如: OpenAI GPT-4V" />
        </a-form-item>
        <a-form-item label="API Endpoint" name="apiEndpoint" :rules="[{required:true, message:'请输入API Endpoint'}]">
          <a-input v-model:value="form.apiEndpoint" placeholder="https://api.openai.com/v1/chat/completions" />
        </a-form-item>
        <a-form-item label="API Key" name="apiKey" :rules="editingId ? [] : [{required:true, message:'请输入API Key'}]">
          <a-input-password v-model:value="form.apiKey" :placeholder="editingId ? '留空则不修改' : '请输入API Key'" />
        </a-form-item>
        <a-form-item label="模型名称" name="modelName" :rules="[{required:true, message:'请输入模型名称'}]">
          <a-input v-model:value="form.modelName" placeholder="gpt-4-vision-preview" />
        </a-form-item>
        <a-form-item label="最大 Token 数" name="maxTokens">
          <a-input-number v-model:value="form.maxTokens" :min="256" :max="4096" style="width:100%" />
        </a-form-item>
        <a-form-item label="超时时间（秒）" name="timeoutSeconds">
          <a-input-number v-model:value="form.timeoutSeconds" :min="5" :max="300" style="width:100%" />
        </a-form-item>
        <a-form-item label="额外参数（JSON）" name="extraParams">
          <a-textarea v-model:value="form.extraParams" :rows="3" placeholder='{"temperature": 0.7}' />
        </a-form-item>
        <a-form-item label="启用状态" name="enabled">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { focalPointApi } from '@/api/focal-point'

const configs = ref([])
const loading = ref(false)
const modalVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref(null)

const form = reactive({
  name: '',
  apiEndpoint: '',
  apiKey: '',
  modelName: '',
  maxTokens: 1024,
  timeoutSeconds: 30,
  extraParams: '',
  enabled: true
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: 'API Endpoint', dataIndex: 'apiEndpoint', key: 'apiEndpoint', ellipsis: true },
  { title: 'API Key', key: 'apiKey', width: 150 },
  { title: '模型', dataIndex: 'modelName', key: 'modelName' },
  { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 150 }
]

onMounted(() => {
  loadConfigs()
})

const loadConfigs = async () => {
  loading.value = true
  try {
    const res = await focalPointApi.listLlmConfigs()
    configs.value = res.data || []
  } catch (e) {
    message.error('加载配置列表失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  resetForm()
  modalVisible.value = true
}

const openEdit = (record) => {
  editingId.value = record.id
  form.name = record.name
  form.apiEndpoint = record.apiEndpoint
  form.apiKey = ''
  form.modelName = record.modelName
  form.maxTokens = record.maxTokens
  form.timeoutSeconds = record.timeoutSeconds
  form.extraParams = record.extraParams || ''
  form.enabled = record.enabled
  modalVisible.value = true
}

const closeModal = () => {
  modalVisible.value = false
  editingId.value = null
}

const resetForm = () => {
  form.name = ''
  form.apiEndpoint = ''
  form.apiKey = ''
  form.modelName = ''
  form.maxTokens = 1024
  form.timeoutSeconds = 30
  form.extraParams = ''
  form.enabled = true
}

const submitForm = async () => {
  try {
    await formRef.value.validateFields()
  } catch (e) {
    return
  }

  saving.value = true
  try {
    const data = { ...form }
    if (editingId.value && !data.apiKey) {
      delete data.apiKey
    }

    if (editingId.value) {
      await focalPointApi.updateLlmConfig(editingId.value, data)
      message.success('配置已更新')
    } else {
      await focalPointApi.createLlmConfig(data)
      message.success('配置已创建')
    }
    modalVisible.value = false
    await loadConfigs()
  } catch (e) {
    message.error('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

const deleteConfig = async (id) => {
  try {
    await focalPointApi.deleteLlmConfig(id)
    message.success('配置已删除')
    await loadConfigs()
  } catch (e) {
    message.error('删除失败')
  }
}
</script>
