FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
# Keep the JVM dependency graph in its own reusable Docker layer.
COPY server/build.gradle.kts server/build.gradle.kts
# settings.gradle.kts includes composeApp. Copying only its build descriptor
# keeps Gradle's project graph valid without copying or building its sources.
COPY composeApp/build.gradle.kts composeApp/build.gradle.kts
COPY shared/build.gradle.kts shared/build.gradle.kts
RUN gradle :server:dependencies --no-daemon --max-workers=1

# Source changes intentionally start only here. The dependency layer above is
# still reused unless a JVM build configuration changes.
COPY composeApp/src composeApp/src
COPY shared/src shared/src
COPY server/src server/src
# The VPS serves the browser bundle and API from one origin.  The bundle is
# built once in CI and copied into the runtime image; the server never compiles
# Kotlin at startup.
RUN gradle -PrenderJsOnly :composeApp:jsBrowserProductionWebpack :server:installDist --no-daemon --max-workers=1

FROM eclipse-temurin:21-jre
WORKDIR /opt/oms
COPY --from=build /workspace/server/build/install/server/ ./
COPY --from=build /workspace/composeApp/build/processedResources/js/main/ ./web/
COPY --from=build /workspace/composeApp/build/kotlin-webpack/js/productionExecutable/ ./web/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
CMD ["bin/server"]
