# 使用官方Maven镜像作为构建环境
FROM maven:3.9.6-amazoncorretto-17 AS build

# 设置工作目录
WORKDIR /app

# 复制pom.xml、settings.xml和源代码
COPY pom.xml .
COPY settings.xml .
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests -s settings.xml

# 使用轻量级OpenJDK运行时镜像
FROM amazoncorretto:17-alpine

# 设置工作目录
WORKDIR /app

# 从构建阶段复制jar文件
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# C-1（阶段D）：AccessTokenSecretGuard 对所有 profile 无条件校验 JWT 密钥，
# 未注入 AIRPOWER_API_ACCESS_TOKEN_SECRET（≥32 字符强随机，如 openssl rand -hex 32）
# 容器会启动即退出，不再静默使用默认弱密钥。需要生产语义请一并注入
# SPRING_PROFILES_ACTIVE=production（关闭包体日志 / api-docs，放行开关显式置 false）。
# 本地跑容器调试才可临时 -e KUBO_SECURITY_ALLOW_WEAK_DEFAULT_SECRET=true，严禁固化进镜像层。
# 运行应用
ENTRYPOINT ["java", "-jar", "app.jar"]