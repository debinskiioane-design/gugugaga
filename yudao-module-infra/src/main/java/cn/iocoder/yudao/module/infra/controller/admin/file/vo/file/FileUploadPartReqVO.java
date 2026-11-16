package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 分片上传地址 Request VO")
@Data
public class FileUploadPartReqVO {

    @NotNull(message = "任务编号不能为空")
    private Long taskId;

    @NotEmpty(message = "分片编号不能为空")
    @Schema(description = "要补签的分片编号，从 1 开始")
    private List<Integer> partNumbers;

}
