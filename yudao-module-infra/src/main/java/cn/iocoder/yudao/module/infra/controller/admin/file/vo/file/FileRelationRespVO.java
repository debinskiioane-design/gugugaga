package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 文件引用 Response VO")
@Data
public class FileRelationRespVO {

    private Long id;

    private Long fileId;

    @Schema(description = "业务类型", example = "article")
    private String bizType;

    @Schema(description = "业务编号", example = "20001")
    private String bizId;

    private LocalDateTime createTime;

}
