package com.opencgl.dubbo.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;

/**
 * 配置 Dubbo 消费端的寻址方式。
 *
 * <p>指定 Provider URL 时采用纯直连模式；只有未指定 Provider URL 时，
 * 才允许当前引用使用注册中心发现服务，避免两种寻址方式同时生效。</p>
 */
final class DubboReferenceConfigurer {

    private DubboReferenceConfigurer() {
    }

    static void configure(
        ReferenceConfig<?> reference,
        RegistryConfig registryConfig,
        String providersUrl
    ) {
        String directUrl = StringUtils.trimToNull(providersUrl);
        if (directUrl != null) {
            reference.setUrl(directUrl);
            return;
        }

        reference.setRegistry(registryConfig);
    }
}
