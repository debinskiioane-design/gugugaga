<template>
  <div v-if="!disabled" class="upload-file">
    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :action="uploadUrl"
      :auto-upload="autoUpload"
      :before-upload="beforeUpload"
      :disabled="disabled"
      :drag="drag"
      :http-request="httpRequest"
      :limit="props.limit"
      :multiple="props.limit > 1"
      :on-error="excelUploadError"
      :on-exceed="handleExceed"
      :on-preview="handlePreview"
      :on-remove="handleRemove"
      :on-success="handleFileSuccess"
      :show-file-list="true"
      class="upload-file-uploader"
      name="file"
    >
      <el-button type="primary">
        <Icon icon="ep:upload-filled" />
        选取文件
      </el-button>
      <template v-if="isShowTip" #tip>
        <div style="font-size: 8px">
          大小不超过 <b style="color: #f56c6c">{{ fileSize }}MB</b>
        </div>
        <div v-if="fileType.length" style="font-size: 8px">
          格式为 <b style="color: #f56c6c">{{ fileType.join('/') }}</b> 的文件
        </div>
      </template>
      <template #file="row">
        <div class="flex items-center">
          <span>{{ row.file.name }}</span>
          <span v-if="(row.file as FileItem).fileId" class="ml-10px text-gray-400">
            #{{ (row.file as FileItem).fileId }}
          </span>
          <span v-if="row.file.status === 'uploading'" class="ml-10px">
            {{ row.file.percentage || 0 }}%
          </span>
          <div class="ml-10px">
            <el-link
              :href="row.file.url"
              :underline="false"
              download
              target="_blank"
              type="primary"
            >
              下载
            </el-link>
          </div>
          <div class="ml-10px">
            <el-button link type="danger" @click="handleRemove(row.file)"> 删除</el-button>
          </div>
        </div>
      </template>
    </el-upload>
    <div v-if="showMediaPicker" class="mt-8px">
      <MediaPicker :show-preview="false" :show-clear="false" @change="onPicked" />
    </div>
  </div>

  <!-- 上传操作禁用时 -->
  <div v-if="disabled" class="upload-file">
    <div v-for="(file, index) in fileList" :key="index" class="flex items-center file-list-item">
      <span>{{ file.name }}</span>
      <div class="ml-10px">
        <el-link :href="file.url" :underline="false" download target="_blank" type="primary">
          下载
        </el-link>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { propTypes } from '@/utils/propTypes'
import type { UploadProps, UploadRawFile, UploadUserFile } from 'element-plus'
import { isString } from '@/utils/is'
import { getFileNameFromUrl } from '@/utils/file'
import { cancelUpload, useUpload } from '@/components/UploadFile/src/useUpload'
import { UploadFile } from 'element-plus/es/components/upload/src/upload'
import { MediaPicker } from '@/components/MediaPicker'
import { readUploadResult, rememberFileUrl, resolveFileUrl, toFileId } from '@/utils/fileUrl'

defineOptions({ name: 'UploadFile' })

type FileItem = UploadUserFile & { fileId?: number }

const message = useMessage() // 消息弹窗
const emit = defineEmits(['update:modelValue', 'update:fileSize'])

const props = defineProps({
  modelValue: propTypes.oneOfType<string | number | Array<string | number>>([
    String,
    Number,
    Array
  ]).isRequired,
  fileType: propTypes.array.def(['doc', 'xls', 'ppt', 'txt', 'pdf']), // 文件类型，空数组表示不限制格式
  fileSize: propTypes.number.def(5), // 大小限制(MB)
  limit: propTypes.number.def(5), // 数量限制
  autoUpload: propTypes.bool.def(true), // 自动上传
  drag: propTypes.bool.def(false), // 拖拽上传
  isShowTip: propTypes.bool.def(true), // 是否显示提示
  disabled: propTypes.bool.def(false), // 是否禁用上传组件 ==> 非必传（默认为 false）
  directory: propTypes.string.def(undefined), // 上传目录 ==> 非必传（默认为 undefined）
  showMediaPicker: propTypes.bool.def(true)
})

// ========== 上传相关 ==========
const fileList = ref<FileItem[]>([])
let emittedModelValue: string | number | Array<string | number> | undefined // 区分自身回写与切换表单后的外部重置

const { uploadUrl, httpRequest } = useUpload(props.directory)

// 移除校验未通过的待上传文件
const removeRejectedFile = (file: UploadRawFile) => {
  const index = fileList.value.findIndex((item) => item.uid === file.uid)
  if (index > -1) {
    fileList.value.splice(index, 1)
  }
}

