package com.local.receiver.api;

import com.local.receiver.model.ObservabilityConfig;
import com.local.receiver.service.ConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigApi {
    private final ConfigService configService;

    public ConfigApi(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/api/config")
    public ObservabilityConfig current() {
        return configService.current();
    }

    @PutMapping("/api/config")
    public ObservabilityConfig update(@RequestBody ObservabilityConfig config) {
        return configService.update(config);
    }
}
