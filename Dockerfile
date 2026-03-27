# =============================================================================
# Stage 1: Builder — Quarkus augmentation (kc.sh build)
# =============================================================================
FROM eclipse-temurin:25-jdk AS builder

ARG KC_VERSION=999.0.0-SNAPSHOT

WORKDIR /opt/keycloak

# Copy your built Keycloak distribution
COPY quarkus/dist/target/keycloak-${KC_VERSION}.tar.gz /tmp/keycloak.tar.gz

# Extract distribution
RUN tar -xzf /tmp/keycloak.tar.gz --strip-components=1 -C /opt/keycloak \
    && rm /tmp/keycloak.tar.gz

# Run Quarkus augmentation
RUN /opt/keycloak/bin/kc.sh build \
    --db=postgres \
    --http-enabled=true \
    --health-enabled=true


# =============================================================================
# Stage 2: Runtime — Minimal JRE
# =============================================================================
FROM eclipse-temurin:25-jre AS runtime

LABEL org.opencontainers.image.title="Keycloak Enterprise" \
      org.opencontainers.image.version="999.0.0-SNAPSHOT" \
      org.opencontainers.image.description="Custom Keycloak fork with enterprise security features"

ENV KC_RUN_IN_CONTAINER=true \
    LANG=en_US.UTF-8

WORKDIR /opt/keycloak

# Install curl (for health checks)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# ✅ Create non-root user (NO UID conflict)
RUN useradd -r -g 0 \
    --home-dir /opt/keycloak \
    --shell /sbin/nologin \
    keycloak

# ✅ Copy built Keycloak with correct ownership
COPY --from=builder --chown=keycloak:0 /opt/keycloak /opt/keycloak

# ✅ FIX: Proper directory permissions (removes gzip cache warnings)
RUN mkdir -p /opt/keycloak/data \
    && mkdir -p /opt/keycloak/data/tmp \
    && mkdir -p /opt/keycloak/data/cache \
    && chown -R keycloak:0 /opt/keycloak \
    && chmod -R g+rwX /opt/keycloak

# Run as non-root user
USER keycloak

# Ports
EXPOSE 8080
EXPOSE 9000

# Start Keycloak
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]