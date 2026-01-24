# 使用 Google Distroless Java 8 (基于 Debian 12)
# 优势：镜像更小、更安全、无 shell、无包管理器
FROM gcr.io/distroless/java:8

WORKDIR /app

# 设置时区（Distroless 支持通过环境变量设置）
ENV TZ=Asia/Shanghai

# 复制 jar 文件
COPY target/fqnovel.jar /app/fqnovel.jar

# 暴露端口
ENV SERVER_PORT=9999
EXPOSE 9999

# JVM 参数（可通过环境变量覆盖）
ENV JAVA_OPTS=""

# 启动应用
# 注意：Distroless 没有 shell，无法使用 bash 过滤日志
# 日志过滤已在 ConsoleNoiseFilter.java 中实现
ENTRYPOINT ["java", "-jar", "/app/fqnovel.jar"]
