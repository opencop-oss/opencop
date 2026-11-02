// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { LayerInfo } from "@opencop/api";
import { startCase } from "lodash";
import { DetailTable, DrawerSideNavLayout, useDefaultVisibleRow } from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaArchive, FaCopy, FaDownload, FaPencilAlt, FaServer, FaUpload } from "react-icons/fa";
import { TbUserFilled, TbUsers } from "react-icons/tb";
import { formatDateMilitary } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useAuditHistorySection } from "~/features/drawers/use-audit-history-section.hook";
import { useAuditRows } from "~/features/drawers/use-audit-rows.hook";
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import type { AdminLayerModalState } from "../AdminLayerDrawer";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { TypeIndicator } from "~/features/layers/TypeIndicator";
import { layerMutations } from "../layers.queries";
import { useUserLabel } from "~/features/users/use-user-label.hook";
import { AdminLayerEditContent } from "./AdminLayerEditContent";
import { LayerMembersSection } from "./LayerMembersSection";
import { LayerUserGroupsSection } from "./LayerUserGroupsSection";
import { LayerLayerGroupsSection } from "./LayerLayerGroupsSection";
import { LayerDataSourcesSection } from "./LayerDataSourcesSection";

interface AdminLayerDrawerContentProps {
    layer: LayerInfo;
    onOpenModal: (modal: AdminLayerModalState) => void;
}

/** Maps a layer lifecycle state to its localized status label key. */
const LAYER_STATUS_KEYS = {
    ACTIVE: "adminLayers.statusActive",
    PENDING_MERGE: "adminLayers.statusPendingMerge",
    MERGE_SOURCE_LOCKED: "adminLayers.statusMergeSourceLocked",
    MERGE_FAILED: "adminLayers.statusMergeFailed",
    ARCHIVED: "adminLayers.statusArchived",
} as const;

export function AdminLayerDrawerContent({
    layer,
    onOpenModal,
}: AdminLayerDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const queryClient = useQueryClient();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";
    // Locked while the members section's bulk change-role panel is open.
    const [membersLocked, setMembersLocked] = useState(false);
    const isArchived = layer.archivedAt != null;
    // The admin list populates lifecycleState (e.g. "ACTIVE"); status is the fallback.
    const layerStatus = layer.lifecycleState ?? layer.status;
    const { mutate: patchLayer } = useMutation(layerMutations.patch(queryClient));

    // Localize the status value; unmapped/unexpected states fall back to a humanized string.
    const statusLabel = useMemo(() => {
        if (!layerStatus) return "-";
        const key = LAYER_STATUS_KEYS[layerStatus as keyof typeof LAYER_STATUS_KEYS];
        return key ? t(key) : startCase(layerStatus.toLowerCase());
    }, [layerStatus, t]);

    // Resolved only once archived, so an active layer doesn't pay for a lookup it never renders.
    const archivedByLabel = useUserLabel(isArchived ? layer.archivedBy : undefined) ?? "-";

    const auditRows = useAuditRows(layer, timezone, "layer");

    // No role check needed — this drawer is reachable only via the admin-guarded route.
    const defaultVisibleRow = useDefaultVisibleRow({
        checked: layer.defaultVisible,
        disabled: isArchived,
        onCheckedChange: (checked) => patchLayer({ id: layer.id, defaultVisible: checked }),
    });

    const rows = [
        {
            key: "name",
            label: t("adminLayers.name"),
            value: layer.name || t("adminLayers.unnamedLayer"),
        },
        {
            key: "accessScope",
            label: t("adminLayers.accessScope"),
            value: <ScopeIndicator scope={layer.accessScope} />,
        },
        {
            key: "status",
            label: t("adminLayers.status"),
            value: statusLabel,
        },
        ...auditRows,
        ...(isArchived
            ? [
                  {
                      key: "archivedAt",
                      label: t("adminLayers.archivedAt"),
                      value: layer.archivedAt
                          ? formatDateMilitary(layer.archivedAt, timezone)
                          : "-",
                  },
                  {
                      key: "archivedBy",
                      label: t("adminLayers.archivedBy"),
                      value: archivedByLabel,
                  },
              ]
            : []),
        defaultVisibleRow,
        ...(layer.description
            ? [{ key: "description", label: t("common.description"), value: layer.description }]
            : []),
    ];

    const essentials = <DetailTable rows={rows} compact />;

    const auditSection = useAuditHistorySection("LAYER", layer.id);

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.layer")),
        {
            key: "members",
            label: t("adminGroups.members"),
            icon: <TbUserFilled />,
            content: (
                <LayerMembersSection
                    layerId={layer.id}
                    canAssignOwner
                    onLockedChange={setMembersLocked}
                />
            ),
        },
        {
            key: "groups",
            label: t("adminLayerGroups.userGroups"),
            icon: <TbUsers />,
            content: <LayerUserGroupsSection layerId={layer.id} />,
        },
        {
            key: "layerGroups",
            label: t("adminLayerGroups.layerGroups"),
            icon: <TypeIndicator type="layer-group" />,
            content: (
                <LayerLayerGroupsSection
                    layerId={layer.id}
                    accessScope={layer.accessScope}
                    readOnly={isArchived}
                />
            ),
        },
        {
            key: "dataSources",
            label: t("adminNav.dataSource"),
            icon: <FaServer />,
            content: <LayerDataSourcesSection layerId={layer.id} assignmentRole="OWNER" />,
        },
    ];

    if (auditSection) sections.push(auditSection);

    return (
        <DrawerSideNavLayout
            name={layer.name || t("adminLayers.unnamedLayer")}
            badgeLabel={t("adminLayers.layer")}
            navLabel={t("adminNav.layers")}
            icon={<TypeIndicator type="layer" />}
            locked={membersLocked}
            actions={
                !isArchived && !editing && !membersLocked ? (
                    <>
                        <DrawerToolbarButton
                            label={t("adminActions.editLayer")}
                            icon={<FaPencilAlt />}
                            onClick={toEdit}
                        />
                        <DrawerToolbarButton
                            label={t("layers.importFile")}
                            icon={<FaUpload />}
                            onClick={() => onOpenModal({ type: "importFile", layerId: layer.id })}
                        />
                        <DrawerToolbarButton
                            label={t("layers.duplicateLayer")}
                            icon={<FaCopy />}
                            onClick={() => onOpenModal({ type: "duplicateLayer", layer })}
                        />
                        <DrawerToolbarButton
                            label={t("common.export")}
                            icon={<FaDownload />}
                            onClick={() =>
                                onOpenModal({
                                    type: "exportLayers",
                                    subjects: [{ id: layer.id, name: layer.name }],
                                })
                            }
                        />
                        <DrawerToolbarButton
                            label={t("common.archive")}
                            icon={<FaArchive />}
                            onClick={() => onOpenModal({ type: "deleteLayer", layer })}
                        />
                    </>
                ) : undefined
            }
            sections={sections}
            editContent={
                editing ? <AdminLayerEditContent layer={layer} onDone={toView} /> : undefined
            }
        />
    );
}
