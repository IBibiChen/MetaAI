# ==================================================
# 阶段 1：Maven 构建
# ==================================================
FROM registry.cn-hangzhou.aliyuncs.com/xother/maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 云效流水线和手工构建都从仓库根目录构建
# 多模块项目必须保留父工程上下文，否则 agent 无法解析 rag 和 external-adapter 模块
COPY . /build

# 当前仓库根目录没有 settings.xml
# 如需私服配置，由流水线注入 Maven settings 或在构建上下文中显式提供
RUN mvn -pl meta-ai-agent -am clean package -DskipTests -B

# ==================================================
# 阶段 2：运行时
# ==================================================
FROM registry.cn-hangzhou.aliyuncs.com/xother/eclipse-temurin:17-jre

WORKDIR /app

# 从构建阶段复制 Spring Boot 可执行 jar
# 普通 jar 不包含 Boot Loader，不能直接 java -jar 运行
COPY --from=build /build/meta-ai-agent/target/*-exec.jar /app/app.jar

# 设置 TZ 时区
ENV TZ=Asia/Shanghai

# 应用容器内固定监听 8008
EXPOSE 8008

# 容器启动时执行的命令
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
