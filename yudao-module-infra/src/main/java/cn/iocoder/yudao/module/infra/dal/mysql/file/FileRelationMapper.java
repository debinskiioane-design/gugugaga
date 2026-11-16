package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileRelationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FileRelationMapper extends BaseMapperX<FileRelationDO> {

    default Long selectCountByFileId(Long fileId) {
        return selectCount(new LambdaQueryWrapperX<FileRelationDO>()
                .eq(FileRelationDO::getFileId, fileId));
    }

    default List<FileRelationDO> selectListByFileId(Long fileId) {
        return selectList(new LambdaQueryWrapperX<FileRelationDO>()
                .eq(FileRelationDO::getFileId, fileId)
                .orderByDesc(FileRelationDO::getId));
    }

    default FileRelationDO selectByBiz(Long fileId, String bizType, String bizId) {
        return selectOne(new LambdaQueryWrapperX<FileRelationDO>()
                .eq(FileRelationDO::getFileId, fileId)
                .eq(FileRelationDO::getBizType, bizType)
                .eq(FileRelationDO::getBizId, bizId));
    }

}
