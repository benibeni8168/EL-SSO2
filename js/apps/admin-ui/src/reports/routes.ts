import type { AppRouteObject } from "../routes";
import { ReportsRoute, ReportsRouteWithTab } from "./routes/Reports";

const routes: AppRouteObject[] = [ReportsRoute, ReportsRouteWithTab];

export default routes;
