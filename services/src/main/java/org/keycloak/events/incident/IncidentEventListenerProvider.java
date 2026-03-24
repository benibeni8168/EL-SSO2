package org.keycloak.events.incident;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerTransaction;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

import org.jboss.logging.Logger;

import static org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction;

public class IncidentEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(IncidentEventListenerProvider.class);

    static final Set<String> DEFAULT_TRIGGERS = Set.of(
            "LOGIN_ERROR", "REGISTER_ERROR", "IMPERSONATE",
            "RESET_PASSWORD_ERROR",
            "USER_DISABLED_BY_PERMANENT_LOCKOUT", "USER_DISABLED_BY_TEMPORARY_LOCKOUT",
            "UPDATE_CREDENTIAL", "REMOVE_CREDENTIAL", "DELETE_ACCOUNT",
            "CLIENT_LOGIN_ERROR", "TOKEN_EXCHANGE_ERROR");

    private final KeycloakSessionFactory sessionFactory;
    private final HttpClient httpClient;
    private final EventListenerTransaction tx;

    public IncidentEventListenerProvider(KeycloakSession session) {
        this.sessionFactory = session.getKeycloakSessionFactory();
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
        if (event.getRealmId() == null) {
            return;
        }

        runJobInTransaction(sessionFactory, session -> {
            RealmModel realm = session.realms().getRealm(event.getRealmId());
            if (realm == null) {
                return;
            }

            IncidentConfigRepresentation config = IncidentConfigStore.readConfig(realm);
            if (config == null || !config.isEnabled() || config.getWebhookUrl() == null
                    || config.getWebhookUrl().isBlank()) {
                return;
            }

            String eventType = event.getType() != null ? event.getType().name() : null;
            if (eventType == null) {
                return;
            }

            Set<String> triggers =
                    config.getTriggerEventTypes() != null && !config.getTriggerEventTypes().isEmpty()
                            ? Set.copyOf(config.getTriggerEventTypes())
                            : DEFAULT_TRIGGERS;

            if (!triggers.contains(eventType)) {
                return;
            }

            String payload = buildEventPayload(event, realm);
            sendToServiceDesk(payload, config);
        });
    }

    private void processAdminEvent(AdminEvent event, boolean includeRepresentation) {
        String realmId = event.getRealmId();
        if (realmId == null && event.getAuthDetails() != null) {
            realmId = event.getAuthDetails().getRealmId();
        }
        if (realmId == null) {
            return;
        }

        final String finalRealmId = realmId;
        runJobInTransaction(sessionFactory, session -> {
            RealmModel realm = session.realms().getRealm(finalRealmId);
            if (realm == null) {
                return;
            }

            IncidentConfigRepresentation config = IncidentConfigStore.readConfig(realm);
            if (config == null || !config.isEnabled() || !config.isIncludeAdminEvents()
                    || config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
                return;
            }

            String payload = buildAdminEventPayload(event, realm);
            sendToServiceDesk(payload, config);
        });
    }

    private String buildEventPayload(Event event, RealmModel realm) {
        StringBuilder details = new StringBuilder("{");
        if (event.getDetails() != null) {
            boolean first = true;
            for (Map.Entry<String, String> entry : event.getDetails().entrySet()) {
                if (!first) details.append(",");
                details.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                       .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
        }
        details.append("}");

        return String.format(
                "{\"source\":\"keycloak\","
                + "\"incidentType\":\"AUTH_EVENT\","
                + "\"eventType\":\"%s\","
                + "\"timestamp\":\"%s\","
                + "\"timestampEpochMs\":%d,"
                + "\"realmName\":\"%s\","
                + "\"clientId\":\"%s\","
                + "\"userId\":\"%s\","
                + "\"ipAddress\":\"%s\","
                + "\"error\":%s,"
                + "\"details\":%s}",
                escapeJson(event.getType() != null ? event.getType().name() : ""),
                Instant.ofEpochMilli(event.getTime()).toString(),
                event.getTime(),
                escapeJson(realm.getName()),
                escapeJson(event.getClientId() != null ? event.getClientId() : ""),
                escapeJson(event.getUserId() != null ? event.getUserId() : ""),
                escapeJson(event.getIpAddress() != null ? event.getIpAddress() : ""),
                event.getError() != null ? "\"" + escapeJson(event.getError()) + "\"" : "null",
                details.toString());
    }

    private String buildAdminEventPayload(AdminEvent event, RealmModel realm) {
        String authDetailsJson = "null";
        if (event.getAuthDetails() != null) {
            authDetailsJson = String.format(
                    "{\"clientId\":\"%s\",\"userId\":\"%s\",\"ipAddress\":\"%s\"}",
                    escapeJson(event.getAuthDetails().getClientId() != null ? event.getAuthDetails().getClientId() : ""),
                    escapeJson(event.getAuthDetails().getUserId() != null ? event.getAuthDetails().getUserId() : ""),
                    escapeJson(event.getAuthDetails().getIpAddress() != null ? event.getAuthDetails().getIpAddress() : ""));
        }

        return String.format(
                "{\"source\":\"keycloak\","
                + "\"incidentType\":\"ADMIN_EVENT\","
                + "\"operationType\":\"%s\","
                + "\"resourceType\":\"%s\","
                + "\"timestamp\":\"%s\","
                + "\"timestampEpochMs\":%d,"
                + "\"realmName\":\"%s\","
                + "\"resourcePath\":\"%s\","
                + "\"error\":%s,"
                + "\"authDetails\":%s}",
                escapeJson(event.getOperationType() != null ? event.getOperationType().name() : ""),
                escapeJson(event.getResourceTypeAsString() != null ? event.getResourceTypeAsString() : ""),
                Instant.ofEpochMilli(event.getTime()).toString(),
                event.getTime(),
                escapeJson(realm.getName()),
                escapeJson(event.getResourcePath() != null ? event.getResourcePath() : ""),
                event.getError() != null ? "\"" + escapeJson(event.getError()) + "\"" : "null",
                authDetailsJson);
    }

    private void sendToServiceDesk(String payload, IncidentConfigRepresentation config) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (config.getApiToken() != null && !config.getApiToken().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiToken());
            }

            httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            logger.warnf("Incident webhook failed with status %d for URL: %s",
                                    response.statusCode(), config.getWebhookUrl());
                        } else {
                            logger.debugf("Incident sent successfully to %s (status: %d)",
                                    config.getWebhookUrl(), response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.errorf(ex, "Incident webhook error for URL %s: %s",
                                config.getWebhookUrl(), ex.getMessage());
                        return null;
                    });
        } catch (IllegalArgumentException e) {
            logger.errorf("Invalid incident webhook URL: %s - %s", config.getWebhookUrl(), e.getMessage());
        } catch (Exception e) {
            logger.errorf(e, "Failed to send incident to %s: %s", config.getWebhookUrl(), e.getMessage());
        }
    }

    static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    @Override
    public void close() {
        // no-op
    }
}
