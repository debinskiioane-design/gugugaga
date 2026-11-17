package cn.iocoder.yudao.module.infra.controller.admin.file.vo.file;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 上传任务分页 Request VO")
@Data
public class FileUploadTaskPageReqVO extends PageParam {

    @Schema(description = "原文件名，模糊匹配", example = "banner")
    private String name;

    @Schema(description = "状态。0 初始化 1 上传中 2 已完成 3 已取消 4 已过期 5 失败", example = "1")
    private Integer status;

}
