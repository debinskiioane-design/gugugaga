<template>
  <div class="upload-box" :style="uploadStyle">
    <el-upload
      v-model:file-list="fileList"
      :accept="fileType.join(',')"
      :action="uploadUrl"
      :before-upload="beforeUpload"
      :class="['upload', drag ? 'no-border' : '']"
      :disabled="disabled"
      :drag="drag"
      :http-request="httpRequest"
      :limit="limit"
      :multiple="true"
      :on-error="uploadError"
      :on-exceed="handleExceed"
      :on-success="uploadSuccess"
      list-type="picture-card"
    >
      <div class="upload-empty">
        <slot name="empty">
          <Icon icon="ep:plus" />
          <!-- <span>请上传图片</span> -->
        </slot>
      </div>
      <template #file="{ file }">
        <img :src="file.url" class="upload-image" />
        <div v-if="file.status === 'uploading'" class="upload-progress">{{ file.percentage || 0 }}%</div>
        <div class="upload-handle" @click.stop>
          <div class="handle-icon" @click="imagePreview(file.url!)">
            <Icon icon="ep:zoom-in" />
            <span>查看</span>
          </div>
          <div v-if="!disabled" class="handle-icon" @click="handleRemove(file)">
            <Icon icon="ep:delete" />
            <span>删除</span>
          </div>
        </div>
      </template>
    </el-upload>
    <div v-if="!disabled && showMediaPicker" class="mt-8px">
      <MediaPicker type="image/" :show-preview="false" :show-clear="false" @change="onPicked" />
    </div>
    <div class="el-upload__tip">
      <slot name="tip"></slot>
    </div>
  </div>
</template>
<script lang="ts" setup>
import type { UploadFile, UploadProps, UploadUserFile } from 'element-plus'
import { ElNotification } from 'element-plus'
import { createImageViewer } from '@/components/ImageViewer'

import { propTypes } from '@/utils/propTypes'
import { cancelUpload, useUpload } from '@/components/UploadFile/src/useUpload'
import { MediaPicker } from '@/components/MediaPicker'
import { readUploadResult, rememberFileUrl, resolveFileUrl, toFileId } from '@/utils/fileUrl'

defineOptions({ name: 'UploadImgs' })

type FileItem = UploadUserFile & { fileId?: number }

const message = useMessage() // 消息弹窗
// 查看图片
const imagePreview = (imgUrl: string) => {
  createImageViewer({
    zIndex: 9999999,
    urlList: [imgUrl]
  })
}

type FileTypes =
  | 'image/apng'
  | 'image/bmp'
  | 'image/gif'
  | 'image/jpeg'
  | 'image/pjpeg'
  | 'image/png'
  | 'image/svg+xml'
  | 'image/tiff'
  | 'image/webp'
  | 'image/x-icon'

const props = defineProps({
  modelValue: propTypes.oneOfType<string | string[]>([String, Array<String>]).isRequired,
  drag: propTypes.bool.def(true), // 是否支持拖拽上传 ==> 非必传（默认为 true）
  disabled: propTypes.bool.def(false), // 是否禁用上传组件 ==> 非必传（默认为 false）
  limit: propTypes.number.def(5), // 最大图片上传数 ==> 非必传（默认为 5张）
  fileSize: propTypes.number.def(5), // 图片大小限制 ==> 非必传（默认为 5M）
  fileType: propTypes.array.def(['image/jpeg', 'image/png', 'image/gif']), // 图片类型限制 ==> 非必传（默认为 ["image/jpeg", "image/png", "image/gif"]）
  height: propTypes.string.def('150px'), // 组件高度 ==> 非必传（默认为 150px）
  width: propTypes.string.def('150px'), // 组件宽度 ==> 非必传（默认为 150px）
  borderradius: propTypes.string.def('8px'), // 组件边框圆角 ==> 非必传（默认为 8px）
  directory: propTypes.string.def(undefined), // 上传目录 ==> 非必传（默认为 undefined）
  showMediaPicker: propTypes.bool.def(true)
})

const uploadStyle = computed(() => ({
  '--upload-width': props.width,
  '--upload-height': props.height,
  '--upload-border-radius': props.borderradius
}))

const { uploadUrl, httpRequest } = useUpload(props.directory)

const fileList = ref<FileItem[]>([])
let emittedModelValue: Array<string | number> | undefined // 区分自身回写与切换表单后的外部重置
/**
 * @description 文件上传之前判断
 * @param rawFile 上传的文件
 * */
const beforeUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const imgSize = rawFile.size / 1024 / 1024 < props.fileSize
  const imgType = props.fileType
  const isValidType = imgType.includes(rawFile.type as FileTypes)
  const isValidSize = imgSize

  if (!isValidType)
    ElNotification({
      title: '温馨提示',
      message: '上传图片不符合所需的格式！',
      type: 'warning'
    })
  if (!isValidSize)
    ElNotification({
      title: '温馨提示',
      message: `上传图片大小不能超过 ${props.fileSize}M！`,
      type: 'warning'
    })

  return isValidType && isValidSize
}

// 图片上传成功：业务字段保存文件编号
interface UploadEmits {
  (e: 'update:modelValue', value: Array<string | number>): void
}