// 文件上传之前判断
const beforeUpload: UploadProps['beforeUpload'] = (file: UploadRawFile) => {
  if (fileList.value.length >= props.limit) {
    message.error(`上传文件数量不能超过${props.limit}个!`)
    removeRejectedFile(file)
    return false
  }
  let fileExtension = ''
  if (file.name.lastIndexOf('.') > -1) {
    fileExtension = file.name.slice(file.name.lastIndexOf('.') + 1)
  }
  const isAllowedType =
    props.fileType.length === 0 ||
    props.fileType.some((type: string) => {
      if (file.type.indexOf(type) > -1) return true
      return !!(fileExtension && fileExtension.indexOf(type) > -1)
    })
  const isLimit = file.size < props.fileSize * 1024 * 1024
  if (!isAllowedType) {
    message.error(`文件格式不正确, 请上传${props.fileType.join('/')}格式!`)
    removeRejectedFile(file)
    return false
  }
  if (!isLimit) {
    message.error(`上传文件大小不能超过${props.fileSize}MB!`)
    removeRejectedFile(file)
    return false
  }
  message.success('正在上传文件，请稍候...')
  return true
}
// 处理上传的文件发生变化
// const handleFileChange = (uploadFile: UploadFile): void => {
//   uploadRef.value.data.path = uploadFile.name
// }
// 文件上传成功
const handleFileSuccess: UploadProps['onSuccess'] = (res: any, uploadFile): void => {
  // 按上传文件定位；表单切换或文件删除后到达的旧回调不能写入新列表。
  const index = fileList.value.findIndex((item) => item.uid === uploadFile.uid)
  if (index < 0) {
    return
  }
  const { id, url } = readUploadResult(res)
  if (id != null) {
    rememberFileUrl(id, url)
  }
  const fileSize = uploadFile.raw?.size
  fileList.value[index] = {
    ...fileList.value[index],
    name: uploadFile.name || getFileNameFromUrl(url),
    url,
    fileId: id,
    status: 'success'
  }
  message.success('上传成功')
  emitUpdateModelValue()
  emit('update:fileSize', fileSize)
}

const onPicked = async (payload: { id?: number; url: string; name: string }) => {
  if (payload.id == null) {
    return
  }
  if (fileList.value.length >= props.limit) {
    message.error(`上传文件数量不能超过${props.limit}个!`)
    return
  }
  if (fileList.value.some((item) => item.fileId === payload.id)) {
    return
  }
  rememberFileUrl(payload.id, payload.url)
  fileList.value.push({
    name: payload.name || `文件 ${payload.id}`,
    url: payload.url || (await resolveFileUrl(payload.id)),
    fileId: payload.id,
    status: 'success',
    uid: Date.now()
  })
  emitUpdateModelValue()
}
// 文件数超出提示
const handleExceed: UploadProps['onExceed'] = (): void => {
  message.error(`上传文件数量不能超过${props.limit}个!`)
}
// 上传错误提示
const excelUploadError: UploadProps['onError'] = (): void => {
  message.error('导入数据失败，请您重新上传！')
}
// 删除上传文件
const handleRemove = (file: UploadFile) => {
  cancelUpload(file.uid)
  const index = fileList.value.findIndex((item) => item.uid === file.uid)
  if (index > -1) {
    fileList.value.splice(index, 1)
    emitUpdateModelValue()
    emit('update:fileSize', undefined)
  }
}
const handlePreview: UploadProps['onPreview'] = (uploadFile) => {
  console.log(uploadFile)
}

const toValueList = (val: string | number | Array<string | number>): Array<string | number> => {
  if (Array.isArray(val)) {
    return val
  }
  if (typeof val === 'number') {
    return [val]
  }
  if (!val) {
    return []
  }
  return String(val)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

// 监听模型绑定值变动：编号解析为可下载地址，旧 URL 原样展示
watch(
  () => props.modelValue,
  async (val) => {
    if (toRaw(val) === emittedModelValue) {
      emittedModelValue = undefined
      return // 保留同一批次尚未完成的上传文件
    }
    emittedModelValue = undefined
    if (val == null || val === '') {
      fileList.value = []
      return
    }

    const values = toValueList(val as string | number | Array<string | number>)
    const next: FileItem[] = []
    for (const item of values) {
      const id = toFileId(item)
      if (id != null) {
        const url = await resolveFileUrl(id)
        next.push({
          name: getFileNameFromUrl(url) || `文件 ${id}`,
          url,
          fileId: id,
          status: 'success',
          uid: id
        })
      } else {
        const url = String(item)
        next.push({
          name: getFileNameFromUrl(url),
          url,
          status: 'success',
          uid: Date.now() + next.length
        })
      }
    }
    fileList.value = next
  },
  { immediate: true, deep: true }
)
// 发送文件编号列表（无编号时回退为 URL，兼容旧数据）
const emitUpdateModelValue = () => {
  const tokens = fileList.value
    .filter((file) => !file.status || file.status === 'success')
    .map((file) => (file.fileId != null ? String(file.fileId) : file.url!))
  let result: string | string[] = tokens
  if (props.limit === 1 || isString(props.modelValue) || typeof props.modelValue === 'number') {
    result = tokens.join(',')
  }
  emittedModelValue = result
  emit('update:modelValue', result)
}
</script>
<style lang="scss" scoped>
.upload-file-uploader {
  margin-bottom: 5px;
}

:deep(.upload-file-list .el-upload-list__item) {
  position: relative;
  margin-bottom: 10px;
  line-height: 2;
  border: 1px solid #e4e7ed;
}

:deep(.el-upload-list__item-file-name) {
  max-width: 250px;
}

:deep(.upload-file-list .ele-upload-list__item-content) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: inherit;
}

:deep(.ele-upload-list__item-content-action .el-link) {
  margin-right: 10px;
}

.file-list-item {
  border: 1px dashed var(--el-border-color-darker);
  border-radius: 8px;
}
</style>
