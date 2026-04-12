package com.cloudalbum.publisher.device.task;

import com.cloudalbum.publisher.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceOfflineScheduler {

    private final DeviceService deviceService;

    @Value("${device.offline-threshold-seconds:90}")
    private long offlineThresholdSeconds;

    @Scheduled(fixedDelayString = "${device.offline-check-interval-ms:30000}")
    public void markOfflineDevices() {
        int count = deviceService.markOfflineDevices(offlineThresholdSeconds);
        if (count > 0) {
            log.info("Device offline check completed, marked offline count={}", count);
        }
    }
}
