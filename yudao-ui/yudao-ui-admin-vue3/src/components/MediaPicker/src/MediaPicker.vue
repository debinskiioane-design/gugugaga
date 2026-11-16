<template>
  <div>
    <el-button :disabled="disabled" @click="open">
      <Icon icon="ep:folder-opened" class="mr-5px" />
      从媒体库选择
    </el-button>
    <span v-if="modelValue" class="ml-10px">文件编号 {{ modelValue }}</span>
    <el-dialog v-model="visible" title="媒体库" width="920px" append-to-body>
      <el-form :inline="true" :model="query" class="-mb-15px">
        <el-form-item label="文件名">
          <el-input v-model="query.name" clearable placeholder="文件名" class="!w-180px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="query.type" clearable placeholder="image/png" class="!w-180px" @keyup.enter="search" />
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

defineOptions({ name: 'MediaPicker' })

defineProps({
  modelValue: {
    type: Number,
    default: undefined
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const visible = ref(false)
const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const current = ref<any>()
const query = reactive({
  pageNo: 1,
  pageSize: 8,
  name: undefined as string | undefined,
  type: undefined as string | undefined
})

const open = () => {
  visible.value = true
  current.value = undefined
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

defineExpose({ open })

const confirm = () => {
  if (!current.value) {
    return
  }
  emit('update:modelValue', current.value.id)
  emit('change', {
    id: current.value.id,
    url: current.value.url,
    name: current.value.name
  })
  visible.value = false
}
</script>
