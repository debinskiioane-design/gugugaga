import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { isFileId, isFileUrl, readUploadResult, toFileId } from '../src/utils/fileId.ts'

describe('fileUrl helpers', () => {
  it('detects file ids and urls', () => {
    assert.equal(isFileId(9), true)
    assert.equal(isFileId('42'), true)
    assert.equal(isFileId('https://cdn.example/a.png'), false)
    assert.equal(isFileUrl('https://cdn.example/a.png'), true)
    assert.equal(isFileUrl('/admin-api/infra/file/1/get/a.png'), true)
    assert.equal(toFileId('42'), 42)
  })

  it('reads server upload response with id+url', () => {
    const result = readUploadResult({ code: 0, data: { id: 8, url: 'https://cdn.example/a.png' } })
    assert.equal(result.id, 8)
    assert.equal(result.url, 'https://cdn.example/a.png')
  })

  it('reads client direct upload response', () => {
    const result = readUploadResult({ data: 'https://cdn.example/b.png', fileId: 11 })
    assert.equal(result.id, 11)
    assert.equal(result.url, 'https://cdn.example/b.png')
  })

  it('reads legacy string url response', () => {
    const result = readUploadResult({ code: 0, data: 'https://cdn.example/c.png' })
    assert.equal(result.id, undefined)
    assert.equal(result.url, 'https://cdn.example/c.png')
  })
})
