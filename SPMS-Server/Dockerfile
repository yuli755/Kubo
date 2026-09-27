# =============================================================================
# 阶段 1：构建环境（带 Maven 依赖预缓存层）
# =============================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build

WORKDIR /app

# 1. 先复制依赖清单与镜像源配置，利用 Docker 分层缓存机制
COPY pom.xml settings.xml ./

# 2. 预热依赖：只要 pom.xml 未发生变动，本层彻底命中缓存，避免每次业务微调全量重新拉取依赖
RUN mvn dependency:go-offline -B -s settings.xml || true

# 3. 复制业务源代码并进行生产打包
COPY src ./src
RUN mvn clean package -DskipTests -s settings.xml

# =============================================================================
# 阶段 2：轻量安全运行时（采用 JRE 替代完整 JDK，镜像由 ~550MB 瘦身至 ~180MB）
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安全加固：创建专属非 root 运行账号，杜绝特权逃逸
RUN addgroup -S -g 10001 kubo && \
    adduser -S -u 10001 -G kubo -s /sbin/nologin kubo && \
    chown -R kubo:kubo /app

# 从构建产物复制最终可执行 JAR
COPY --from=build --chown=kubo:kubo /app/target/*.jar app.jar

# 切换为无特权用户运行
USER kubo:kubo

# 暴露后端端口
EXPOSE 8080

# 容器感知与稳定性 JVM 参数：
# - XX:+UseContainerSupport: 准确识别 cgroup CPU / 内存配额
# - XX:MaxRAMPercentage=75.0: 自动按容器限额分配堆内存，预留 25% 给堆外与元空间，防止被宿主 OOM-Killer 掐死
# - XX:+ExitOnOutOfMemoryError: 发生 OOM 时立刻退出，交由 Docker / K8s 重启自愈
# - Djava.security.egd: 加速安全随机数生成，避免启动阻塞
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# 容器探针健康检查
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health 2>/dev/null || exit 1

# 启动 Spring Boot 应用
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
