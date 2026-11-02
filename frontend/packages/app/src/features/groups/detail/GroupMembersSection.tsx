// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { User } from "@opencop/api";
import { createColumnHelper, usernameColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { useUserPickerPool } from "~/features/users/use-user-picker-pool.hook";
import { formatDateOnly } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { groupMutations, groupQueries } from "../groups.queries";
import type { GroupMember } from "../groups.queries";

const assignedCols = createColumnHelper<GroupMember>();

/** Members of a user group, with inline assign/revoke. Mounted by the group drawer's side-nav. */
export function GroupMembersSection({ groupId }: { groupId: string }): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const queryClient = useQueryClient();
    const userPool = useUserPickerPool();
    const patchMembers = useMutation(groupMutations.patchMembers(queryClient));

    const assignedColumns = useMemo(
        () => [
            usernameColumn<GroupMember>(t("adminUsers.user")),
            assignedCols.accessor("joinedAt", {
                header: t("adminGroups.joinedAt"),
                // Same zone the column's date filter resolves in, so the day a row shows is
                // the day it matches. Without it the cell renders UTC and fails its own filter.
                cell: (info) => formatDateOnly(info.getValue(), timezone),
                enableSorting: true,
                meta: {
                    filterDef: { label: t("adminGroups.joinedAt"), type: "date" as const },
                },
                filterFn: "ocDateFilter",
            }),
        ],
        [t, timezone],
    );
    const unassignedColumns = useMemo(() => [usernameColumn<User>(t("adminUsers.user"))], [t]);

    const props = useRelationSection<GroupMember, User>({
        assignedQuery: groupQueries.members(groupId),
        ...userPool,
        getAssignedRowId: (row) => row.userId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.userGroup"),
            relationLabel: t("drawerSections.relation.members"),
            poolLabel: t("drawerSections.pool.directoryUsers"),
            description: t("drawerSections.description.groupMembers"),
            listAriaLabel: t("adminGroups.members"),
            assignAriaLabel: t("adminDialogs.addNewUsers"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids) => patchMembers.mutateAsync({ groupId, add: ids }),
        onRevoke: (userId) => patchMembers.mutateAsync({ groupId, remove: [userId] }),
    });

    return <DrawerRelationSection {...props} />;
}
