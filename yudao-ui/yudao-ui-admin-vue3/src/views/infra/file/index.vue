<template>
  <doc-alert title="上传下载" url="https://doc.iocoder.cn/file/" />
  <!-- 搜索 -->
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="文件名" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入文件名"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="文件路径" prop="path">
        <el-input
          v-model="queryParams.path"
          placeholder="请输入文件路径"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="文件类型" prop="type" width="80">
        <el-input
          v-model="queryParams.type"
          placeholder="请输入文件类型"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="创建时间" prop="createTime">
        <el-date-picker
          v-model="queryParams.createTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm">
          <Icon icon="ep:upload" class="mr-5px" /> 上传文件
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="checkedIds.length === 0"
          @click="handleDeleteBatch"
          v-hasPermi="['infra:file:delete']"
        >
          <Icon icon="ep:delete" class="mr-5px" /> 批量删除
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <MediaPicker v-model="pickedId" />
    <div v-if="pickedId" class="mt-8px">当前选择的文件编号：{{ pickedId }}</div>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="handleRowCheckboxChange">
      <el-table-column type="selection" width="55" />
      <el-table-column label="编号" align="center" prop="id" width="90" />
      <el-table-column label="文件名" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="文件路径" align="center" prop="path" :show-overflow-tooltip="true" />
      <el-table-column label="URL" align="center" prop="url" :show-overflow-tooltip="true" />
      <el-table-column
        label="文件大小"
        align="center"
        prop="size"
        width="120"
        :formatter="fileSizeFormatter"
      />
      <el-table-column label="文件类型" align="center" prop="type" width="180px" />
      <el-table-column label="文件内容" align="center" prop="url" width="110px">
        <template #default="{ row }">
          <el-image
            v-if="row.type.includes('image')"
            class="h-80px w-80px"
            lazy
            :src="row.url"
            :preview-src-list="[row.url]"
            preview-teleported
            fit="cover"
          />
          <el-link
            v-else-if="row.type.includes('pdf')"
            type="primary"
            :href="row.url"
            :underline="false"
            target="_blank"
          >
            预览
          </el-link>
          <el-link
            v-else
            type="primary"
            download
            :href="row.url"
            :underline="false"
            target="_blank"
          >
            下载
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        label="上传时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="copyToClipboard(String(scope.row.id))">
            复制编号
          </el-button>
          <el-button link type="primary" @click="openRelations(scope.row)">引用</el-button>
          <el-button link type="primary" @click="copyToClipboard(scope.row.url)">
            复制链接
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['infra:file:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <ContentWrap>
    <el-form :inline="true" class="-mb-15px" label-width="68px">
      <el-form-item label="任务文件">
        <el-input
          v-model="taskQuery.name"
          placeholder="请输入原文件名"
          clearable
          class="!w-240px"
          @keyup.enter="loadTasks"
        />
      </el-form-item>
      <el-form-item label="任务状态">
        <el-select v-model="taskQuery.status" clearable placeholder="全部" class="!w-160px">
          <el-option v-for="item in taskStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="loadTasks"><Icon icon="ep:search" class="mr-5px" /> 搜索任务</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="taskLoading" :data="taskList" class="mt-12px">
      <el-table-column label="任务编号" prop="id" width="100" />
      <el-table-column label="原文件名" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ taskStatusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="大小" prop="size" width="120" :formatter="fileSizeFormatter" />
      <el-table-column label="分片" width="80">
        <template #default="{ row }">{{ row.multipart ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="文件编号" prop="fileId" width="100" />
      <el-table-column label="过期时间" prop="expireTime" width="180" :formatter="dateFormatter" />
    </el-table>
    <Pagination
      :total="taskTotal"
      v-model:page="taskQuery.pageNo"
      v-model:limit="taskQuery.pageSize"
      @pagination="loadTasks"
    />
  </ContentWrap>

  <!-- 表单弹窗：添加/修改 -->
  <FileForm ref="formRef" @success="getList" />
  <el-dialog v-model="relationVisible" title="文件引用" width="640px">
    <div class="mb-12px">文件编号 {{ relationFileId }}。业务表保存这个编号，不把完整地址当作唯一标识。</div>
    <el-table :data="relations" empty-text="还没有业务引用">
      <el-table-column label="业务类型" prop="bizType" />
      <el-table-column label="业务编号" prop="bizId" />
    </el-table>
  </el-dialog>
</template>
<script lang="ts" setup>
import { fileSizeFormatter } from '@/utils'
import { dateFormatter } from '@/utils/formatTime'
import * as FileApi from '@/api/infra/file'
import FileForm from './FileForm.vue'
import { MediaPicker } from '@/components/MediaPicker'
import { useClipboard } from '@vueuse/core'

defineOptions({ name: 'InfraFile' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  type: undefined,
  path: undefined,
  createTime: []
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await FileApi.getFilePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 添加/修改操作 */
const formRef = ref()
const openForm = () => {
  formRef.value.open()
}

const taskStatusOptions = [
  { value: 0, label: '初始化' },
  { value: 1, label: '上传中' },
  { value: 2, label: '已完成' },
  { value: 3, label: '已取消' },
  { value: 4, label: '已过期' },
  { value: 5, label: '失败' }
]
const taskStatusLabel = (status: number) =>
  taskStatusOptions.find((item) => item.value === status)?.label || String(status)
const taskLoading = ref(false)
const taskList = ref<FileApi.FileUploadTaskPageRespVO[]>([])
const taskTotal = ref(0)
const taskQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined as string | undefined,
  status: undefined as number | undefined
})
const loadTasks = async () => {
  taskLoading.value = true
  try {
    const data = await FileApi.getUploadTaskPage(taskQuery)
    taskList.value = data.list
    taskTotal.value = data.total
  } finally {
    taskLoading.value = false
  }
}

const pickedId = ref<number>()
const relationVisible = ref(false)
const relationFileId = ref<number>()
const relations = ref<FileApi.FileRelationRespVO[]>([])
const openRelations = async (row) => {
  relationFileId.value = row.id
  relationVisible.value = true
  relations.value = await FileApi.getFileRelationList(row.id)
}

/** 复制到剪贴板方法 */
const copyToClipboard = async (text: string) => {
  const { copy, copied, isSupported } = useClipboard({ legacy: true, source: text })
  if (!isSupported) {
    message.error(t('common.copyError'))
    return
  }
  await copy()
  if (unref(copied)) {
    message.success(t('common.copySuccess'))
  }
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await FileApi.deleteFile(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 批量删除按钮操作 */
const checkedIds = ref<number[]>([])
const handleRowCheckboxChange = (rows) => {
  checkedIds.value = rows.map((row) => row.id)
}

const handleDeleteBatch = async () => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起批量删除
    await FileApi.deleteFileList(checkedIds.value)
    checkedIds.value = []
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 初始化 **/
onMounted(() => {
  getList()
  loadTasks()
})
</script>
