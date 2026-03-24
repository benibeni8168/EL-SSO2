import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../utils/generateEncodedPath";
import type { AppRouteObject } from "../../routes";

export type BackupsParams = { realm: string };

const BackupsSection = lazy(() => import("../BackupsSection"));

export const BackupsRoute: AppRouteObject = {
  path: "/:realm/backups",
  element: <BackupsSection />,
  breadcrumb: (t) => t("backups"),
  handle: {
    access: ["view-realm", "manage-realm"],
  },
};

export const toBackups = (params: BackupsParams): Partial<Path> => ({
  pathname: generateEncodedPath(BackupsRoute.path, params),
});
