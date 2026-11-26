# ===================================================================
# Stage 1: Build com JDK 21.0.13.1 (CVE-2026-114938 corrigida) e Gradle Wrapper
# Eclipse Temurin 21.0.13.1+2 — tag fixada para evitar regressao de seguranca
# ===================================================================
FROM eclipse-temurin:21.0.13.1_2-jdk-jammy AS builder

WORKDIR /workspace

# Copia primeiro os descritores de build e o Gradle Wrapper para aproveitar cache de camadas
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copia o codigo-fonte e gera o bootJar
COPY src ./src

RUN ./gradlew bootJar --no-daemon

# ===================================================================
# Stage 2: Runtime leve com JRE 21.0.13.1 (glibc para JAVE/FFmpeg nativo)
# Eclipse Temurin 21.0.13.1+2 — tag fixada para evitar regressao de seguranca
# ===================================================================
FROM eclipse-temurin:21.0.13.1_2-jre-jammy AS runtime

# Cria usuario e grupo nao-root
RUN groupadd --system --gid 1001 soniclab \
    && useradd --system --uid 1001 --gid soniclab --shell /bin/false soniclab

WORKDIR /app

# Garante diretorio da aplicacao, uploads locais (se usados em dev) e /tmp com permissoes adequadas
RUN mkdir -p /app/uploads /tmp \
    && chown -R soniclab:soniclab /app \
    && chmod 1777 /tmp

# Copia apenas o JAR executavel gerado no stage anterior
COPY --from=builder --chown=soniclab:soniclab /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar

USER soniclab:soniclab

ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
