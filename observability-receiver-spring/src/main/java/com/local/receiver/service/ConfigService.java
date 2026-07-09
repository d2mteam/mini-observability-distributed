package com.local.receiver.service;

import com.local.receiver.model.ObservabilityConfig;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {
    private ObservabilityConfig config = ObservabilityConfig.defaults();

    public synchronized ObservabilityConfig current() {
        return config;
    }

    public synchronized ObservabilityConfig update(ObservabilityConfig next) {
        config = next == null ? ObservabilityConfig.defaults() : next;
        return config;
    }
}
