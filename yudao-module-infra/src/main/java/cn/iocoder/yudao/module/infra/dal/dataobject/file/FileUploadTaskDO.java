package cn.iocoder.yudao.module.infra.dal.dataobject.file;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 文件直传任务。对象名由后端生成，uploadId 只存在这里。
 */
@TableName("infra_file_upload_task")
@KeySequence("infra_file_upload_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TenantIgnore
public class FileUploadTaskDO extends BaseDO {

    private Long id;
    private Long configId;
    private Long ownerUserId;
    private String name;
    private String path;
    private String type;
    private Long size;
    private String hash;
    /**
     * 0 初始化，1 上传中，2 已完成，3 已取消，4 已过期，5 失败
     */
    private Integer status;
    private Boolean multipart;
    private String uploadId;
    private Long fileId;
    private LocalDateTime expireTime;

}
