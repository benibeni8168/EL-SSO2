import type KeycloakAdminClient from "@keycloak/keycloak-admin-client";
import { fetchWithError } from "@keycloak/keycloak-admin-client";
import { getAuthorizationHeaders } from "../../utils/getAuthorizationHeaders";
import { joinPath } from "../../utils/joinPath";

export type PamConfigRepresentation = {
  enabled?: boolean;
  webhookUrl?: string;
  apiToken?: string;
  privilegedClients?: string[];
  allClientsPrivileged?: boolean;
  notifySessions?: boolean;
  checkTimeoutSeconds?: number;
};

export type PamTestResultRepresentation = {
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

export function getPamConfig(
  adminClient: KeycloakAdminClient,
): Promise<PamConfigRepresentation> {
  return requestJson<PamConfigRepresentation>(adminClient, "pam", {
    method: "GET",
  });
}

export function putPamConfig(
  adminClient: KeycloakAdminClient,
  config: PamConfigRepresentation,
): Promise<void> {
  return adminClient.getAccessToken().then(async (accessToken) => {
    await fetchWithError(
      joinPath(
        adminClient.baseUrl,
        "admin/realms",
        encodeURIComponent(adminClient.realmName),
        "pam",
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

export function postPamTest(
  adminClient: KeycloakAdminClient,
): Promise<PamTestResultRepresentation> {
  return requestJson<PamTestResultRepresentation>(adminClient, "pam/test", {
    method: "POST",
  });
}
