// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { MapTileUserGroupGrant } from "@opencop/api";
import { groupNameColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { groupQueries } from "~/features/groups/groups.queries";
import type { Group } from "~/features/groups/groups.queries";
import { tileGrantMutations, tileQueries } from "~/features/imagery/imagery.queries";

/**
 * User groups that can see an imagery tile.
 *
 * <p>A grant carries no role: every member of a granted group can see the tile, and nothing more.
 * Managing tiles is global-admin only, so the section is always editable and the admin-only group
 * list it draws candidates from is always reachable.
 */
export function TileUserGroupsSection({ tileId }: { tileId: string }): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const pool = useQuery(groupQueries.adminList());
    const assignUserGroups = useMutation(tileGrantMutations.assignUserGroups(queryClient));
    const revokeUserGroups = useMutation(tileGrantMutations.revokeUserGroups(queryClient));

    const assignedColumns = useMemo(
        () => [
            groupNameColumn<MapTileUserGroupGrant>(
                t("adminGroups.name"),
                t("adminGroups.scimManaged"),
            ),
        ],
        [t],
    );
    const unassignedColumns = useMemo(
        () => [groupNameColumn<Group>(t("adminGroups.name"), t("adminGroups.scimManaged"))],
        [t],
    );

    const props = useRelationSection<MapTileUserGroupGrant, Group>({
        assignedQuery: tileQueries.userGroups(tileId),
        pool: pool.data ?? [],
        poolLoading: pool.isLoading,
        getAssignedRowId: (row) => row.userGroupId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.imageryTile"),
            relationLabel: t("drawerSections.relation.userGroups"),
            poolLabel: t("drawerSections.pool.userGroups"),
            description: t("drawerSections.description.tileUserGroups"),
            listAriaLabel: t("adminLayerGroups.userGroups"),
            assignAriaLabel: t("adminNav.groups"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids) => assignUserGroups.mutateAsync({ id: tileId, userGroupIds: ids }),
        onRevoke: (groupId) =>
            revokeUserGroups.mutateAsync({ id: tileId, userGroupIds: [groupId] }),
    });

    return <DrawerRelationSection {...props} />;
}
