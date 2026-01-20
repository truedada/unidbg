package com.anjia.unidbgserver.config;

import com.anjia.unidbgserver.service.FQDeviceRotationService;
import com.anjia.unidbgserver.service.FQRegisterKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupStatusLogger {

    private final FQApiProperties fqApiProperties;
    private final FQDeviceRotationService deviceRotationService;
    private final FQRegisterKeyService registerKeyService;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String poolName = deviceRotationService.getCurrentProfileName();
        String deviceId = fqApiProperties.getDevice() != null ? fqApiProperties.getDevice().getDeviceId() : null;
        String installId = fqApiProperties.getDevice() != null ? fqApiProperties.getDevice().getInstallId() : null;
        int poolSize = fqApiProperties.getDevicePool() != null ? fqApiProperties.getDevicePool().size() : 0;

        log.info("运行配置：devicePoolSize={}, activePoolName={}, deviceId={}, installId={}", poolSize, poolName, deviceId, installId);

        Map<String, Object> cache = registerKeyService.getCacheStatus();
        log.info("运行配置：registerkey cache: {}", cache);

        long lastRotateAt = deviceRotationService.getLastRotateAtMs();
        if (lastRotateAt > 0) {
            log.info("运行配置：lastRotateAtMs={}, lastRotateReason={}, lastRotateProfileName={}",
                lastRotateAt, deviceRotationService.getLastRotateReason(), deviceRotationService.getLastRotateProfileName());
        }
    }
}

