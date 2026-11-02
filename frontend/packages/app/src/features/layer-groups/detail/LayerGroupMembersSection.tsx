// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { User } from "@opencop/api";
import { roleColumn, usernameColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { layerGroupRoleOptions } from "~/features/layer-groups/layer-groups.util";
import { useUserPickerPool } from "~/features/users/use-user-picker-pool.hook";
import { LayerGroupRoles, layerGroupMutations, layerGroupQueries } from "../layer-groups.queries";
import type { LayerGroupMember, LayerGroupRole } from "../layer-groups.queries";

/** Members of a layer group, with inline assign/revoke. Mounted by the admin layer-group drawer's side-nav. */
export function LayerGroupMembersSection({
    groupId,
    readOnly = false,
}: {
    groupId: string;
    readOnly?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const userPool = useUserPickerPool();
    // Same cached query useRelationSection runs — read here to reason about owners.
    const members = useQuery(layerGroupQueries.members(groupId));
    const patchMembers = useMutation(layerGroupMutations.patchMembers(queryClient));

    // The group must always keep at least one owner.
    const owners = useMemo(
        () => (members.data ?? []).filter((m) => m.role === LayerGroupRoles.OWNER),
        [members.data],
    );

    const assignedColumns = useMemo(
        () => [
            usernameColumn<LayerGroupMember>(t("adminUsers.user")),
            roleColumn<LayerGroupMember>(t("adminUsers.role"), layerGroupRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [usernameColumn<User>(t("adminUsers.user"))], [t]);

    const props = useRelationSection<LayerGroupMember, User, LayerGroupRole>({
        assignedQuery: layerGroupQueries.members(groupId),
        ...userPool,
        getAssignedRowId: (row) => row.userId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: layerGroupRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.layerGroup"),
            relationLabel: t("drawerSections.relation.members"),
            poolLabel: t("drawerSections.pool.directoryUsers"),
            description: t("drawerSections.description.layerGroupMembers"),
            listAriaLabel: t("adminLayerGroups.members"),
            assignAriaLabel: t("adminDialogs.addNewUsers"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            revokeDisabledReason: t("adminLayerGroups.ownerRequiredWarning"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids, role) =>
            role
                ? patchMembers.mutateAsync({
                      groupId,
                      add: ids.map((userId) => ({ userId, role })),
                  })
                : Promise.resolve(),
        onRevoke: (userId) => patchMembers.mutateAsync({ groupId, remove: [userId] }),
        // The group must keep an owner — block revoking every owner at once
        // (the server rejects it via LayerGroupPolicy.assertRetainsOwner).
        canRevokeFor: (ids) => {
            if (ids.length === 0) return true;
            const idSet = new Set(ids);
            return !(owners.length > 0 && owners.every((o) => idSet.has(o.userId)));
        },
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
