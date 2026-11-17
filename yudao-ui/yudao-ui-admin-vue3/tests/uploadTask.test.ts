import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  canResume,
  isUploadAbort,
  planParts,
  partsToUpload,
  readEtag,
  runDirectUpload,
  TASK_COMPLETED,
  TASK_INIT,
  TASK_UPLOADING
} from '../src/components/UploadFile/src/uploadTask.ts'
import type { DirectUploadClient, ResumeStore, StoredUpload, UploadSource } from '../src/components/UploadFile/src/uploadTask.ts'

function source(size: number, name = 'photo.png', type = 'image/png'): UploadSource {
  return {
    name,
    size,
    lastModified: 7,
    type,
    slice(start, end) {
      return new Blob([new Uint8Array(end - start)])
    }
  }
}

function memoryStore(initial?: StoredUpload): ResumeStore & { value?: StoredUpload; cleared: boolean } {
  const box: ResumeStore & { value?: StoredUpload; cleared: boolean } = {
    value: initial,
    cleared: false,
    load: () => box.value,
    save: (_key, value) => {
      box.value = value
    },
    clear: () => {
      box.value = undefined
      box.cleared = true
    }
  }
  return box
}

test('planParts covers the whole file and only the last part may be short', () => {
  const parts = planParts(10, 4)
  assert.deepEqual(
    parts.map((part) => [part.partNumber, part.end - part.start]),
    [
      [1, 4],
      [2, 4],
      [3, 2]
    ]
  )
})

test('canResume rejects a part whose size is not the planned size', () => {
  const planned = planParts(10, 4)
  assert.equal(canResume(planned, [{ partNumber: 1, size: 4 }]), true)
  assert.equal(canResume(planned, [{ partNumber: 1, size: 3 }]), false)
  assert.equal(canResume(planned, [{ partNumber: 4, size: 4 }]), false)
})

test('partsToUpload keeps only server parts that still have a local ETag', () => {
  const planned = planParts(10, 4)
  const result = partsToUpload(planned, [{ partNumber: 1, size: 4 }], [
    { partNumber: 1, eTag: 'a' },
    { partNumber: 2, eTag: 'stale' }
  ])
  assert.deepEqual(result.done, [{ partNumber: 1, eTag: 'a' }])
  assert.deepEqual(
    result.missing.map((part) => part.partNumber),
    [2, 3]
  )
})

test('readEtag requires the storage response header', () => {
  assert.equal(readEtag({ etag: '"abc"' }), '"abc"')
  assert.throws(() => readEtag({}), /ETag/)
})

test('small file is confirmed and does not complete a multipart upload', async () => {
  const calls: string[] = []
  const progress: number[] = []
  const client: DirectUploadClient = {
    init: async () => {
      calls.push('init')
      return { taskId: 5, type: 'image/png', multipart: false, partSize: 8, uploadUrl: 'https://storage.example/put' }
    },
    partUrls: async () => {
      throw new Error('small file must not ask for part urls')
    },
    getTask: async () => {
      throw new Error('missing')
    },
    confirm: async (id) => {
      calls.push('confirm:' + id)
      return 90
    },
    complete: async () => {
      throw new Error('small file must not complete multipart')
    },
    abort: async () => {
      calls.push('abort')
    },
    presignGet: async (id) => {
      calls.push('get:' + id)
      return 'https://cdn.example/photo.png'
    },
    put: async (_url, _body, contentType, onLoaded) => {
      calls.push('put:' + contentType)
      onLoaded(4)
      return '"etag"'
    }
  }
  const store = memoryStore()
  const result = await runDirectUpload({
    file: source(4),
    directory: 'cms',
    client,
    store,
    onProgress: (percent) => progress.push(percent)
  })
  assert.deepEqual(calls, ['init', 'put:image/png', 'confirm:5', 'get:90'])
  assert.equal(result.data, 'https://cdn.example/photo.png')
  assert.equal(result.fileId, 90)
  assert.equal(progress.at(-1), 100)
  assert.equal(store.cleared, true)
})

test('multipart upload sends every PUT ETag and skips parts already stored', async () => {
  const puts: number[] = []
  const client: DirectUploadClient = {
    init: async () => {
      throw new Error('resume must not create another upload')
    },
    partUrls: async (_taskId, partNumbers) =>
      partNumbers.map((partNumber) => ({ partNumber, uploadUrl: 'https://storage.example/part-' + partNumber })),
    getTask: async () => ({
      id: 8,
      status: TASK_UPLOADING,
      size: 10,
      multipart: true,
      parts: [{ partNumber: 1, size: 4 }]
    }),
    confirm: async () => {
      throw new Error('multipart must not confirm')
    },
    complete: async (_taskId, parts) => {
      assert.deepEqual(parts, [
        { partNumber: 1, eTag: 'kept' },
        { partNumber: 2, eTag: 'new-2' },
        { partNumber: 3, eTag: 'new-3' }
      ])
      return 11
    },
    abort: async () => {
      throw new Error('compatible resume must not abort')
    },
    presignGet: async () => 'https://cdn.example/video.mp4',
    put: async (url, body, contentType) => {
      assert.equal(contentType, undefined)
      assert.equal(body.size > 0, true)
      const partNumber = Number(url.slice(url.lastIndexOf('-') + 1))
      puts.push(partNumber)
      return 'new-' + partNumber
    }
  }
  const store = memoryStore({
    taskId: 8,
    size: 10,
    partSize: 4,
    multipart: true,
    parts: [{ partNumber: 1, eTag: 'kept' }]
  })
  const result = await runDirectUpload({
    file: source(10, 'clip.mp4', 'video/mp4'),
    client,
    store
  })
  assert.deepEqual(puts.sort(), [2, 3])
  assert.equal(result.fileId, 11)
  assert.equal(store.cleared, true)
})

