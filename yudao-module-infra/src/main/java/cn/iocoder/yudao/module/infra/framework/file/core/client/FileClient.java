package cn.iocoder.yudao.module.infra.framework.file.core.client;

import java.util.List;

/**
 * 文件客户端
 *
 * @author 芋道源码
 */
public interface FileClient {

    /**
     * 获得客户端编号
     *
     * @return 客户端编号
     */
    Long getId();

    /**
     * 上传文件
     *
     * @param content 文件流
     * @param path    相对路径
     * @return 完整路径，即 HTTP 访问地址
     * @throws Exception 上传文件时，抛出 Exception 异常
     */
    String upload(byte[] content, String path, String type) throws Exception;

    /**
     * 删除文件
     *
     * @param path 相对路径
     * @throws Exception 删除文件时，抛出 Exception 异常
     */
    void delete(String path) throws Exception;

    /**
     * 获得文件的内容
     *
     * @param path 相对路径
     * @return 文件的内容
     */
    byte[] getContent(String path) throws Exception;

    // ========== 文件签名，目前仅 S3 支持 ==========

    /**
     * 获得文件预签名地址，用于上传
     *
     * @param path 相对路径
     * @return 文件预签名地址
     */
    default String presignPutUrl(String path) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 获得文件预签名地址，用于上传。contentType 仅在存储器需要把它签进 URL 时生效。
     *
     * @param path        相对路径
     * @param contentType 浏览器实际上传时携带的 Content-Type，允许空
     * @return 文件预签名地址
     */
    default String presignPutUrl(String path, String contentType) {
        return presignPutUrl(path);
    }

    /**
     * 生成文件预签名地址，用于读取
     *
     * @param url 完整的文件访问地址
     * @param expirationSeconds 访问有效期，单位秒
     * @return 文件预签名地址
     */
    default String presignGetUrl(String url, Integer expirationSeconds) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 查询对象。不存在时返回 null。
     */
    default FileObjectMeta head(String path) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 读取对象开头，用来识别文件头。不把整个对象拉回应用。
     */
    default byte[] readPrefix(String path, int maxBytes) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 创建分片上传，返回 uploadId。
     */
    default String createMultipartUpload(String path, String contentType) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 为已有 uploadId 签发单个分片的上传地址。不会新建一次分片上传。
     */
    default String presignUploadPart(String path, String uploadId, int partNumber) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    default List<FilePartSummary> listParts(String path, String uploadId) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    default void completeMultipartUpload(String path, String uploadId, List<FilePartSummary> parts) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    default void abortMultipartUpload(String path, String uploadId) {
        throw new UnsupportedOperationException("不支持的操作");
    }

    /**
     * 不带签名的访问地址。私有桶上它不能直接打开，读取时再另行签发。
     */
    default String stableUrl(String path) {
        throw new UnsupportedOperationException("不支持的操作");
    }

}
