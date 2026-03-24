import {
  ActionGroup,
  Alert,
  AlertVariant,
  Button,
  DataList,
  DataListCell,
  DataListItem,
  DataListItemCells,
  DataListItemRow,
  EmptyState,
  EmptyStateBody,
  EmptyStateFooter,
  Form,
  FormGroup,
  Modal,
  ModalVariant,
  PageSection,
  Spinner,
  Stack,
  StackItem,
  Text,
  TextVariants,
  Title,
} from "@patternfly/react-core";
import { TextControl, useAlerts, useFetch } from "@keycloak/keycloak-ui-shared";
import { useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../admin-client";
import { useRealm } from "../context/realm-context/RealmContext";
import { fetchWithError } from "@keycloak/keycloak-admin-client";
import { getAuthorizationHeaders } from "../utils/getAuthorizationHeaders";
import { joinPath } from "../utils/joinPath";

type CloudAppRepresentation = {
  id: string;
  clientId: string;
  name?: string;
  description?: string;
  enabled: boolean;
};

type RegisterFormValues = {
  clientId: string;
  name: string;
  description: string;
};

async function listCloudApps(
  adminClient: { baseUrl: string; realmName: string; getAccessToken: () => Promise<string | undefined> },
  realm: string,
): Promise<CloudAppRepresentation[]> {
  const accessToken = await adminClient.getAccessToken();
  const response = await fetchWithError(
    joinPath(adminClient.baseUrl, "admin/realms", encodeURIComponent(realm), "cloud-apps"),
    {
      method: "GET",
      headers: { ...getAuthorizationHeaders(accessToken) },
    },
  );
  return response.json();
}

async function registerCloudApp(
  adminClient: { baseUrl: string; realmName: string; getAccessToken: () => Promise<string | undefined> },
  realm: string,
  data: RegisterFormValues,
): Promise<CloudAppRepresentation> {
  const accessToken = await adminClient.getAccessToken();
  const response = await fetchWithError(
    joinPath(adminClient.baseUrl, "admin/realms", encodeURIComponent(realm), "cloud-apps"),
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...getAuthorizationHeaders(accessToken),
      },
      body: JSON.stringify(data),
    },
  );
  return response.json();
}

export const CloudAppsTab = () => {
  const { t } = useTranslation();
  const { adminClient } = useAdminClient();
  const { realm } = useRealm();
  const { addAlert, addError } = useAlerts();
  const [apps, setApps] = useState<CloudAppRepresentation[]>([]);
  const [loading, setLoading] = useState(true);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const form = useForm<RegisterFormValues>({ mode: "onChange" });
  const { handleSubmit, reset } = form;

  useFetch(
    () => listCloudApps(adminClient, realm),
    (result) => {
      setApps(result);
      setLoading(false);
    },
    [realm, refreshKey],
  );

  const onRegister = async (values: RegisterFormValues) => {
    try {
      setBusy(true);
      await registerCloudApp(adminClient, realm, values);
      addAlert(t("cloudAppRegistered"), AlertVariant.success);
      setRegisterOpen(false);
      reset();
      setRefreshKey((k) => k + 1);
    } catch (error) {
      addError("cloudAppRegisterError", error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <PageSection variant="light">
      <Stack hasGutter>
        <StackItem>
          <Alert variant="info" title={t("cloudAppsInfo")} isInline>
            {t("cloudAppsInfoHelp")}
          </Alert>
        </StackItem>
        <StackItem>
          <Button
            variant="primary"
            onClick={() => {
              reset();
              setRegisterOpen(true);
            }}
            data-testid="register-cloud-app"
          >
            {t("registerCloudApp")}
          </Button>
        </StackItem>
        <StackItem>
          {loading ? (
            <Spinner />
          ) : apps.length === 0 ? (
            <EmptyState>
              <Title headingLevel="h4" size="lg">
                {t("cloudAppsEmpty")}
              </Title>
              <EmptyStateBody>{t("cloudAppsEmptyHelp")}</EmptyStateBody>
              <EmptyStateFooter>
                <Button
                  variant="primary"
                  onClick={() => {
                    reset();
                    setRegisterOpen(true);
                  }}
                >
                  {t("registerCloudApp")}
                </Button>
              </EmptyStateFooter>
            </EmptyState>
          ) : (
            <DataList aria-label={t("cloudApps")}>
              {apps.map((app) => (
                <DataListItem key={app.id} aria-labelledby={`app-${app.id}`}>
                  <DataListItemRow>
                    <DataListItemCells
                      dataListCells={[
                        <DataListCell key="clientId" width={2}>
                          <span id={`app-${app.id}`}>
                            <strong>{app.clientId}</strong>
                          </span>
                        </DataListCell>,
                        <DataListCell key="name">
                          <Text component={TextVariants.small}>
                            {app.name || "—"}
                          </Text>
                        </DataListCell>,
                        <DataListCell key="description">
                          <Text component={TextVariants.small}>
                            {app.description || "—"}
                          </Text>
                        </DataListCell>,
                        <DataListCell key="status">
                          <Text component={TextVariants.small}>
                            {app.enabled ? t("enabled") : t("disabled")}
                          </Text>
                        </DataListCell>,
                      ]}
                    />
                  </DataListItemRow>
                </DataListItem>
              ))}
            </DataList>
          )}
        </StackItem>
      </Stack>

      <Modal
        variant={ModalVariant.medium}
        title={t("registerCloudApp")}
        isOpen={registerOpen}
        onClose={() => setRegisterOpen(false)}
      >
        <FormProvider {...form}>
          <Form onSubmit={handleSubmit(onRegister)}>
            <TextControl
              name="clientId"
              label={t("clientId")}
              labelIcon={t("clientIdHelp")}
              rules={{ required: t("required") }}
              defaultValue=""
            />
            <TextControl
              name="name"
              label={t("name")}
              defaultValue=""
            />
            <TextControl
              name="description"
              label={t("description")}
              defaultValue=""
            />
            <FormGroup fieldId="cloud-app-profile-note">
              <Alert variant="info" title={t("cloudAppProfileNote")} isInline>
                {t("cloudAppProfileNoteHelp")}
              </Alert>
            </FormGroup>
            <ActionGroup>
              <Button
                variant="primary"
                type="submit"
                isDisabled={busy}
                data-testid="confirm-register-cloud-app"
              >
                {t("register")}
              </Button>
              <Button
                variant="secondary"
                onClick={() => setRegisterOpen(false)}
                isDisabled={busy}
              >
                {t("cancel")}
              </Button>
            </ActionGroup>
          </Form>
        </FormProvider>
      </Modal>
    </PageSection>
  );
};
