FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY server server
COPY composeApp composeApp
COPY shared shared
RUN gradle -PrenderJsOnly :server:installDist --no-daemon --max-workers=2

FROM eclipse-temurin:21-jre
WORKDIR /opt/oms
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/server/build/install/server/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
CMD ["bin/server"]
