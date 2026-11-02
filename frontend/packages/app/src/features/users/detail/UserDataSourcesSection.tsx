// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AssignedDataSource, DataSourceInfo, DataSourceRole } from "@opencop/api";
import { createColumnHelper, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { dataSourceRoleOptions } from "~/features/data-sources/data-sources.util";
import {
    dataSourceMutations,
    dataSourceQueries,
} from "~/features/data-sources/data-sources.queries";
import { userQueries } from "~/features/users/users.queries";
import { SourceSummaryAccessMode } from "~/features/data-sources/SourceSummaryAccessMode";

const assignedCols = createColumnHelper<AssignedDataSource>();
const unassignedCols = createColumnHelper<DataSourceInfo>();

/** Data sources assigned to a user, with inline assign/revoke. Mounted by the user drawer's side-nav. */
export function UserDataSourcesSection({ userId }: { userId: string }): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const pool = useQuery(dataSourceQueries.adminList());
    const addUsers = useMutation(dataSourceMutations.addUsers(queryClient));
    const deleteUser = useMutation(dataSourceMutations.deleteUser(queryClient));

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor("serverName", {
                header: t("common.name"),
                enableSorting: true,
                cell: (info) => info.getValue() ?? "-",
            }),
            assignedCols.accessor("transportType", {
                header: t("admin.protocol"),
                enableSorting: true,
            }),
            assignedCols.accessor("access", {
                header: t("admin.accessMode"),
                enableSorting: true,
                cell: (info) => (
                    <SourceSummaryAccessMode
                        access={info.getValue()}
                        transportType={info.row.original.transportType}
                    />
                ),
            }),
            roleColumn<AssignedDataSource>(t("adminUsers.role"), dataSourceRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(
        () => [
            unassignedCols.accessor("name", {
                header: t("common.name"),
                enableSorting: true,
                cell: (info) => info.getValue() || info.row.original.id,
            }),
        ],
        [t],
    );

    const props = useRelationSection<AssignedDataSource, DataSourceInfo, DataSourceRole>({
        assignedQuery: userQueries.assignedDataSources(userId),
        pool: pool.data ?? [],
        poolLoading: pool.isLoading,
        getAssignedRowId: (row) => row.serverId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: dataSourceRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.user"),
            entityReceivesAccess: true,
            relationLabel: t("drawerSections.relation.dataSources"),
            poolLabel: t("drawerSections.pool.dataSources"),
            description: t("drawerSections.description.userDataSources"),
            listAriaLabel: t("adminUsers.manageDataSources"),
            assignAriaLabel: t("adminDialogs.addDataSources"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids, role) =>
            role
                ? Promise.all(
                      ids.map((serverId) =>
                          addUsers.mutateAsync({ serverId, userId: [userId], role }),
                      ),
                  )
                : Promise.resolve(),
        onRevoke: (serverId) => deleteUser.mutateAsync({ serverId, userId }),
    });

    return <DrawerRelationSection {...props} />;
}
