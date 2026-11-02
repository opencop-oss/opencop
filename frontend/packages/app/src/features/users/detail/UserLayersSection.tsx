// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AssignedLayer, LayerInfo, LayerRole } from "@opencop/api";
import { createColumnHelper, nameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { layerRoleOptions } from "~/features/layers/layers.util";
import { layerMutations, layerQueries } from "~/features/layers/layers.queries";
import { userQueries } from "~/features/users/users.queries";

const assignedCols = createColumnHelper<AssignedLayer>();

/** Layers assigned to a user, with inline assign/revoke. Mounted by the user drawer's side-nav. */
export function UserLayersSection({ userId }: { userId: string }): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const pool = useQuery(layerQueries.adminList());
    const assignUser = useMutation(layerMutations.assignUser(queryClient));
    const removeUser = useMutation(layerMutations.removeUser(queryClient));

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor("layerName", {
                header: t("common.name"),
                enableSorting: true,
                cell: (info) => info.getValue() ?? "-",
            }),
            roleColumn<AssignedLayer>(t("adminUsers.role"), layerRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [nameColumn<LayerInfo>(t("common.name"))], [t]);

    const props = useRelationSection<AssignedLayer, LayerInfo, LayerRole>({
        assignedQuery: userQueries.assignedLayers(userId),
        pool: pool.data ?? [],
        poolLoading: pool.isLoading,
        getAssignedRowId: (row) => row.layerId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: layerRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.user"),
            entityReceivesAccess: true,
            relationLabel: t("drawerSections.relation.layers"),
            poolLabel: t("drawerSections.pool.layers"),
            description: t("drawerSections.description.userLayers"),
            listAriaLabel: t("adminUsers.manageLayers"),
            assignAriaLabel: t("adminDialogs.assignLayers"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids, role) =>
            role
                ? Promise.all(
                      ids.map((layerId) => assignUser.mutateAsync({ layerId, userId, role })),
                  )
                : Promise.resolve(),
        onRevoke: (layerId) => removeUser.mutateAsync({ layerId, userId }),
    });

    return <DrawerRelationSection {...props} />;
}
