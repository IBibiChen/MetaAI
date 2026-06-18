# ==================================================
# 阶段 1：前端构建
# ==================================================
FROM node:22-bookworm-slim AS console-build

WORKDIR /build/meta-ai-console

# 先复制依赖清单，便于 Docker 复用 npm ci 缓存层
COPY meta-ai-console/package*.json ./
RUN npm ci

# 构建生产前端，.env.production 负责声明 /meta-ai/ 和 /api/meta-ai
COPY meta-ai-console/ ./
RUN npm run build

# ==================================================
# 阶段 2：Maven 构建
# ==================================================
FROM registry.cn-hangzhou.aliyuncs.com/xother/maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 云效流水线和手工构建都从仓库根目录构建
# 多模块项目必须保留父工程上下文，否则 agent 无法解析 rag 和 external-adapter 模块
COPY . /build
COPY --from=console-build /build/meta-ai-console/dist /build/meta-ai-console/dist

# 前端 dist 已由 console-build 阶段生成
# meta-ai.console.skip=true 用于跳过 pom 中的 npm install / build
# -Pdocker 用于排除 application*.properties，镜像运行时改由外部配置注入
# 当前仓库根目录没有 settings.xml
# 如需私服配置，由流水线注入 Maven settings 或在构建上下文中显式提供
# BuildKit cache mount 持久化 Maven 本地仓库，避免源码变更导致依赖重复下载
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -pl :meta-ai-agent -am clean package -DskipTests -Dmeta-ai.console.skip=true -Pdocker -B

# ==================================================
# 阶段 3：运行时
# ==================================================
FROM registry.cn-hangzhou.aliyuncs.com/xother/eclipse-temurin:17-jre

WORKDIR /app

# 从构建阶段复制 Spring Boot 可执行 jar
# 普通 jar 不包含 Boot Loader，不能直接 java -jar 运行
COPY --from=build /build/meta-ai-agent/target/meta-ai-agent-*.jar /app/app.jar

# 设置 TZ 时区
ENV TZ=Asia/Shanghai

# 应用容器内固定监听 8008
EXPOSE 8008

# 容器启动时执行的命令
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
