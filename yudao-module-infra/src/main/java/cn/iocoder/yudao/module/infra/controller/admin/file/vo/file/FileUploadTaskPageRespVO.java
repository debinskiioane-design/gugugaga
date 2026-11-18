package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 上传任务分页 Response VO")
@Data
public class FileUploadTaskPageRespVO {

    private Long id;

    @Schema(description = "原文件名")
    private String name;

    @Schema(description = "0 初始化 1 上传中 2 已完成 3 已取消 4 已过期 5 失败")
    private Integer status;

    private Long size;

    private Boolean multipart;

    private Long fileId;

    private Long ownerUserId;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

}
