// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { LayerUserGroupAssignment, LayerRole } from "@opencop/api";
import { createColumnHelper, groupNameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { layerRoleOptions } from "~/features/layers/layers.util";
import { groupQueries } from "~/features/groups/groups.queries";
import type { Group } from "~/features/groups/groups.queries";
import { layerMutations, layerQueries } from "../layers.queries";

const assignedCols = createColumnHelper<LayerUserGroupAssignment>();

/**
 * User groups assigned to a layer, with inline assign/revoke. Mounted by the
 * admin layer drawer's side-nav, and (read-only for non-admins) by the My Layers
 * drawer. Group assign/revoke are admin-only backend operations, so `readOnly`
 * hides the controls and skips the admin-only group pool fetch.
 */
export function LayerUserGroupsSection({
    layerId,
    readOnly = false,
}: {
    layerId: string;
    readOnly?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const pool = useQuery({ ...groupQueries.adminList(), enabled: !readOnly });
    const assignGroup = useMutation(layerMutations.assignGroup(queryClient));
    const removeGroup = useMutation(layerMutations.removeGroup(queryClient));

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor("groupName", {
                header: t("adminGroups.name"),
                enableSorting: true,
            }),
            roleColumn<LayerUserGroupAssignment>(t("adminGroups.role"), layerRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(
        () => [groupNameColumn<Group>(t("adminGroups.name"), t("adminGroups.scimManaged"))],
        [t],
    );

    const props = useRelationSection<LayerUserGroupAssignment, Group, LayerRole>({
        assignedQuery: layerQueries.userGroups(layerId),
        pool: pool.data ?? [],
        poolLoading: pool.isLoading,
        getAssignedRowId: (row) => row.groupId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: layerRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.layer"),
            relationLabel: t("drawerSections.relation.userGroups"),
            poolLabel: t("drawerSections.pool.userGroups"),
            description: t("drawerSections.description.layerUserGroups"),
            listAriaLabel: t("adminLayerGroups.userGroups"),
            assignAriaLabel: t("adminNav.groups"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: async (ids, role) => {
            if (!role) return;
            await Promise.all(
                ids.map((groupId) => assignGroup.mutateAsync({ groupId, layerId, role })),
            );
        },
        onRevoke: (groupId) => removeGroup.mutateAsync({ layerId, groupId }),
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
