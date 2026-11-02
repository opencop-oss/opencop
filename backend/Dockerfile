# Copyright 2026 NexTech Solutions LLC
# SPDX-License-Identifier: Apache-2.0

ARG RUNTIME_STAGE=runtime-alpine
ARG OPENCOP_BASE_IMAGE=opencop-base:latest

# Stage 1: Build the service
FROM ${OPENCOP_BASE_IMAGE} AS builder

WORKDIR /workspace

ARG MODULE_DIR
ARG BYTEDECO_CLASSIFIER

ENV MAVEN_CACHE_REPO=/root/.m2-cache/repository \
    MAVEN_IMAGE_REPO=/root/.m2/repository

# Copy build tooling and pom first so dependency resolution is cached
# independently of source changes
COPY ${MODULE_DIR}/pom.xml ./pom.xml
COPY ${MODULE_DIR}/.mvn .mvn
COPY ${MODULE_DIR}/mvnw mvnw
RUN chmod +x mvnw

RUN --mount=type=cache,id=opencop-maven-repository,target=/root/.m2-cache/repository,sharing=locked \
    rm -rf "$MAVEN_CACHE_REPO/com/opencop" \
 && mkdir -p "$MAVEN_CACHE_REPO/com" \
 && cp -a "$MAVEN_IMAGE_REPO/com/opencop" "$MAVEN_CACHE_REPO/com/" \
 && ./mvnw -Dmaven.repo.local="$MAVEN_CACHE_REPO" ${BYTEDECO_CLASSIFIER:+-Dbytedeco.classifier=${BYTEDECO_CLASSIFIER}} dependency:go-offline

# Now copy source — only this layer and below rebuild on code changes
COPY ${MODULE_DIR}/src ./src

RUN --mount=type=cache,id=opencop-maven-repository,target=/root/.m2-cache/repository,sharing=locked \
    rm -rf "$MAVEN_CACHE_REPO/com/opencop" \
 && mkdir -p "$MAVEN_CACHE_REPO/com" \
 && cp -a "$MAVEN_IMAGE_REPO/com/opencop" "$MAVEN_CACHE_REPO/com/" \
 && ./mvnw -Dmaven.repo.local="$MAVEN_CACHE_REPO" ${BYTEDECO_CLASSIFIER:+-Dbytedeco.classifier=${BYTEDECO_CLASSIFIER}} package -Dskip.unit.tests=true -Dskip.component.tests=true \
 && set -- $(find target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar') \
 && [ "$#" -eq 1 ] \
 && cp "$1" /workspace/app.jar


# Stage 2a: Default runtime image
FROM alpine:3.20 AS runtime-alpine

ENV JAVA_HOME=/opt/jre/jre-25
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=builder /opt/jre/jre-25 $JAVA_HOME

# Stage 2b: glibc runtime image for native libraries that do not support musl.
FROM eclipse-temurin:25-jre-noble AS runtime-glibc

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      libdrm2 \
      libva-drm2 \
      libva2 \
      wget \
 && rm -rf /var/lib/apt/lists/*

# Stage 3: Runtime image
FROM ${RUNTIME_STAGE}

ARG APPLICATION_USER=spring
RUN addgroup --system "$APPLICATION_USER" \
 && adduser  --system "$APPLICATION_USER" --ingroup "$APPLICATION_USER"

RUN mkdir /app && chown -R "$APPLICATION_USER" /app
WORKDIR /app

ARG JAVA_NATIVE_ACCESS_OPTIONS=""
ENV APPLICATIONINSIGHTS_AGENT_JAR=/app/applicationinsights-agent.jar \
    JAVA_TOOL_OPTIONS="-javaagent:/app/applicationinsights-agent.jar -Dotel.instrumentation.micrometer.enabled=true -Dotel.instrumentation.micrometer.prometheus-mode.enabled=true" \
    JDK_JAVA_OPTIONS="${JAVA_NATIVE_ACCESS_OPTIONS}"

COPY --from=builder \
  --chown=${APPLICATION_USER}:${APPLICATION_USER} \
  --chmod=0444 \
  /opt/applicationinsights-agent.jar /app/applicationinsights-agent.jar

ARG BUILD_GIT_SHA=unknown
ARG APP_VERSION=
ARG BUILD_REVISION=
ARG BUILD_BRANCH=
ARG BUILD_DATE=
ENV BUILD_GIT_SHA=${BUILD_GIT_SHA} APP_VERSION=${APP_VERSION} BUILD_REVISION=${BUILD_REVISION} BUILD_BRANCH=${BUILD_BRANCH} BUILD_DATE=${BUILD_DATE}
LABEL org.opencontainers.image.revision=${BUILD_REVISION} org.opencontainers.image.created=${BUILD_DATE}

COPY --from=builder \
  --chown=${APPLICATION_USER}:${APPLICATION_USER} \
  --chmod=0444 \
  /workspace/app.jar /app/app.jar

EXPOSE 8080
USER $APPLICATION_USER
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
