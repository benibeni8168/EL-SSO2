import { Tab, TabTitleText, PageSection } from "@patternfly/react-core";
import { useTranslation } from "react-i18next";
import {
  RoutableTabs,
  useRoutableTab,
} from "../components/routable-tabs/RoutableTabs";
import { useRealm } from "../context/realm-context/RealmContext";
import { ViewHeader } from "../components/view-header/ViewHeader";
import { toReports } from "./routes/Reports";
import { AuthenticationReport } from "./AuthenticationReport";
import { AdminActivityReport } from "./AdminActivityReport";
import { SecurityReport } from "./SecurityReport";
import { UserActivityReport } from "./UserActivityReport";

export default function ReportsSection() {
  const { t } = useTranslation();
  const { realm } = useRealm();

  const authTab = useRoutableTab(toReports({ realm, tab: "authentication" }));
  const adminTab = useRoutableTab(toReports({ realm, tab: "admin-activity" }));
  const securityTab = useRoutableTab(toReports({ realm, tab: "security" }));
  const userTab = useRoutableTab(toReports({ realm, tab: "user-activity" }));

  return (
    <>
      <ViewHeader titleKey="auditReports" />
      <PageSection variant="light" className="pf-v5-u-p-0">
        <RoutableTabs
          isBox
          defaultLocation={toReports({ realm, tab: "authentication" })}
        >
          <Tab
            title={<TabTitleText>{t("authenticationReport")}</TabTitleText>}
            data-testid="reports-authentication-tab"
            {...authTab}
          >
            <AuthenticationReport />
          </Tab>
          <Tab
            title={<TabTitleText>{t("adminActivityReport")}</TabTitleText>}
            data-testid="reports-admin-activity-tab"
            {...adminTab}
          >
            <AdminActivityReport />
          </Tab>
          <Tab
            title={<TabTitleText>{t("securityReport")}</TabTitleText>}
            data-testid="reports-security-tab"
            {...securityTab}
          >
            <SecurityReport />
          </Tab>
          <Tab
            title={<TabTitleText>{t("userActivityReport")}</TabTitleText>}
            data-testid="reports-user-activity-tab"
            {...userTab}
          >
            <UserActivityReport />
          </Tab>
        </RoutableTabs>
      </PageSection>
    </>
  );
}
