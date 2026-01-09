#!/bin/bash
# OpenCGL 启动脚本
# 添加 JVM 参数解决 Java 模块化访问限制问题

JVM_OPTS="--add-opens java.base/java.math=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/java.io=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/sun.security.ssl=ALL-UNNAMED"

# 如果你使用 Maven 运行
mvn javafx:run -Djavafx.args="$JVM_OPTS"

# 或者如果你直接运行 JAR
# java $JVM_OPTS -jar your-app.jar
