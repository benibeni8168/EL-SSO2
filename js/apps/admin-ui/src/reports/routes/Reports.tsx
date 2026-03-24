import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../utils/generateEncodedPath";
import type { AppRouteObject } from "../../routes";

export type ReportsTab = "authentication" | "admin-activity" | "security" | "user-activity";

export type ReportsParams = {
  realm: string;
  tab?: ReportsTab;
};

const ReportsSection = lazy(() => import("../ReportsSection"));

export const ReportsRoute: AppRouteObject = {
  path: "/:realm/reports",
  element: <ReportsSection />,
  breadcrumb: (t) => t("reports"),
  handle: {
    access: "view-events",
  },
};

export const ReportsRouteWithTab: AppRouteObject = {
  ...ReportsRoute,
  path: "/:realm/reports/:tab",
};

export const toReports = (params: ReportsParams): Partial<Path> => {
  const path = params.tab ? ReportsRouteWithTab.path : ReportsRoute.path;
  return {
    pathname: generateEncodedPath(path, params),
  };
};
