import type KeycloakAdminClient from "@keycloak/keycloak-admin-client";
import { fetchWithError } from "@keycloak/keycloak-admin-client";
import { getAuthorizationHeaders } from "../../utils/getAuthorizationHeaders";
import { joinPath } from "../../utils/joinPath";

export type IncidentConfigRepresentation = {
  enabled?: boolean;
  webhookUrl?: string;
  apiToken?: string;
  triggerEventTypes?: string[];
  includeAdminEvents?: boolean;
};

export type IncidentTestResultRepresentation = {
  sent?: boolean;
  status?: string;
  message?: string;
};

async function requestJson<T>(
  adminClient: KeycloakAdminClient,
  path: string,
  init: RequestInit,
): Promise<T> {
  const accessToken = await adminClient.getAccessToken();
  const headers = new Headers(init.headers);
  Object.entries(getAuthorizationHeaders(accessToken)).forEach(([key, value]) =>
    headers.set(key, value),
  );
  const response = await fetchWithError(
    joinPath(
      adminClient.baseUrl,
      "admin/realms",
      encodeURIComponent(adminClient.realmName),
      path,
    ),
    {
      ...init,
      headers,
    },
  );

  return response.json();
}

export function getIncidentConfig(
  adminClient: KeycloakAdminClient,
): Promise<IncidentConfigRepresentation> {
  return requestJson<IncidentConfigRepresentation>(adminClient, "incident", {
    method: "GET",
  });
}

export function putIncidentConfig(
  adminClient: KeycloakAdminClient,
  config: IncidentConfigRepresentation,
): Promise<void> {
  return adminClient.getAccessToken().then(async (accessToken) => {
    await fetchWithError(
      joinPath(
        adminClient.baseUrl,
        "admin/realms",
        encodeURIComponent(adminClient.realmName),
        "incident",
      ),
      {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...getAuthorizationHeaders(accessToken),
        },
        body: JSON.stringify(config),
      },
    );
  });
}

export function postIncidentTest(
  adminClient: KeycloakAdminClient,
): Promise<IncidentTestResultRepresentation> {
  return requestJson<IncidentTestResultRepresentation>(adminClient, "incident/test", {
    method: "POST",
  });
}
