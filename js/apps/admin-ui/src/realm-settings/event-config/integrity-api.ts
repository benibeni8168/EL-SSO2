import type KeycloakAdminClient from "@keycloak/keycloak-admin-client";
import { fetchWithError } from "@keycloak/keycloak-admin-client";
import { getAuthorizationHeaders } from "../../utils/getAuthorizationHeaders";
import { joinPath } from "../../utils/joinPath";

export type EventIntegrityResult = {
  checked: number;
  valid: number;
  tampered: number;
  legacyNoHash: number;
};

export async function getEventIntegrity(
  adminClient: KeycloakAdminClient,
): Promise<EventIntegrityResult> {
  const accessToken = await adminClient.getAccessToken();
  const response = await fetchWithError(
    joinPath(
      adminClient.baseUrl,
      "admin/realms",
      encodeURIComponent(adminClient.realmName),
      "event-integrity",
    ),
    {
      method: "GET",
      headers: { ...getAuthorizationHeaders(accessToken) },
    },
  );
  return response.json();
}
