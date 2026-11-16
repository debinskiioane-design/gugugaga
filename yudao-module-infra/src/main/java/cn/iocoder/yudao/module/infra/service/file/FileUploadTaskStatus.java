package cn.iocoder.yudao.module.infra.service.file;

/**
 * 上传任务和文件主数据的状态。
 */
public final class FileUploadTaskStatus {

    public static final int INIT = 0;
    public static final int UPLOADING = 1;
    public static final int COMPLETED = 2;
    public static final int ABORTED = 3;
    public static final int EXPIRED = 4;
    public static final int FAILED = 5;

    public static final int FILE_NORMAL = 1;

    public static final String ACTION_CONFIRM = "CONFIRM";
    public static final String ACTION_COMPLETE = "COMPLETE";
    public static final String ACTION_ABORT = "ABORT";
    public static final String ACTION_CLEAN = "CLEAN";

    private FileUploadTaskStatus() {
    }

}
