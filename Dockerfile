FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY server server
COPY composeApp composeApp
COPY shared shared
RUN gradle -PrenderJsOnly :server:installDist --no-daemon --max-workers=2

FROM eclipse-temurin:21-jre
WORKDIR /opt/oms
COPY --from=build /workspace/server/build/install/server/ ./
COPY --from=build /workspace/server/build/generated/render-web/ ./static/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
CMD ["bin/server"]
