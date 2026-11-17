/** 与 FileUploadTaskStatus 一致。 */
export const TASK_INIT = 0
export const TASK_UPLOADING = 1
export const TASK_COMPLETED = 2

const PART_CONCURRENCY = 3
const PUT_ATTEMPTS = 3
const DEFAULT_PART_SIZE = 8 * 1024 * 1024

export interface UploadPartPlan {
  partNumber: number
  start: number
  end: number
}

export interface StoredPart {
  partNumber: number
  eTag: string
}

export interface StoredUpload {
  taskId: number
  size: number
  partSize: number
  multipart: boolean
  parts: StoredPart[]
}

export interface UploadSource {
  name: string
  size: number
  lastModified: number
  type?: string
  slice(start: number, end: number): Blob
}

export interface DirectUploadClient {
  init(body: {
    name: string
    directory?: string
    size: number
    contentType?: string
  }): Promise<{
    taskId: number
    type: string
    multipart: boolean
    partSize?: number
    uploadUrl?: string
  }>
  partUrls(taskId: number, partNumbers: number[]): Promise<{ partNumber: number; uploadUrl: string }[]>
  getTask(id: number): Promise<{
    id: number
    status: number
    size: number
    multipart: boolean
    fileId?: number
    parts?: { partNumber: number; size: number }[]
  }>
  confirm(id: number): Promise<number>
  complete(taskId: number, parts: StoredPart[]): Promise<number>
  abort(id: number): Promise<void>
  presignGet(id: number): Promise<string>
  put(
    url: string,
    body: Blob,
    contentType: string | undefined,
    onLoaded: (loaded: number) => void,
    signal?: AbortSignal
  ): Promise<string>
}

export interface ResumeStore {
  load(key: string): StoredUpload | undefined
  save(key: string, value: StoredUpload): void
  clear(key: string): void
}

export function resumeKey(file: Pick<UploadSource, 'name' | 'size' | 'lastModified'>): string {
  return ['yudao-upload', file.name, file.size, file.lastModified].join(':')
}

export function planParts(size: number, partSize: number): UploadPartPlan[] {
  if (size < 1 || partSize < 1) {
    throw new Error('分片参数不正确')
  }
  const parts: UploadPartPlan[] = []
  let start = 0
  let partNumber = 1
  while (start < size) {
    const end = Math.min(start + partSize, size)
    parts.push({ partNumber, start, end })
    start = end
    partNumber++
  }
  return parts
}

/** 服务端已有分片必须和本次分片计划的字节数一致，否则不能续传。 */
export function canResume(planned: UploadPartPlan[], serverParts: { partNumber: number; size: number }[]): boolean {
  const expected = new Map(planned.map((part) => [part.partNumber, part.end - part.start]))
  return serverParts.every((part) => expected.get(part.partNumber) === part.size)
}

export function partsToUpload(
  planned: UploadPartPlan[],
  serverParts: { partNumber: number; size: number }[],
  stored: StoredPart[]
): { done: StoredPart[]; missing: UploadPartPlan[] } {
  const server = new Map(serverParts.map((part) => [part.partNumber, part.size]))
  const etags = new Map(stored.map((part) => [part.partNumber, part.eTag]))
  const done: StoredPart[] = []
  const missing: UploadPartPlan[] = []
  for (const part of planned) {
    const size = part.end - part.start
    const etag = etags.get(part.partNumber)
    if (server.get(part.partNumber) === size && etag) {
      done.push({ partNumber: part.partNumber, eTag: etag })
    } else {
      missing.push(part)
    }
  }
  return { done, missing }
}

export function isUploadAbort(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }
  const name = (error as { name?: string }).name
  const code = (error as { code?: string }).code
  return name === 'AbortError' || name === 'CanceledError' || code === 'ERR_CANCELED'
}

