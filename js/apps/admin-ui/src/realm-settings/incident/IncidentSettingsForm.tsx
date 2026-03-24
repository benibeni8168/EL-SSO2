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
  getIncidentConfig,
  postIncidentTest,
  putIncidentConfig,
  IncidentConfigRepresentation,
} from "./incident-api";

type IncidentFormValues = {
  incidentEnabled: boolean;
  incidentWebhookUrl: string;
  incidentApiToken: string;
  incidentTriggerEventTypes: string;
  incidentIncludeAdminEvents: boolean;
};

const toFormValues = (config: IncidentConfigRepresentation): IncidentFormValues => ({
  incidentEnabled: !!config.enabled,
  incidentWebhookUrl: config.webhookUrl ?? "",
  incidentApiToken: config.apiToken ?? "",
  incidentTriggerEventTypes: config.triggerEventTypes
    ? config.triggerEventTypes.join(", ")
    : "",
  incidentIncludeAdminEvents: !!config.includeAdminEvents,
});

const toConfigRepresentation = (values: IncidentFormValues): IncidentConfigRepresentation => ({
  enabled: values.incidentEnabled,
  webhookUrl: values.incidentWebhookUrl,
  apiToken: values.incidentApiToken,
  triggerEventTypes: values.incidentTriggerEventTypes
    ? values.incidentTriggerEventTypes
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean)
    : [],
  includeAdminEvents: values.incidentIncludeAdminEvents,
});

export const IncidentSettingsForm = () => {
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

  const form = useForm<IncidentFormValues>({
    mode: "onChange",
    defaultValues: toFormValues({}),
  });

  const { control, reset, handleSubmit, formState } = form;

  const enabled = useWatch({ control, name: "incidentEnabled" });

  useFetch(
    () => getIncidentConfig(adminClient),
    (config) => {
      reset(toFormValues(config));
      setLoading(false);
    },
    [realm],
  );

  const onSave = async (values: IncidentFormValues) => {
    try {
      setBusy(true);
      await putIncidentConfig(adminClient, toConfigRepresentation(values));
      addAlert(t("incidentSaveSuccess"), AlertVariant.success);
    } catch (error) {
      addError("incidentSaveError", error);
    } finally {
      setBusy(false);
    }
  };

  const onTest = async () => {
    try {
      setBusy(true);
      setTestResult(null);
      const result = await postIncidentTest(adminClient);
      setTestResult({
        success: !!result.sent,
        message: result.message ?? t("incidentTestSuccess"),
      });
    } catch (error) {
      setTestResult({
        success: false,
        message: t("incidentTestError"),
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
            name="incidentEnabled"
            label={t("incidentEnabled")}
            labelOn={t("on")}
            labelOff={t("off")}
          />
        </StackItem>
        {enabled && (
          <>
            <StackItem>
              <TextControl
                name="incidentWebhookUrl"
                label={t("incidentWebhookUrl")}
                labelIcon={t("incidentWebhookUrlHelp")}
                defaultValue=""
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="incidentApiToken"
                label={t("incidentApiToken")}
                labelIcon={t("incidentApiTokenHelp")}
                defaultValue=""
                type="password"
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="incidentTriggerEventTypes"
                label={t("incidentTriggerEventTypes")}
                labelIcon={t("incidentTriggerEventTypesHelp")}
                defaultValue=""
              />
            </StackItem>
            <StackItem>
              <SwitchControl
                name="incidentIncludeAdminEvents"
                label={t("incidentIncludeAdminEvents")}
                labelIcon={t("incidentIncludeAdminEventsHelp")}
                labelOn={t("on")}
                labelOff={t("off")}
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
          <FormGroup fieldId="incident-actions">
            <ActionGroup>
              <Button
                variant="primary"
                onClick={handleSubmit(onSave)}
                isDisabled={busy || !formState.isDirty}
                data-testid="save-incident"
              >
                {t("save")}
              </Button>
              <Button
                variant="secondary"
                onClick={onTest}
                isDisabled={busy || !enabled || formState.isDirty}
                data-testid="test-incident"
              >
                {t("incidentTestConnection")}
              </Button>
            </ActionGroup>
          </FormGroup>
        </StackItem>
      </Stack>
    </FormProvider>
  );
};
