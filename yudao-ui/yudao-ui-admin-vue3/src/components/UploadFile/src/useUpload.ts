import * as FileApi from '@/api/infra/file'
import { UploadRequestOptions, UploadProgressEvent } from 'element-plus/es/components/upload/src/upload'
import axios, { AxiosProgressEvent, AxiosResponse } from 'axios'
import { readEtag, ResumeStore, runDirectUpload, StoredUpload } from './uploadTask'

const activeUploads = new Map<number, AbortController>()

/** 取消仍在进行的直传，并通知后端中止任务。 */
export const cancelUpload = (uid?: number) => {
  if (uid == null) {
    return
  }
  activeUploads.get(uid)?.abort()
}

/**
 * 获得上传 URL
 */
export const getUploadUrl = (): string => {
  return import.meta.env.VITE_BASE_URL + import.meta.env.VITE_API_URL + '/infra/file/upload'
}

export const useUpload = (directory?: string) => {
  // 后端上传地址
  const uploadUrl = getUploadUrl()
  // 是否使用前端直连上传
  const isClientUpload = UPLOAD_TYPE.CLIENT === import.meta.env.VITE_UPLOAD_TYPE
  // 重写ElUpload上传方法
  const httpRequest = async (options: UploadRequestOptions) => {
    // 文件上传进度监听
    const uploadProgressHandler = (evt: AxiosProgressEvent) => {
      const upEvt: UploadProgressEvent = Object.assign(evt.event)
      upEvt.percent = evt.progress ? evt.progress * 100 : 0
      options.onProgress?.(upEvt) // 触发 el-upload 的 on-progress
    }

    // 模式一：前端直传。对象名、确认和分片都走上传任务，浏览器不持有存储密钥。
    if (isClientUpload) {
      const file = options.file
      const controller = new AbortController()
      if (file.uid != null) {
        activeUploads.set(file.uid, controller)
      }
      try {
        return await runDirectUpload({
          file,
          directory,
          client: directUploadClient,
          store: sessionResumeStore,
          signal: controller.signal,
          onProgress: (percent) => {
            const upEvt = { percent } as UploadProgressEvent
            options.onProgress?.(upEvt)
          }
        })
      } finally {
        if (file.uid != null) {
          activeUploads.delete(file.uid)
        }
      }
    } else {
      // 模式二：后端上传
      // 重写 el-upload httpRequest 文件上传成功会走成功的钩子，失败走失败的钩子
      return new Promise((resolve, reject) => {
        FileApi.updateFile({ file: options.file, directory }, uploadProgressHandler)
          .then((res) => {
            if (res.code === 0) {
              resolve(res)
            } else {
              reject(res)
            }
          })
          .catch((res) => {
            reject(res)
          })
      })
    }
  }

  return {
    uploadUrl,
    httpRequest
  }
}

const sessionResumeStore: ResumeStore = {
  load(key) {
    return readStored(key)
  },
  save(key, value) {
    writeStored(key, value)
  },
  clear(key) {
    try {
      sessionStorage.removeItem(key)
    } catch {
      // 没有会话存储时忽略。
    }
  }
}

function readStored(key: string): StoredUpload | undefined {
  try {
    const text = sessionStorage.getItem(key)
    if (!text) {
      return undefined
    }
    const parsed = JSON.parse(text) as StoredUpload
    if (!parsed || typeof parsed.taskId !== 'number' || !Array.isArray(parsed.parts)) {
      return undefined
    }
    return parsed
  } catch {
    return undefined
  }
}

function writeStored(key: string, value: StoredUpload) {
  try {
    sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    // 浏览器拒绝写入时仍继续本次上传，只是不能续传。
  }
}

const directUploadClient = {
  init: (body: { name: string; directory?: string; size: number; contentType?: string }) =>
    FileApi.initUploadTask(body),
  partUrls: (taskId: number, partNumbers: number[]) => FileApi.getUploadPartUrls(taskId, partNumbers),
  getTask: (id: number) => FileApi.getUploadTask(id),
  confirm: (id: number) => FileApi.confirmUploadTask(id),
  complete: (taskId: number, parts: { partNumber: number; eTag: string }[]) =>
    FileApi.completeUploadTask(taskId, parts),
  abort: async (id: number) => {
    await FileApi.abortUploadTask(id)
  },
  presignGet: (id: number) => FileApi.presignGetFile(id),
  async put(
    url: string,
    body: Blob,
    contentType: string | undefined,
    onLoaded: (loaded: number) => void,
    signal?: AbortSignal
  ) {
    const response = await axios.put(url, body, {
      headers: contentType ? { 'Content-Type': contentType } : { 'Content-Type': undefined },
      signal,
      onUploadProgress: (evt: AxiosProgressEvent) => onLoaded(evt.loaded)
    })
    return readEtag((response as AxiosResponse).headers || {})
  }
}

/**
 * 上传类型
 */
enum UPLOAD_TYPE {
  // 客户端直接上传（只支持S3服务）
  CLIENT = 'client',
  // 客户端发送到后端上传
  SERVER = 'server'
}
