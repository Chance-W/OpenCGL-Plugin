package com.opencgl;

import java.net.URL;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.service.GenericService;

import com.alibaba.fastjson.JSON;
import com.opencgl.plugin.api.PluginUI;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

/**
 * 示例插件B：实现通用Plugin接口，包含静态资源
 */
public class Plugin2 implements PluginUI {

    // 保存自己的ClassLoader
    private final ClassLoader pluginCl;

    public Plugin2() {
        this.pluginCl = this.getClass().getClassLoader();
    }

    @Override
    public String directoryName() {
        return "插件开发模板演示目录";
    }

    @Override
    public String name() {
        return "插件开发3";
    }

    @Override
    public URL iconPath() {
        return null;
    }

    @Override
    public UIType type() {
        return UIType.JAVAFX;
    }

    @Override
    public Object createView() {
        // 实例变量：插件自身资源
        Button uiButton = new Button("test2：点击调用Dubbo2");
        uiButton.setOnAction(event -> {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(pluginCl);
                // 直接调用Dubbo，无需TCCL切换（由主程序处理）
                // 示例Dubbo调用：假设有一个Dubbo服务
                ApplicationConfig applicationConfig = new ApplicationConfig();
                applicationConfig.setName("OpenCGLService");

                applicationConfig.setQosEnable(false);
                RegistryConfig registryConfig = new RegistryConfig();
                registryConfig.setAddress("zookeeper://" + "1.1.1.1:2181");
                registryConfig.setGroup("dubbo");
                registryConfig.setTimeout(1000000000);


                ReferenceConfig<GenericService> reference = new ReferenceConfig<>();
                reference.setUrl("dubbo://10.45.109.148:20889");
                reference.setRegistry(registryConfig);
                reference.setTimeout(1000000000);
                reference.setRetries(-1);
                reference.setApplication(applicationConfig);
                reference.setProtocol("dubbo");
                reference.setGeneric(true);

                reference.setInterface("com.ztesoft.zsmart.bss.payment.csrbilling.service.api.common.service.PcPaymentInfoQueryService"); // 替换为实际接口
                reference.setUrl("dubbo://10.45.109.148:20889"); // 示例URL
                reference.setRetries(-1);
                GenericService service = reference.get();
                System.out.println("tttttttt1     cccccccc");
                Object result = service.$invoke("qryPaymentInfo", new String[]{"com.ztesoft.zsmart.bss.payment.csrbilling.service.api.common.param.PcPaymentInfoQueryRequest"}, new Object[]{JSON.parseObject("{\"paymentReqId\":\"POS2024011256931905\",\"custId\":2,\"extAttrList\":[{\"attrCode\":\"\",\"attrValue\":\"\"}]}")});
                System.out.println("Dubbo result: " + result);
                System.out.println("tttttttt1");
            }
            finally {
                Thread.currentThread().setContextClassLoader(old);
            }

        });
        StackPane pane = new StackPane();
        pane.setStyle("-fx-background-color: #e0f7fa; -fx-padding: 20px;");

        pane.getChildren().add(uiButton);
        return pane;
    }

    @Override
    public void dispose() {
        System.out.println("[PluginB] 静态资源已清理");
    }

}

