import {
  ActionGroup,
  Alert,
  AlertVariant,
  Button,
  FormGroup,
  Spinner,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import {
  SwitchControl,
  TextControl,
  useAlerts,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import { useState } from "react";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../../admin-client";
import { useRealm } from "../../context/realm-context/RealmContext";
import {
  getPamConfig,
  postPamTest,
  putPamConfig,
  PamConfigRepresentation,
} from "./pam-api";

type PamFormValues = {
  pamEnabled: boolean;
  pamWebhookUrl: string;
  pamApiToken: string;
  pamAllClientsPrivileged: boolean;
  pamPrivilegedClients: string;
  pamNotifySessions: boolean;
  pamCheckTimeoutSeconds: number;
};

const toFormValues = (config: PamConfigRepresentation): PamFormValues => ({
  pamEnabled: !!config.enabled,
  pamWebhookUrl: config.webhookUrl ?? "",
  pamApiToken: config.apiToken ?? "",
  pamAllClientsPrivileged: !!config.allClientsPrivileged,
  pamPrivilegedClients: config.privilegedClients
    ? config.privilegedClients.join(", ")
    : "",
  pamNotifySessions: !!config.notifySessions,
  pamCheckTimeoutSeconds: config.checkTimeoutSeconds ?? 5,
});

const toConfigRepresentation = (values: PamFormValues): PamConfigRepresentation => ({
  enabled: values.pamEnabled,
  webhookUrl: values.pamWebhookUrl,
  apiToken: values.pamApiToken,
  allClientsPrivileged: values.pamAllClientsPrivileged,
  privilegedClients: values.pamPrivilegedClients
    ? values.pamPrivilegedClients
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
    : [],
  notifySessions: values.pamNotifySessions,
  checkTimeoutSeconds: values.pamCheckTimeoutSeconds,
});

export const PamSettingsForm = () => {
  const { t } = useTranslation();
  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();
  const { realm } = useRealm();
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [testResult, setTestResult] = useState<{
    success: boolean;
    message: string;
  } | null>(null);

  const form = useForm<PamFormValues>({
    mode: "onChange",
    defaultValues: toFormValues({}),
  });

  const { control, reset, handleSubmit, formState } = form;

  const enabled = useWatch({ control, name: "pamEnabled" });
  const allPrivileged = useWatch({ control, name: "pamAllClientsPrivileged" });

  useFetch(
    () => getPamConfig(adminClient),
    (config) => {
      reset(toFormValues(config));
      setLoading(false);
    },
    [realm],
  );

  const onSave = async (values: PamFormValues) => {
    try {
      setBusy(true);
      await putPamConfig(adminClient, toConfigRepresentation(values));
      addAlert(t("pamSaveSuccess"), AlertVariant.success);
    } catch (error) {
      addError("pamSaveError", error);
    } finally {
      setBusy(false);
    }
  };

  const onTest = async () => {
    try {
      setBusy(true);
      setTestResult(null);
      const result = await postPamTest(adminClient);
      setTestResult({
        success: !!result.sent,
        message: result.message ?? t("pamTestSuccess"),
      });
    } catch (error) {
      setTestResult({
        success: false,
        message: t("pamTestError"),
      });
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return <Spinner />;
  }

  return (
    <FormProvider {...form}>
      <Stack hasGutter>
        <StackItem>
          <SwitchControl
            name="pamEnabled"
            label={t("pamEnabled")}
            labelOn={t("on")}
            labelOff={t("off")}
          />
        </StackItem>
        {enabled && (
          <>
            <StackItem>
              <TextControl
                name="pamWebhookUrl"
                label={t("pamWebhookUrl")}
                labelIcon={t("pamWebhookUrlHelp")}
                defaultValue=""
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="pamApiToken"
                label={t("pamApiToken")}
                labelIcon={t("pamApiTokenHelp")}
                defaultValue=""
                type="password"
              />
            </StackItem>
            <StackItem>
              <SwitchControl
                name="pamAllClientsPrivileged"
                label={t("pamAllClientsPrivileged")}
                labelOn={t("on")}
                labelOff={t("off")}
              />
            </StackItem>
            {!allPrivileged && (
              <StackItem>
                <TextControl
                  name="pamPrivilegedClients"
                  label={t("pamPrivilegedClients")}
                  labelIcon={t("pamPrivilegedClientsHelp")}
                  defaultValue=""
                />
              </StackItem>
            )}
            <StackItem>
              <SwitchControl
                name="pamNotifySessions"
                label={t("pamNotifySessions")}
                labelIcon={t("pamNotifySessionsHelp")}
                labelOn={t("on")}
                labelOff={t("off")}
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="pamCheckTimeoutSeconds"
                label={t("pamCheckTimeout")}
                defaultValue="5"
                type="number"
              />
            </StackItem>
          </>
        )}
        {testResult && (
          <StackItem>
            <Alert
              variant={testResult.success ? "success" : "danger"}
              title={testResult.message}
              isInline
            />
          </StackItem>
        )}
        <StackItem>
          <FormGroup fieldId="pam-actions">
            <ActionGroup>
              <Button
                variant="primary"
                onClick={handleSubmit(onSave)}
                isDisabled={busy || !formState.isDirty}
                data-testid="save-pam"
              >
                {t("save")}
              </Button>
              <Button
                variant="secondary"
                onClick={onTest}
                isDisabled={busy || !enabled || formState.isDirty}
                data-testid="test-pam"
              >
                {t("pamTestConnection")}
              </Button>
            </ActionGroup>
          </FormGroup>
        </StackItem>
      </Stack>
    </FormProvider>
  );
};
