// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { keyBy } from "lodash";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AccessScope, LayerRole } from "@opencop/api";
import { createColumnHelper, nameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { layerRoleOptions } from "~/features/layers/layers.util";
import {
    layerGroupMutations,
    layerGroupQueries,
} from "~/features/layer-groups/layer-groups.queries";
import type { LayerGroup, LayerGroupLayer } from "~/features/layer-groups/layer-groups.queries";
import { isAssignableLayerGroup } from "~/features/layer-groups/layer-groups.util";

const assignedCols = createColumnHelper<LayerGroupLayer>();

/**
 * The layer *groups* a layer belongs to, with inline assign/revoke — the reverse of
 * `LayerGroupLayersSection`. Mounted by the admin layer drawer's side-nav, and read-only by the
 * My Layers drawer. Distinct from `LayerUserGroupsSection`, which manages *user* groups.
 */
export function LayerLayerGroupsSection({
    layerId,
    accessScope,
    readOnly = false,
}: {
    layerId: string;
    /** The layer's own scope — decides which groups are legal targets. */
    accessScope: AccessScope;
    readOnly?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const groups = useQuery({ ...layerGroupQueries.adminList(), enabled: !readOnly });
    const assignToGroups = useMutation(layerGroupMutations.assignLayersToGroups(queryClient));
    const removeLayer = useMutation(layerGroupMutations.removeLayer(queryClient));

    // A GLOBAL group rejects non-GLOBAL layers server-side (409), so those groups aren't offered.
    const pool = useMemo(
        () => (groups.data ?? []).filter((group) => isAssignableLayerGroup(group, accessScope)),
        [groups.data, accessScope],
    );

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor("layerGroupName", {
                header: t("adminLayerGroups.layerGroup"),
                enableSorting: true,
                cell: (info) => info.getValue() ?? "-",
            }),
            roleColumn<LayerGroupLayer>(t("adminGroups.role"), layerRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [nameColumn<LayerGroup>(t("common.name"))], [t]);

    const props = useRelationSection<LayerGroupLayer, LayerGroup, LayerRole>({
        assignedQuery: layerGroupQueries.forLayer(layerId),
        pool,
        poolLoading: groups.isLoading,
        getAssignedRowId: (row) => row.layerGroupId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: layerRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.layer"),
            relationLabel: t("drawerSections.relation.layerGroups"),
            poolLabel: t("drawerSections.pool.layerGroups"),
            description: t("drawerSections.description.layerLayerGroups"),
            listAriaLabel: t("adminLayerGroups.layerGroups"),
            assignAriaLabel: t("adminDialogs.assignLayerGroups"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminLayerGroups.failedToRemoveLayer"),
            assignError: t("adminLayerGroups.failedToAssignLayer"),
        }),
        // The mutation owns the per-group fan-out, so a partial failure here reports the same way
        // it does from the bulk dialog: the groups that failed are named in the error toast.
        onAssign: async (groupIds, role) => {
            if (!role) return;
            const groupsById = keyBy(pool, "id");
            const selected = groupIds.map((id) => ({
                id,
                name: groupsById[id]?.name ?? id,
            }));
            await assignToGroups.mutateAsync({ groups: selected, layerIds: [layerId], role });
        },
        onRevoke: (layerGroupId) => removeLayer.mutateAsync({ groupId: layerGroupId, layerId }),
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
