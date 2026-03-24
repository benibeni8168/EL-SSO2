import {
  Button,
  Checkbox,
  Form,
  FormGroup,
  Modal,
  ModalVariant,
  TextArea,
  TextInput,
} from "@patternfly/react-core";
import { useState } from "react";
import { useTranslation } from "react-i18next";

type CreateBackupModalProps = {
  onClose: () => void;
  onConfirm: (request: BackupRequest) => void;
};

export type BackupRequest = {
  realmName?: string;
  includeUsers: boolean;
  includeClients: boolean;
  includeRoles: boolean;
  description?: string;
};

export const CreateBackupModal = ({ onClose, onConfirm }: CreateBackupModalProps) => {
  const { t } = useTranslation();
  const [request, setRequest] = useState<BackupRequest>({
    includeUsers: true,
    includeClients: true,
    includeRoles: true,
  });

  return (
    <Modal
      variant={ModalVariant.medium}
      title={t("createBackup")}
      isOpen
      onClose={onClose}
      actions={[
        <Button key="confirm" variant="primary" onClick={() => onConfirm(request)}>
          {t("createBackup")}
        </Button>,
        <Button key="cancel" variant="link" onClick={onClose}>
          {t("cancel")}
        </Button>,
      ]}
    >
      <Form>
        <FormGroup label={t("realmName")} fieldId="realmName">
          <TextInput
            id="realmName"
            placeholder={t("allRealms")}
            value={request.realmName || ""}
            onChange={(_, value) => setRequest({ ...request, realmName: value || undefined })}
          />
        </FormGroup>
        <FormGroup label={t("description")} fieldId="description">
          <TextArea
            id="description"
            value={request.description || ""}
            onChange={(_, value) => setRequest({ ...request, description: value })}
          />
        </FormGroup>
        <FormGroup label={t("backupIncludeOptions")} fieldId="options">
          <Checkbox
            id="includeUsers"
            label={t("backupIncludeUsers")}
            isChecked={request.includeUsers}
            onChange={(_, checked) => setRequest({ ...request, includeUsers: checked })}
          />
          <Checkbox
            id="includeClients"
            label={t("backupIncludeClients")}
            isChecked={request.includeClients}
            onChange={(_, checked) => setRequest({ ...request, includeClients: checked })}
          />
          <Checkbox
            id="includeRoles"
            label={t("backupIncludeRoles")}
            isChecked={request.includeRoles}
            onChange={(_, checked) => setRequest({ ...request, includeRoles: checked })}
          />
        </FormGroup>
      </Form>
    </Modal>
  );
};
