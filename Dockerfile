FROM node:24-alpine AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.10.0-eclipse-temurin-27 AS backend
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
COPY --from=frontend /workspace/src/main/resources/site ./src/main/resources/site
RUN mvn -q -DskipTests -Dneko.frontend.skip=true package

FROM debian:13-slim AS jdk

ARG LIBERICA_VERSION=27.0.1.0.2+5
ARG LIBERICA_SHA256=5a1f69d9997c15769e9408a26e511110ce599977c927e57d7c8bfa928a64b4a1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && JDK_FILE="bellsoft-jdk${LIBERICA_VERSION}-linux-amd64-lite.tar.gz" \
    && (curl -fL --http1.1 --retry 10 --retry-all-errors --retry-delay 5 \
            --connect-timeout 30 --speed-limit 1024 --speed-time 30 -C - \
            "https://download.bell-sw.com/java/${LIBERICA_VERSION}/${JDK_FILE}" \
            -o /tmp/jdk.tar.gz \
        || { rm -f /tmp/jdk.tar.gz; \
             curl -fL --http1.1 --retry 10 --retry-all-errors --retry-delay 5 \
                 --connect-timeout 30 --speed-limit 1024 --speed-time 30 -C - \
                 "https://github.com/bell-sw/Liberica/releases/download/${LIBERICA_VERSION}/${JDK_FILE}" \
                 -o /tmp/jdk.tar.gz; }) \
    && echo "${LIBERICA_SHA256}  /tmp/jdk.tar.gz" | sha256sum -c - \
    && mkdir -p /opt/java \
    && tar -xzf /tmp/jdk.tar.gz -C /opt/java --strip-components=1 \
    && rm /tmp/jdk.tar.gz \
    && /opt/java/bin/java -version 2>&1 | grep -F '27.0.1.0.2'

FROM debian:13-slim
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends adduser ca-certificates libstdc++6 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=jdk /opt/java /opt/java
COPY --from=backend /workspace/target/NekoAuth-1.0.0.jar app.jar
RUN adduser --system --group --home /app --no-create-home neko \
    && mkdir -p /app/data \
    && chown -R neko:neko /app
USER neko
EXPOSE 65534
ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:${PATH} \
    LANG=C.UTF-8 \
    LANGUAGE=C.UTF-8 \
    LC_ALL=C.UTF-8
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Dfile.encoding=UTF-8", \
    "-Dsun.jnu.encoding=UTF-8", \
    "-Djava.awt.headless=true", \
    "-jar", "/app/app.jar"]
