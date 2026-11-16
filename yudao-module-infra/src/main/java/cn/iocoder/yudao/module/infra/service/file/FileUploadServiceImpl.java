package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadCompleteReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadInitReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadInitRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadPartReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadPartRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadTaskRespVO;
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
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.hutool.core.date.DatePattern.PURE_DATE_PATTERN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_IS_EMPTY;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_PART_ETAG_MISMATCH;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_TOO_LARGE;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_TYPE_NOT_ALLOWED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_OBJECT_MISSING;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_SIZE_MISMATCH;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TASK_EXPIRED;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TASK_FORBIDDEN;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TASK_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TASK_STATE;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TYPE_MISMATCH;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.ABORTED;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.ACTION_ABORT;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.ACTION_CLEAN;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.ACTION_COMPLETE;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.ACTION_CONFIRM;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.COMPLETED;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.EXPIRED;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.FAILED;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.FILE_NORMAL;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.INIT;
import static cn.iocoder.yudao.module.infra.service.file.FileUploadTaskStatus.UPLOADING;

/**
 * 直传和分片的状态机。控制器不接触 S3 客户端。
 */
@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    /**
     * 比全局白名单更严：不接受 HTML、SVG、XML。
     */
    private static final Set<String> MEDIA_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
            "image/vnd.microsoft.icon", "image/tiff", "image/avif", "image/heic", "image/heif",
            "application/pdf", "text/plain", "text/csv", "text/markdown",
            "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip", "application/x-rar-compressed", "application/x-7z-compressed", "application/gzip",
            "audio/mpeg", "audio/vnd.wave", "audio/vorbis", "audio/ogg", "audio/opus", "audio/mp4", "audio/webm",
            "audio/x-aac", "audio/x-flac", "audio/amr", "audio/x-ms-wma",
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/webm", "video/x-ms-wmv", "video/x-flv",
            "application/x-matroska", "video/x-matroska"
    );

    private static final int PREFIX_BYTES = 4096;
    private static final int MAX_PART_NUMBER = 10000;
    private static final int PRIVATE_URL_SECONDS = 300;

    @Resource
    private FileConfigService fileConfigService;
    @Resource
    private FileMapper fileMapper;
    @Resource
    private FileUploadTaskMapper taskMapper;
    @Resource
    private FileRelationMapper relationMapper;
    @Resource
    private FileOperationLogMapper operationLogMapper;
    @Resource
    private FileUploadUserProvider userProvider;

    @Value("${yudao.file.upload.multipart-threshold:52428800}")
    private long multipartThreshold;
    @Value("${yudao.file.upload.part-size:8388608}")
    private long partSize;
    @Value("${yudao.file.upload.max-size:5368709120}")
    private long maxSize;
    @Value("${yudao.file.upload.expire-hours:24}")
    private long expireHours;

    @Override
    public FileUploadInitRespVO init(FileUploadInitReqVO reqVO) {
        String name = FilePathUtils.validateFileName(reqVO.getName());
        FilePathUtils.validateDirectory(reqVO.getDirectory());
        if (reqVO.getSize() == null || reqVO.getSize() < 1) {
            throw exception(FILE_IS_EMPTY);
        }
        if (reqVO.getSize() > maxSize) {
            throw exception(FILE_TOO_LARGE);
        }
        String nameType = FileTypeUtils.getMineType(name);
        String signedType = StrUtil.blankToDefault(reqVO.getContentType(), nameType);
        if (!MEDIA_TYPES.contains(nameType) || !nameType.equals(signedType)) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }

        FileClient client = masterClient();
        String path = randomPath(name, reqVO.getDirectory());
        boolean multipart = reqVO.getSize() >= multipartThreshold;
        String uploadId = null;
        String uploadUrl = null;
        if (multipart) {
            uploadId = client.createMultipartUpload(path, signedType);
        } else {
            uploadUrl = client.presignPutUrl(path, signedType);
        }

        FileUploadTaskDO task = new FileUploadTaskDO();
        task.setConfigId(client.getId());
        task.setOwnerUserId(userProvider.getUserId());
        task.setName(name);
        task.setPath(path);
        task.setType(signedType);
        task.setSize(reqVO.getSize());
        task.setHash(reqVO.getHash());
        task.setStatus(INIT);
        task.setMultipart(multipart);
        task.setUploadId(uploadId);
        task.setExpireTime(LocalDateTime.now().plusHours(expireHours));
        taskMapper.insert(task);
        log.info("[init][task({}) multipart({})]", task.getId(), multipart);

        FileUploadInitRespVO resp = new FileUploadInitRespVO();
        resp.setTaskId(task.getId());
        resp.setConfigId(client.getId());
        resp.setPath(path);
        resp.setType(signedType);
        resp.setMultipart(multipart);
        resp.setPartSize(partSize);
        resp.setUploadUrl(uploadUrl);
        return resp;
    }

    @Override
    public List<FileUploadPartRespVO> partUrls(FileUploadPartReqVO reqVO) {
        FileUploadTaskDO task = requireActive(reqVO.getTaskId());
        if (!Boolean.TRUE.equals(task.getMultipart())) {
            throw exception(FILE_UPLOAD_TASK_STATE);
        }
        FileClient client = clientOf(task);
        List<FileUploadPartRespVO> urls = new ArrayList<>();
        for (Integer partNumber : reqVO.getPartNumbers()) {
            if (partNumber == null || partNumber < 1 || partNumber > MAX_PART_NUMBER) {
                throw exception(FILE_UPLOAD_TASK_STATE);
            }
            FileUploadPartRespVO item = new FileUploadPartRespVO();
            item.setPartNumber(partNumber);
            item.setUploadUrl(client.presignUploadPart(task.getPath(), task.getUploadId(), partNumber));
            urls.add(item);
        }
        if (task.getStatus() == INIT) {
            task.setStatus(UPLOADING);
            taskMapper.updateById(task);
        }
        return urls;
    }

    @Override
    public FileUploadTaskRespVO getTask(Long id) {
        FileUploadTaskDO task = requireOwner(id);
        FileUploadTaskRespVO resp = new FileUploadTaskRespVO();
        resp.setId(task.getId());
        resp.setStatus(displayStatus(task));
        resp.setPath(task.getPath());
        resp.setType(task.getType());
        resp.setSize(task.getSize());
        resp.setMultipart(task.getMultipart());
        resp.setFileId(task.getFileId());
        resp.setExpireTime(task.getExpireTime());
        resp.setParts(List.of());
        if (!Boolean.TRUE.equals(task.getMultipart()) || StrUtil.isEmpty(task.getUploadId()) || !isOpen(task)) {
            return resp;
        }
        List<FileUploadTaskRespVO.Part> parts = new ArrayList<>();
        for (FilePartSummary summary : clientOf(task).listParts(task.getPath(), task.getUploadId())) {
            FileUploadTaskRespVO.Part part = new FileUploadTaskRespVO.Part();
            part.setPartNumber(summary.getPartNumber());
            part.setSize(summary.getSize());
            parts.add(part);
        }
        resp.setParts(parts);
        return resp;
    }

    @Override
    public Long confirm(Long taskId) {
        FileUploadTaskDO task = requireActive(taskId);
        if (Boolean.TRUE.equals(task.getMultipart())) {
            throw exception(FILE_UPLOAD_TASK_STATE);
        }
        try {
            FileObjectMeta meta = requireObject(task);
            Long fileId = insertFile(task, meta);
            markCompleted(task, fileId);
            writeLog(task, ACTION_CONFIRM, true, "确认成功");
            return fileId;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            writeLog(task, ACTION_CONFIRM, false, "数据库写入失败");
            throw ex;
        }
    }

    @Override
    public Long complete(FileUploadCompleteReqVO reqVO) {
        FileUploadTaskDO existing = requireOwner(reqVO.getTaskId());
        if (existing.getStatus() != null && existing.getStatus() == COMPLETED && existing.getFileId() != null) {
            return existing.getFileId();
        }
        FileUploadTaskDO task = requireActive(reqVO.getTaskId());
        if (!Boolean.TRUE.equals(task.getMultipart())) {
            throw exception(FILE_UPLOAD_TASK_STATE);
        }
        FileClient client = clientOf(task);
        List<FilePartSummary> stored = client.listParts(task.getPath(), task.getUploadId());
        assertPartsMatch(reqVO.getParts(), stored);
        client.completeMultipartUpload(task.getPath(), task.getUploadId(), stored);
        try {
            FileObjectMeta meta = requireObject(task);
            Long fileId = insertFile(task, meta);
            markCompleted(task, fileId);
            writeLog(task, ACTION_COMPLETE, true, "分片完成");
            return fileId;
        } catch (ServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            writeLog(task, ACTION_COMPLETE, false, "数据库写入失败");
            throw ex;
        }
    }

    @Override
    public void abort(Long taskId) {
        FileUploadTaskDO task = requireOwner(taskId);
        if (task.getStatus() != null && (task.getStatus() == ABORTED || task.getStatus() == COMPLETED)) {
            return;
        }
        if (pastExpire(task) && task.getStatus() != null && task.getStatus() != INIT && task.getStatus() != UPLOADING) {
            throw exception(FILE_UPLOAD_TASK_STATE);
        }
        if (!abortStorage(task, ACTION_ABORT)) {
            throw exception(FILE_UPLOAD_TASK_STATE);
        }
        task.setStatus(ABORTED);
        taskMapper.updateById(task);
        writeLog(task, ACTION_ABORT, true, "已取消");
    }

    @Override
    public int cleanExpired() {
        int handled = 0;
        for (FileUploadTaskDO task : taskMapper.selectExpired(LocalDateTime.now())) {
            if (!abortStorage(task, ACTION_CLEAN)) {
                continue;
            }
            task.setStatus(EXPIRED);
            taskMapper.updateById(task);
            writeLog(task, ACTION_CLEAN, true, "过期任务已清理");
            handled++;
        }
        for (FileOperationLogDO failed : operationLogMapper.selectFailed(100)) {
            if (failed.getTaskId() == null) {
                continue;
            }
            FileUploadTaskDO task = taskMapper.selectById(failed.getTaskId());
            if (task == null || task.getStatus() == null
                    || (task.getStatus() != INIT && task.getStatus() != UPLOADING)) {
                continue;
            }
            task.setStatus(FAILED);
            taskMapper.updateById(task);
            writeLog(task, ACTION_CLEAN, true, "库写入失败，对象保留并标记失败");
            handled++;
        }
        return handled;
    }

    @Override
    public FileRelationDO createRelation(Long fileId, String bizType, String bizId) {
        if (fileMapper.selectById(fileId) == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        FileRelationDO existing = relationMapper.selectByBiz(fileId, bizType, bizId);
        if (existing != null) {
            return existing;
        }
        FileRelationDO relation = new FileRelationDO();
        relation.setFileId(fileId);
        relation.setBizType(bizType);
        relation.setBizId(bizId);
        relationMapper.insert(relation);
        return relation;
    }

    @Override
    public String presignGetByFileId(Long fileId) {
        FileDO file = fileMapper.selectById(fileId);
        if (file == null) {
            throw exception(FILE_NOT_EXISTS);
        }
        FileClient client = clientOf(file.getConfigId());
        return client.presignGetUrl(file.getPath(), PRIVATE_URL_SECONDS);
    }

    private FileObjectMeta requireObject(FileUploadTaskDO task) {
        FileObjectMeta meta = clientOf(task).head(task.getPath());
        if (meta == null || meta.getSize() == null) {
            throw exception(FILE_UPLOAD_OBJECT_MISSING);
        }
        if (!task.getSize().equals(meta.getSize())) {
            throw exception(FILE_UPLOAD_SIZE_MISMATCH);
        }
        byte[] prefix = clientOf(task).readPrefix(task.getPath(), PREFIX_BYTES);
        if (prefix == null || prefix.length == 0) {
            throw exception(FILE_UPLOAD_OBJECT_MISSING);
        }
        String sniffed = FileTypeUtils.getMineType(prefix, task.getName());
        if (!task.getType().equals(sniffed)) {
            throw exception(FILE_UPLOAD_TYPE_MISMATCH);
        }
        return meta;
    }

    private Long insertFile(FileUploadTaskDO task, FileObjectMeta meta) {
        FileDO existing = fileMapper.selectLatestByConfigIdAndPath(task.getConfigId(), task.getPath());
        if (existing != null) {
            return existing.getId();
        }
        FileDO file = new FileDO();
        file.setConfigId(task.getConfigId());
        file.setName(task.getName());
        file.setPath(task.getPath());
        file.setUrl(clientOf(task).stableUrl(task.getPath()));
        file.setType(task.getType());
        file.setSize(meta.getSize());
        file.setHash(task.getHash());
        file.setStatus(FILE_NORMAL);
        fileMapper.insert(file);
        return file.getId();
    }

    private void assertPartsMatch(List<FileUploadCompleteReqVO.Part> claimed, List<FilePartSummary> stored) {
        if (claimed.size() != stored.size()) {
            throw exception(FILE_PART_ETAG_MISMATCH);
        }
        Map<Integer, String> actual = new HashMap<>();
        for (FilePartSummary part : stored) {
            actual.put(part.getPartNumber(), normalizeEtag(part.getETag()));
        }
        for (FileUploadCompleteReqVO.Part part : claimed) {
            String etag = actual.get(part.getPartNumber());
            if (etag == null || !etag.equals(normalizeEtag(part.getETag()))) {
                throw exception(FILE_PART_ETAG_MISMATCH);
            }
        }
    }

    private boolean abortStorage(FileUploadTaskDO task, String action) {
        if (!Boolean.TRUE.equals(task.getMultipart()) || StrUtil.isEmpty(task.getUploadId())) {
            return true;
        }
        try {
            clientOf(task).abortMultipartUpload(task.getPath(), task.getUploadId());
            return true;
        } catch (RuntimeException ex) {
            if (isAlreadyGone(ex)) {
                return true;
            }
            writeLog(task, action, false, "取消分片失败");
            log.warn("[abortStorage][task({}) 取消分片失败]", task.getId());
            return false;
        }
    }

    private void markCompleted(FileUploadTaskDO task, Long fileId) {
        task.setStatus(COMPLETED);
        task.setFileId(fileId);
        taskMapper.updateById(task);
    }

    private FileUploadTaskDO requireActive(Long id) {
        FileUploadTaskDO task = requireOwner(id);
        if (task.getStatus() != null && task.getStatus() == COMPLETED) {
            throw exception(FILE_UPLOAD_TASK_STATE);
        }
        if (!isOpen(task)) {
            if (pastExpire(task)) {
                throw exception(FILE_UPLOAD_TASK_EXPIRED);
            }
            throw exception(FILE_UPLOAD_TASK_STATE);
        }
        return task;
    }

    private FileUploadTaskDO requireOwner(Long id) {
        FileUploadTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(FILE_UPLOAD_TASK_NOT_EXISTS);
        }
        if (!userProvider.getUserId().equals(task.getOwnerUserId())) {
            throw exception(FILE_UPLOAD_TASK_FORBIDDEN);
        }
        return task;
    }

    private boolean isOpen(FileUploadTaskDO task) {
        if (task.getStatus() == null || (task.getStatus() != INIT && task.getStatus() != UPLOADING)) {
            return false;
        }
        return !pastExpire(task);
    }

    private boolean pastExpire(FileUploadTaskDO task) {
        return task.getExpireTime() != null && task.getExpireTime().isBefore(LocalDateTime.now());
    }

    private int displayStatus(FileUploadTaskDO task) {
        if (isOpen(task) || task.getStatus() == null) {
            return task.getStatus() == null ? INIT : task.getStatus();
        }
        if (pastExpire(task) && (task.getStatus() == INIT || task.getStatus() == UPLOADING)) {
            return EXPIRED;
        }
        return task.getStatus();
    }

    private void writeLog(FileUploadTaskDO task, String action, boolean success, String message) {
        FileOperationLogDO row = new FileOperationLogDO();
        row.setTaskId(task.getId());
        row.setFileId(task.getFileId());
        row.setAction(action);
        row.setSuccess(success);
        row.setMessage(StrUtil.maxLength(message, 500));
        operationLogMapper.insert(row);
    }

    private FileClient masterClient() {
        FileClient client = fileConfigService.getMasterFileClient();
        Assert.notNull(client, "客户端(master) 不能为空");
        return client;
    }

    private FileClient clientOf(FileUploadTaskDO task) {
        return clientOf(task.getConfigId());
    }

    private FileClient clientOf(Long configId) {
        FileClient client = fileConfigService.getFileClient(configId);
        Assert.notNull(client, "客户端({}) 不能为空", configId);
        return client;
    }

    private static String randomPath(String name, String directory) {
        String ext = FileUtil.extName(name);
        String objectName = IdUtil.fastSimpleUUID() + (StrUtil.isEmpty(ext) ? "" : "." + ext);
        String path = LocalDateTimeUtil.format(LocalDateTime.now(), PURE_DATE_PATTERN) + "/" + objectName;
        if (StrUtil.isNotEmpty(directory)) {
            path = directory + "/" + path;
        }
        FilePathUtils.validatePath(path);
        return path;
    }

    private static String normalizeEtag(String etag) {
        if (etag == null) {
            return "";
        }
        return StrUtil.removeSuffix(StrUtil.removePrefix(etag.trim(), "\""), "\"");
    }

    private static boolean isAlreadyGone(RuntimeException ex) {
        String text = ex.getClass().getSimpleName();
        if (text.contains("NoSuchUpload") || text.contains("NoSuchKey")) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && (message.contains("NoSuchUpload") || message.contains("NoSuchKey"));
    }

}
