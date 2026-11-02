// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AccessScope, LayerRole } from "@opencop/api";
import { createColumnHelper, nameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import {
    isLayerRoleAtLeast,
    layerRoleOptions,
    weakestLayerRole,
} from "~/features/layers/layers.util";
import { layerQueries } from "~/features/layers/layers.queries";
import { layerGroupMutations, layerGroupQueries } from "../layer-groups.queries";
import type { LayerGroupAccessScope, LayerGroupLayer } from "../layer-groups.queries";
import { canAssignLayerAsNonAdmin, isAssignableLayerGroup } from "../layer-groups.util";

const assignedCols = createColumnHelper<LayerGroupLayer>();

interface LayerGroupLayersSectionProps {
    groupId: string;
    /** Used to name this group in a failed-assign message. */
    groupName: string;
    /** The group's own scope — decides which layers it can legally take. */
    accessScope: LayerGroupAccessScope;
    layerScope: "admin" | "my";
    readOnly?: boolean;
}

interface LayerPoolItem {
    id: string;
    name: string;
    accessScope: AccessScope;
    /** The caller's effective role. Only known on the "my" pool; the admin pool is unrestricted. */
    role?: LayerRole;
}

/** Layers assigned to a layer group, with inline assign/revoke. Mounted by the admin layer-group drawer's side-nav. */
export function LayerGroupLayersSection({
    groupId,
    groupName,
    accessScope,
    layerScope,
    readOnly = false,
}: LayerGroupLayersSectionProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const adminLayers = useQuery({
        ...layerQueries.adminList(),
        enabled: !readOnly && layerScope === "admin",
    });
    const myLayers = useQuery({
        ...layerQueries.myList(),
        enabled: !readOnly && layerScope === "my",
    });
    const assignLayers = useMutation(layerGroupMutations.assignLayersToGroups(queryClient));
    const removeLayer = useMutation(layerGroupMutations.removeLayer(queryClient));

    /**
     * Mirrors the server so the picker offers exactly what it accepts: a GLOBAL group takes only
     * GLOBAL layers, and the "my" pool (non-admins) is further limited to PRIVATE layers the
     * caller holds EDITOR or above on. The hook then drops already-assigned ones.
     */
    const pool = useMemo<LayerPoolItem[]>(
        () =>
            layerScope === "admin"
                ? (adminLayers.data ?? [])
                      .map((layer) => ({
                          id: layer.id,
                          name: layer.name,
                          accessScope: layer.accessScope,
                      }))
                      .filter((layer) => isAssignableLayerGroup({ accessScope }, layer.accessScope))
                : (myLayers.data ?? [])
                      .map((layer) => ({
                          id: layer.layerId,
                          name: layer.layerName,
                          accessScope: layer.accessScope,
                          role: layer.role,
                      }))
                      .filter(
                          (layer) =>
                              isAssignableLayerGroup({ accessScope }, layer.accessScope) &&
                              canAssignLayerAsNonAdmin(layer),
                      ),
        [adminLayers.data, myLayers.data, accessScope, layerScope],
    );
    const poolLoading = layerScope === "admin" ? adminLayers.isLoading : myLayers.isLoading;

    /**
     * `LayerGroupPolicy.assertCanAssignRole` in opencop-java rejects an assignment role above the
     * actor's own role on **any** layer in the batch, so the ceiling is the weakest role across the
     * selection. It answers with an opaque `{"error":"forbidden"}` — `CoreSecurityErrorResponse`
     * emits a constant and routes the reason to the audit log, never the body — so nothing in the
     * response can drive a per-layer error UI, which is why this is prevented here rather than
     * left to fail.
     *
     * The admin pool carries no per-user role and admins are exempt server-side, so it is
     * unclamped.
     */
    const roleOptionsFor = useMemo(() => {
        const allRoles = layerRoleOptions(t);
        if (layerScope === "admin") return allRoles;
        return (selectedIds: string[]) => {
            const selectedRoles = pool
                .filter((layer) => selectedIds.includes(layer.id))
                .map((layer) => layer.role)
                .filter((role): role is LayerRole => role != null);
            const ceiling = weakestLayerRole(selectedRoles);
            if (ceiling == null) return allRoles;
            return allRoles.filter((option) => isLayerRoleAtLeast(ceiling, option.value));
        };
    }, [t, layerScope, pool]);

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor("layerName", {
                header: t("adminGroups.layer"),
                enableSorting: true,
                cell: (info) => info.getValue() ?? "-",
            }),
            roleColumn<LayerGroupLayer>(t("adminGroups.role"), layerRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [nameColumn<LayerPoolItem>(t("common.name"))], [t]);

    const props = useRelationSection<LayerGroupLayer, LayerPoolItem, LayerRole>({
        assignedQuery: layerGroupQueries.layers(groupId),
        pool,
        poolLoading,
        getAssignedRowId: (row) => row.layerId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: roleOptionsFor,
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.layerGroup"),
            entityReceivesAccess: true,
            relationLabel: t("drawerSections.relation.layers"),
            poolLabel: t("drawerSections.pool.layers"),
            description: t("drawerSections.description.layerGroupLayers"),
            listAriaLabel: t("adminLayerGroups.layers"),
            assignAriaLabel: t("adminDialogs.assignLayers"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminLayerGroups.failedToRemoveLayer"),
            assignError: t("adminLayerGroups.failedToAssignLayer"),
        }),
        // A one-element `groups` array — the same mutation the layer-side entry points use, so the
        // invalidation targets stay in one place instead of being mirrored per call site.
        onAssign: (ids, role) => {
            // Defensive: `roleOptions` is always supplied above, so AssignPanel gates Add on a
            // chosen role and never calls this without one. Reject rather than silently no-op if
            // that ever changes, so the failure surfaces instead of swallowing the assign.
            if (!role) return Promise.reject(new Error("assign called without a role"));
            return assignLayers.mutateAsync({
                groups: [{ id: groupId, name: groupName }],
                layerIds: ids,
                role,
            });
        },
        onRevoke: (layerId) => removeLayer.mutateAsync({ groupId, layerId }),
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
