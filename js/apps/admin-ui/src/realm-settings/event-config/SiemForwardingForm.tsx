import {
  ActionGroup,
  Alert,
  AlertVariant,
  Button,
  Divider,
  FormGroup,
  Spinner,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import {
  SelectControl,
  SelectVariant,
  SwitchControl,
  TextAreaControl,
  TextControl,
  useAlerts,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import { useState } from "react";
import { FormProvider, UseFormReturn, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../../admin-client";
import { useRealm } from "../../context/realm-context/RealmContext";
import {
  getSiemConfig,
  postSiemTest,
  putSiemConfig,
  SiemConfigRepresentation,
  SiemScope,
} from "./siem-api";

export type SiemFormValues = {
  siemEnabled: boolean;
  siemInheritGlobal: boolean;
  siemExportUrl: string;
  siemAuthToken: string;
  siemSigningSecret: string;
  siemFormat: string;
  siemIncludeAdminEvents: boolean;
  siemIncludeEvents: string;
  siemExcludeEvents: string;
  siemCustomHeaders: string;
};

type Props = {
  form: UseFormReturn<SiemFormValues>;
  scope?: SiemScope;
  isMasterRealm?: boolean;
  onSaved?: () => void;
};

export const listToText = (value?: string[] | Record<string, string>) => {
  if (Array.isArray(value)) {
    return value.join(", ");
  }
  if (value && typeof value === "object") {
    return Object.entries(value)
      .map(([key, entry]) => `${key}:${entry}`)
      .join(", ");
  }
  return "";
};

export const textToList = (value: string) =>
  value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);

export const textToHeaders = (value: string) =>
  Object.fromEntries(
    textToList(value)
      .map((pair) => pair.split(":"))
      .filter(([name, headerValue]) => name && headerValue)
      .map(([name, headerValue]) => [name.trim(), headerValue.trim()]),
  );

export const SiemForwardingForm = ({
  form,
  scope = "realm",
  isMasterRealm = false,
  onSaved,
}: Props) => {
  const { t } = useTranslation();
  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();
  const { realm } = useRealm();
  const [config, setConfig] = useState<SiemConfigRepresentation>();
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const { control, reset, handleSubmit, getValues, formState } = form;

  const enabled = useWatch({ control, name: "siemEnabled" });
  const inheritGlobal = useWatch({ control, name: "siemInheritGlobal" });

  const toFormValues = (value: SiemConfigRepresentation): SiemFormValues => ({
    siemEnabled: !!value.enabled,
    siemInheritGlobal: value.inheritGlobal ?? true,
    siemExportUrl: value.exportUrl ?? "",
    siemAuthToken: value.authToken ?? "",
    siemSigningSecret: value.signingSecret ?? "",
    siemFormat: value.format ?? "json",
    siemIncludeAdminEvents: value.includeAdminEvents ?? true,
    siemIncludeEvents: listToText(value.includeEvents),
    siemExcludeEvents: listToText(value.excludeEvents),
    siemCustomHeaders: listToText(value.customHeaders),
  });

  useFetch(
    () => getSiemConfig(adminClient, scope),
    (value) => {
      setConfig(value);
      reset(toFormValues(value));
      setLoading(false);
    },
    [realm, scope],
  );

  const buildPayload = (): SiemConfigRepresentation => {
    let siemInheritGlobal = getValues("siemInheritGlobal");
    const siemEnabled = getValues("siemEnabled");

    if (
      scope === "realm" &&
      siemInheritGlobal &&
      config &&
      siemEnabled !== !!config.enabled
    ) {
      siemInheritGlobal = false;
    }

    return {
      enabled: siemEnabled,
      inheritGlobal: siemInheritGlobal,
      exportUrl: getValues("siemExportUrl"),
      authToken: getValues("siemAuthToken"),
      signingSecret: getValues("siemSigningSecret"),
      format: getValues("siemFormat"),
      includeAdminEvents: getValues("siemIncludeAdminEvents"),
      includeEvents: textToList(getValues("siemIncludeEvents") || ""),
      excludeEvents: textToList(getValues("siemExcludeEvents") || ""),
      customHeaders: textToHeaders(getValues("siemCustomHeaders") || ""),
    };
  };

  const onSave = async () => {
    try {
      setBusy(true);
      await putSiemConfig(adminClient, scope, buildPayload());
      const latest = await getSiemConfig(adminClient, scope);
      setConfig(latest);
      reset(toFormValues(latest));
      addAlert(
        scope === "global" ? t("siemGlobalSaved") : t("siemForwardingSaved"),
        AlertVariant.success,
      );
      onSaved?.();
    } catch (error) {
      addError(
        scope === "global" ? "siemGlobalSaveError" : "siemForwardingSaveError",
        error,
      );
    } finally {
      setBusy(false);
    }
  };

  const onTest = async () => {
    try {
      setBusy(true);
      await postSiemTest(adminClient);
      addAlert(t("siemTestSent"), AlertVariant.success);
    } catch (error) {
      addError("siemTestFailed", error);
    } finally {
      setBusy(false);
    }
  };

  if (loading || !config) {
    return <Spinner />;
  }

  const canEdit = scope === "global" || !inheritGlobal;

  return (
    <FormProvider {...form}>
      <Stack hasGutter>
        {scope === "realm" && (
          <StackItem>
            <Alert
              variant="info"
              title={
                inheritGlobal
                  ? t("siemUsingInheritedConfig")
                  : t("siemUsingCustomConfig")
              }
              isInline
            >
              {config.exportUrl
                ? t("siemEffectiveUrl", {
                    url: config.exportUrl,
                  })
                : t("siemNoEffectiveUrl")}
            </Alert>
          </StackItem>
        )}
        {scope === "realm" && (
          <StackItem>
            <SwitchControl
              name="siemEnabled"
              label={t("siemEnabled")}
              labelIcon={t("siemEnabledHelp")}
              labelOn={t("on")}
              labelOff={t("off")}
            />
          </StackItem>
        )}
        {scope === "realm" && (
          <StackItem>
            <SwitchControl
              name="siemInheritGlobal"
              label={t("siemInheritGlobal")}
              labelIcon={t("siemInheritGlobalHelp")}
              labelOn={t("on")}
              labelOff={t("off")}
            />
          </StackItem>
        )}
        {canEdit && (
          <>
            <StackItem>
              <TextControl
                name="siemExportUrl"
                label={t("siemExportUrl")}
                labelIcon={t("siemExportUrlHelp")}
                defaultValue=""
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="siemAuthToken"
                label={t("siemAuthToken")}
                labelIcon={t("siemAuthTokenHelp")}
                defaultValue=""
                type="password"
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="siemSigningSecret"
                label={t("siemSigningSecret")}
                labelIcon={t("siemSigningSecretHelp")}
                defaultValue=""
                type="password"
              />
            </StackItem>
            <StackItem>
              <SelectControl
                name="siemFormat"
                label={t("siemFormat")}
                labelIcon={t("siemFormatHelp")}
                controller={{ defaultValue: "json" }}
                options={["json", "cef", "syslog"]}
                variant={SelectVariant.single}
              />
            </StackItem>
            <StackItem>
              <SwitchControl
                name="siemIncludeAdminEvents"
                label={t("siemIncludeAdminEvents")}
                labelIcon={t("siemIncludeAdminEventsHelp")}
                labelOn={t("on")}
                labelOff={t("off")}
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="siemIncludeEvents"
                label={t("siemIncludeEvents")}
                labelIcon={t("siemIncludeEventsHelp")}
                defaultValue=""
              />
            </StackItem>
            <StackItem>
              <TextControl
                name="siemExcludeEvents"
                label={t("siemExcludeEvents")}
                labelIcon={t("siemExcludeEventsHelp")}
                defaultValue=""
              />
            </StackItem>
            <StackItem>
              <TextAreaControl
                name="siemCustomHeaders"
                label={t("siemCustomHeaders")}
                labelIcon={t("siemCustomHeadersHelp")}
                defaultValue=""
              />
            </StackItem>
          </>
        )}
        <StackItem>
          <FormGroup label={t("siemActions")} fieldId="siem-actions">
            <ActionGroup>
              <Button
                variant="primary"
                onClick={handleSubmit(onSave)}
                isDisabled={busy || !formState.isDirty}
                data-testid="save-siem"
              >
                {t("save")}
              </Button>
              <Button
                variant="secondary"
                onClick={onTest}
                isDisabled={busy || !enabled}
                data-testid="test-siem"
              >
                {t("siemSendTest")}
              </Button>
            </ActionGroup>
          </FormGroup>
        </StackItem>
        {isMasterRealm && scope === "global" && (
          <StackItem>
            <Alert variant="info" title={t("siemGlobalDefaults")} isInline>
              {t("siemGlobalDefaultsHelp")}
            </Alert>
          </StackItem>
        )}
      </Stack>
      <Divider />
    </FormProvider>
  );
};
