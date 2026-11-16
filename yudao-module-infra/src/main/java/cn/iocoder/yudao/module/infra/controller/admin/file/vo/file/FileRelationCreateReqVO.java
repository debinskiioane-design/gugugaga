package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 文件引用 Request VO")
@Data
public class FileRelationCreateReqVO {

    @NotNull(message = "文件编号不能为空")
    private Long fileId;

    @NotEmpty(message = "业务类型不能为空")
    @Schema(description = "业务类型，例如 article", example = "article")
    private String bizType;

    @NotEmpty(message = "业务编号不能为空")
    private String bizId;

}
