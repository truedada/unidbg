package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.FQApiProperties;
import com.anjia.unidbgserver.dto.DeviceInfo;
import com.anjia.unidbgserver.dto.FQSearchRequest;
import com.anjia.unidbgserver.dto.FqVariable;
import com.anjia.unidbgserver.utils.FQApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.GZIPInputStream;
import java.net.URI;

/**
 * 设备信息风控（ILLEGAL_ACCESS）时的自愈：自动更换设备信息并刷新 registerkey。
 * 不写入配置文件、不重启进程，仅在内存中生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FQDeviceRotationService {

    private final FQApiProperties fqApiProperties;
    private final FQRegisterKeyService registerKeyService;
    private final FQEncryptServiceWorker fqEncryptServiceWorker;
    private final FQApiUtils fqApiUtils;
    private final UpstreamRateLimiter upstreamRateLimiter;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastRotateAtMs = 0L;
    private final AtomicInteger poolIndex = new AtomicInteger(0);

    @PostConstruct
    public void initDevicePoolOnStartup() {
        List<FQApiProperties.DeviceProfile> pool = fqApiProperties.getDevicePool();
        if (pool == null || pool.isEmpty()) {
            return;
        }

        int limit = Math.max(1, fqApiProperties.getDevicePoolSize());
        if (pool.size() > limit) {
            pool = new ArrayList<>(pool.subList(0, limit));
            fqApiProperties.setDevicePool(pool);
        }

        String startupName = fqApiProperties.getDevicePoolStartupName();
        if (startupName != null) {
            startupName = startupName.trim();
        }

        int selectedIndex = findByName(pool, startupName);

        // 若未指定启动设备，则按配置选择随机/首个
        if (selectedIndex < 0 && fqApiProperties.isDevicePoolShuffleOnStartup() && pool.size() > 1) {
            Collections.shuffle(pool, ThreadLocalRandom.current());
        }
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        // 启动探测：随机启动但跳过“启动就风控”的设备（例如 search 无 search_id）
        if (selectedIndex >= 0 && fqApiProperties.isDevicePoolProbeOnStartup() && pool.size() > 1) {
            int maxAttempts = Math.max(1, fqApiProperties.getDevicePoolProbeMaxAttempts());
            maxAttempts = Math.min(maxAttempts, pool.size());

            int attempt = 0;
            int startIdx = selectedIndex;
            int idx = startIdx;
            boolean ok = false;
            while (attempt < maxAttempts) {
                FQApiProperties.DeviceProfile candidate = pool.get(idx);
                applyDeviceProfile(candidate);
                attempt++;
                ok = probeSearchOk();
                if (ok) {
                    selectedIndex = idx;
                    break;
                }
                idx = (idx + 1) % pool.size();
            }

            if (!ok) {
                // 兜底：探测全失败时，仍按原逻辑使用当前 selectedIndex
                FQApiProperties.DeviceProfile fallback = pool.get(selectedIndex);
                applyDeviceProfile(fallback);
                log.warn("启动设备探测全部失败，回退使用设备池设备：name={}", fallback != null ? fallback.getName() : null);
            } else {
                log.info("启动设备探测通过：selectedIndex={}, attempts={}", selectedIndex, attempt);
            }
        } else {
            FQApiProperties.DeviceProfile selected = pool.get(selectedIndex);
            applyDeviceProfile(selected);
        }

        poolIndex.set(Math.floorMod(selectedIndex + 1, pool.size()));

        FQApiProperties.DeviceProfile selected = pool.get(selectedIndex);
        String name = selected != null ? selected.getName() : null;
        String deviceId = selected != null && selected.getDevice() != null ? selected.getDevice().getDeviceId() : null;
        String installId = selected != null && selected.getDevice() != null ? selected.getDevice().getInstallId() : null;
        log.info("启动已选择设备池设备：name={}, deviceId={}, installId={}", name, deviceId, installId);
    }

    private int findByName(List<FQApiProperties.DeviceProfile> pool, String name) {
        if (pool == null || pool.isEmpty() || name == null || name.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < pool.size(); i++) {
            FQApiProperties.DeviceProfile profile = pool.get(i);
            if (profile == null || profile.getName() == null) {
                continue;
            }
            if (name.equals(profile.getName().trim())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 启动轻量探测：发起一次 search 请求，要求返回 code=0 且包含 search_id 或 books 列表。
     * 不做解密/不依赖 registerkey，仅用于剔除“启动就被拦截”的设备指纹。
     */
    private boolean probeSearchOk() {
        try {
            FQSearchRequest searchRequest = new FQSearchRequest();
            searchRequest.setQuery("小说");
            searchRequest.setOffset(0);
            searchRequest.setCount(1);
            searchRequest.setTabType(1);
            searchRequest.setPassback(0);
            searchRequest.setIsFirstEnterSearch(true);

            FqVariable var = new FqVariable(fqApiProperties);
            String base = fqApiUtils.getBaseUrl();
            String url = base.replace("api5-normal-sinfonlineb", "api5-normal-sinfonlinec")
                + "/reading/bookapi/search/tab/v";
            Map<String, String> params = fqApiUtils.buildSearchParams(var, searchRequest);
            String fullUrl = fqApiUtils.buildUrlWithParams(url, params);

            Map<String, String> headers = buildSearchHeadersForProbe();
            upstreamRateLimiter.acquire();
            Map<String, String> signedHeaders = fqEncryptServiceWorker.generateSignatureHeaders(fullUrl, headers).get();
            if (signedHeaders == null || signedHeaders.isEmpty()) {
                return false;
            }

            HttpHeaders httpHeaders = new HttpHeaders();
            signedHeaders.forEach(httpHeaders::set);
            headers.forEach(httpHeaders::set);
            HttpEntity<String> entity = new HttpEntity<>(httpHeaders);

            ResponseEntity<byte[]> response = restTemplate.exchange(URI.create(fullUrl), HttpMethod.GET, entity, byte[].class);
            String body = decodeUpstreamResponse(response);
            if (body == null) {
                return false;
            }
            String trimmed = body.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("<")) {
                return false;
            }

            JsonNode root = objectMapper.readTree(trimmed);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                return false;
            }
            String searchId = extractSearchIdDeep(root);
            if (searchId != null && !searchId.isEmpty()) {
                return true;
            }
            JsonNode books = root.path("data").path("books");
            return books.isArray() && books.size() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> buildSearchHeadersForProbe() {
        Map<String, String> base = fqApiUtils.buildCommonHeaders();
        if (base.containsKey("authorization")) {
            return base;
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : base.entrySet()) {
            ordered.put(entry.getKey(), entry.getValue());
            if ("x-reading-request".equalsIgnoreCase(entry.getKey())) {
                ordered.put("authorization", "Bearer");
            }
        }
        if (!ordered.containsKey("authorization")) {
            ordered.put("authorization", "Bearer");
        }
        return ordered;
    }

    private String extractSearchIdDeep(JsonNode root) {
        if (root == null) {
            return "";
        }
        java.util.ArrayDeque<JsonNode> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            JsonNode node = stack.pop();
            if (node == null || node.isNull() || node.isMissingNode()) {
                continue;
            }
            if (node.isObject()) {
                String direct = node.path("search_id").asText("");
                if (direct != null && !direct.trim().isEmpty()) {
                    return direct.trim();
                }
                String alt = node.path("search_id_str").asText("");
                if (alt != null && !alt.trim().isEmpty()) {
                    return alt.trim();
                }
                node.fields().forEachRemaining(e -> stack.push(e.getValue()));
            } else if (node.isArray()) {
                for (JsonNode child : node) {
                    stack.push(child);
                }
            }
        }
        return "";
    }

    private String decodeUpstreamResponse(ResponseEntity<byte[]> response) {
        if (response == null) {
            return "";
        }
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return "";
        }

        boolean isGzip = false;
        List<String> enc = response.getHeaders() != null ? response.getHeaders().get("Content-Encoding") : null;
        if (enc != null) {
            for (String e : enc) {
                if (e != null && e.toLowerCase(Locale.ROOT).contains("gzip")) {
                    isGzip = true;
                    break;
                }
            }
        }
        if (!isGzip && body.length >= 2 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b) {
            isGzip = true;
        }
        if (!isGzip) {
            return new String(body, StandardCharsets.UTF_8);
        }

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(body))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gzipInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /**
     * 尝试旋转设备（带冷却时间，避免并发风暴）。
     *
     * @return 旋转成功返回新设备信息，否则返回 null
     */
    public DeviceInfo rotateIfNeeded(String reason) {
        return rotateInternal(reason, false);
    }

    /**
     * 强制旋转设备（忽略冷却时间），用于单次请求内的自愈流程。
     * 仍然会加锁，避免并发下“乱序切换”。
     */
    public DeviceInfo forceRotate(String reason) {
        return rotateInternal(reason, true);
    }

    private DeviceInfo rotateInternal(String reason, boolean ignoreCooldown) {
        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(0L, fqApiProperties.getDeviceRotateCooldownMs());
        if (!ignoreCooldown && now - lastRotateAtMs < cooldownMs) {
            return null;
        }

        lock.lock();
        try {
            now = System.currentTimeMillis();
            cooldownMs = Math.max(0L, fqApiProperties.getDeviceRotateCooldownMs());
            if (!ignoreCooldown && now - lastRotateAtMs < cooldownMs) {
                return null;
            }

            DeviceInfo deviceInfo = rotateFromConfiguredPool(reason);
            if (deviceInfo == null) {
                log.warn("检测到风控/异常，但未配置 fq.api.device-pool，无法自动切换设备：reason={}", reason);
                return null;
            }

            lastRotateAtMs = now;
            try {
                registerKeyService.clearCache();
                registerKeyService.refreshRegisterKey();
            } catch (Exception e) {
                log.warn("设备旋转后刷新 registerkey 失败（可忽略，下次请求会再刷新）", e);
            }

            return deviceInfo;
        } finally {
            lock.unlock();
        }
    }

    private DeviceInfo rotateFromConfiguredPool(String reason) {
        List<FQApiProperties.DeviceProfile> pool = fqApiProperties.getDevicePool();
        if (pool == null || pool.isEmpty()) {
            return null;
        }

        String currentDeviceId = fqApiProperties.getDevice() != null ? fqApiProperties.getDevice().getDeviceId() : null;
        String currentInstallId = fqApiProperties.getDevice() != null ? fqApiProperties.getDevice().getInstallId() : null;

        FQApiProperties.DeviceProfile profile = null;
        int idx = -1;
        for (int i = 0; i < pool.size(); i++) {
            int candidateIdx = Math.floorMod(poolIndex.getAndIncrement(), pool.size());
            FQApiProperties.DeviceProfile candidate = pool.get(candidateIdx);
            if (candidate == null) {
                continue;
            }

            String candidateDeviceId = candidate.getDevice() != null ? candidate.getDevice().getDeviceId() : null;
            String candidateInstallId = candidate.getDevice() != null ? candidate.getDevice().getInstallId() : null;

            boolean sameDeviceId = candidateDeviceId != null && candidateDeviceId.equals(currentDeviceId);
            boolean sameInstallId = candidateInstallId != null && candidateInstallId.equals(currentInstallId);
            if (sameDeviceId && sameInstallId) {
                continue;
            }

            profile = candidate;
            idx = candidateIdx;
            break;
        }

        if (profile == null) {
            return null;
        }

        applyDeviceProfile(profile);

        String name = profile.getName();
        String deviceId = profile.getDevice() != null ? profile.getDevice().getDeviceId() : null;
        String installId = profile.getDevice() != null ? profile.getDevice().getInstallId() : null;
        log.warn("检测到风控/异常，已从配置池切换设备：index={}, name={}, deviceId={}, installId={}, reason={}",
            idx, name, deviceId, installId, reason);

        return toDeviceInfo(profile);
    }

    private DeviceInfo toDeviceInfo(FQApiProperties.DeviceProfile profile) {
        if (profile == null) {
            return null;
        }
        FQApiProperties.Device device = profile.getDevice();
        return DeviceInfo.builder()
            .userAgent(profile.getUserAgent())
            .cookie(profile.getCookie())
            .aid(device != null ? device.getAid() : null)
            .cdid(device != null ? device.getCdid() : null)
            .deviceBrand(device != null ? device.getDeviceBrand() : null)
            .deviceId(device != null ? device.getDeviceId() : null)
            .deviceType(device != null ? device.getDeviceType() : null)
            .dpi(device != null ? device.getDpi() : null)
            .hostAbi(device != null ? device.getHostAbi() : null)
            .installId(device != null ? device.getInstallId() : null)
            .resolution(device != null ? device.getResolution() : null)
            .romVersion(device != null ? device.getRomVersion() : null)
            .updateVersionCode(device != null ? device.getUpdateVersionCode() : null)
            .versionCode(device != null ? device.getVersionCode() : null)
            .versionName(device != null ? device.getVersionName() : null)
            .osVersion(device != null ? device.getOsVersion() : null)
            .osApi(device != null ? parseIntOrNull(device.getOsApi()) : null)
            .build();
    }

    private Integer parseIntOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            String v = value.trim();
            if (v.isEmpty()) {
                return null;
            }
            return Integer.valueOf(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyDeviceProfile(FQApiProperties.DeviceProfile profile) {
        if (profile == null) {
            return;
        }
        if (profile.getUserAgent() != null) {
            fqApiProperties.setUserAgent(profile.getUserAgent());
        }
        if (profile.getCookie() != null) {
            fqApiProperties.setCookie(profile.getCookie());
        }

        if (fqApiProperties.getDevice() == null) {
            fqApiProperties.setDevice(new FQApiProperties.Device());
        }
        if (profile.getDevice() == null) {
            return;
        }

        FQApiProperties.Device src = profile.getDevice();
        FQApiProperties.Device dst = fqApiProperties.getDevice();
        copyIfPresent(src.getAid(), dst::setAid);
        copyIfPresent(src.getCdid(), dst::setCdid);
        copyIfPresent(src.getDeviceBrand(), dst::setDeviceBrand);
        copyIfPresent(src.getDeviceId(), dst::setDeviceId);
        copyIfPresent(src.getDeviceType(), dst::setDeviceType);
        copyIfPresent(src.getDpi(), dst::setDpi);
        copyIfPresent(src.getHostAbi(), dst::setHostAbi);
        copyIfPresent(src.getInstallId(), dst::setInstallId);
        copyIfPresent(src.getResolution(), dst::setResolution);
        copyIfPresent(src.getRomVersion(), dst::setRomVersion);
        copyIfPresent(src.getUpdateVersionCode(), dst::setUpdateVersionCode);
        copyIfPresent(src.getVersionCode(), dst::setVersionCode);
        copyIfPresent(src.getVersionName(), dst::setVersionName);
        copyIfPresent(src.getOsVersion(), dst::setOsVersion);
        copyIfPresent(src.getOsApi(), dst::setOsApi);
    }

    private void copyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value == null) {
            return;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return;
        }
        setter.accept(v);
    }
}
