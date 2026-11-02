// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { MapTileMember, User } from "@opencop/api";
import { createColumnHelper, usernameColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { useUserPickerPool } from "~/features/users/use-user-picker-pool.hook";
import { tileGrantMutations, tileQueries } from "~/features/imagery/imagery.queries";

const assignedCols = createColumnHelper<MapTileMember>();

/**
 * Users who can see an imagery tile, with inline assign/revoke.
 *
 * <p>A grant carries no role: imagery is managed by global admins, so holding one means the tile is
 * visible to that user and nothing more. Managing tiles is global-admin only, so the section is
 * always editable.
 */
export function TileMembersSection({ tileId }: { tileId: string }): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const userPool = useUserPickerPool();
    const assignMembers = useMutation(tileGrantMutations.assignMembers(queryClient));
    const revokeMembers = useMutation(tileGrantMutations.revokeMembers(queryClient));

    const assignedColumns = useMemo(
        () => [
            // Not usernameColumn: username is null when the user is not provisioned locally,
            // so fall back to the id rather than rendering a blank cell.
            assignedCols.accessor((row) => row.username ?? row.userId, {
                id: "username",
                header: t("adminUsers.user"),
                enableSorting: true,
                meta: { filterDef: { label: t("adminUsers.user"), type: "string" } },
                filterFn: "ocStringFilter",
            }),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [usernameColumn<User>(t("adminUsers.user"))], [t]);

    const props = useRelationSection<MapTileMember, User>({
        assignedQuery: tileQueries.members(tileId),
        ...userPool,
        getAssignedRowId: (row) => row.userId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.imageryTile"),
            relationLabel: t("drawerSections.relation.members"),
            poolLabel: t("drawerSections.pool.directoryUsers"),
            description: t("drawerSections.description.tileMembers"),
            listAriaLabel: t("adminActions.users"),
            assignAriaLabel: t("adminDialogs.addNewUsers"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids) => assignMembers.mutateAsync({ id: tileId, userIds: ids }),
        onRevoke: (userId) => revokeMembers.mutateAsync({ id: tileId, userIds: [userId] }),
    });

    return <DrawerRelationSection {...props} />;
}
