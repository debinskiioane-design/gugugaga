package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.file.FileUploadTaskPageReqVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileUploadTaskDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileUploadTaskMapper extends BaseMapperX<FileUploadTaskDO> {

    default PageResult<FileUploadTaskDO> selectPage(FileUploadTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FileUploadTaskDO>()
                .likeIfPresent(FileUploadTaskDO::getName, reqVO.getName())
                .eqIfPresent(FileUploadTaskDO::getStatus, reqVO.getStatus())
                .orderByDesc(FileUploadTaskDO::getId));
    }

    default List<FileUploadTaskDO> selectExpired(LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<FileUploadTaskDO>()
                .in(FileUploadTaskDO::getStatus, 0, 1)
                .lt(FileUploadTaskDO::getExpireTime, now));
    }

}
