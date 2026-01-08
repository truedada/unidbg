package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.FQDownloadProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * 上游请求全局限流（按最小间隔）：
 * 通过 CAS 保证在多线程下请求间隔不被打穿。
 */
@Component
@RequiredArgsConstructor
public class UpstreamRateLimiter {

    private final FQDownloadProperties downloadProperties;
    private final AtomicLong nextAllowedAtNanos = new AtomicLong(0L);

    public void acquire() {
        long intervalMs = downloadProperties.getRequestIntervalMs();
        if (intervalMs <= 0) {
            return;
        }

        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMs);
        while (true) {
            long now = System.nanoTime();
            long prev = nextAllowedAtNanos.get();
            long start = Math.max(now, prev);
            long next = start + intervalNanos;
            if (nextAllowedAtNanos.compareAndSet(prev, next)) {
                long wait = start - now;
                if (wait > 0) {
                    LockSupport.parkNanos(wait);
                }
                return;
            }
        }
    }
}

