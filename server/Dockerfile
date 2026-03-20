# Force Render to re-sync
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# Copy Maven wrapper and pom file from the 'server' subdirectory
COPY server/mvnw ./
COPY server/.mvn/ .mvn/
COPY server/pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy source code from the 'server' subdirectory
COPY server/src/ ./src/

# Build the application
RUN ./mvnw clean package -DskipTests

# Expose port and run the application
EXPOSE 8081
CMD ["java", "-jar", "target/schoollink-0.0.1-SNAPSHOT.jar"]
