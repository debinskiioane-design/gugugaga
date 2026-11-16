package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_UPLOAD_TASK_FORBIDDEN;

/**
 * 当前登录用户。单独拿出来，是为了测试时不必启动安全框架。
 */
@Component
public class FileUploadUserProvider {

    public Long getUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(FILE_UPLOAD_TASK_FORBIDDEN);
        }
        return userId;
    }

}
