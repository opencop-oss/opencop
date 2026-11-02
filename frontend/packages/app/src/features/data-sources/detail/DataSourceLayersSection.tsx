// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { DataSourceLayer, DataSourceRole } from "@opencop/api";
import { createColumnHelper, nameColumn, roleColumn } from "@opencop/ui";
import { DrawerRelationSection } from "~/features/drawers/DrawerRelationSection";
import { baseRelationLabels } from "~/features/drawers/relation-section.util";
import { useRelationSection } from "~/features/drawers/use-relation-section.hook";
import { dataSourceRoleOptions } from "~/features/data-sources/data-sources.util";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceMutations, dataSourceQueries } from "../data-sources.queries";

/** Minimal shape shared by the admin (`LayerInfo`) and "my" (`MyLayerInfo`) layer pools. */
interface LayerPoolItem {
    id: string;
    name: string;
}

const assignedCols = createColumnHelper<DataSourceLayer>();

interface DataSourceLayersSectionProps {
    serverId: string;
    /** "my" uses the caller's own layers as the assignable pool; "admin" uses all layers. */
    layerScope?: "admin" | "my";
    readOnly?: boolean;
}

/** Layers granted to a data source, with inline assign/revoke. Mounted by the data source drawer's side-nav. */
export function DataSourceLayersSection({
    serverId,
    layerScope = "admin",
    readOnly = false,
}: DataSourceLayersSectionProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();

    const adminLayers = useQuery({ ...layerQueries.adminList(), enabled: layerScope === "admin" });
    const myLayers = useQuery({ ...layerQueries.myList(), enabled: layerScope === "my" });

    const pool = useMemo<LayerPoolItem[]>(
        () =>
            layerScope === "my"
                ? (myLayers.data ?? []).map((l) => ({ id: l.layerId, name: l.layerName }))
                : (adminLayers.data ?? []).map((l) => ({ id: l.id, name: l.name })),
        [layerScope, myLayers.data, adminLayers.data],
    );
    const poolLoading = layerScope === "my" ? myLayers.isLoading : adminLayers.isLoading;

    const addLayers = useMutation(dataSourceMutations.addLayers(queryClient));
    const deleteLayer = useMutation(dataSourceMutations.deleteLayer(queryClient));

    const assignedColumns = useMemo(
        () => [
            assignedCols.accessor((row) => row.layer.name, {
                id: "layerName",
                header: t("common.name"),
                enableSorting: true,
                cell: (info) => info.getValue() ?? "-",
            }),
            roleColumn<DataSourceLayer>(t("adminUsers.role"), dataSourceRoleOptions(t)),
        ],
        [t],
    );
    const unassignedColumns = useMemo(() => [nameColumn<LayerPoolItem>(t("common.name"))], [t]);

    const props = useRelationSection<DataSourceLayer, LayerPoolItem, DataSourceRole>({
        assignedQuery: dataSourceQueries.layers(serverId),
        pool,
        poolLoading,
        getAssignedRowId: (row) => row.layer.id,
        getUnassignedRowId: (row) => row.id,
        assignedColumns,
        unassignedColumns,
        roleOptions: dataSourceRoleOptions(t),
        labels: baseRelationLabels(t, {
            entityLabel: t("drawerSections.entity.dataSource"),
            relationLabel: t("drawerSections.relation.layers"),
            poolLabel: t("drawerSections.pool.layers"),
            description: t("drawerSections.description.dataSourceLayers"),
            listAriaLabel: t("adminActions.layers"),
            assignAriaLabel: t("adminDialogs.assignLayers"),
            rolePlaceholder: t("adminDialogs.rolePlaceholder"),
            revokeError: t("adminDialogs.revokeFailed"),
            assignError: t("adminDialogs.assignFailed"),
        }),
        onAssign: (ids, role) =>
            role ? addLayers.mutateAsync({ serverId, layerIds: ids, role }) : Promise.resolve(),
        onRevoke: (layerId) => deleteLayer.mutateAsync({ serverId, layerId }),
    });

    return <DrawerRelationSection {...props} readOnly={readOnly} />;
}
