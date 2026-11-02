// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { LayerInfo, LayerRole } from "@opencop/api";
import { createColumnHelper, nameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { layerRoleOptions } from "~/features/layers/layers.util";
import { layerQueries } from "~/features/layers/layers.queries";
import { groupMutations, groupQueries } from "../groups.queries";
import type { GroupLayerAssignment } from "../groups.queries";

const assignedCols = createColumnHelper<GroupLayerAssignment>();

/** Layers assigned to a user group, with inline assign/revoke. Mounted by the group drawer's side-nav. */
export function GroupLayersSection({ groupId }: { groupId: string }): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const pool = useQuery(layerQueries.adminList());
    const assignToLayer = useMutation(groupMutations.assignToLayer(queryClient));
    const removeFromLayer = useMutation(groupMutations.removeFromLayer(queryClient));

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor("layerName", {
                header: t("adminGroups.layer"),
                enableSorting: true,
                cell: (info) => info.getValue() ?? "-",
            }),
            roleColumn<GroupLayerAssignment>(t("adminGroups.role"), layerRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [nameColumn<LayerInfo>(t("common.name"))], [t]);

    const props = useRelationSection<GroupLayerAssignment, LayerInfo, LayerRole>({
        assignedQuery: groupQueries.groupLayers(groupId),
        pool: pool.data ?? [],
        poolLoading: pool.isLoading,
        getAssignedRowId: (row) => row.layerId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: layerRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.userGroup"),
            entityReceivesAccess: true,
            relationLabel: t("drawerSections.relation.layers"),
            poolLabel: t("drawerSections.pool.layers"),
            description: t("drawerSections.description.groupLayers"),
            listAriaLabel: t("adminGroups.layers"),
            assignAriaLabel: t("adminDialogs.assignLayers"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminGroups.failedToRemoveLayer"),
            assignError: t("adminGroups.failedToAssignLayer"),
        }),
        onAssign: (ids, role) =>
            role
                ? Promise.all(
                      ids.map((layerId) => assignToLayer.mutateAsync({ groupId, layerId, role })),
                  )
                : Promise.resolve(),
        onRevoke: (layerId) => removeFromLayer.mutateAsync({ groupId, layerId }),
    });

    return <DrawerRelationSection {...props} />;
}
