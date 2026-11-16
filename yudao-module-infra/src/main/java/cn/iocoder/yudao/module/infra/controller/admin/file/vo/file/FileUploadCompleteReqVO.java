package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 完成分片 Request VO")
@Data
public class FileUploadCompleteReqVO {

    @NotNull(message = "任务编号不能为空")
    private Long taskId;

    @NotEmpty(message = "分片不能为空")
    @Valid
    private List<Part> parts;

    @Data
    public static class Part {

        @NotNull(message = "分片编号不能为空")
        private Integer partNumber;

        @NotEmpty(message = "ETag 不能为空")
        private String eTag;

    }

}
