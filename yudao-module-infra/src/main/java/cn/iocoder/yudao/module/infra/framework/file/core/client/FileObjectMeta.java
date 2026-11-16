package cn.iocoder.yudao.module.infra.framework.file.core.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HEAD 对象得到的元数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileObjectMeta {

    private Long size;
    private String contentType;
    private String eTag;

}
