package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 分片上传地址 Response VO")
@Data
public class FileUploadPartRespVO {

    private Integer partNumber;
    private String uploadUrl;

}
