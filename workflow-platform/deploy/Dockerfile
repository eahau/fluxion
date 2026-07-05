# ============================================================
# Fluxion 多阶段构建 — 支持 Native Image（零 JRE）+ 传统 Fat Jar
# ============================================================
#
# 用法：
#   # 1. Native Image（推荐，~2MB 运行时镜像，零 JRE）
#   docker build -t fluxion-admin:native --target native .
#
#   # 2. 传统 Fat Jar（兼容性好，需 JRE 21）
#   docker build -t fluxion-admin:jvm --target jvm .
#
# 运行：
#   docker run -d -p 8080:8080 \
#     -e SPRING_PROFILES_ACTIVE=prod \
#     fluxion-admin:native

# ── 公共构建阶段 ──────────────────────────────────────────────
FROM ghcr.io/graalvm/graalvm-community:21 AS builder
WORKDIR /build

# 先拷贝 Gradle wrapper 和构建脚本（利用 Docker 缓存层）
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

# 拷贝所有子模块的 build.gradle.kts（依赖不变时跳过下载）
COPY fluxion-core/build.gradle.kts fluxion-core/
COPY fluxion-core-spring-boot/build.gradle.kts fluxion-core-spring-boot/
COPY fluxion-decorator-impl/core/build.gradle.kts fluxion-decorator-impl/core/
COPY fluxion-decorator-impl/spring-boot/build.gradle.kts fluxion-decorator-impl/spring-boot/
COPY fluxion-adapter-spi/build.gradle.kts fluxion-adapter-spi/
COPY fluxion-adapter-http/core/build.gradle.kts fluxion-adapter-http/core/
COPY fluxion-adapter-http/springmvc/build.gradle.kts fluxion-adapter-http/springmvc/
COPY fluxion-adapter-http/springmvc/spring-boot/build.gradle.kts fluxion-adapter-http/springmvc/spring-boot/
COPY fluxion-adapter-http/springmvc/nacos/build.gradle.kts fluxion-adapter-http/springmvc/nacos/
COPY fluxion-adapter-http/springmvc/apollo/build.gradle.kts fluxion-adapter-http/springmvc/apollo/
COPY fluxion-adapter-rpc/dubbo/build.gradle.kts fluxion-adapter-rpc/dubbo/
COPY fluxion-adapter-rpc/grpc/build.gradle.kts fluxion-adapter-rpc/grpc/
COPY fluxion-adapter-rpc/spring-boot/build.gradle.kts fluxion-adapter-rpc/spring-boot/
COPY fluxion-adapter-mq/kafka/build.gradle.kts fluxion-adapter-mq/kafka/
COPY fluxion-adapter-mq/spring-boot/build.gradle.kts fluxion-adapter-mq/spring-boot/
COPY fluxion-builtin-functions/core/build.gradle.kts fluxion-builtin-functions/core/
COPY fluxion-builtin-functions/spring-boot/build.gradle.kts fluxion-builtin-functions/spring-boot/
COPY fluxion-redis/core/build.gradle.kts fluxion-redis/core/
COPY fluxion-redis/spring-boot/build.gradle.kts fluxion-redis/spring-boot/
COPY fluxion-redis/lettuce/build.gradle.kts fluxion-redis/lettuce/
COPY fluxion-redis/redisson/build.gradle.kts fluxion-redis/redisson/
COPY fluxion-redis/spring-data/build.gradle.kts fluxion-redis/spring-data/
COPY fluxion-script-engine/core/build.gradle.kts fluxion-script-engine/core/
COPY fluxion-script-engine/spring-boot/build.gradle.kts fluxion-script-engine/spring-boot/
COPY fluxion-config/core/build.gradle.kts fluxion-config/core/
COPY fluxion-config/apollo/build.gradle.kts fluxion-config/apollo/
COPY fluxion-config/nacos/build.gradle.kts fluxion-config/nacos/
COPY fluxion-config/http/build.gradle.kts fluxion-config/http/
COPY fluxion-config/spring-boot/build.gradle.kts fluxion-config/spring-boot/
COPY fluxion-config/registry-http/build.gradle.kts fluxion-config/registry-http/
COPY fluxion-di/core/build.gradle.kts fluxion-di/core/
COPY fluxion-di/spring/build.gradle.kts fluxion-di/spring/
COPY fluxion-runtime/build.gradle.kts fluxion-runtime/
COPY fluxion-runtime/core/build.gradle.kts fluxion-runtime/core/
COPY fluxion-runtime/spring-boot/build.gradle.kts fluxion-runtime/spring-boot/
COPY fluxion-admin/build.gradle.kts fluxion-admin/
COPY doc/ doc/

# 下载依赖（利用 Docker 缓存层，依赖不变时秒过）
RUN chmod +x gradlew && \
    ./gradlew :fluxion-admin:dependencies --no-daemon || true

# 拷贝全部源码并编译
COPY . .
RUN ./gradlew :fluxion-admin:bootJar --no-daemon

# ── 阶段2a：Native Image（静态链接，零 JRE）─────────────────
FROM ghcr.io/graalvm/native-image-community:21-musl AS native-builder
WORKDIR /build
COPY --from=builder /build .
RUN ./gradlew :fluxion-admin:nativeCompile --no-daemon \
    -Pagent \
    -Dorg.gradle.jvmargs="-Xmx6g"

# 运行时：distroless static（~2MB，无 shell/无 JRE/无包管理器）
FROM gcr.io/distroless/static AS native
COPY --from=native-builder /build/fluxion-admin/build/native/nativeCompile/fluxion-admin /app/fluxion-admin
EXPOSE 8080
ENTRYPOINT ["/app/fluxion-admin"]

# ── 阶段2b：传统 Fat Jar（需 JRE 21）──────────────────────
FROM eclipse-temurin:21-jre-alpine AS jvm
WORKDIR /app
COPY --from=builder /build/fluxion-admin/build/libs/fluxion-admin-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
