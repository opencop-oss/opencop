// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { UserGroupSearchResult } from "@opencop/api";
import { groupNameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import {
    grantableLayerGroupRoleOptions,
    layerGroupRoleOptions,
} from "~/features/layer-groups/layer-groups.util";
import { useUserGroupPickerPool } from "~/features/groups/use-user-group-picker-pool.hook";
import { layerGroupMutations, layerGroupQueries } from "../layer-groups.queries";
import type { LayerGroupRole, LayerGroupUserGroup } from "../layer-groups.queries";

/**
 * User groups assigned to a layer group, with inline assign/revoke.
 *
 * `actorRole` clamps which roles may be granted. Omit it for callers the server exempts from the
 * ceiling, which is global admins.
 */
export function LayerGroupUserGroupsSection({
    groupId,
    actorRole,
    readOnly = false,
}: {
    groupId: string;
    actorRole?: LayerGroupRole;
    readOnly?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const groupPool = useUserGroupPickerPool({ enabled: !readOnly });
    const patchUserGroups = useMutation(layerGroupMutations.patchUserGroups(queryClient));
    // Same cached query useRelationSection runs — read here to build the change-role rows, which
    // have to be passed *into* the hook and so cannot come from its own `assigned`.
    const assignedUserGroups = useQuery(layerGroupQueries.userGroups(groupId));

    const assignedColumns = useMemo(
        () => [
            groupNameColumn<LayerGroupUserGroup>(
                t("adminGroups.name"),
                t("adminGroups.scimManaged"),
            ),
            roleColumn<LayerGroupUserGroup>(t("adminGroups.role"), layerGroupRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(
        () => [
            groupNameColumn<UserGroupSearchResult>(
                t("adminGroups.name"),
                t("adminGroups.scimManaged"),
            ),
        ],
        [t],
    );
    // The assigned list renders every role as a label, but only roles at or below the actor's own
    // may be granted; the server refuses the rest with an opaque 403.
    const grantableRoles = useMemo(
        () => grantableLayerGroupRoleOptions(t, actorRole),
        [t, actorRole],
    );

    const roleLabels = useMemo(
        () => new Map(layerGroupRoleOptions(t).map((option) => [option.value, option.label])),
        [t],
    );
    // Rows shown in the bulk change-role dialog for the selected groups. Without this the dialog
    // opens empty and its Apply button can never enable.
    const getChangeRoleRows = useCallback(
        (ids: string[]) => {
            const byId = new Map(
                (assignedUserGroups.data ?? []).map((group) => [group.userGroupId, group]),
            );
            return ids
                .map((id) => byId.get(id))
                .filter((group): group is LayerGroupUserGroup => group != null)
                .map((group) => ({
                    id: group.userGroupId,
                    label: group.name,
                    role: roleLabels.get(group.role) ?? group.role,
                }));
        },
        [assignedUserGroups.data, roleLabels],
    );

    const props = useRelationSection<LayerGroupUserGroup, UserGroupSearchResult, LayerGroupRole>({
        assignedQuery: layerGroupQueries.userGroups(groupId),
        ...groupPool,
        getAssignedRowId: (row) => row.userGroupId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: grantableRoles,
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.layerGroup"),
            relationLabel: t("drawerSections.relation.userGroups"),
            poolLabel: t("drawerSections.pool.userGroups"),
            description: t("drawerSections.description.layerGroupUserGroups"),
            listAriaLabel: t("adminLayerGroups.userGroups"),
            assignAriaLabel: t("adminNav.groups"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids, role) =>
            role
                ? patchUserGroups.mutateAsync({
                      groupId,
                      add: ids.map((userGroupId) => ({ userGroupId, role })),
                  })
                : Promise.resolve(),
        onRevoke: (userGroupId) => patchUserGroups.mutateAsync({ groupId, remove: [userGroupId] }),
        // An add for an already-assigned group upserts its role server-side, so change-role and
        // assign are the same call.
        onChangeRole: (ids, role) =>
            patchUserGroups.mutateAsync({
                groupId,
                add: ids.map((userGroupId) => ({ userGroupId, role })),
            }),
        getChangeRoleRows,
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
