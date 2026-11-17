import * as FileApi from '@/api/infra/file'
import { isFileId, isFileUrl, toFileId } from '@/utils/fileId'

export { isFileId, isFileUrl, readUploadResult, toFileId } from '@/utils/fileId'

const urlCache = new Map<number, string>()

/**
 * 将业务字段解析为可展示地址：编号走 /infra/file/url，旧 URL 原样返回。
 */
export const resolveFileUrl = async (value: unknown): Promise<string> => {
  if (value == null || value === '') {
    return ''
  }
  if (typeof value === 'string' && isFileUrl(value) && !isFileId(value)) {
    return value.trim()
  }
  const id = toFileId(value)
  if (id == null) {
    return typeof value === 'string' ? value : String(value)
  }
  const cached = urlCache.get(id)
  if (cached) {
    return cached
  }
  const url = await FileApi.getFileUrl(id)
  if (url) {
    urlCache.set(id, url)
  }
  return url || ''
}

export const rememberFileUrl = (id: number, url: string) => {
  if (id && url) {
    urlCache.set(id, url)
  }
}
