// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { DataSourceRole, DataSourceUser, User } from "@opencop/api";
import { roleColumn, usernameColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { dataSourceRoleOptions } from "~/features/data-sources/data-sources.util";
import { useUserPickerPool } from "~/features/users/use-user-picker-pool.hook";
import { dataSourceMutations, dataSourceQueries } from "../data-sources.queries";

/** Users granted access to a data source, with inline assign/revoke. Mounted by the data source drawer's side-nav. */
export function DataSourceUsersSection({
    serverId,
    readOnly = false,
}: {
    serverId: string;
    readOnly?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const userPool = useUserPickerPool();
    const addUsers = useMutation(dataSourceMutations.addUsers(queryClient));
    const deleteUser = useMutation(dataSourceMutations.deleteUser(queryClient));

    const assignedColumns = useMemo(
        () => [
            usernameColumn<DataSourceUser>(t("adminUsers.user")),
            roleColumn<DataSourceUser>(t("adminUsers.role"), dataSourceRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [usernameColumn<User>(t("adminUsers.user"))], [t]);

    const props = useRelationSection<DataSourceUser, User, DataSourceRole>({
        assignedQuery: dataSourceQueries.users(serverId),
        ...userPool,
        getAssignedRowId: (row) => row.userId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: dataSourceRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.dataSource"),
            relationLabel: t("drawerSections.relation.users"),
            poolLabel: t("drawerSections.pool.directoryUsers"),
            description: t("drawerSections.description.dataSourceUsers"),
            listAriaLabel: t("adminActions.users"),
            assignAriaLabel: t("adminDialogs.addNewUsers"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids, role) =>
            role ? addUsers.mutateAsync({ serverId, userId: ids, role }) : Promise.resolve(),
        onRevoke: (userId) => deleteUser.mutateAsync({ serverId, userId }),
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
