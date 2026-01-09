package com.opencgl.dubbo.mock.engine;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.service.GenericService;

public class TestZK {
    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            ApplicationConfig application = new ApplicationConfig();
            application.setName("test-zk");

            RegistryConfig registryConfig = new RegistryConfig();
            registryConfig.setAddress("zookeeper://10.45.66.105:2182");
            registryConfig.setClient("mockcurator");
            registryConfig.setRegisterMode("interface");
            
            java.util.Map<String, String> parameters = new java.util.HashMap<>();
            parameters.put("metadata-type", "local");
            registryConfig.setParameters(parameters);

            ProtocolConfig protocol = new ProtocolConfig();
            protocol.setName("dubbo");
            protocol.setPort(20889);

            ServiceConfig<GenericService> service = new ServiceConfig<>();
            service.setApplication(application);
            service.setRegistry(registryConfig);
            service.setProtocol(protocol);
            service.setInterface("com.example.TestService2");
            service.setGeneric("true");
            service.setRef(new GenericService() {
                @Override
                public Object $invoke(String s, String[] strings, Object[] objects) { return null; }
            });
            service.export();
            System.out.println("Exported successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
