package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 文件直传初始化 Response VO")
@Data
public class FileUploadInitRespVO {

    private Long taskId;
    private Long configId;
    private String path;
    private String type;
    private Boolean multipart;
    private Long partSize;
    /**
     * 小文件的 PUT 地址。分片时为空，改调 part-urls。
     */
    private String uploadUrl;

}