const emit = defineEmits<UploadEmits>()
const uploadSuccess: UploadProps['onSuccess'] = (res: any, uploadFile): void => {
  // 按上传文件定位；表单切换或文件删除后到达的旧回调不能写入新列表。
  const index = fileList.value.findIndex((item) => item.uid === uploadFile.uid)
  if (index < 0) {
    return
  }
  const { id, url } = readUploadResult(res)
  if (id != null) {
    rememberFileUrl(id, url)
  }
  fileList.value[index] = {
    ...fileList.value[index],
    name: url,
    url,
    fileId: id,
    status: 'success'
  }
  message.success('上传成功')
  emitUpdateModelValue()
}

const onPicked = async (payload: { id?: number; url: string; name: string }) => {
  if (payload.id == null) {
    return
  }
  if (fileList.value.length >= props.limit) {
    handleExceed()
    return
  }
  if (fileList.value.some((item) => item.fileId === payload.id)) {
    return
  }
  rememberFileUrl(payload.id, payload.url)
  fileList.value.push({
    name: payload.name || payload.url,
    url: payload.url || (await resolveFileUrl(payload.id)),
    fileId: payload.id,
    status: 'success',
    uid: Date.now()
  })
  emitUpdateModelValue()
}

// 监听模型绑定值变动
watch(
  () => props.modelValue,
  async (val) => {
    if (toRaw(val) === emittedModelValue) {
      emittedModelValue = undefined
      return // 保留同一批次尚未完成的上传文件
    }
    emittedModelValue = undefined
    if (!val || (Array.isArray(val) && val.length === 0)) {
      fileList.value = []
      return
    }

    const values = (Array.isArray(val) ? val : [val]) as Array<string | number>
    const next: FileItem[] = []
    for (const item of values) {
      const id = toFileId(item)
      if (id != null) {
        const url = await resolveFileUrl(id)
        next.push({
          name: url.substring(url.lastIndexOf('/') + 1) || `文件 ${id}`,
          url,
          fileId: id,
          status: 'success',
          uid: id
        })
      } else {
        const url = String(item)
        next.push({
          name: url.substring(url.lastIndexOf('/') + 1),
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
// 发送文件编号列表（无编号时回退 URL）
const emitUpdateModelValue = () => {
  const tokens: Array<string | number> = fileList.value
    .filter((file) => !file.status || file.status === 'success')
    .map((file) => (file.fileId != null ? file.fileId : file.url!))
  emittedModelValue = tokens
  emit('update:modelValue', tokens)
}
// 删除图片
const handleRemove = (uploadFile: UploadFile) => {
  cancelUpload(uploadFile.uid)
  fileList.value = fileList.value.filter(
    (item) => item.url !== uploadFile.url || item.name !== uploadFile.name
  )
  emitUpdateModelValue()
}

// 图片上传错误提示
const uploadError = () => {
  ElNotification({
    title: '温馨提示',
    message: '图片上传失败，请您重新上传！',
    type: 'error'
  })
}

// 文件数超出提示
const handleExceed = () => {
  ElNotification({
    title: '温馨提示',
    message: `当前最多只能上传 ${props.limit} 张图片，请移除后上传！`,
    type: 'warning'
  })
}
</script>

<style lang="scss" scoped>
.is-error {
  .upload {
    :deep(.el-upload--picture-card),
    :deep(.el-upload-dragger) {
      border: 1px dashed var(--el-color-danger) !important;

      &:hover {
        border-color: var(--el-color-primary) !important;
      }
    }
  }
}

:deep(.disabled) {
  .el-upload--picture-card,
  .el-upload-dragger {
    cursor: not-allowed;
    background: var(--el-disabled-bg-color) !important;
    border: 1px dashed var(--el-border-color-darker);

    &:hover {
      border-color: var(--el-border-color-darker) !important;
    }
  }
}

.upload-box {
  .no-border {
    :deep(.el-upload--picture-card) {
      border: none !important;
    }
  }

  :deep(.upload) {
    .el-upload-dragger {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
      padding: 0;
      overflow: hidden;
      border: 1px dashed var(--el-border-color-darker);
      border-radius: var(--upload-border-radius);

      &:hover {
        border: 1px dashed var(--el-color-primary);
      }
    }

    .el-upload-dragger.is-dragover {
      background-color: var(--el-color-primary-light-9);
      border: 2px dashed var(--el-color-primary) !important;
    }

    .el-upload-list__item,
    .el-upload--picture-card {
      width: var(--upload-width);
      height: var(--upload-height);
      background-color: transparent;
      border-radius: var(--upload-border-radius);
    }

    .upload-image {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }

    .upload-progress {
      position: absolute;
      right: 0;
      bottom: 0;
      left: 0;
      z-index: 1;
      padding: 4px 0;
      font-size: 12px;
      color: #fff;
      text-align: center;
      background: rgb(0 0 0 / 55%);
    }

    .upload-handle {
      position: absolute;
      top: 0;
      right: 0;
      display: flex;
      width: 100%;
      height: 100%;
      cursor: pointer;
      background: rgb(0 0 0 / 60%);
      opacity: 0;
      box-sizing: border-box;
      transition: var(--el-transition-duration-fast);
      align-items: center;
      justify-content: center;

      .handle-icon {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 0 6%;
        color: aliceblue;

        .el-icon {
          margin-bottom: 15%;
          font-size: 140%;
        }

        span {
          font-size: 100%;
        }
      }
    }

    .el-upload-list__item {
      &:hover {
        .upload-handle {
          opacity: 1;
        }
      }
    }

    .upload-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      font-size: 12px;
      line-height: 30px;
      color: var(--el-color-info);

      .el-icon {
        font-size: 28px;
        color: var(--el-text-color-secondary);
      }
    }
  }

  .el-upload__tip {
    line-height: 15px;
    text-align: center;
  }
}
</style>
