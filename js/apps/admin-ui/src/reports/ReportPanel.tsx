import {
  ActionGroup,
  Button,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  EmptyState,
  EmptyStateBody,
  FormGroup,
  Grid,
  GridItem,
  PageSection,
  Spinner,
  TextInput,
  Title,
} from "@patternfly/react-core";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../admin-client";
import { useRealm } from "../context/realm-context/RealmContext";

type ReportSummary = {
  totalEvents: number;
  successCount: number;
  failureCount: number;
  countsByType: Record<string, number>;
  dateFrom?: string;
  dateTo?: string;
  generatedAt?: string;
  generatedByUserId?: string;
  realmName?: string;
  reportType?: string;
};

type ReportPanelProps = {
  reportPath: string;
  exportFilenameBase: string;
};

export const ReportPanel = ({ reportPath, exportFilenameBase }: ReportPanelProps) => {
  const { t } = useTranslation();
  const { adminClient } = useAdminClient();
  const { realm } = useRealm();

  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [summary, setSummary] = useState<ReportSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const buildUrl = (endpoint: string, extraParams?: Record<string, string>) => {
    const params = new URLSearchParams();
    if (dateFrom) params.append("dateFrom", dateFrom);
    if (dateTo) params.append("dateTo", dateTo);
    if (extraParams) {
      Object.entries(extraParams).forEach(([k, v]) => params.append(k, v));
    }
    const qs = params.toString();
    return `${adminClient.baseUrl}/admin/realms/${realm}/audit-reports/${reportPath}/${endpoint}${qs ? "?" + qs : ""}`;
  };

  const generateReport = async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await adminClient.getAccessToken();
      const res = await fetch(buildUrl("summary"), {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setSummary(await res.json());
    } catch (e: any) {
      setError(String(e.message));
    } finally {
      setLoading(false);
    }
  };

  const download = async (format: "json" | "csv") => {
    const token = await adminClient.getAccessToken();
    const res = await fetch(buildUrl("export", { format }), {
      headers: { Authorization: `Bearer ${token}` },
    });
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${exportFilenameBase}.${format}`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <PageSection>
      <Grid hasGutter>
        <GridItem span={3}>
          <FormGroup label={t("reportDateFrom")}>
            <TextInput
              value={dateFrom}
              onChange={(_e, v) => setDateFrom(v)}
              placeholder="yyyy-MM-dd"
            />
          </FormGroup>
        </GridItem>
        <GridItem span={3}>
          <FormGroup label={t("reportDateTo")}>
            <TextInput
              value={dateTo}
              onChange={(_e, v) => setDateTo(v)}
              placeholder="yyyy-MM-dd"
            />
          </FormGroup>
        </GridItem>
        <GridItem span={12}>
          <ActionGroup>
            <Button variant="primary" onClick={generateReport} isLoading={loading}>
              {t("generateReport")}
            </Button>
            <Button
              variant="secondary"
              onClick={() => download("csv")}
              isDisabled={!summary}
            >
              {t("downloadCsv")}
            </Button>
            <Button
              variant="secondary"
              onClick={() => download("json")}
              isDisabled={!summary}
            >
              {t("downloadJson")}
            </Button>
          </ActionGroup>
        </GridItem>

        {loading && (
          <GridItem span={12}>
            <Spinner />
          </GridItem>
        )}

        {error && (
          <GridItem span={12}>
            <Title headingLevel="h4">{error}</Title>
          </GridItem>
        )}

        {!loading && !summary && !error && (
          <GridItem span={12}>
            <EmptyState>
              <EmptyStateBody>{t("noReportData")}</EmptyStateBody>
            </EmptyState>
          </GridItem>
        )}

        {summary && (
          <GridItem span={12}>
            <DescriptionList isHorizontal>
              <DescriptionListGroup>
                <DescriptionListTerm>{t("reportGeneratedAt")}</DescriptionListTerm>
                <DescriptionListDescription>{summary.generatedAt}</DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>{t("reportGeneratedBy")}</DescriptionListTerm>
                <DescriptionListDescription>{summary.generatedByUserId}</DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>{t("reportTotalEvents")}</DescriptionListTerm>
                <DescriptionListDescription>{summary.totalEvents}</DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>{t("reportSuccessCount")}</DescriptionListTerm>
                <DescriptionListDescription>{summary.successCount}</DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>{t("reportFailureCount")}</DescriptionListTerm>
                <DescriptionListDescription>{summary.failureCount}</DescriptionListDescription>
              </DescriptionListGroup>
            </DescriptionList>
            {summary.countsByType && Object.keys(summary.countsByType).length > 0 && (
              <>
                <Title headingLevel="h4" style={{ marginTop: "1rem" }}>
                  {t("reportCountsByType")}
                </Title>
                <DescriptionList isHorizontal>
                  {Object.entries(summary.countsByType).map(([type, count]) => (
                    <DescriptionListGroup key={type}>
                      <DescriptionListTerm>{type}</DescriptionListTerm>
                      <DescriptionListDescription>{count}</DescriptionListDescription>
                    </DescriptionListGroup>
                  ))}
                </DescriptionList>
              </>
            )}
          </GridItem>
        )}
      </Grid>
    </PageSection>
  );
};
