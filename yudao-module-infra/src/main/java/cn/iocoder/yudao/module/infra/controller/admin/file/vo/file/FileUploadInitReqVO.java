package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 文件直传初始化 Request VO")
@Data
public class FileUploadInitReqVO {

    @NotEmpty(message = "文件名不能为空")
    @Schema(description = "原文件名", requiredMode = Schema.RequiredMode.REQUIRED, example = "photo.jpg")
    private String name;

    @Schema(description = "目录", example = "cms")
    private String directory;

    @NotNull(message = "文件大小不能为空")
    @Schema(description = "文件大小，字节", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long size;

    @Schema(description = "浏览器实际上传时使用的 Content-Type", example = "image/jpeg")
    private String contentType;

    @Schema(description = "客户端声明的哈希")
    private String hash;

}
