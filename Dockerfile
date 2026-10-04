FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
RUN apt-get update && apt-get install -y --no-install-recommends brotli && rm -rf /var/lib/apt/lists/*
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
# The development artifact is tens of megabytes and dominates cold startup.
# Production Webpack is deliberately allowed to finish; CI has a bounded job
# timeout and publishes only after the optimized artifact is complete.
RUN gradle -PrenderJsOnly :composeApp:jsBrowserProductionWebpack :server:installDist --no-daemon --max-workers=1

RUN mkdir -p /workspace/web \
    && cp -a /workspace/composeApp/build/processedResources/js/main/. /workspace/web/ \
    && cp -a /workspace/composeApp/build/kotlin-webpack/js/productionExecutable/. /workspace/web/ \
    && cp /workspace/composeApp/src/webMain/resources/login.html /workspace/web/login.html \
    && asset_hash=$(sha256sum /workspace/web/composeApp.js | cut -c1-12) \
    && mv /workspace/web/composeApp.js "/workspace/web/composeApp.${asset_hash}.js" \
    && sed -i -E "s#composeApp\\.js(\\?[^\"']*)?#composeApp.${asset_hash}.js#g" /workspace/web/index.html /workspace/web/login.html \
    && sed -i "s#skiko\\.wasm#skiko.wasm?v=${asset_hash}#g" "/workspace/web/composeApp.${asset_hash}.js" \
    && test -f /workspace/web/skiko.wasm \
    && js_bytes=$(stat -c %s "/workspace/web/composeApp.${asset_hash}.js") \
    && wasm_bytes=$(stat -c %s /workspace/web/skiko.wasm) \
    && printf '{"version":"%s","assets":[{"url":"%s","bytes":%s},{"url":"skiko.wasm?v=%s","bytes":%s}]}\n' "$asset_hash" "composeApp.${asset_hash}.js" "$js_bytes" "$asset_hash" "$wasm_bytes" > /workspace/web/assets-manifest.json \
    && cp /workspace/web/index.html /workspace/web/app.html \
    && find /workspace/web -type f \( -name '*.js' -o -name '*.css' -o -name '*.wasm' \) -exec gzip -9 -k {} + \
    && find /workspace/web -type f \( -name '*.js' -o -name '*.css' -o -name '*.wasm' \) -exec brotli -q 9 -f -k {} + \
    && echo 'Optimized browser asset sizes:' \
    && find /workspace/web -maxdepth 1 -type f \( -name 'composeApp.*' -o -name '*.wasm*' \) -printf '%f %s bytes\n' | sort

FROM eclipse-temurin:21-jre
WORKDIR /opt/oms
COPY --from=build /workspace/server/build/install/server/ ./
COPY --from=build /workspace/web/ ./web/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
EXPOSE 8080
CMD ["bin/server"]
