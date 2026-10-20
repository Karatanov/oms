# syntax=docker/dockerfile:1.7

# Keep JVM dependency resolution separate from both application build targets.
# The CI workflow exports this stage to BuildKit's GitHub Actions cache, so an
# unchanged Gradle graph does not have to be downloaded on every deployment.
FROM gradle:8.14-jdk21 AS gradle-base
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

# A server-only change must not rebuild the production WASM bundle.  Splitting
# these stages lets BuildKit reuse the completed frontend stage independently.
FROM gradle-base AS frontend-build
COPY composeApp/src composeApp/src
COPY shared/src shared/src
RUN gradle -PrenderJsOnly :composeApp:jsBrowserProductionWebpack --no-daemon --max-workers=1

# Keep a stable, small frontend hand-off directory.  CI can produce exactly
# this directory in a dedicated Gradle job and let the image build reuse it;
# a normal local docker build still uses this stage and remains self-contained.
FROM frontend-build AS frontend-built-layout
RUN mkdir -p /workspace/frontend/processed /workspace/frontend/executable /workspace/frontend/source-resources \
    && cp -a /workspace/composeApp/build/processedResources/js/main/. /workspace/frontend/processed/ \
    && cp -a /workspace/composeApp/build/kotlin-webpack/js/productionExecutable/. /workspace/frontend/executable/ \
    && cp /workspace/composeApp/src/webMain/resources/login.html /workspace/frontend/source-resources/login.html \
    && cp /workspace/composeApp/src/webMain/resources/favicon.svg /workspace/frontend/source-resources/favicon.svg \
    && cp /workspace/composeApp/src/webMain/resources/social-preview.png /workspace/frontend/source-resources/social-preview.png

# The CI workflow downloads its Gradle-built frontend artifact to this
# directory and selects this stage with FRONTEND_SOURCE=frontend-prebuilt.
# BuildKit skips this otherwise-unreferenced stage for ordinary local builds.
FROM scratch AS frontend-prebuilt
COPY frontend-dist/ /workspace/frontend/

FROM gradle-base AS server-build
COPY shared/src shared/src
COPY server/src server/src
RUN gradle :server:installDist --no-daemon --max-workers=1

# Select either the local Gradle stage or the artifact downloaded by CI.
ARG FRONTEND_SOURCE=frontend-built-layout
FROM ${FRONTEND_SOURCE} AS frontend-source

# The VPS serves the browser bundle and API from one origin.  This small
# packaging stage is intentionally separate: build metadata changes only
# repackage already-built assets instead of triggering Gradle/Webpack again.
FROM debian:bookworm-slim AS web-build
RUN apt-get update && apt-get install -y --no-install-recommends brotli && rm -rf /var/lib/apt/lists/*
COPY --from=frontend-source /workspace/frontend/ /workspace/frontend/
ARG OMS_GIT_SHA=local
ARG OMS_BUILD_DATE=local
RUN mkdir -p /workspace/web \
    && cp -a /workspace/frontend/processed/. /workspace/web/ \
    && cp -a /workspace/frontend/executable/. /workspace/web/ \
    && cp /workspace/frontend/source-resources/login.html /workspace/web/login.html \
    && cp /workspace/frontend/source-resources/favicon.svg /workspace/web/favicon.svg \
    && cp /workspace/frontend/source-resources/social-preview.png /workspace/web/social-preview.png \
    && wasm_asset=$(find /workspace/web -maxdepth 1 -type f -name '*.wasm' ! -name 'skiko.wasm' -printf '%f\n' | head -n 1) \
    && test -n "$wasm_asset" && test -f "/workspace/web/$wasm_asset" \
    && sed -i "s#skiko\\.wasm#$wasm_asset#g" /workspace/web/composeApp.js \
    && asset_hash=$(sha256sum /workspace/web/composeApp.js | cut -c1-12) \
    && mv /workspace/web/composeApp.js "/workspace/web/composeApp.${asset_hash}.js" \
    && sed -i -E "s#composeApp\\.js(\\?[^\"']*)?#composeApp.${asset_hash}.js#g" /workspace/web/index.html /workspace/web/login.html \
    && sed -i "s#__OMS_WASM_ASSET__#$wasm_asset#g" /workspace/web/index.html \
    && js_bytes=$(stat -c %s "/workspace/web/composeApp.${asset_hash}.js") \
    && wasm_bytes=$(stat -c %s "/workspace/web/$wasm_asset") \
    && printf '{"version":"%s","assets":[{"url":"%s","bytes":%s},{"url":"%s","bytes":%s}]}\n' "$asset_hash" "composeApp.${asset_hash}.js" "$js_bytes" "$wasm_asset" "$wasm_bytes" > /workspace/web/assets-manifest.json \
    && cp /workspace/web/index.html /workspace/web/app.html \
    && git_short=$(printf '%s' "$OMS_GIT_SHA" | cut -c1-12) \
    && sed -i "s|__OMS_GIT_COMMIT__|$git_short|g; s|__OMS_BUILD_DATE__|$OMS_BUILD_DATE|g" /workspace/web/app.html \
    && find /workspace/web -type f \( -name '*.js' -o -name '*.css' -o -name '*.wasm' \) -exec gzip -9 -k {} + \
    && find /workspace/web -type f \( -name '*.js' -o -name '*.css' -o -name '*.wasm' \) -exec brotli -q 9 -f -k {} + \
    && echo 'Optimized browser asset sizes:' \
    && find /workspace/web -maxdepth 1 -type f \( -name 'composeApp.*' -o -name '*.wasm*' \) -printf '%f %s bytes\n' | sort

FROM eclipse-temurin:21-jre
WORKDIR /opt/oms
ARG OMS_GIT_SHA=local
# The SIR PDF endpoint renders the approved XLSX template with LibreOffice.
# Keeping this in the runtime image makes the conversion deterministic and
# avoids depending on a desktop Office installation or an external service.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice-calc fonts-crosextra-carlito \
    && rm -rf /var/lib/apt/lists/*
COPY --from=server-build /workspace/server/build/install/server/ ./
COPY --from=web-build /workspace/web/ ./web/
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0"
ENV OMS_GIT_REVISION="$OMS_GIT_SHA"
EXPOSE 8080
CMD ["bin/server"]
