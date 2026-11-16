package cn.iocoder.yudao.module.infra.dal.mysql.file;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileOperationLogDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FileOperationLogMapper extends BaseMapperX<FileOperationLogDO> {

    default List<FileOperationLogDO> selectFailed(int limit) {
        return selectList(new LambdaQueryWrapperX<FileOperationLogDO>()
                .eq(FileOperationLogDO::getSuccess, false)
                .orderByAsc(FileOperationLogDO::getId)
                .last("LIMIT " + limit));
    }

}