export function readEtag(headers: { etag?: string; get?: (name: string) => string | null }): string {
  const raw = (headers.etag || headers.get?.('etag') || headers.get?.('ETag') || '').trim()
  if (!raw) {
    throw new Error('存储响应没有 ETag。请在桶的 CORS 中暴露 ETag 响应头。')
  }
  return raw
}

/**
 * 浏览器直传。小文件 PUT 后由后端 HEAD 确认；达到阈值后分片。
 * 续传只跳过服务端已经存在、且本地记得 ETag 的分片。ETag 以 PUT 响应为准。
 */
export async function runDirectUpload(options: {
  file: UploadSource
  directory?: string
  client: DirectUploadClient
  store: ResumeStore
  signal?: AbortSignal
  onProgress?: (percent: number) => void
}): Promise<{ data: string; fileId: number }> {
  const { file, client, store, signal } = options
  const key = resumeKey(file)
  const report = (percent: number) => options.onProgress?.(Math.max(0, Math.min(100, percent)))
  report(0)

  const stored = store.load(key)
  const resumed = await resumeTask(file, stored, client)
  const task = resumed ?? (await beginTask(file, options.directory, client))
  const record: StoredUpload = {
    taskId: task.taskId,
    size: file.size,
    partSize: task.partSize,
    multipart: task.multipart,
    parts: resumed?.parts ?? []
  }
  store.save(key, record)

  try {
    throwIfAborted(signal)
    let fileId: number
    if (task.fileId) {
      fileId = task.fileId
    } else if (!task.multipart) {
      if (!task.uploadUrl || !task.type) {
        throw new Error('直传地址不完整')
      }
      await putWithRetry(client, task.uploadUrl, file.slice(0, file.size), task.type, (loaded) => {
        report(file.size === 0 ? 0 : Math.min(99, Math.floor((loaded / file.size) * 100)))
      }, signal)
      fileId = await client.confirm(task.taskId)
    } else {
      fileId = await uploadParts(file, task, record, client, store, key, report, signal)
    }

    const url = await client.presignGet(fileId)
    store.clear(key)
    report(100)
    return { data: url, fileId }
  } catch (error) {
    if (isUploadAbort(error)) {
      await abortQuietly(client, task.taskId)
      store.clear(key)
    }
    throw error
  }
}

interface ActiveTask {
  taskId: number
  type: string
  multipart: boolean
  partSize: number
  uploadUrl?: string
  parts: StoredPart[]
  fileId?: number
}

async function beginTask(
  file: UploadSource,
  directory: string | undefined,
  client: DirectUploadClient
): Promise<ActiveTask> {
  const created = await client.init({
    name: file.name,
    directory,
    size: file.size,
    contentType: file.type || undefined
  })
  return {
    taskId: created.taskId,
    type: created.type,
    multipart: created.multipart,
    partSize: created.partSize && created.partSize > 0 ? created.partSize : DEFAULT_PART_SIZE,
    uploadUrl: created.uploadUrl,
    parts: []
  }
}

async function resumeTask(
  file: UploadSource,
  stored: StoredUpload | undefined,
  client: DirectUploadClient
): Promise<ActiveTask | undefined> {
  if (!stored || stored.size !== file.size) {
    return undefined
  }
  let current: Awaited<ReturnType<DirectUploadClient['getTask']>>
  try {
    current = await client.getTask(stored.taskId)
  } catch {
    return undefined
  }
  if (current.fileId && current.status === TASK_COMPLETED) {
    return {
      taskId: current.id,
      type: '',
      multipart: false,
      partSize: stored.partSize || DEFAULT_PART_SIZE,
      uploadUrl: undefined,
      parts: [] as StoredPart[],
      fileId: current.fileId
    }
  }
  const open = current.status === TASK_INIT || current.status === TASK_UPLOADING
  if (!open || current.size !== file.size) {
    await abortQuietly(client, current.id)
    return undefined
  }
  if (!current.multipart) {
    await abortQuietly(client, current.id)
    return undefined
  }
  const partSize = stored.partSize || DEFAULT_PART_SIZE
  const planned = planParts(file.size, partSize)
  const serverParts = current.parts || []
  if (!canResume(planned, serverParts)) {
    await abortQuietly(client, current.id)
    return undefined
  }
  return {
    taskId: current.id,
    type: '',
    multipart: true,
    partSize,
    uploadUrl: undefined,
    parts: partsToUpload(planned, serverParts, stored.parts).done
  }
}

