// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DataSourceRoles } from "@opencop/api";
import type { ConnectionState, DataSourceRole, TransportType } from "@opencop/api";
import type { TFunction } from "i18next";
import type { RoleDescriptor } from "~/features/layer-groups/layer-groups.util";
import type { DrawerRelationRoleOption } from "~/features/drawers/drawer-relation.model";

/** The name each transport is shown by, everywhere the UI names one. */
export const TRANSPORT_LABEL_KEYS = {
    TCP: "admin.tcp",
    UDP: "admin.udp",
    KAFKA: "admin.kafka",
    RTSP: "admin.rtsp",
} as const satisfies Record<TransportType, string>;

export const DATA_SOURCE_ROLE_CONFIG: Record<DataSourceRole, { colorPalette: string }> = {
    OWNER: { colorPalette: "purple" },
    ADMIN: { colorPalette: "red" },
    VIEWER: { colorPalette: "sky" },
};

// Role explanations shown in the My Data Sources Role column header tooltip.
// Reuses the shared `roles.*Desc` i18n keys.
export const DATA_SOURCE_ROLE_DESCRIPTIONS: RoleDescriptor[] = [
    { role: "OWNER", color: "purple", descriptionKey: "roles.ownerDesc" },
    { role: "ADMIN", color: "red", descriptionKey: "roles.adminDesc" },
    { role: "VIEWER", color: "sky", descriptionKey: "roles.viewerDesc" },
];

// `ConnectionState` includes a `(string & {})` fallback so the server can emit
// values outside the canonical union — defensive `toUpperCase()` here matches
// the rest of the codebase rather than trusting an open union.
export function isConnected(state: ConnectionState | string | undefined): boolean {
    return state?.toUpperCase() === "CONNECTED";
}

/** Data-source access roles: viewer / admin / owner. */
export function dataSourceRoleOptions(t: TFunction): DrawerRelationRoleOption<DataSourceRole>[] {
    return [
        { label: t("roles.viewer"), value: DataSourceRoles.VIEWER },
        { label: t("roles.admin"), value: DataSourceRoles.ADMIN },
        { label: t("roles.owner"), value: DataSourceRoles.OWNER },
    ];
}
