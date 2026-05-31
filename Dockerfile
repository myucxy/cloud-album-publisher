ARG BASE_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${BASE_IMAGE}

WORKDIR /app

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8910 \
    DB_HOST=mysql \
    DB_PORT=3306 \
    DB_NAME=cloud_album \
    DB_USER=root \
    DB_PASSWORD=root \
    REDIS_HOST=redis \
    REDIS_PORT=6379 \
    MINIO_ENDPOINT=http://minio:9000 \
    RELEASE_MANIFEST_PATH=/app/releases/manifest.json \
    RELEASE_DOWNLOADS_LOCATION=file:/app/releases/ \
    JAVA_OPTS="-Xms512m -Xmx1024m"

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgtk2.0-0 \
    libglib2.0-0 \
    libcairo2 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libgdk-pixbuf-2.0-0 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libfontconfig1 \
    libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

COPY docker-build/app.jar /app/app.jar
COPY docker-build/releases/ /app/releases/

RUN mkdir -p /app/data /app/logs

EXPOSE 8910

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=${SERVER_PORT} --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
