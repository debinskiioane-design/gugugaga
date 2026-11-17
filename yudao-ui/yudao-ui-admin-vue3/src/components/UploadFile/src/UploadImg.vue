<template>
  <div class="upload-box" :style="uploadStyle">
    <el-upload
      :id="uuid"
      :accept="fileType.join(',')"
      :action="uploadUrl"
      :before-upload="beforeUpload"
      :class="['upload', drag ? 'no-border' : '']"
      :disabled="disabled"
      :drag="drag"
      :http-request="httpRequest"
      :multiple="false"
      :on-error="uploadError"
      :on-success="uploadSuccess"
      :show-file-list="false"
    >
      <template v-if="previewUrl">
        <img :src="previewUrl" class="upload-image" />
        <div v-if="uploading" class="upload-progress">上传中 {{ percent }}%</div>
        <div class="upload-handle" @click.stop>
          <div v-if="!disabled" class="handle-icon" @click="editImg">
            <Icon icon="ep:edit" />
            <span v-if="showBtnText">{{ t('action.edit') }}</span>
          </div>
          <div class="handle-icon" @click="imagePreview(previewUrl)">
            <Icon icon="ep:zoom-in" />
            <span v-if="showBtnText">{{ t('action.detail') }}</span>
          </div>
          <div v-if="showDelete && !disabled" class="handle-icon" @click="deleteImg">
            <Icon icon="ep:delete" />
            <span v-if="showBtnText">{{ t('action.del') }}</span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="upload-empty">
          <slot name="empty">
            <Icon v-if="!uploading" icon="ep:plus" />
            <span v-else>上传中 {{ percent }}%</span>
          </slot>
        </div>
      </template>
    </el-upload>
    <div v-if="!disabled && showMediaPicker" class="mt-8px">
      <MediaPicker
        v-model="pickedId"
        type="image/"
        :show-preview="false"
        :show-clear="false"
        @change="onPicked"
      />
    </div>
    <div v-if="fileIdLabel" class="el-upload__tip">文件编号 {{ fileIdLabel }}</div>
    <div class="el-upload__tip">
      <slot name="tip"></slot>
    </div>
  </div>
</template>

<script lang="ts" setup>
import type { UploadProps } from 'element-plus'

import { generateUUID } from '@/utils'
import { propTypes } from '@/utils/propTypes'
import { createImageViewer } from '@/components/ImageViewer'
import { useUpload } from '@/components/UploadFile/src/useUpload'
import { MediaPicker } from '@/components/MediaPicker'
import { readUploadResult, rememberFileUrl, resolveFileUrl, toFileId } from '@/utils/fileUrl'

defineOptions({ name: 'UploadImg' })

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

// 接受父组件参数；modelValue 存 infra_file 编号，兼容旧 URL 字符串
const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: ''
  },
  drag: propTypes.bool.def(true),
  disabled: propTypes.bool.def(false),
  fileSize: propTypes.number.def(5),
  fileType: propTypes.array.def(['image/jpeg', 'image/png', 'image/gif']),
  height: propTypes.string.def('150px'),
  width: propTypes.string.def('150px'),
  borderradius: propTypes.string.def('8px'),
  showDelete: propTypes.bool.def(true),
  showBtnText: propTypes.bool.def(true),
  directory: propTypes.string.def(undefined),
  showMediaPicker: propTypes.bool.def(true)
})

const uploadStyle = computed(() => ({
  '--upload-width': props.width,
  '--upload-height': props.height,
  '--upload-border-radius': props.borderradius
}))

const { t } = useI18n()
const message = useMessage()
const uuid = ref('id-' + generateUUID())
const previewUrl = ref('')
const pickedId = ref<number | undefined>()

const fileIdLabel = computed(() => toFileId(props.modelValue))

const imagePreview = (imgUrl: string) => {
  createImageViewer({
    zIndex: 9999999,
    urlList: [imgUrl]
  })
}

const emit = defineEmits(['update:modelValue', 'change'])

const deleteImg = () => {
  previewUrl.value = ''
  pickedId.value = undefined
  emit('update:modelValue', undefined)
  emit('change', { id: undefined, url: '', name: '' })
}

