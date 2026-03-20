# 第一阶段：构建应用
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# 复制整个 server 目录并构建
COPY server/ ./
RUN mvn clean package -DskipTests

# 第二阶段：运行应用
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# 从构建阶段复制生成的 jar 包（使用通配符匹配 schoollink-server-*.jar）
COPY --from=build /app/target/schoollink-server-*.jar app.jar

EXPOSE 8081
# 增加一些 JVM 参数以适应 Render 的内存限制
CMD ["java", "-Xmx400m", "-jar", "app.jar"]
