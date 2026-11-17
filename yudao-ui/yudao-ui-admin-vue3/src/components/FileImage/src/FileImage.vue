<template>
  <el-image
    v-if="displayUrl"
    :src="displayUrl"
    :class="imageClass"
    :style="imageStyle"
    :fit="fit"
    :preview-src-list="preview ? [displayUrl] : undefined"
    :preview-teleported="preview"
    v-bind="$attrs"
  >
    <template v-if="$slots.error" #error>
      <slot name="error" />
    </template>
  </el-image>
  <div v-else-if="$slots.error">
    <slot name="error" />
  </div>
</template>
<script lang="ts" setup>
import { resolveFileUrl } from '@/utils/fileUrl'

defineOptions({ name: 'FileImage', inheritAttrs: false })

const props = defineProps({
  /** infra_file 编号，或旧版完整 URL */
  src: {
    type: [String, Number],
    default: undefined
  },
  fit: {
    type: String,
    default: 'cover'
  },
  preview: {
    type: Boolean,
    default: false
  },
  imageClass: {
    type: String,
    default: undefined
  },
  imageStyle: {
    type: [String, Object],
    default: undefined
  }
})

const displayUrl = ref('')

watch(
  () => props.src,
  async (value) => {
    if (value == null || value === '') {
      displayUrl.value = ''
      return
    }
    try {
      displayUrl.value = await resolveFileUrl(value)
    } catch {
      displayUrl.value = typeof value === 'string' && value.includes('/') ? value : ''
    }
  },
  { immediate: true }
)
</script>