async function abortQuietly(client: DirectUploadClient, taskId: number) {
  try {
    await client.abort(taskId)
  } catch {
    // 旧任务清不掉时仍重新初始化，过期清理会再处理。
  }
}

function throwIfAborted(signal?: AbortSignal) {
  if (signal?.aborted) {
    const error = new Error('上传已取消')
    error.name = 'AbortError'
    throw error
  }
}

async function putWithRetry(
  client: DirectUploadClient,
  url: string,
  body: Blob,
  contentType: string | undefined,
  onLoaded: (loaded: number) => void,
  signal?: AbortSignal
) {
  let lastError: unknown
  for (let attempt = 1; attempt <= PUT_ATTEMPTS; attempt++) {
    throwIfAborted(signal)
    try {
      return await client.put(url, body, contentType, onLoaded, signal)
    } catch (error) {
      if (isUploadAbort(error)) {
        throw error
      }
      lastError = error
    }
  }
  throw lastError instanceof Error ? lastError : new Error('上传失败')
}

async function uploadParts(
  file: UploadSource,
  task: { taskId: number; partSize: number; parts: StoredPart[]; fileId?: number },
  record: StoredUpload,
  client: DirectUploadClient,
  store: ResumeStore,
  key: string,
  report: (percent: number) => void,
  signal?: AbortSignal
) {
  if (task.fileId) {
    return task.fileId
  }
  const planned = planParts(file.size, task.partSize)
  const known = new Map(task.parts.map((part) => [part.partNumber, part.eTag]))
  const missing = planned.filter((part) => !known.has(part.partNumber))
  const inflight = new Map<number, number>()
  const doneBytes = () =>
    planned.reduce((sum, part) => (known.has(part.partNumber) ? sum + (part.end - part.start) : sum), 0)

  const reportBytes = () => {
    let bytes = doneBytes()
    for (const loaded of inflight.values()) {
      bytes += loaded
    }
    report(Math.min(99, Math.floor((bytes / file.size) * 100)))
  }
  reportBytes()

  const queue = [...missing]
  const workers = Array.from({ length: Math.min(PART_CONCURRENCY, queue.length) }, async () => {
    while (queue.length > 0) {
      const part = queue.shift()
      if (!part) {
        return
      }
      throwIfAborted(signal)
      const urls = await client.partUrls(task.taskId, [part.partNumber])
      const url = urls.find((item) => item.partNumber === part.partNumber)?.uploadUrl
      if (!url) {
        throw new Error('分片地址不完整')
      }
      inflight.set(part.partNumber, 0)
      const etag = await putWithRetry(
        client,
        url,
        file.slice(part.start, part.end),
        undefined,
        (loaded) => {
          inflight.set(part.partNumber, Math.min(loaded, part.end - part.start))
          reportBytes()
        },
        signal
      )
      inflight.delete(part.partNumber)
      known.set(part.partNumber, etag)
      record.parts = planned
        .filter((item) => known.has(item.partNumber))
        .map((item) => ({ partNumber: item.partNumber, eTag: known.get(item.partNumber)! }))
      store.save(key, record)
      reportBytes()
    }
  })
  if (workers.length > 0) {
    await Promise.all(workers)
  }
  const parts = planned.map((part) => {
    const etag = known.get(part.partNumber)
    if (!etag) {
      throw new Error('分片 ETag 不完整')
    }
    return { partNumber: part.partNumber, eTag: etag }
  })
  return client.complete(task.taskId, parts)
}
