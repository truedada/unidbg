package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.FQApiProperties;
import com.anjia.unidbgserver.dto.DeviceInfo;
import com.anjia.unidbgserver.dto.DeviceRegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 设备信息风控（ILLEGAL_ACCESS）时的自愈：自动更换设备信息并刷新 registerkey。
 * 不写入配置文件、不重启进程，仅在内存中生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FQDeviceRotationService {

    private final DeviceGeneratorService deviceGeneratorService;
    private final FQApiProperties fqApiProperties;
    private final FQRegisterKeyService registerKeyService;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile long lastRotateAtMs = 0L;

    /**
     * 尝试旋转设备（带冷却时间，避免并发风暴）。
     *
     * @return 旋转成功返回新设备信息，否则返回 null
     */
    public DeviceInfo rotateIfNeeded(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastRotateAtMs < 5000L) {
            return null;
        }

        lock.lock();
        try {
            now = System.currentTimeMillis();
            if (now - lastRotateAtMs < 5000L) {
                return null;
            }

            DeviceRegisterRequest request = DeviceRegisterRequest.builder().build();
            DeviceInfo deviceInfo = deviceGeneratorService.generateDeviceInfo(request);
            if (deviceInfo == null) {
                log.warn("设备旋转失败：生成设备信息为空，reason={}", reason);
                return null;
            }

            applyDeviceInfo(deviceInfo);
            lastRotateAtMs = now;
            log.warn("检测到风控/异常，已自动更换设备信息：deviceId={}, installId={}, reason={}",
                deviceInfo.getDeviceId(), deviceInfo.getInstallId(), reason);

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

    private void applyDeviceInfo(DeviceInfo deviceInfo) {
        fqApiProperties.setUserAgent(deviceInfo.getUserAgent());
        fqApiProperties.setCookie(deviceInfo.getCookie());

        if (fqApiProperties.getDevice() == null) {
            fqApiProperties.setDevice(new FQApiProperties.Device());
        }

        FQApiProperties.Device device = fqApiProperties.getDevice();
        device.setAid(deviceInfo.getAid());
        device.setCdid(deviceInfo.getCdid());
        device.setDeviceBrand(deviceInfo.getDeviceBrand());
        device.setDeviceId(deviceInfo.getDeviceId());
        device.setDeviceType(deviceInfo.getDeviceType());
        device.setDpi(deviceInfo.getDpi());
        device.setHostAbi(deviceInfo.getHostAbi());
        device.setInstallId(deviceInfo.getInstallId());
        device.setResolution(deviceInfo.getResolution());
        device.setRomVersion(deviceInfo.getRomVersion());
        device.setUpdateVersionCode(deviceInfo.getUpdateVersionCode());
        device.setVersionCode(deviceInfo.getVersionCode());
        device.setVersionName(deviceInfo.getVersionName());
        device.setOsVersion(deviceInfo.getOsVersion());
        device.setOsApi(deviceInfo.getOsApi() != null ? String.valueOf(deviceInfo.getOsApi()) : device.getOsApi());
    }
}

