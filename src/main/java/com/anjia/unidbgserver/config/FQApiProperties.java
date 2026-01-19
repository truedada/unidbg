package com.anjia.unidbgserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FQ API 配置属性
 * 用于管理FQ API的设备参数和请求配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "fq.api")
public class FQApiProperties {
    
    /**
     * API基础URL
     */
    private String baseUrl = "https://api5-normal-sinfonlineb.fqnovel.com";
    
    /**
     * 默认User-Agent
     */
    private String userAgent = "com.dragon.read.oversea.gp/68132 (Linux; U; Android 10; zh_CN; OnePlus11; Build/V291IR;tt-ok/3.12.13.4-tiktok)";
    
    /**
     * Cookie配置
     */
    private String cookie = "store-region=cn-zj; store-region-src=did; install_id=933935730456617";
    
    /**
     * 设备参数配置
     */
    private Device device = new Device();

    /**
     * 预置设备池：风控（ILLEGAL_ACCESS 等）时按配置切换设备信息。
     * <p>
     * 配置示例：
     * <pre>
     * fq:
     *   api:
     *     device-pool:
     *       - name: dev1
     *         user-agent: ...
     *         cookie: ...
     *         device: { ... }
     *       - name: dev2
     *         ...
     * </pre>
     */
    private List<DeviceProfile> devicePool = new ArrayList<>();

    /**
     * 设备池生效数量（默认 3）：仅使用前 N 个设备，避免配置过大/频繁轮换。
     */
    private int devicePoolSize = 3;

    /**
     * 启动时是否随机选择设备池中的一个作为当前设备（默认 true）。
     */
    private boolean devicePoolShuffleOnStartup = true;

    /**
     * 启动时优先选择指定 name 的设备（可选）。
     * <p>
     * 配置后将忽略 {@link #devicePoolShuffleOnStartup} 的随机选择逻辑，
     * 始终使用该 name 对应的设备作为启动设备（未找到则回退到原有逻辑）。
     * <pre>
     * fq:
     *   api:
     *     device-pool-startup-name: dev001
     * </pre>
     */
    private String devicePoolStartupName;

    /**
     * 启动时是否对“随机选中的设备”做一次轻量探测（默认 false）。
     * <p>
     * 用于规避某些设备一上来就触发风控/空响应（例如 SEARCH_NO_SEARCH_ID）。
     * 探测失败则会继续尝试设备池内的下一个设备，直到成功或耗尽尝试次数。
     */
    private boolean devicePoolProbeOnStartup = false;

    /**
     * 启动探测最大尝试次数（默认 3）。
     * 实际尝试次数不会超过 devicePoolSize 和 devicePool 实际数量。
     */
    private int devicePoolProbeMaxAttempts = 3;

    /**
     * 风控切换冷却时间（毫秒，默认 30000）。
     * 避免触发风控后频繁轮换设备导致“疯狂切换/疯狂刷新 registerkey”。
     */
    private long deviceRotateCooldownMs = 30_000L;

    @Data
    public static class DeviceProfile {
        /**
         * 可选：用于日志识别
         */
        private String name;

        private String userAgent;
        private String cookie;
        private Device device = new Device();
    }
    
    @Data
    public static class Device {
        /**
         * 设备唯一标识符
         */
        private String cdid = "17f05006-423a-4172-be4b-7d26a42f2f4a";
        
        /**
         * 安装ID
         */
        private String installId = "933935730456617";
        
        /**
         * 设备ID
         */
        private String deviceId = "933935730452521";
        
        /**
         * 应用ID
         */
        private String aid = "1967";
        
        /**
         * 版本代码
         */
        private String versionCode = "68132";
        
        /**
         * 版本名称
         */
        private String versionName = "6.8.1.32";
        
        /**
         * 更新版本代码
         */
        private String updateVersionCode = "68132";
        
        /**
         * 设备类型
         */
        private String deviceType = "OnePlus11";
        
        /**
         * 设备品牌
         */
        private String deviceBrand = "OnePlus";
        
        /**
         * ROM版本
         */
        private String romVersion = "V291IR+release-keys";
        
        /**
         * 分辨率
         */
        private String resolution = "3200*1440";
        
        /**
         * DPI
         */
        private String dpi = "640";
        
        /**
         * 主机ABI
         */
        private String hostAbi = "arm64-v8a";

        /**
         * Android 版本（例如 13）
         */
        private String osVersion = "13";

        /**
         * Android API（例如 32）
         */
        private String osApi = "32";
    }
}
