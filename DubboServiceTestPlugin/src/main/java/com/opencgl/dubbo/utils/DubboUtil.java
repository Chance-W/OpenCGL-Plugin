package com.opencgl.dubbo.utils;


import org.apache.commons.lang.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.opencgl.dubbo.model.DubboRequest;
import com.opencgl.dubbo.model.DubboResponse;

/**
 * @author Chance.W
 */
@SuppressWarnings("unused")
public class DubboUtil {
    private static final Logger log = LoggerFactory.getLogger(DubboUtil.class);
    private final DubboRequest dubboRequest;

    private ApplicationConfig applicationConfig = null;

    private RegistryConfig registryConfig = null;

    public DubboUtil(DubboRequest dubboRequest) {
        this.dubboRequest = dubboRequest;
        init();
    }


    public DubboResponse sendMessage() throws GenericException {
        ReferenceConfig<GenericService> reference = null;
        GenericService genericService;
        Object o;
        try {
            reference = generateGenericServiceReferenceConfig();
            genericService = reference.get();
            if (StringUtils.isEmpty(dubboRequest.getReqType()) || StringUtils.isEmpty(dubboRequest.getReqJsonMessage())) {
                o = genericService.$invoke(dubboRequest.getMethod(), new String[]{}, new Object[]{});
            }
            else {
                o = genericService.$invoke(dubboRequest.getMethod(), new String[]{dubboRequest.getReqType()}, new Object[]{JSON.parseObject(dubboRequest.getReqJsonMessage())});
            }
        }
        finally {
            if (reference != null){
                reference.destroy();
                reference.getApplication().getScopeModel().destroy();
            }
        }
        return DubboResponse.builder().object(o).build();

    }

    private ReferenceConfig<GenericService> generateGenericServiceReferenceConfig() {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        if (!StringUtils.isEmpty(dubboRequest.getDubboProvidersUrl())) {
            reference.setUrl(dubboRequest.getDubboProvidersUrl());
        }
        reference.setRegistry(registryConfig);
        reference.setTimeout(1000000000);
        reference.setRetries(-1);
        reference.setApplication(applicationConfig);
        reference.setProtocol("dubbo");
        reference.setGeneric(true);
        reference.setInterface(dubboRequest.getInterfaceName());
        return reference;
    }

    public ReferenceConfig<GenericService> getReference(Class<?> interfaceClass) {
        ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
        if (!StringUtils.isEmpty(dubboRequest.getDubboProvidersUrl())) {
            reference.setUrl(dubboRequest.getDubboProvidersUrl());
            reference.setTimeout(1000000000);
            reference.setRetries(1);
        }
        reference.setRegistry(registryConfig);
        reference.setApplication(applicationConfig);
        reference.setProtocol("dubbo");
        reference.setInterface(interfaceClass);
        return reference;
    }

    private void init() {
        applicationConfig = new ApplicationConfig();
        applicationConfig.setName("OpenCGLService");
        applicationConfig.setQosEnable(false);

        registryConfig = new RegistryConfig();
        registryConfig.setAddress("zookeeper://" + dubboRequest.getDubboRegistryAddr());
        registryConfig.setGroup(dubboRequest.getDubboRegistryGroup());
        registryConfig.setTimeout(1000000000);
    }
}
