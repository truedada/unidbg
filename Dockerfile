ARG DEBIAN_IMAGE=debian:trixie-slim

FROM eclipse-temurin:8-jre-jammy AS jre

# Debian 运行时镜像（可在构建时用 DEBIAN_IMAGE 覆盖，如 debian:latest）
FROM ${DEBIAN_IMAGE}

WORKDIR /app

# 拷贝 JRE 8（避免在 Debian 上额外装 JDK/JRE）
COPY --from=jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="$JAVA_HOME/bin:$PATH"

# 默认使用中国时区（可通过运行时设置 TZ 覆盖）
ENV TZ=Asia/Shanghai
RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tzdata ca-certificates \
  && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
  && echo $TZ > /etc/timezone \
  && rm -rf /var/lib/apt/lists/*

# 运行时如果 /app 下存在 application.yml，会优先使用（否则使用 jar 内置默认配置）
COPY target/fqnovel.jar /app/fqnovel.jar

ENV SERVER_PORT=9999
EXPOSE 9999

ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/fqnovel.jar"]
