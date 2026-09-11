FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY server server
COPY composeApp composeApp
COPY shared shared
RUN gradle -PrenderJsOnly :composeApp:jsBrowserDevelopmentWebpack :server:installDist --no-daemon --max-workers=2 \
    && web_assets_dir=$(find /workspace/composeApp/build -type f -name index.html -printf '%h\\n' | head -n 1) \
    && test -n "$web_assets_dir" \
    && mkdir -p /workspace/server/build/render-web \
    && cp -a "$web_assets_dir"/. /workspace/server/build/render-web/

FROM eclipse-temurin:21-jre
WORKDIR /opt/oms
COPY --from=build /workspace/server/build/install/server/ ./
COPY --from=build /workspace/server/build/render-web/ ./static/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
CMD ["bin/server"]
