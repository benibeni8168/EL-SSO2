package org.keycloak.events.log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerTransaction;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

import org.jboss.logging.Logger;

import static org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction;

public class LogExportEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(LogExportEventListenerProvider.class);

    private final KeycloakSessionFactory sessionFactory;
    private final HttpClient httpClient;
    private final LogExportConfig staticConfig;
    private final EventListenerTransaction tx;

    public LogExportEventListenerProvider(KeycloakSession session, LogExportConfig config) {
        this.sessionFactory = session.getKeycloakSessionFactory();
        this.staticConfig = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.tx = new EventListenerTransaction(this::processAdminEvent, this::processEvent);
        session.getTransactionManager().enlistAfterCompletion(tx);
    }

    @Override
    public void onEvent(Event event) {
        tx.addEvent(event);
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        tx.addAdminEvent(event, includeRepresentation);
    }

    private void processEvent(Event event) {
        runJobInTransaction(sessionFactory, session -> {
            LogExportConfig config = resolveConfig(session, event.getRealmId());
            if (config == null || !config.hasExportUrl()) {
                logger.debugf("Log export skipped: no export URL configured");
                return;
            }
            if (!shouldExportEvent(event, config)) {
                logger.debugf("Event %s filtered out by include/exclude configuration", event.getType());
                return;
            }
            String payload = LogExportPayloadBuilder.buildPayload(event, config.getFormat());
            sendToExternalSystem(payload, config);
        });
    }

    private void processAdminEvent(AdminEvent event, boolean includeRepresentation) {
        String realmId = event.getRealmId();
        if (realmId == null && event.getAuthDetails() != null) {
            realmId = event.getAuthDetails().getRealmId();
        }
        final String finalRealmId = realmId;
        runJobInTransaction(sessionFactory, session -> {
            LogExportConfig config = resolveConfig(session, finalRealmId);
            if (config == null || !config.hasExportUrl()) {
                logger.debugf("Log export skipped: no export URL configured");
                return;
            }
            if (!config.isIncludeAdminEvents()) {
                logger.debugf("Admin event export disabled by configuration");
                return;
            }
            if (!shouldExportAdminEvent(event, config)) {
                logger.debugf("Admin event %s filtered out by include/exclude configuration",
                        event.getOperationType());
                return;
            }
            String payload = LogExportPayloadBuilder.buildPayload(event, config.getFormat());
            sendToExternalSystem(payload, config);
        });
    }

    private boolean shouldExportEvent(Event event, LogExportConfig config) {
        String eventType = event.getType() != null ? event.getType().name() : null;
        if (eventType == null) {
            return false;
        }
        return shouldExport(eventType, config);
    }

    private boolean shouldExportAdminEvent(AdminEvent event, LogExportConfig config) {
        String operationType = event.getOperationType() != null ? event.getOperationType().name() : null;
        if (operationType == null) {
            return false;
        }
        return shouldExport(operationType, config);
    }

    private boolean shouldExport(String type, LogExportConfig config) {
        if (!config.getIncludeEvents().isEmpty()) {
            return config.getIncludeEvents().contains(type);
        }
        if (!config.getExcludeEvents().isEmpty()) {
            return !config.getExcludeEvents().contains(type);
        }
        return true;
    }

    private void sendToExternalSystem(String payload, LogExportConfig config) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getExportUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", getContentType(config))
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (config.getAuthToken() != null && !config.getAuthToken().isEmpty()) {
                requestBuilder.header("Authorization", config.getAuthToken());
            }

            for (Map.Entry<String, String> header : config.getCustomHeaders().entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            logger.warnf("Log export failed with status %d for URL: %s. Response: %s",
                                    response.statusCode(), config.getExportUrl(),
                                    truncateResponse(response.body()));
                        } else {
                            logger.debugf("Log exported successfully to %s (status: %d)",
                                    config.getExportUrl(), response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.errorf(ex, "Log export error for URL %s: %s",
                                config.getExportUrl(), ex.getMessage());
                        return null;
                    });
        } catch (IllegalArgumentException e) {
            logger.errorf("Invalid export URL configured: %s - %s", config.getExportUrl(), e.getMessage());
        } catch (Exception e) {
            logger.errorf(e, "Failed to send log export to %s: %s", config.getExportUrl(), e.getMessage());
        }
    }

    private String getContentType(LogExportConfig config) {
        switch (config.getFormat()) {
            case LogExportConfig.FORMAT_SYSLOG:
            case LogExportConfig.FORMAT_CEF:
                return "text/plain";
            case LogExportConfig.FORMAT_JSON:
            default:
                return "application/json";
        }
    }

    private String truncateResponse(String response) {
        if (response == null) {
            return "";
        }
        int maxLength = 200;
        if (response.length() > maxLength) {
            return response.substring(0, maxLength) + "...";
        }
        return response;
    }

    private LogExportConfig resolveConfig(KeycloakSession session, String realmId) {
        if (realmId == null || realmId.isBlank()) {
            return staticConfig != null && staticConfig.hasExportUrl() ? staticConfig : null;
        }

        RealmModel realm = session.realms().getRealm(realmId);
        if (realm == null) {
            realm = session.realms().getRealmByName(realmId);
        }

        if (realm != null) {
            SiemConfigRepresentation effective = SiemConfigStore.resolveEffectiveConfig(
                    session,
                    realm,
                    staticConfig,
                    SiemConfigStore.hasListenerConfigured(realm));
            return toLogExportConfig(effective);
        }
        return staticConfig != null && staticConfig.hasExportUrl() ? staticConfig : null;
    }

    private LogExportConfig toLogExportConfig(SiemConfigRepresentation config) {
        if (config == null || !config.isEnabled() || config.getExportUrl() == null || config.getExportUrl().isBlank()) {
            return null;
        }
        return new LogExportConfig(
                config.getExportUrl(),
                config.getAuthToken(),
                config.getFormat(),
                config.getIncludeEvents(),
                config.getExcludeEvents(),
                config.isIncludeAdminEvents(),
                config.getCustomHeaders()
        );
    }

    public void sendTestEvent(LogExportConfig config, RealmModel realm) {
        Event testEvent = new Event();
        testEvent.setId("siem-test");
        testEvent.setTime(System.currentTimeMillis());
        testEvent.setType(EventType.LOGIN);
        testEvent.setRealmId(realm.getId());
        testEvent.setRealmName(realm.getName());
        testEvent.setClientId("admin-console");
        testEvent.setUserId("siem-test-user");
        testEvent.setIpAddress("127.0.0.1");
        sendToExternalSystem(LogExportPayloadBuilder.buildPayload(testEvent, config.getFormat()), config);
    }

    @Override
    public void close() {
        // no-op
    }
}
