FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
# Keep the dependency graph in its own Docker layer. Render preserves
# intermediate BuildKit layers, so an ordinary Kotlin/UI change below does not
# redownload Gradle plugins and Maven/NPM dependencies on every deployment.
COPY server/build.gradle.kts server/build.gradle.kts
COPY composeApp/build.gradle.kts composeApp/build.gradle.kts
COPY shared/build.gradle.kts shared/build.gradle.kts
RUN gradle -PrenderJsOnly :server:dependencies :composeApp:dependencies --no-daemon --max-workers=1

# Source changes intentionally start only here. The dependency layer above is
# still reused unless Gradle build configuration itself has changed.
COPY server/src server/src
COPY composeApp/src composeApp/src
COPY shared/src shared/src
RUN gradle -PrenderJsOnly :composeApp:jsBrowserDevelopmentWebpack :server:installDist --no-daemon --max-workers=1 \
    && test -f /workspace/composeApp/build/processedResources/js/main/index.html \
    && test -f /workspace/composeApp/build/kotlin-webpack/js/developmentExecutable/composeApp.js \
    && mkdir -p /workspace/server/build/render-web \
    && cp -a /workspace/composeApp/build/processedResources/js/main/. /workspace/server/build/render-web/ \
    && cp -a /workspace/composeApp/build/kotlin-webpack/js/developmentExecutable/. /workspace/server/build/render-web/

FROM eclipse-temurin:21-jre
WORKDIR /opt/oms
COPY --from=build /workspace/server/build/install/server/ ./
COPY --from=build /workspace/server/build/render-web/ ./static/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
CMD ["bin/server"]
