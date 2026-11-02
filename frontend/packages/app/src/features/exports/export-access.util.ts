// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { DataSourceRoles, LayerRoles, type DataSourceRole, type LayerRole } from "@opencop/api";

function canAdministerLayer(role: LayerRole, isAdmin: boolean): boolean {
    return isAdmin || role === LayerRoles.OWNER || role === LayerRoles.ADMIN;
}

export function canExportLayer(role: LayerRole, isAdmin: boolean): boolean {
    return canAdministerLayer(role, isAdmin);
}

export function canMergeLayer(role: LayerRole, isAdmin: boolean): boolean {
    return canAdministerLayer(role, isAdmin);
}

export function canExportDataSource(role: DataSourceRole, isAdmin: boolean): boolean {
    return isAdmin || role === DataSourceRoles.OWNER || role === DataSourceRoles.ADMIN;
}
