import {
  AlertVariant,
  Button,
  ButtonVariant,
  EmptyState,
  EmptyStateBody,
  EmptyStateHeader,
  EmptyStateIcon,
  PageSection,
  ToolbarItem,
} from "@patternfly/react-core";
import { fetchWithError } from "@keycloak/keycloak-admin-client";
import {
  DatabaseIcon,
  DownloadIcon,
  HistoryIcon,
  TrashIcon,
} from "@patternfly/react-icons";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";
import { saveAs } from "file-saver";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../admin-client";
import {
  useAlerts,
  KeycloakSpinner,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import { useConfirmDialog } from "../components/confirm-dialog/ConfirmDialog";
import { ViewHeader } from "../components/view-header/ViewHeader";
import { getAuthorizationHeaders } from "../utils/getAuthorizationHeaders";
import { joinPath } from "../utils/joinPath";
import { CreateBackupModal, type BackupRequest } from "./CreateBackupModal";
import { RestoreBackupModal, type RestoreRequest } from "./RestoreBackupModal";

// Types for backup data
type BackupMetadata = {
  backupId: string;
  description: string;
  createdAt: number;
  createdBy: string;
  realms: string[];
  sizeBytes: number;
  status: string;
  downloadUrl: string;
};

export default function BackupsSection() {
  const { t } = useTranslation();
  const { adminClient } = useAdminClient();
  const { addAlert, addError } = useAlerts();

  const [backups, setBackups] = useState<BackupMetadata[]>();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [selectedBackup, setSelectedBackup] = useState<string>();
  const [backupToRestore, setBackupToRestore] = useState<
    BackupMetadata | undefined
  >();
  const [key, setKey] = useState(0);

  const refresh = () => setKey((value) => value + 1);

  const backupEndpoint = (...segments: string[]) =>
    joinPath(adminClient.baseUrl, "admin", "backups", ...segments);

  const getAuthHeaders = async () =>
    getAuthorizationHeaders(await adminClient.getAccessToken());

  // Fetch backups from API using useFetch pattern
  useFetch(
    async () => {
      const response = await fetchWithError(backupEndpoint(), {
        headers: await getAuthHeaders(),
      });
      return response.json() as Promise<BackupMetadata[]>;
    },
    (data) => setBackups(data),
    [key],
  );

  // Create backup handler
  const handleCreateBackup = async (request: BackupRequest) => {
    try {
      await fetchWithError(backupEndpoint(), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(await getAuthHeaders()),
        },
        body: JSON.stringify(request),
      });
      addAlert(t("backupCreatedSuccess"), AlertVariant.success);
      refresh();
    } catch (error) {
      addError("backupCreateError", error);
    }
    setCreateModalOpen(false);
  };

  // Download through an authenticated fetch so this works with admin tokens and non-root base paths.
  const handleDownload = async (backupId: string) => {
    const encodedBackupId = encodeURIComponent(backupId);
    try {
      const response = await fetchWithError(
        backupEndpoint(encodedBackupId, "download"),
        {
          headers: await getAuthHeaders(),
        },
      );
      const blob = await response.blob();
      saveAs(blob, `${backupId}.zip`);
    } catch (error) {
      addError("backupDownloadError", error);
    }
  };

  // Restore backup handler
  const handleRestoreBackup = async (request: RestoreRequest) => {
    if (!backupToRestore) return;
    const encodedBackupId = encodeURIComponent(backupToRestore.backupId);
    try {
      await fetchWithError(backupEndpoint(encodedBackupId, "restore"), {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(await getAuthHeaders()),
        },
        body: JSON.stringify(request),
      });
      addAlert(t("restoreBackupSuccess"), AlertVariant.success);
      refresh();
    } catch (error) {
      addError("restoreBackupError", error);
    } finally {
      setBackupToRestore(undefined);
    }
  };

  // Delete confirmation dialog
  const [toggleDeleteDialog, DeleteConfirm] = useConfirmDialog({
    titleKey: "deleteBackup",
    messageKey: "deleteBackupConfirm",
    continueButtonLabel: "delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      if (!selectedBackup) return;
      try {
        const encodedBackupId = encodeURIComponent(selectedBackup);
        await fetchWithError(backupEndpoint(encodedBackupId), {
          method: "DELETE",
          headers: await getAuthHeaders(),
        });
        addAlert(t("backupDeletedSuccess"), AlertVariant.success);
        refresh();
      } catch (error) {
        addError("backupDeleteError", error);
      }
    },
  });

  // Format file size
  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  // Format date
  const formatDate = (timestamp: number) => {
    return new Date(timestamp).toLocaleString();
  };

  if (!backups) {
    return <KeycloakSpinner />;
  }

  return (
    <>
      <DeleteConfirm />
      {createModalOpen && (
        <CreateBackupModal
          onClose={() => setCreateModalOpen(false)}
          onConfirm={handleCreateBackup}
        />
      )}
      {backupToRestore && (
        <RestoreBackupModal
          backupId={backupToRestore.backupId}
          onConfirm={handleRestoreBackup}
          onClose={() => setBackupToRestore(undefined)}
        />
      )}
      <ViewHeader titleKey="backups" subKey="backupsDescription" />
      <PageSection variant="light">
        <ToolbarItem>
          <Button onClick={() => setCreateModalOpen(true)}>
            {t("createBackup")}
          </Button>
        </ToolbarItem>

        {backups.length === 0 ? (
          <EmptyState>
            <EmptyStateHeader
              titleText={t("noBackups")}
              icon={<EmptyStateIcon icon={DatabaseIcon} />}
              headingLevel="h2"
            />
            <EmptyStateBody>{t("noBackupsInstructions")}</EmptyStateBody>
            <Button onClick={() => setCreateModalOpen(true)}>
              {t("createBackup")}
            </Button>
          </EmptyState>
        ) : (
          <Table aria-label={t("backups")} variant="compact">
            <Thead>
              <Tr>
                <Th>{t("backupId")}</Th>
                <Th>{t("description")}</Th>
                <Th>{t("createdAt")}</Th>
                <Th>{t("backupCreatedBy")}</Th>
                <Th>{t("realms")}</Th>
                <Th>{t("size")}</Th>
                <Th>{t("status")}</Th>
                <Th>{t("actions")}</Th>
              </Tr>
            </Thead>
            <Tbody>
              {backups.map((backup) => (
                <Tr key={backup.backupId}>
                  <Td>{backup.backupId}</Td>
                  <Td>{backup.description || "-"}</Td>
                  <Td>{formatDate(backup.createdAt)}</Td>
                  <Td>{backup.createdBy}</Td>
                  <Td>{backup.realms.join(", ")}</Td>
                  <Td>{formatSize(backup.sizeBytes)}</Td>
                  <Td>{backup.status}</Td>
                  <Td>
                    <Button
                      variant="plain"
                      aria-label={t("download")}
                      onClick={() => handleDownload(backup.backupId)}
                    >
                      <DownloadIcon />
                    </Button>
                    <Button
                      variant="plain"
                      aria-label={t("restoreBackup")}
                      onClick={() => setBackupToRestore(backup)}
                    >
                      <HistoryIcon />
                    </Button>
                    <Button
                      variant="plain"
                      aria-label={t("delete")}
                      onClick={() => {
                        setSelectedBackup(backup.backupId);
                        toggleDeleteDialog();
                      }}
                    >
                      <TrashIcon />
                    </Button>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </PageSection>
    </>
  );
}
