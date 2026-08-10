package org.dromara.system.service.support;

import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

/**
 * Provides object-storage clients for system services.
 */
@Component
public class OssClientProvider {

    public OssClient current() {
        return OssFactory.instance();
    }

    public OssClient byService(String service) {
        return OssFactory.instance(service);
    }
}
