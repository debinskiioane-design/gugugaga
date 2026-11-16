import request from '@/config/axios'

// 文件预签名地址 Response VO
export interface FilePresignedUrlRespVO {
  // 文件配置编号
  configId: number
  // 文件上传 URL
  uploadUrl: string
  // 文件 URL
  url: string
  // 文件路径
  path: string
}

// 查询文件列表
export const getFilePage = (params: PageParam) => {
  return request.get({ url: '/infra/file/page', params })
}

// 删除文件
export const deleteFile = (id: number) => {
  return request.delete({ url: '/infra/file/delete?id=' + id })
}

// 批量删除文件
export const deleteFileList = (ids: number[]) => {
  return request.delete({ url: '/infra/file/delete-list', params: { ids: ids.join(',') } })
}

// 获取文件预签名地址
export const getFilePresignedUrl = (name: string, directory?: string) => {
  return request.get<FilePresignedUrlRespVO>({
    url: '/infra/file/presigned-url',
    params: { name, directory }
  })
}

// 创建文件
export const createFile = (data: any) => {
  return request.post({ url: '/infra/file/create', data })
}

// 上传文件
export const updateFile = (data: any, onUploadProgress?: Function) => {
  return request.upload({ url: '/infra/file/upload', data, onUploadProgress })
}

export interface FileUploadInitRespVO {
  taskId: number
  configId: number
  path: string
  type: string
  multipart: boolean
  partSize: number
  uploadUrl?: string
}

export interface FileUploadPartRespVO {
  partNumber: number
  uploadUrl: string
}

export interface FileUploadTaskRespVO {
  id: number
  status: number
  path: string
  type: string
  size: number
  multipart: boolean
  fileId?: number
  expireTime?: string
  parts: { partNumber: number; size: number }[]
}

// 初始化直传。对象名由后端生成，业务不传存储地址。
export const initUploadTask = (data: {
  name: string
  directory?: string
  size: number
  contentType?: string
}) => {
  return request.post<FileUploadInitRespVO>({ url: '/infra/file/upload-task/init', data })
}

export const getUploadPartUrls = (taskId: number, partNumbers: number[]) => {
  return request.post<FileUploadPartRespVO[]>({
    url: '/infra/file/upload-task/part-urls',
    data: { taskId, partNumbers }
  })
}

export const getUploadTask = (id: number) => {
  return request.get<FileUploadTaskRespVO>({ url: '/infra/file/upload-task/get', params: { id } })
}

export const confirmUploadTask = (id: number) => {
  return request.post<number>({ url: '/infra/file/upload-task/confirm?id=' + id })
}

export const completeUploadTask = (taskId: number, parts: { partNumber: number; eTag: string }[]) => {
  return request.post<number>({ url: '/infra/file/upload-task/complete', data: { taskId, parts } })
}

export const abortUploadTask = (id: number) => {
  return request.post<boolean>({ url: '/infra/file/upload-task/abort?id=' + id })
}

export const presignGetFile = (id: number) => {
  return request.get<string>({ url: '/infra/file/presigned-get', params: { id } })
}

export interface FileRelationRespVO {
  id: number
  fileId: number
  bizType: string
  bizId: string
  createTime?: string
}

export const getFileRelationList = (fileId: number) => {
  return request.get<FileRelationRespVO[]>({ url: '/infra/file/relation/list', params: { fileId } })
}
