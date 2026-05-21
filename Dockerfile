# ============================================
# 校园智能服务小助手 - Docker 镜像
# 基于 OpenJDK 17 slim 镜像
# ============================================
FROM openjdk:17-slim
WORKDIR /app
COPY target/campus-assistant-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]
