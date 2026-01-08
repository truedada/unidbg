package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.UnidbgProperties;
import com.anjia.unidbgserver.unidbg.IdleFQ;
import com.anjia.unidbgserver.utils.TempFileUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

@Slf4j
public class FQEncryptService {

    private final IdleFQ idleFQ;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern HEADER_COLON_PAIR = Pattern.compile("^[A-Za-z0-9-]{1,64}:\\s*.+$");

    public FQEncryptService(UnidbgProperties properties) {
        // 根据配置设置是否显示日志
        this.idleFQ = new IdleFQ(properties.isVerbose(), properties.getApkPath(), properties.getApkClasspath());
        log.info("FQ签名服务初始化完成");
    }

    /**
     * 生成FQ应用的签名headers
     *
     * @param url 请求的URL
     * @param headers 请求头信息，格式为key\r\nvalue\r\n的字符串
     * @return 包含各种签名header的Map
     */
    public Map<String, String> generateSignatureHeaders(String url, String headers) {
        try {
            log.debug("准备生成FQ签名 - URL: {}", url);
            log.debug("准备生成FQ签名 - Headers: {}", headers);

            // 调用IdleFQ的签名生成方法
            String signatureResult = idleFQ.generateSignature(url, headers);

            if (signatureResult == null || signatureResult.isEmpty()) {
                log.error("签名生成失败，返回结果为空");
                return Collections.emptyMap();
            }

            // 解析返回的签名结果
            Map<String, String> result = parseSignatureResult(signatureResult);

            removeHeaderIgnoreCase(result, "X-Neptune");

            log.debug("FQ签名生成成功: {}", result);
            return result;

        } catch (Exception e) {
            log.error("生成FQ签名失败", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 生成FQ应用的签名headers (重载方法，支持Map格式的headers)
     *
     * @param url 请求的URL
     * @param headerMap 请求头的Map，key为header名称，value为header值
     * @return 包含各种签名header的Map
     */
    public Map<String, String> generateSignatureHeaders(String url, Map<String, String> headerMap) {
        if (headerMap == null || headerMap.isEmpty()) {
            return generateSignatureHeaders(url, "");
        }

        // 将Map转换为\r\n分隔的字符串格式
        StringBuilder headerBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            headerBuilder.append(entry.getKey()).append("\r\n")
                .append(entry.getValue()).append("\r\n");
        }

        // 移除最后的\r\n
        String headers = headerBuilder.toString();
        if (headers.endsWith("\r\n")) {
            headers = headers.substring(0, headers.length() - 2);
        }

        return generateSignatureHeaders(url, headers);
    }

    /**
     * 解析签名生成结果
     * 根据返回的字符串解析出各个header值
     */
    private Map<String, String> parseSignatureResult(String signatureResult) {
        if (signatureResult == null) {
            return Collections.emptyMap();
        }

        String normalized = signatureResult.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1) JSON 格式：{"X-Argus":"...","X-Khronos":"..."}
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            try {
                Map<String, String> jsonMap = objectMapper.readValue(normalized, new TypeReference<Map<String, String>>() {});
                return jsonMap != null ? new HashMap<>(jsonMap) : Collections.emptyMap();
            } catch (Exception ignored) {
                // fallback to line-based parsing
            }
        }

        // 2) 行格式：支持
        //    - key\nvalue\nkey\nvalue...
        //    - key: value\nkey2: value2...
        String[] lines = normalized.split("\n");
        Map<String, String> result = new HashMap<>();

        boolean looksLikeColonPairs = false;
        for (String line : lines) {
            if (HEADER_COLON_PAIR.matcher(line.trim()).matches()) {
                looksLikeColonPairs = true;
                break;
            }
        }

        if (looksLikeColonPairs) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                int idx = trimmed.indexOf(':');
                if (idx <= 0) continue;
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
        } else if (lines.length >= 2 && lines.length % 2 == 0) {
            for (int i = 0; i < lines.length - 1; i += 2) {
                String key = lines[i].trim();
                String value = lines[i + 1].trim();
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
        } else {
            // 兜底：尝试按空白分隔的 key=value
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                int idx = trimmed.indexOf('=');
                if (idx <= 0) continue;
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                if (!key.isEmpty()) {
                    result.put(key, value);
                }
            }
        }

        // 常见签名头部可能存在大小写差异，这里仅做存在性提示，不做强制
        boolean hasArgus = result.keySet().stream().anyMatch(k -> "x-argus".equals(k.toLowerCase(Locale.ROOT)) || "x-gorgon".equals(k.toLowerCase(Locale.ROOT)));
        if (!hasArgus) {
            log.warn("签名结果解析后未发现常见签名头部，raw={}", normalized.length() > 200 ? normalized.substring(0, 200) + "..." : normalized);
        }

        return result;
    }

    private void removeHeaderIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty() || name == null) {
            return;
        }
        String target = name.toLowerCase(Locale.ROOT);
        Set<String> toRemove = new HashSet<>();
        for (String key : headers.keySet()) {
            if (key != null && key.toLowerCase(Locale.ROOT).equals(target)) {
                toRemove.add(key);
            }
        }
        toRemove.forEach(headers::remove);
    }

    /**
     * 清理资源
     */
    public void destroy() {
        // 清理IdleFQ资源
        if (idleFQ != null) {
            idleFQ.destroy();
        }

        // 清理临时文件
        TempFileUtils.cleanup();

        log.info("FQ签名服务资源释放完成");
    }
}
