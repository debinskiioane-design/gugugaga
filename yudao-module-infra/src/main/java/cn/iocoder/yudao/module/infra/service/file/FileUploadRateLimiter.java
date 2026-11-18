package cn.iocoder.yudao.module.infra.service.file;

import jakarta.annotation.Resource;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateLimiterConfig;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.TOO_MANY_REQUESTS;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 按登录用户限制直传初始化和分片地址签发。计数在 Redis，不记录预签名地址。
 */
@Component
public class FileUploadRateLimiter {

    private static final String KEY_PREFIX = "rate_limiter:file-upload:";

    @Resource
    private RedissonClient redissonClient;

    @Value("${yudao.file.upload.init-per-minute:30}")
    private int initPerMinute;

    @Value("${yudao.file.upload.part-url-per-minute:120}")
    private int partUrlPerMinute;

    public void acquireInit(Long userId) {
        acquire("init:" + userId, initPerMinute);
    }

    public void acquirePartUrl(Long userId) {
        acquire("part:" + userId, partUrlPerMinute);
    }

    private void acquire(String key, int count) {
        if (!Boolean.TRUE.equals(limiter(key, count).tryAcquire())) {
            throw exception(TOO_MANY_REQUESTS);
        }
    }

    private RRateLimiter limiter(String key, int count) {
        RRateLimiter limiter = redissonClient.getRateLimiter(KEY_PREFIX + key);
        Duration duration = Duration.ofMinutes(1);
        RateLimiterConfig config = limiter.getConfig();
        if (config == null) {
            limiter.trySetRate(RateType.OVERALL, count, duration);
            limiter.expire(duration);
            return limiter;
        }
        if (config.getRateType() == RateType.OVERALL
                && Objects.equals(config.getRate(), (long) count)
                && Objects.equals(config.getRateInterval(), TimeUnit.MINUTES.toMillis(1))) {
            return limiter;
        }
        limiter.setRate(RateType.OVERALL, count, duration);
        limiter.expire(duration);
        return limiter;
    }

}
