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
     * 上游连接超时（ms）
     */
    private long upstreamConnectTimeoutMs = 8000;

    /**
     * 上游读取超时（ms）
     */
    private long upstreamReadTimeoutMs = 15000;

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

    /**
     * 自动重启开关：当连续异常达到阈值后，主动退出进程（由 Docker/systemd 拉起）。
     */
    private boolean autoRestartEnabled = true;

    /**
     * 触发自动重启的连续异常次数阈值
     */
    private int autoRestartErrorThreshold = 3;

    /**
     * 统计窗口（ms）：窗口外会重置计数
     */
    private long autoRestartWindowMs = 5 * 60 * 1000L;

    /**
     * 两次自动重启最小间隔（ms），避免重启风暴
     */
    private long autoRestartMinIntervalMs = 60 * 1000L;

    /**
     * 强制退出（ms）：如果 System.exit 因 shutdown hook 卡住，超过该时间后调用 Runtime.halt 直接结束进程。
     * 设为 0 可关闭（默认开启，避免容器无法重启）。
     */
    private long autoRestartForceHaltAfterMs = 10_000L;
}