const percent = ref(0)
const uploading = ref(false)
const { uploadUrl, httpRequest: rawHttpRequest } = useUpload(props.directory)
const httpRequest: typeof rawHttpRequest = async (options) => {
  uploading.value = true
  percent.value = 0
  try {
    return await rawHttpRequest({
      ...options,
      onProgress: (evt) => {
        percent.value = Math.round(evt.percent || 0)
        options.onProgress?.(evt)
      }
    })
  } finally {
    uploading.value = false
  }
}

const editImg = () => {
  const dom = document.querySelector(`#${uuid.value} .el-upload__input`)
  dom && dom.dispatchEvent(new MouseEvent('click'))
}

const beforeUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const imgSize = rawFile.size / 1024 / 1024 < props.fileSize
  const imgType = props.fileType
  if (!imgType.includes(rawFile.type as FileTypes))
    message.notifyWarning('上传图片不符合所需的格式！')
  if (!imgSize) message.notifyWarning(`上传图片大小不能超过 ${props.fileSize}M！`)
  return imgType.includes(rawFile.type as FileTypes) && imgSize
}

const uploadSuccess: UploadProps['onSuccess'] = (res: any): void => {
  const { id, url } = readUploadResult(res)
  message.success('上传成功')
  if (id != null) {
    rememberFileUrl(id, url)
    previewUrl.value = url
    pickedId.value = id
    emit('update:modelValue', id)
    emit('change', { id, url, name: '' })
    return
  }
  // 极端兼容：没有编号时仍回写地址，避免完全丢图
  previewUrl.value = url
  emit('update:modelValue', url)
  emit('change', { id: undefined, url, name: '' })
}

const onPicked = (payload: { id?: number; url: string; name: string }) => {
  if (payload.id == null) {
    deleteImg()
    return
  }
  previewUrl.value = payload.url
  emit('update:modelValue', payload.id)
  emit('change', payload)
}

watch(
  () => props.modelValue,
  async (value) => {
    const id = toFileId(value)
    pickedId.value = id
    if (value == null || value === '') {
      previewUrl.value = ''
      return
    }
    try {
      previewUrl.value = await resolveFileUrl(value)
    } catch {
      previewUrl.value = typeof value === 'string' ? value : ''
    }
  },
  { immediate: true }
)

const uploadError = () => {
  message.notifyError('图片上传失败，请您重新上传！')
}
</script>
<style lang="scss" scoped>
.is-error {
  .upload {
    :deep(.el-upload),
    :deep(.el-upload-dragger) {
      border: 1px dashed var(--el-color-danger) !important;

      &:hover {
        border-color: var(--el-color-primary) !important;
      }
    }
  }
}

:deep(.disabled) {
  .el-upload,
  .el-upload-dragger {
    cursor: not-allowed !important;
    background: var(--el-disabled-bg-color);
    border: 1px dashed var(--el-border-color-darker) !important;

    &:hover {
      border: 1px dashed var(--el-border-color-darker) !important;
    }
  }
}

.upload-box {
  .no-border {
    :deep(.el-upload) {
      border: none !important;
    }
  }

  :deep(.upload) {
    .el-upload {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      width: var(--upload-width);
      height: var(--upload-height);
      overflow: hidden;
      border: 1px dashed var(--el-border-color-darker);
      border-radius: var(--upload-border-radius);
      transition: var(--el-transition-duration-fast);

      &:hover {
        border-color: var(--el-color-primary);

        .upload-handle {
          opacity: 1;
        }
      }

      .el-upload-dragger {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
        padding: 0;
        overflow: hidden;
        background-color: transparent;
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

      .upload-empty {
        position: relative;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        line-height: 30px;
        color: var(--el-color-info);

        .el-icon {
          font-size: 28px;
          color: var(--el-text-color-secondary);
        }
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
            margin-bottom: 40%;
            font-size: 130%;
            line-height: 130%;
          }

          span {
            font-size: 85%;
            line-height: 85%;
          }
        }
      }
    }
  }

  .el-upload__tip {
    line-height: 18px;
    text-align: center;
  }
}
</style>
