package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 上传失败和清理记录。message 只写原因，不写密钥和预签名地址。
 */
@TableName("infra_file_operation_log")
@KeySequence("infra_file_operation_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TenantIgnore
public class FileOperationLogDO extends BaseDO {

    private Long id;
    private Long taskId;
    private Long fileId;
    private String action;
    private Boolean success;
    private String message;

}
