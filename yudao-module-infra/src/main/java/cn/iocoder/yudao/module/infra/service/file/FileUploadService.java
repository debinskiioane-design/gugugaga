package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadCompleteReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadInitReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadInitRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadPartReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadPartRespVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadTaskPageReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadTaskPageRespVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadTaskRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileRelationDO;

import java.util.List;

public interface FileUploadService {

    FileUploadInitRespVO init(FileUploadInitReqVO reqVO);

    List<FileUploadPartRespVO> partUrls(FileUploadPartReqVO reqVO);

    FileUploadTaskRespVO getTask(Long id);

    PageResult<FileUploadTaskPageRespVO> getTaskPage(FileUploadTaskPageReqVO reqVO);

    Long confirm(Long taskId);

    Long complete(FileUploadCompleteReqVO reqVO);

    void abort(Long taskId);

    /**
     * 清理过期分片，并给库写入失败的任务打上失败标记。返回处理的任务数。
     */
    int cleanExpired();

    FileRelationDO createRelation(Long fileId, String bizType, String bizId);

    List<FileRelationDO> listRelations(Long fileId);

    String presignGetByFileId(Long fileId);

}
