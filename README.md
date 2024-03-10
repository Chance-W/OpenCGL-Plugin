# OpenCGL-Plugin

## 说明

公共插件开发指导手册
模板 module 可见[TemplatePlugin](TemplatePlugin)
按照模板编写自己的工具，可选择是否引入 OpenCGL-Base，OpenCGL-Base提供了一些公共的数据，信息以及依赖，还有树视图的通用实现，如果需要的化，需要下载OpenCGL-Base源码，编译 install 到本地仓库即可，源码的地址附在了文章的下面
在 resources 目录下添加[plugin-info.json]和图标(TemplatePlugin/src/main/resources/plugin-info.json)，支持一个 jar
包多个组件

### 插件参数信息解释

```json
[
  {
    "pluginName": "插件开发模板",
    "fatherName": "插件开发模板演示目录",
    "controllerType": "fxml",
    "fxmlPath": "com/opencgl/template/views/TemplateWidgetView.fxml",
    "jarName": "TemplatePlugin.jar",
    "className": "",
    "iconPath": "icon/decode.png",
    "enable": "true",
    "pluginInfo": "插件开发模板演示工具"
  }
]
```

- pluginName：OpenCGL客户端工具显示的组件名称
- fatherName: OpenCGL客户端工具显示的组件的目录名称,
- controllerType: 加载类型，支持 jar 和 fxml 两种，fxml 表示通过fxmlPath去加载，暂时只支持fxml加载方式，jar包加载方式忘移植了
- fxmlPath: controllerType为 fxml 时，对应 javafx 的 fxml 的路径
- jarName: 对应插件jar包的名称
- className: 暂未使用
- iconPath: 插件的图标路径
- enable: 是否启用
- pluginInfo: 鼠标悬停时的插件介绍说明

## 如何打包

两种方式，由于有一些功能是定制化的，所以我通过编写OpenCGL-Base引入依赖的方式加载的， 你可以直接将 OpenCGL-Base模块mvn install
到本地仓库，也可以在根 POM 直接在<modules>添加 Base 的模块

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