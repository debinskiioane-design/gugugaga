package cn.iocoder.yudao.module.infra.job.file;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.infra.service.file.FileUploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 清理过期分片。任务登记在 infra_job，处理器名是 fileUploadCleanJob。
 */
@Component
@Slf4j
public class FileUploadCleanJob implements JobHandler {

    @Resource
    private FileUploadService fileUploadService;

    @Override
    @TenantIgnore
    public String execute(String param) {
        int count = fileUploadService.cleanExpired();
        log.info("[execute][清理上传任务 ({}) 个]", count);
        return String.format("清理上传任务 %s 个", count);
    }

}
