<template>
  <div class="media-picker">
    <div v-if="showPreview && previewUrl" class="media-picker__preview">
      <el-image
        v-if="isImagePreview"
        :src="previewUrl"
        fit="contain"
        class="media-picker__image"
        :preview-src-list="[previewUrl]"
        preview-teleported
      />
      <div v-else class="media-picker__file">
        <div class="media-picker__name">{{ previewName || `文件编号 ${modelValue}` }}</div>
        <el-link :href="previewUrl" target="_blank" type="primary" :underline="false">打开</el-link>
      </div>
    </div>
    <div v-else-if="showPreview && modelValue" class="media-picker__meta">文件编号 {{ modelValue }}</div>
    <div class="media-picker__actions">
      <el-button :disabled="disabled" @click="open">
        <Icon icon="ep:folder-opened" class="mr-5px" />
        从媒体库选择
      </el-button>
      <el-button v-if="showClear && modelValue && !disabled" link type="danger" @click="clear">清除</el-button>
    </div>
    <el-dialog v-model="visible" title="媒体库" width="920px" append-to-body>
      <el-form :inline="true" :model="query" class="-mb-15px">
        <el-form-item label="文件名">
          <el-input v-model="query.name" clearable placeholder="文件名" class="!w-180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input
            v-model="query.type"
            clearable
            :placeholder="typePlaceholder"
            class="!w-180px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button @click="search">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="list" highlight-current-row @current-change="onCurrent">
        <el-table-column label="编号" prop="id" width="90" />
        <el-table-column label="文件名" prop="name" :show-overflow-tooltip="true" />
        <el-table-column label="类型" prop="type" width="160" :show-overflow-tooltip="true" />
        <el-table-column label="大小" prop="size" width="100" />
      </el-table>
      <div class="mt-12px flex justify-end">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!current" @click="confirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script lang="ts" setup>
import * as FileApi from '@/api/infra/file'
import { isFileUrl, rememberFileUrl, resolveFileUrl, toFileId } from '@/utils/fileUrl'

defineOptions({ name: 'MediaPicker' })

const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: undefined
  },
  disabled: {
    type: Boolean,
    default: false
  },
  /** 打开弹窗时预填的类型过滤，例如 image/ */
  type: {
    type: String,
    default: undefined
  },
  showPreview: {
    type: Boolean,
    default: true
  },
  showClear: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const visible = ref(false)
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const current = ref<any>()
const previewUrl = ref('')
const previewName = ref('')
const query = reactive({
  pageNo: 1,
  pageSize: 8,
  name: undefined as string | undefined,
  type: undefined as string | undefined
})

const typePlaceholder = computed(() => props.type || 'image/png')
const isImagePreview = computed(() => {
  const url = previewUrl.value
  const name = previewName.value || url
  return /\.(png|jpe?g|gif|webp|bmp|svg)(\?|$)/i.test(name) || url.startsWith('data:image')
})

const open = () => {
  visible.value = true
  current.value = undefined
  query.type = props.type
  load()
}

const search = () => {
  query.pageNo = 1
  load()
}

const load = async () => {
  loading.value = true
  try {
    const data = await FileApi.getFilePage(query)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const onCurrent = (row) => {
  current.value = row
}

const clear = () => {
  previewUrl.value = ''
  previewName.value = ''
  emit('update:modelValue', undefined)
  emit('change', { id: undefined, url: '', name: '' })
}

const refreshPreview = async () => {
  const id = toFileId(props.modelValue)
  if (id == null) {
    if (isFileUrl(props.modelValue)) {
      previewUrl.value = String(props.modelValue)
      previewName.value = ''
      return
    }
    previewUrl.value = ''
    previewName.value = ''
    return
  }
  try {
    previewUrl.value = await resolveFileUrl(id)
  } catch {
    previewUrl.value = ''
  }
}

watch(
  () => props.modelValue,
  () => {
    refreshPreview()
  },
  { immediate: true }
)

defineExpose({ open })

const confirm = () => {
  if (!current.value) {
    return
  }
  const id = current.value.id
  const url = current.value.url || ''
  const name = current.value.name || ''
  rememberFileUrl(id, url)
  previewUrl.value = url
  previewName.value = name
  emit('update:modelValue', id)
  emit('change', { id, url, name })
  visible.value = false
}
</script>
<style scoped>
.media-picker__preview {
  margin-bottom: 8px;
}
.media-picker__image {
  width: 120px;
  height: 120px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}
.media-picker__file {
  display: flex;
  gap: 12px;
  align-items: center;
}
.media-picker__name {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.media-picker__meta {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
}
.media-picker__actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
