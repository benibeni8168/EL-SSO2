import type KeycloakAdminClient from "@keycloak/keycloak-admin-client";
import { fetchWithError } from "@keycloak/keycloak-admin-client";
import { getAuthorizationHeaders } from "../../utils/getAuthorizationHeaders";
import { joinPath } from "../../utils/joinPath";

export type SiemScope = "realm" | "global";

export type SiemConfigRepresentation = {
  enabled?: boolean;
  inheritGlobal?: boolean;
  transport?: "http" | string;
  exportUrl?: string;
  authToken?: string;
  signingSecret?: string;
  format?: "json" | "syslog" | "cef" | string;
  includeAdminEvents?: boolean;
  includeEvents?: string[];
  excludeEvents?: string[];
  customHeaders?: Record<string, string>;
  realmOverride?: SiemConfigRepresentation;
  globalDefaults?: SiemConfigRepresentation;
};

export type SiemTestResultRepresentation = {
  sent?: boolean;
  status?: string;
  message?: string;
};

const pathForScope = (scope: SiemScope) =>
  scope === "global" ? "siem/global" : "siem";

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

export function getSiemConfig(
  adminClient: KeycloakAdminClient,
  scope: SiemScope,
) {
  return requestJson<SiemConfigRepresentation>(
    adminClient,
    pathForScope(scope),
    {
      method: "GET",
    },
  );
}

export function putSiemConfig(
  adminClient: KeycloakAdminClient,
  scope: SiemScope,
  config: SiemConfigRepresentation,
) {
  return adminClient.getAccessToken().then(async (accessToken) => {
    await fetchWithError(
      joinPath(
        adminClient.baseUrl,
        "admin/realms",
        encodeURIComponent(adminClient.realmName),
        pathForScope(scope),
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

export function postSiemTest(adminClient: KeycloakAdminClient) {
  return requestJson<SiemTestResultRepresentation>(adminClient, "siem/test", {
    method: "POST",
  });
}
