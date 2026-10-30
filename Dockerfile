FROM node:24-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
COPY --from=frontend /workspace/src/main/resources/site ./src/main/resources/site
RUN mvn -q -DskipTests -Dneko.frontend.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S neko && adduser -S neko -G neko
WORKDIR /app
COPY --from=backend /workspace/target/NekoAuth-1.0.0.jar app.jar
RUN mkdir -p /app/data && chown -R neko:neko /app
USER neko
EXPOSE 65534
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
