package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 上传任务 Response VO")
@Data
public class FileUploadTaskRespVO {

    private Long id;
    private Integer status;
    private String path;
    private String type;
    private Long size;
    private Boolean multipart;
    private Long fileId;
    private LocalDateTime expireTime;
    private List<Part> parts;

    @Data
    public static class Part {

        private Integer partNumber;
        private Long size;

    }

}
