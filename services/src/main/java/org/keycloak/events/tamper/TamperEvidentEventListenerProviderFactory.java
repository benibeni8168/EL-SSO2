package org.keycloak.events.tamper;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/**
 * Factory for the tamper-evident event listener.
 * Maintains per-factory hash chain state so all sessions share one chain.
 * 
 * Supports optional HMAC-SHA256 for stronger tamper protection when configured
 * with an HMAC secret key via the "hmac-key" config option or KC_TAMPER_HMAC_KEY
 * environment variable.
 */
public class TamperEvidentEventListenerProviderFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "tamper-evident";
    public static final String HMAC_KEY_ENV_VAR = "KC_TAMPER_HMAC_KEY";

    private final AtomicReference<String> lastEventHash = new AtomicReference<>("");
    private final AtomicReference<String> lastAdminEventHash = new AtomicReference<>("");

    private String hmacSecretKey;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new TamperEvidentEventListenerProvider(lastEventHash, lastAdminEventHash, hmacSecretKey);
    }

    @Override
    public void init(Config.Scope config) {
        this.hmacSecretKey = config.get("hmac-key", System.getenv(HMAC_KEY_ENV_VAR));
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name("hmac-key")
                .type("string")
                .helpText("Secret key for HMAC-SHA256 computation. If not provided, falls back to plain SHA-256. " +
                          "Can also be set via the " + HMAC_KEY_ENV_VAR + " environment variable.")
                .secret(true)
                .add()
                .build();
    }
}
