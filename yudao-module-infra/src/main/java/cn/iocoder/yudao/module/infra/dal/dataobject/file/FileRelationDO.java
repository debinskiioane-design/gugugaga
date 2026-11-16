package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 业务只存文件编号。文章、站点以后写 bizType 和 bizId，不存完整 URL。
 */
@TableName("infra_file_relation")
@KeySequence("infra_file_relation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TenantIgnore
public class FileRelationDO extends BaseDO {

    private Long id;
    private Long fileId;
    private String bizType;
    private String bizId;

}
