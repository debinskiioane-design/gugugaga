package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.util.ReflectUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadCompleteReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadInitReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadPartReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadTaskPageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileOperationLogDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileRelationDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileUploadTaskDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileOperationLogMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileRelationMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileUploadTaskMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileObjectMeta;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FilePartSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.TOO_MANY_REQUESTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_PART_ETAG_MISMATCH;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_REFERENCED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_OBJECT_MISSING;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_SIZE_MISMATCH;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TASK_EXPIRED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TASK_FORBIDDEN;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TYPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileUploadServiceImplTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    private FileUploadServiceImpl service;
    private FileConfigService fileConfigService;
    private FileMapper fileMapper;
    private FileUploadTaskMapper taskMapper;
    private FileRelationMapper relationMapper;
    private FileOperationLogMapper operationLogMapper;
    private FileUploadUserProvider userProvider;
    private FileUploadRateLimiter rateLimiter;
    private FileClient client;
    private final AtomicReference<FileUploadTaskDO> stored = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        service = new FileUploadServiceImpl();
        fileConfigService = mock(FileConfigService.class);
        fileMapper = mock(FileMapper.class);
        taskMapper = mock(FileUploadTaskMapper.class);
        relationMapper = mock(FileRelationMapper.class);
        operationLogMapper = mock(FileOperationLogMapper.class);
        userProvider = mock(FileUploadUserProvider.class);
        rateLimiter = mock(FileUploadRateLimiter.class);
        client = mock(FileClient.class);
        ReflectUtil.setFieldValue(service, "fileConfigService", fileConfigService);
        ReflectUtil.setFieldValue(service, "fileMapper", fileMapper);
        ReflectUtil.setFieldValue(service, "taskMapper", taskMapper);
        ReflectUtil.setFieldValue(service, "relationMapper", relationMapper);
        ReflectUtil.setFieldValue(service, "operationLogMapper", operationLogMapper);
        ReflectUtil.setFieldValue(service, "userProvider", userProvider);
        ReflectUtil.setFieldValue(service, "rateLimiter", rateLimiter);
        ReflectUtil.setFieldValue(service, "multipartThreshold", 50L);
        ReflectUtil.setFieldValue(service, "partSize", 8L);
        ReflectUtil.setFieldValue(service, "maxSize", 1000L);
        ReflectUtil.setFieldValue(service, "expireHours", 24L);
        when(userProvider.getUserId()).thenReturn(5L);
        when(client.getId()).thenReturn(1L);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(fileConfigService.getFileClient(1L)).thenReturn(client);
        when(taskMapper.insert(any(FileUploadTaskDO.class))).thenAnswer(invocation -> {
            FileUploadTaskDO task = invocation.getArgument(0);
            task.setId(7L);
            stored.set(task);
            return 1;
        });
        when(taskMapper.selectById(7L)).thenAnswer(invocation -> stored.get());
        when(taskMapper.updateById(any(FileUploadTaskDO.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(fileMapper.insert(any(FileDO.class))).thenAnswer(invocation -> {
            FileDO file = invocation.getArgument(0);
            file.setId(3L);
            return 1;
        });
        when(operationLogMapper.insert(any(FileOperationLogDO.class))).thenReturn(1);
    }

    @Test
    public void testInit_smallFileDoesNotCreateMultipart() {
        when(client.presignPutUrl(anyString(), anyString())).thenReturn("https://upload.example/put");

        service.init(req(10L, "photo.png", "image/png"));

        verify(client, never()).createMultipartUpload(anyString(), anyString());
        verify(client).presignPutUrl(anyString(), org.mockito.ArgumentMatchers.eq("image/png"));
    }

    @Test
    public void testInit_rateLimitDoesNotSign() {
        doThrow(new ServiceException(TOO_MANY_REQUESTS)).when(rateLimiter).acquireInit(5L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.init(req(10L, "photo.png", "image/png")));

        assertEquals(TOO_MANY_REQUESTS.getCode(), ex.getCode());
        verify(client, never()).presignPutUrl(anyString(), anyString());
        verify(taskMapper, never()).insert(any(FileUploadTaskDO.class));
    }

    @Test
    public void testGetTaskPage_marksPastDueOpenTaskExpired() {
        FileUploadTaskDO task = openTask(false);
        task.setName("banner.webp");
        task.setExpireTime(LocalDateTime.now().minusMinutes(1));
        when(taskMapper.selectPage(any(FileUploadTaskPageReqVO.class)))
                .thenReturn(new PageResult<>(List.of(task), 1L));

        var page = service.getTaskPage(new FileUploadTaskPageReqVO());

        assertEquals(1, page.getList().size());
        assertEquals(4, page.getList().get(0).getStatus());
        assertEquals("banner.webp", page.getList().get(0).getName());
    }

    @Test
    public void testInit_largeFileCreatesMultipart() {
        when(client.createMultipartUpload(anyString(), anyString())).thenReturn("upload-1");

        var resp = service.init(req(50L, "photo.png", "image/png"));

        assertTrue(resp.getMultipart());
        assertEquals("upload-1", stored.get().getUploadId());
        verify(client, never()).presignPutUrl(anyString(), anyString());
    }

    @Test
    public void testPartUrls_doesNotCreateAnotherUpload() {
        stored.set(openTask(true));
        when(client.presignUploadPart(anyString(), anyString(), anyInt())).thenReturn("https://upload.example/part");
        FileUploadPartReqVO req = new FileUploadPartReqVO();
        req.setTaskId(7L);
        req.setPartNumbers(List.of(1, 2));

        service.partUrls(req);

        verify(client, never()).createMultipartUpload(anyString(), anyString());
        verify(client).presignUploadPart(stored.get().getPath(), "upload-1", 1);
    }

    @Test
    public void testPartUrls_otherUserForbidden() {
        stored.set(openTask(true));
        when(userProvider.getUserId()).thenReturn(9L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.partUrls(partReq()));

        assertEquals(FILE_UPLOAD_TASK_FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    public void testPartUrls_expiredDoesNotSign() {
        FileUploadTaskDO task = openTask(true);
        task.setExpireTime(LocalDateTime.now().minusMinutes(1));
        stored.set(task);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.partUrls(partReq()));

        assertEquals(FILE_UPLOAD_TASK_EXPIRED.getCode(), ex.getCode());
        verify(client, never()).presignUploadPart(anyString(), anyString(), anyInt());
    }

    @Test
    public void testGet_expiredDoesNotListParts() {
        FileUploadTaskDO task = openTask(true);
        task.setExpireTime(LocalDateTime.now().minusMinutes(1));
        stored.set(task);

        var resp = service.getTask(7L);

        assertEquals(FileUploadTaskStatus.EXPIRED, resp.getStatus());
        assertTrue(resp.getParts().isEmpty());
        verify(client, never()).listParts(anyString(), anyString());
    }

    @Test
    public void testConfirm_missingObject() {
        stored.set(openTask(false));
        when(client.head(anyString())).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(7L));

        assertEquals(FILE_UPLOAD_OBJECT_MISSING.getCode(), ex.getCode());
        verify(fileMapper, never()).insert(any(FileDO.class));
    }

    @Test
    public void testConfirm_sizeMismatch() {
        stored.set(openTask(false));
        when(client.head(anyString())).thenReturn(new FileObjectMeta(9L, "image/png", "etag"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(7L));

        assertEquals(FILE_UPLOAD_SIZE_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    public void testConfirm_typeMismatch() {
        stored.set(openTask(false));
        when(client.head(anyString())).thenReturn(new FileObjectMeta(10L, "image/png", "etag"));
        when(client.readPrefix(anyString(), anyInt())).thenReturn("<html></html>".getBytes());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.confirm(7L));

        assertEquals(FILE_UPLOAD_TYPE_MISMATCH.getCode(), ex.getCode());
        verify(fileMapper, never()).insert(any(FileDO.class));
    }

    @Test
    public void testConfirm_success() {
        stored.set(openTask(false));
        when(client.head(anyString())).thenReturn(new FileObjectMeta(10L, "image/png", "etag"));
        when(client.readPrefix(anyString(), anyInt())).thenReturn(PNG);
        when(client.stableUrl(anyString())).thenReturn("https://static.example/a.png");

        Long fileId = service.confirm(7L);

        assertEquals(3L, fileId);
        assertEquals(FileUploadTaskStatus.COMPLETED, stored.get().getStatus());
        verify(operationLogMapper).insert(any(FileOperationLogDO.class));
    }

    @Test
    public void testComplete_wrongEtag() {
        stored.set(openTask(true));
        when(client.listParts(anyString(), anyString()))
                .thenReturn(List.of(new FilePartSummary(1, "\"abc\"", 10L)));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.complete(completeReq("nope")));

        assertEquals(FILE_PART_ETAG_MISMATCH.getCode(), ex.getCode());
        verify(client, never()).completeMultipartUpload(anyString(), anyString(), any());
    }

    @Test
    public void testComplete_twiceReturnsSameFile() {
        stored.set(openTask(true));
        when(client.listParts(anyString(), anyString()))
                .thenReturn(List.of(new FilePartSummary(1, "abc", 10L)));
        when(client.head(anyString())).thenReturn(new FileObjectMeta(10L, "image/png", "etag"));
        when(client.readPrefix(anyString(), anyInt())).thenReturn(PNG);
        when(client.stableUrl(anyString())).thenReturn("https://static.example/a.png");

        Long first = service.complete(completeReq("abc"));
        Long second = service.complete(completeReq("abc"));

        assertEquals(3L, first);
        assertEquals(first, second);
        verify(client).completeMultipartUpload(anyString(), anyString(), any());
    }

    @Test
    public void testDelete_referencedFileStays() throws Exception {
        FileServiceImpl fileService = new FileServiceImpl();
        ReflectUtil.setFieldValue(fileService, "fileMapper", fileMapper);
        ReflectUtil.setFieldValue(fileService, "fileRelationMapper", relationMapper);
        ReflectUtil.setFieldValue(fileService, "fileConfigService", fileConfigService);
        FileDO file = new FileDO();
        file.setId(3L);
        file.setConfigId(1L);
        file.setPath("20240101/a.png");
        when(fileMapper.selectById(3L)).thenReturn(file);
        when(relationMapper.selectCountByFileId(3L)).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class, () -> fileService.deleteFile(3L));

        assertEquals(FILE_REFERENCED.getCode(), ex.getCode());
        verify(client, never()).delete(anyString());
    }

    @Test
    public void testListRelations_returnsRowsForExistingFile() {
        FileDO file = new FileDO();
        file.setId(3L);
        when(fileMapper.selectById(3L)).thenReturn(file);
        FileRelationDO row = new FileRelationDO();
        row.setFileId(3L);
        row.setBizType("article");
        row.setBizId("20");
        when(relationMapper.selectListByFileId(3L)).thenReturn(List.of(row));

        List<FileRelationDO> rows = service.listRelations(3L);

        assertEquals(1, rows.size());
        assertEquals("article", rows.get(0).getBizType());
        assertEquals("20", rows.get(0).getBizId());
    }

    @Test
    public void testListRelations_missingFile() {
        when(fileMapper.selectById(3L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.listRelations(3L));

        assertEquals(FILE_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    public void testClean_twiceAndAbortFailure() {
        FileUploadTaskDO task = openTask(true);
        task.setExpireTime(LocalDateTime.now().minusHours(1));
        when(taskMapper.selectExpired(any())).thenReturn(List.of(task), List.of());
        when(operationLogMapper.selectFailed(100)).thenReturn(List.of());

        assertEquals(1, service.cleanExpired());
        assertEquals(0, service.cleanExpired());
        assertEquals(FileUploadTaskStatus.EXPIRED, task.getStatus());

        FileUploadTaskDO stuck = openTask(true);
        stuck.setId(8L);
        stuck.setExpireTime(LocalDateTime.now().minusHours(1));
        when(taskMapper.selectById(8L)).thenReturn(stuck);
        when(taskMapper.selectExpired(any())).thenReturn(List.of(stuck));
        doThrow(new IllegalStateException("network")).when(client).abortMultipartUpload(anyString(), anyString());
        when(operationLogMapper.selectFailed(100)).thenReturn(List.of());

        assertEquals(0, service.cleanExpired());
        assertEquals(FileUploadTaskStatus.INIT, stuck.getStatus());

        FileOperationLogDO failed = new FileOperationLogDO();
        failed.setTaskId(8L);
        failed.setSuccess(false);
        when(taskMapper.selectExpired(any())).thenReturn(List.of());
        when(operationLogMapper.selectFailed(100)).thenReturn(List.of(failed));

        assertEquals(1, service.cleanExpired());
        assertEquals(FileUploadTaskStatus.FAILED, stuck.getStatus());
    }

    private FileUploadInitReqVO req(Long size, String name, String type) {
        FileUploadInitReqVO req = new FileUploadInitReqVO();
        req.setName(name);
        req.setSize(size);
        req.setContentType(type);
        return req;
    }

    private FileUploadPartReqVO partReq() {
        FileUploadPartReqVO req = new FileUploadPartReqVO();
        req.setTaskId(7L);
        req.setPartNumbers(List.of(1));
        return req;
    }

    private FileUploadCompleteReqVO completeReq(String etag) {
        FileUploadCompleteReqVO req = new FileUploadCompleteReqVO();
        req.setTaskId(7L);
        FileUploadCompleteReqVO.Part part = new FileUploadCompleteReqVO.Part();
        part.setPartNumber(1);
        part.setETag(etag);
        req.setParts(List.of(part));
        return req;
    }

    private FileUploadTaskDO openTask(boolean multipart) {
        FileUploadTaskDO task = new FileUploadTaskDO();
        task.setId(7L);
        task.setConfigId(1L);
        task.setOwnerUserId(5L);
        task.setName("a.png");
        task.setPath("20240101/a.png");
        task.setType("image/png");
        task.setSize(10L);
        task.setStatus(FileUploadTaskStatus.INIT);
        task.setMultipart(multipart);
        task.setUploadId(multipart ? "upload-1" : null);
        task.setExpireTime(LocalDateTime.now().plusHours(1));
        return task;
    }

}
