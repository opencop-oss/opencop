// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { DataSourceLayer, SourceCandidate } from "@opencop/api";
import { createColumnHelper, nameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import {
    dataSourceMutations,
    dataSourceQueries,
} from "~/features/data-sources/data-sources.queries";
import { dataSourceRoleOptions } from "~/features/data-sources/data-sources.util";
import { layerQueries } from "../layers.queries";

const assignedCols = createColumnHelper<DataSourceLayer>();

/** Data sources attached to a layer, with inline attach/detach. Mounted by the admin layer drawer's side-nav. */
export function LayerDataSourcesSection({
    layerId,
    assignmentRole,
    readOnly = false,
}: {
    layerId: string;
    assignmentRole: "ADMIN" | "OWNER";
    readOnly?: boolean;
}): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const pool = useQuery({
        ...dataSourceQueries.candidatesForLayer(layerId),
        enabled: !readOnly && !!layerId,
    });
    const addLayers = useMutation(dataSourceMutations.addLayers(queryClient));
    const deleteLayer = useMutation(dataSourceMutations.deleteLayer(queryClient));

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor("serverName", {
                header: t("common.name"),
                enableSorting: true,
                cell: (info) => info.getValue() ?? "-",
            }),
            roleColumn<DataSourceLayer>(t("adminUsers.role"), dataSourceRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [nameColumn<SourceCandidate>(t("common.name"))], [t]);

    const props = useRelationSection<DataSourceLayer, SourceCandidate>({
        assignedQuery: layerQueries.dataSources(layerId),
        pool: pool.data ?? [],
        poolLoading: pool.isLoading,
        getAssignedRowId: (row) => row.serverId,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.layer"),
            relationLabel: t("drawerSections.relation.dataSources"),
            poolLabel: t("drawerSections.pool.dataSources"),
            description: t("drawerSections.description.layerDataSources"),
            listAriaLabel: t("adminDialogs.dataSources"),
            assignAriaLabel: t("adminDialogs.addDataSources"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids) =>
            Promise.all(
                ids.map((serverId) =>
                    addLayers.mutateAsync({ serverId, layerIds: [layerId], role: assignmentRole }),
                ),
            ),
        onRevoke: (serverId) => deleteLayer.mutateAsync({ serverId, layerId }),
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