test('a mismatched resume aborts the old task and starts a new one', async () => {
  const calls: string[] = []
  const client: DirectUploadClient = {
    init: async () => {
      calls.push('init')
      return { taskId: 2, type: 'image/png', multipart: false, uploadUrl: 'https://storage.example/new' }
    },
    partUrls: async () => [],
    getTask: async () => ({
      id: 1,
      status: TASK_INIT,
      size: 10,
      multipart: true,
      parts: [{ partNumber: 1, size: 99 }]
    }),
    confirm: async () => 3,
    complete: async () => {
      throw new Error('new task is not multipart')
    },
    abort: async (id) => {
      calls.push('abort:' + id)
    },
    presignGet: async () => 'https://cdn.example/photo.png',
    put: async () => 'etag'
  }
  await runDirectUpload({
    file: source(10),
    client,
    store: memoryStore({ taskId: 1, size: 10, partSize: 4, multipart: true, parts: [] })
  })
  assert.deepEqual(calls, ['abort:1', 'init'])
})

test('a finished task is not uploaded again', async () => {
  const calls: string[] = []
  const client: DirectUploadClient = {
    init: async () => {
      throw new Error('finished task must not init')
    },
    partUrls: async () => [],
    getTask: async () => ({
      id: 4,
      status: TASK_COMPLETED,
      size: 4,
      multipart: false,
      fileId: 70,
      parts: []
    }),
    confirm: async () => {
      throw new Error('finished task must not confirm')
    },
    complete: async () => {
      throw new Error('finished task must not complete')
    },
    abort: async () => {
      throw new Error('finished task must not abort')
    },
    presignGet: async (id) => {
      calls.push('get:' + id)
      return 'https://cdn.example/done.png'
    },
    put: async () => {
      throw new Error('finished task must not put')
    }
  }
  const result = await runDirectUpload({
    file: source(4),
    client,
    store: memoryStore({ taskId: 4, size: 4, partSize: 8, multipart: false, parts: [] })
  })
  assert.deepEqual(calls, ['get:70'])
  assert.equal(result.fileId, 70)
})

test('missing ETag does not complete the multipart upload', async () => {
  let completed = false
  const client: DirectUploadClient = {
    init: async () => ({ taskId: 9, type: 'video/mp4', multipart: true, partSize: 4, uploadUrl: undefined }),
    partUrls: async (_taskId, partNumbers) =>
      partNumbers.map((partNumber) => ({ partNumber, uploadUrl: 'https://storage.example/part' })),
    getTask: async () => {
      throw new Error('none')
    },
    confirm: async () => 0,
    complete: async () => {
      completed = true
      return 1
    },
    abort: async () => undefined,
    presignGet: async () => 'https://cdn.example/x',
    put: async () => {
      throw new Error('存储响应没有 ETag。请在桶的 CORS 中暴露 ETag 响应头。')
    }
  }
  await assert.rejects(
    runDirectUpload({ file: source(10, 'clip.mp4', 'video/mp4'), client, store: memoryStore() }),
    /ETag/
  )
  assert.equal(completed, false)
})

test('a failed PUT is retried and then confirmed', async () => {
  let attempts = 0
  const client: DirectUploadClient = {
    init: async () => ({
      taskId: 3,
      type: 'image/png',
      multipart: false,
      uploadUrl: 'https://storage.example/put'
    }),
    partUrls: async () => [],
    getTask: async () => {
      throw new Error('none')
    },
    confirm: async () => 8,
    complete: async () => {
      throw new Error('single upload must not complete multipart')
    },
    abort: async () => {
      throw new Error('retry must not abort')
    },
    presignGet: async () => 'https://cdn.example/photo.png',
    put: async () => {
      attempts++
      if (attempts < 3) {
        throw new Error('network')
      }
      return '"ok"'
    }
  }
  const result = await runDirectUpload({ file: source(4), client, store: memoryStore() })
  assert.equal(attempts, 3)
  assert.equal(result.fileId, 8)
})

test('cancel aborts the task and does not confirm', async () => {
  const calls: string[] = []
  const signal = new AbortController()
  const client: DirectUploadClient = {
    init: async () => ({
      taskId: 6,
      type: 'image/png',
      multipart: false,
      uploadUrl: 'https://storage.example/put'
    }),
    partUrls: async () => [],
    getTask: async () => {
      throw new Error('none')
    },
    confirm: async () => {
      throw new Error('cancelled upload must not confirm')
    },
    complete: async () => {
      throw new Error('cancelled upload must not complete')
    },
    abort: async (id) => {
      calls.push('abort:' + id)
    },
    presignGet: async () => 'https://cdn.example/photo.png',
    put: async () => {
      signal.abort()
      const error = new Error('上传已取消')
      error.name = 'AbortError'
      throw error
    }
  }
  const store = memoryStore()
  await assert.rejects(
    runDirectUpload({ file: source(4), client, store, signal: signal.signal }),
    (error) => isUploadAbort(error)
  )
  assert.deepEqual(calls, ['abort:6'])
  assert.equal(store.cleared, true)
})
