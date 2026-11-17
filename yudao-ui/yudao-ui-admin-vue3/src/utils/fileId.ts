/** 判断业务字段是否已是 infra_file 编号（兼容数字与数字字符串）。 */
export const isFileId = (value: unknown): value is number | string => {
  if (typeof value === 'number') {
    return Number.isFinite(value) && value > 0
  }
  if (typeof value !== 'string') {
    return false
  }
  const trimmed = value.trim()
  return /^\d+$/.test(trimmed) && Number(trimmed) > 0
}

export const toFileId = (value: unknown): number | undefined => {
  if (!isFileId(value)) {
    return undefined
  }
  return typeof value === 'number' ? value : Number(value.trim())
}

/** 已经是可直接展示的地址（旧数据）。 */
export const isFileUrl = (value: unknown): value is string => {
  if (typeof value !== 'string') {
    return false
  }
  const v = value.trim()
  return (
    v.startsWith('http://') ||
    v.startsWith('https://') ||
    v.startsWith('/') ||
    v.startsWith('blob:') ||
    v.startsWith('data:')
  )
}

/** 从上传响应取出编号与地址（兼容旧接口只返回 URL 字符串）。 */
export const readUploadResult = (
  res: any
): { id?: number; url: string } => {
  const data = res?.data ?? res
  if (data == null) {
    return { url: '' }
  }
  if (typeof data === 'string') {
    return { url: data, id: res?.fileId }
  }
  const id = data.id ?? data.fileId ?? res?.fileId
  const url = data.url ?? data.data ?? ''
  return {
    id: id != null ? Number(id) : undefined,
    url: typeof url === 'string' ? url : ''
  }
}
