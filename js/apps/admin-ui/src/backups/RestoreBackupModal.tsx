import {
  Alert,
  AlertVariant,
  Button,
  Form,
  FormGroup,
  Modal,
  ModalVariant,
} from "@patternfly/react-core";
import { useState } from "react";
import { useTranslation } from "react-i18next";

export type RestoreRequest = {
  strategy: "IGNORE_EXISTING" | "OVERWRITE_EXISTING";
};

type RestoreBackupModalProps = {
  backupId: string;
  onConfirm: (request: RestoreRequest) => void;
  onClose: () => void;
};

export const RestoreBackupModal = ({
  onConfirm,
  onClose,
}: RestoreBackupModalProps) => {
  const { t } = useTranslation();
  const [strategy, setStrategy] = useState<
    "IGNORE_EXISTING" | "OVERWRITE_EXISTING"
  >("IGNORE_EXISTING");

  const isOverwrite = strategy === "OVERWRITE_EXISTING";

  return (
    <Modal
      variant={ModalVariant.medium}
      title={t("restoreBackup")}
      isOpen
      onClose={onClose}
      actions={[
        <Button
          key="confirm"
          variant={isOverwrite ? "danger" : "primary"}
          onClick={() => onConfirm({ strategy })}
        >
          {t("restoreBackup")}
        </Button>,
        <Button key="cancel" variant="link" onClick={onClose}>
          {t("cancel")}
        </Button>,
      ]}
    >
      <Form>
        <Alert
          variant={AlertVariant.warning}
          isInline
          title={t("restoreBackupConfirm")}
        />
        <FormGroup label={t("restoreStrategy")} fieldId="restoreStrategy">
          <select
            id="restoreStrategy"
            value={strategy}
            onChange={(e) =>
              setStrategy(
                e.target.value as "IGNORE_EXISTING" | "OVERWRITE_EXISTING",
              )
            }
            style={{ width: "100%" }}
          >
            <option value="IGNORE_EXISTING">
              {t("restoreStrategyIgnore")}
            </option>
            <option value="OVERWRITE_EXISTING">
              {t("restoreStrategyOverwrite")}
            </option>
          </select>
        </FormGroup>
      </Form>
    </Modal>
  );
};
