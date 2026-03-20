# 使用预装了 Maven 和 JDK 17 的镜像
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# 复制整个 server 目录
COPY server/ ./

# 直接使用 maven 构建打包
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=build /app/target/schoollink-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
