package cn.iocoder.yudao.module.infra.framework.file.core.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 存储器上已经收到的一个分片。ETag 以存储器返回的为准。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilePartSummary {

    private Integer partNumber;
    private String eTag;
    private Long size;

}
