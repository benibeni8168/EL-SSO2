import type UserRepresentation from "@keycloak/keycloak-admin-client/lib/defs/userRepresentation";
import { KeycloakSelect, useAlerts } from "@keycloak/keycloak-ui-shared";
import {
  Alert,
  Button,
  ButtonVariant,
  FileUpload,
  FormGroup,
  Modal,
  ModalVariant,
  Radio,
  SelectOption,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../admin-client";
import { useRealm } from "../context/realm-context/RealmContext";

type CollisionOption = "SKIP" | "OVERWRITE" | "FAIL";

type BulkImportUsersModalProps = {
  isOpen: boolean;
  onClose: () => void;
  refresh: () => void;
};

function parseCSV(text: string): UserRepresentation[] {
  const lines = text.split(/\r?\n/).filter((l) => l.trim());
  if (lines.length < 2) return [];
  const headers = lines[0].split(",").map((h) => h.trim());
  return lines.slice(1).map((line) => {
    const values = line.split(",").map((v) => v.trim());
    const row: Record<string, string> = {};
    headers.forEach((h, i) => {
      row[h] = values[i] ?? "";
    });
    const user: UserRepresentation = {
      username: row["username"] || undefined,
      email: row["email"] || undefined,
      firstName: row["firstName"] || undefined,
      lastName: row["lastName"] || undefined,
      enabled: row["enabled"] ? row["enabled"].toLowerCase() === "true" : true,
      emailVerified: row["emailVerified"]
        ? row["emailVerified"].toLowerCase() === "true"
        : false,
    };
    if (row["groups"]) {
      user.groups = row["groups"].split(";").filter(Boolean);
    }
    if (row["realmRoles"]) {
      user.realmRoles = row["realmRoles"].split(";").filter(Boolean);
    }
    if (row["password"]) {
      const temporary =
        row["temporary"] === undefined || row["temporary"] === ""
          ? true
          : row["temporary"].toLowerCase() === "true";
      user.credentials = [
        { type: "password", value: row["password"], temporary },
      ];
    }
    return user;
  });
}

const CSV_TEMPLATE =
  "username,email,firstName,lastName,enabled,emailVerified,password,temporary,groups,realmRoles\n" +
  "john.doe,john@example.com,John,Doe,true,true,Password1!,true,/group1;/group2,role1;role2\n";

export function BulkImportUsersModal({
  isOpen,
  onClose,
  refresh,
}: BulkImportUsersModalProps) {
  const { t } = useTranslation();
  const { adminClient } = useAdminClient();
  const { realm } = useRealm();
  const { addAlert, addError } = useAlerts();

  const [format, setFormat] = useState<"csv" | "json">("csv");
  const [fileContent, setFileContent] = useState("");
  const [filename, setFilename] = useState("");
  const [collisionPolicy, setCollisionPolicy] =
    useState<CollisionOption>("SKIP");
  const [collisionOpen, setCollisionOpen] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<{
    added: number;
    skipped: number;
    overwritten: number;
  } | null>(null);
  const [parseError, setParseError] = useState<string | null>(null);

  const handleClose = () => {
    setFileContent("");
    setFilename("");
    setResult(null);
    setParseError(null);
    setCollisionPolicy("SKIP");
    setFormat("csv");
    onClose();
  };

  const downloadTemplate = () => {
    const blob = new Blob([CSV_TEMPLATE], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "users-import-template.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImport = async () => {
    setParseError(null);
    setResult(null);
    let users: UserRepresentation[];
    try {
      if (format === "csv") {
        users = parseCSV(fileContent);
        if (users.length === 0) {
          setParseError(t("importUsersParseError"));
          return;
        }
      } else {
        const parsed = JSON.parse(fileContent);
        users = Array.isArray(parsed) ? parsed : [parsed];
      }
    } catch {
      setParseError(t("importUsersParseError"));
      return;
    }

    setImporting(true);
    try {
      const response = await adminClient.realms.partialImport({
        realm,
        rep: {
          users,
          ifResourceExists: collisionPolicy,
        },
      });
      const added = response.added ?? 0;
      const skipped = response.skipped ?? 0;
      const overwritten = response.overwritten ?? 0;
      setResult({ added, skipped, overwritten });
      addAlert(t("importUsersSuccess", { added, skipped, overwritten }));
      refresh();
    } catch (error) {
      addError("importUsersError", error);
    } finally {
      setImporting(false);
    }
  };

  const collisionOptions: CollisionOption[] = ["SKIP", "OVERWRITE", "FAIL"];
  const collisionLabels: Record<CollisionOption, string> = {
    SKIP: t("importUsersIfExistsSkip"),
    OVERWRITE: t("importUsersIfExistsOverwrite"),
    FAIL: t("importUsersIfExistsFail"),
  };

  return (
    <Modal
      variant={ModalVariant.medium}
      title={t("importUsersModalTitle")}
      isOpen={isOpen}
      onClose={handleClose}
      actions={[
        <Button
          key="import"
          variant={ButtonVariant.primary}
          onClick={handleImport}
          isLoading={importing}
          isDisabled={!fileContent || importing}
        >
          {t("importUsers")}
        </Button>,
        <Button key="cancel" variant={ButtonVariant.link} onClick={handleClose}>
          {t("cancel")}
        </Button>,
      ]}
    >
      <Stack hasGutter>
        <StackItem>
          <FormGroup label={t("importUsersFormat")} fieldId="import-format">
            <Radio
              id="format-csv"
              name="format"
              label={t("importUsersFormatCsv")}
              isChecked={format === "csv"}
              onChange={() => {
                setFormat("csv");
                setFileContent("");
                setFilename("");
              }}
            />
            <Radio
              id="format-json"
              name="format"
              label={t("importUsersFormatJson")}
              isChecked={format === "json"}
              onChange={() => {
                setFormat("json");
                setFileContent("");
                setFilename("");
              }}
            />
          </FormGroup>
        </StackItem>

        {format === "csv" && (
          <StackItem>
            <Button variant="link" isInline onClick={downloadTemplate}>
              {t("importUsersDownloadTemplate")}
            </Button>
          </StackItem>
        )}

        <StackItem>
          <FormGroup fieldId="import-file">
            <FileUpload
              id="import-file"
              value={fileContent}
              filename={filename}
              filenamePlaceholder={t("importUsersDropHint")}
              browseButtonText={t("browse")}
              onFileInputChange={(_event, file) => {
                setFilename(file.name);
                const reader = new FileReader();
                reader.onload = (e) =>
                  setFileContent((e.target?.result as string) ?? "");
                reader.readAsText(file);
              }}
              onClearClick={() => {
                setFileContent("");
                setFilename("");
                setParseError(null);
                setResult(null);
              }}
              dropzoneProps={{
                accept:
                  format === "csv"
                    ? { "text/csv": [".csv"] }
                    : { "application/json": [".json"] },
                maxSize: 5 * 1024 * 1024,
              }}
            />
          </FormGroup>
        </StackItem>

        <StackItem>
          <FormGroup
            label={t("importUsersIfExists")}
            fieldId="import-collision"
          >
            <KeycloakSelect
              toggleId="import-collision"
              onToggle={setCollisionOpen}
              isOpen={collisionOpen}
              selections={collisionLabels[collisionPolicy]}
              onSelect={(value) => {
                setCollisionPolicy(value as CollisionOption);
                setCollisionOpen(false);
              }}
            >
              {collisionOptions.map((opt) => (
                <SelectOption key={opt} value={opt}>
                  {collisionLabels[opt]}
                </SelectOption>
              ))}
            </KeycloakSelect>
          </FormGroup>
        </StackItem>

        {parseError && (
          <StackItem>
            <Alert variant="danger" isInline title={parseError} />
          </StackItem>
        )}

        {result && (
          <StackItem>
            <Alert
              variant="success"
              isInline
              title={t("importUsersSuccess", {
                added: result.added,
                skipped: result.skipped,
                overwritten: result.overwritten,
              })}
            />
          </StackItem>
        )}
      </Stack>
    </Modal>
  );
}
