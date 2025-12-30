# OpenCGL-Plugin

## 说明

公共插件开发指导手册
模板 module 可见[TemplatePlugin](TemplatePlugin)
按照模板编写自己的工具，可选择是否引入 OpenCGL-Base，OpenCGL-Base提供了一些公共的数据，信息以及依赖，还有树视图的通用实现，如果需要的化，需要下载OpenCGL-Base源码，编译
install 到本地仓库即可，源码的地址附在了文章的下面

### 集成
#### 依赖PluginApiModule

```xml

<dependency>
    <groupId>OpenCGL-Plugin</groupId>
    <artifactId>PluginApiModule</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```
### 实现com.opencgl.plugin.api.PluginUI接口
```java
package com.opencgl;

import java.io.IOException;
import java.net.URL;

import com.opencgl.plugin.api.PluginUI;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

public class TemplatePluginUI implements PluginUI {

    // 插件自己的ClassLoader
    private final ClassLoader pluginCl;

    public TemplatePluginUI() {
        this.pluginCl = this.getClass().getClassLoader();
    }


    @Override
    public String directoryName() {
        return "插件开发模板演示目录";
    }

    @Override
    public String name() {
        return "插件开发模板1";
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
        FXMLLoader loader = new FXMLLoader();
        loader.setClassLoader(pluginCl);
        loader.setLocation(this.getClass().getClassLoader().getResource("com/opencgl/template/views/TemplateWidgetView.fxml"));
        Parent root;
        try {
            root = loader.load();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    @Override
    public void dispose() {

    }
}
```

#### SPI配置
src/main/resources/META-INF/services目录下创建com.opencgl.plugin.api.PluginUI文件，内容为实现类，以插件模板为例，内容如下
```text
com.opencgl.TemplatePluginUI
```



## 联系方式

如有问题可直接通过以下方式联系

- 邮箱：chance.w@qq.com;chance_w@126.com
- 微信号：Chance_W-
- 钉钉号：xxx

## 特别感谢

感谢MaterialFX作者，编写了如此炫酷的展示和优化，让OpenCGL的桌面客户端也能焕发光彩

开源地址：https://github.com/palexdev/MaterialFX

感谢追风开源了xJavaFxTool，让我可以从中受益，学到到 javafx 的很多知识，同时插件工程也移植了部分追风的插件

开源地址：https://gitee.com/xwintop/xJavaFxTool

## 客户端仓库地址

https://gitee.com/chance_w/OpenCGL_New

## OpenCGL-Base仓库地址

https://gitee.com/chance_w/open-cgl-base

## 丢几张图

### DUBBO

![dubboRequest_1.png](markdown-assets%2FdubboRequest_1.png)
![dubboRequest_2.png](markdown-assets%2FdubboRequest_2.png)

### REST

![rest-request-1.png](markdown-assets%2Frest-request-1.png)

### Soap

![soap-request-1.png](markdown-assets%2Fsoap-request-1.png)

### Cron表达式

![cron-exp.png](markdown-assets%2Fcron-exp.png)
注：此插件是从https://gitee.com/xwintop/xJavaFxTool处移植

后续待补充。。。