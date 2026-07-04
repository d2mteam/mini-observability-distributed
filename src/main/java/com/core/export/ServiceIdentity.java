package com.core.export;

import java.util.UUID;

public record ServiceIdentity(String serviceName, String instanceId) {
    public static ServiceIdentity create(String serviceName) {
        return new ServiceIdentity(serviceName, UUID.randomUUID().toString());
    }
}
