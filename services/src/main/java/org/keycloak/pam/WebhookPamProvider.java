package org.keycloak.pam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.keycloak.events.pam.PamConfigRepresentation;

import org.jboss.logging.Logger;

public class WebhookPamProvider implements PamProvider {

    private static final Logger logger = Logger.getLogger(WebhookPamProvider.class);

    private final PamConfigRepresentation config;
    private final HttpClient httpClient;

    public WebhookPamProvider(PamConfigRepresentation config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public PamCheckResult checkAccess(String userId, String username, String clientId, String realmId, String ipAddress) {
        if (config == null || config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            return new PamCheckResult(false, "PAM system not configured", null);
        }

        String url = config.getWebhookUrl() + "/check";
        long timestamp = System.currentTimeMillis() / 1000L;
        String payload = String.format(
                "{\"userId\":\"%s\",\"username\":\"%s\",\"clientId\":\"%s\",\"realmId\":\"%s\",\"ipAddress\":\"%s\",\"timestamp\":%d}",
                escapeJson(userId), escapeJson(username), escapeJson(clientId), escapeJson(realmId), escapeJson(ipAddress), timestamp);

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(config.getCheckTimeoutSeconds() > 0 ? config.getCheckTimeoutSeconds() : 5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (config.getApiToken() != null && !config.getApiToken().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiToken());
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                logger.warnf("PAM check failed with status %d for URL: %s", response.statusCode(), url);
                return new PamCheckResult(false, "PAM system returned error: " + response.statusCode(), null);
            }

            return parseCheckResponse(response.body());

        } catch (Exception e) {
            logger.errorf(e, "PAM check call failed for URL %s: %s", url, e.getMessage());
            return new PamCheckResult(false, "PAM system unreachable", null);
        }
    }

    @Override
    public void notifySession(String event, String sessionId, String userId, String username, String clientId, String realmId, String ipAddress) {
        if (config == null || config.getWebhookUrl() == null || config.getWebhookUrl().isBlank()) {
            return;
        }

        String url = config.getWebhookUrl() + "/session";
        long timestamp = System.currentTimeMillis() / 1000L;
        String payload = String.format(
                "{\"event\":\"%s\",\"sessionId\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"clientId\":\"%s\",\"realmId\":\"%s\",\"ipAddress\":\"%s\",\"timestamp\":%d}",
                escapeJson(event), escapeJson(sessionId != null ? sessionId : ""),
                escapeJson(userId), escapeJson(username), escapeJson(clientId != null ? clientId : ""),
                escapeJson(realmId), escapeJson(ipAddress != null ? ipAddress : ""), timestamp);

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (config.getApiToken() != null && !config.getApiToken().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiToken());
            }

            httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 400) {
                            logger.warnf("PAM session notification failed with status %d for URL: %s",
                                    response.statusCode(), url);
                        } else {
                            logger.debugf("PAM session notification sent successfully to %s (status: %d)",
                                    url, response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.errorf(ex, "PAM session notification error for URL %s: %s", url, ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            logger.errorf(e, "Failed to send PAM session notification to %s: %s", url, e.getMessage());
        }
    }

    @Override
    public void close() {
        // no-op
    }

    private PamCheckResult parseCheckResponse(String body) {
        if (body == null || body.isBlank()) {
            return new PamCheckResult(false, "Empty response from PAM system", null);
        }
        try {
            boolean allowed = body.contains("\"allowed\":true");
            String reason = extractJsonString(body, "reason");
            String pamSessionRef = extractJsonString(body, "pamSessionRef");
            return new PamCheckResult(allowed, reason, pamSessionRef);
        } catch (Exception e) {
            logger.warnf("Failed to parse PAM check response: %s", body);
            return new PamCheckResult(false, "Invalid response from PAM system", null);
        }
    }

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start < 0) {
            return null;
        }
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end < 0) {
            return null;
        }
        return json.substring(start, end);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
