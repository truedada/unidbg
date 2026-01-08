package com.anjia.unidbgserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 下载/上游请求相关配置
 */
@Data
@ConfigurationProperties(prefix = "fq.download")
public class FQDownloadProperties {

    /**
     * 上游接口最小请求间隔（ms），用于限制 QPS。
     * 例如 500ms ~= 1 秒 2 次。
     */
    private long requestIntervalMs = 500;

    /**
     * 可恢复错误时的最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 初始重试延迟（ms）
     */
    private long retryDelayMs = 1500;

    /**
     * 最大重试延迟（ms）
     */
    private long retryMaxDelayMs = 10000;

    /**
     * 单章接口触发时的预取章节数（用于减少上游请求次数）
     */
    private int chapterPrefetchSize = 30;

    /**
     * 章节内容缓存最大条数
     */
    private int chapterCacheMaxEntries = 500;

    /**
     * 章节缓存 TTL（ms）
     */
    private long chapterCacheTtlMs = 30 * 60 * 1000L;

    /**
     * 目录缓存 TTL（ms）
     */
    private long directoryCacheTtlMs = 30 * 60 * 1000L;
}
