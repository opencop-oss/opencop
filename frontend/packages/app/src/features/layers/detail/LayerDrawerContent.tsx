// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import {
    DetailTable,
    DrawerSideNavLayout,
    ResourceTypeBadge,
    useDefaultVisibleRow,
} from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaArchive, FaCopy, FaDownload, FaPencilAlt, FaServer, FaUpload } from "react-icons/fa";
import { TbUserFilled, TbUsers } from "react-icons/tb";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import { useAuditRows } from "~/features/drawers/use-audit-rows.hook";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";
import { PanelExclusionDrawerButton } from "~/features/filters/PanelExclusionDrawerButton";
import { layerMutations } from "../layers.queries";
import { LAYER_ROLE_CONFIG } from "../layers.util";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { TypeIndicator } from "~/features/layers/TypeIndicator";
import { LayerEditContent } from "./LayerEditContent";
import { LayerMembersSection } from "./LayerMembersSection";
import { LayerUserGroupsSection } from "./LayerUserGroupsSection";
import { LayerLayerGroupsSection } from "./LayerLayerGroupsSection";
import { LayerDataSourcesSection } from "./LayerDataSourcesSection";
import type { LayerModalState } from "../LayerDrawer";

interface LayerDrawerContentProps {
    layer: MyLayerInfo;
    onOpenModal: (modal: LayerModalState) => void;
    isArchived: boolean;
}

export function LayerDrawerContent({
    layer,
    onOpenModal,
    isArchived,
}: LayerDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const timezone = useUserTimezone();
    const queryClient = useQueryClient();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";
    // Locked while the members section's bulk change-role panel is open.
    const [membersLocked, setMembersLocked] = useState(false);

    const isOwner = layer.role === "OWNER";
    const canManageUsers = isOwner || layer.role === "ADMIN";
    const relationsReadOnly = !canManageUsers || isArchived;
    // Group ↔ layer assignment is an admin-only backend operation, so the Groups
    // section is view-only for non-admins even when they own the layer.
    const isAdmin = useIsAdmin();

    const { mutate: patchLayer } = useMutation(layerMutations.patch(queryClient));

    const defaultVisibleRow = useDefaultVisibleRow({
        checked: layer.defaultVisible,
        disabled: !canManageUsers || isArchived,
        onCheckedChange: (checked) => patchLayer({ id: layer.layerId, defaultVisible: checked }),
    });

    const auditRows = useAuditRows(layer, timezone, "layer");

    const rows = [
        { key: "name", label: t("layers.name"), value: layer.layerName },
        {
            key: "role",
            label: t("layers.role"),
            value: (
                <ResourceTypeBadge
                    label={layer.role}
                    colorPalette={LAYER_ROLE_CONFIG[layer.role].colorPalette}
                />
            ),
        },
        {
            key: "accessScope",
            label: t("adminLayers.accessScope"),
            value: <ScopeIndicator scope={layer.accessScope} />,
        },
        ...auditRows,
        defaultVisibleRow,
    ];

    const essentials = <DetailTable rows={rows} compact />;

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.layer")),
        {
            key: "members",
            label: t("layers.manageMembers"),
            icon: <TbUserFilled />,
            content: (
                <LayerMembersSection
                    layerId={layer.layerId}
                    readOnly={relationsReadOnly}
                    canAssignOwner={isOwner}
                    onLockedChange={setMembersLocked}
                />
            ),
        },
        {
            key: "groups",
            label: t("adminLayerGroups.userGroups"),
            icon: <TbUsers />,
            content: (
                <LayerUserGroupsSection layerId={layer.layerId} readOnly={!isAdmin || isArchived} />
            ),
        },
        {
            key: "layerGroups",
            label: t("adminLayerGroups.layerGroups"),
            icon: <TypeIndicator type="layer-group" />,
            // Always view-only here, even for admins: group membership is managed from the admin
            // layers page and the layer group's own drawer, not from My Layers.
            content: (
                <LayerLayerGroupsSection
                    layerId={layer.layerId}
                    accessScope={layer.accessScope}
                    readOnly
                />
            ),
        },
        {
            key: "dataSources",
            label: t("layers.viewDataSources"),
            icon: <FaServer />,
            content: (
                <LayerDataSourcesSection
                    layerId={layer.layerId}
                    assignmentRole={isAdmin ? "OWNER" : "ADMIN"}
                    readOnly={relationsReadOnly}
                />
            ),
        },
    ];

    return (
        <DrawerSideNavLayout
            name={layer.layerName}
            badgeLabel={t("adminLayers.layer")}
            navLabel={t("adminNav.layers")}
            icon={<TypeIndicator type="layer" />}
            locked={membersLocked}
            actions={
                !isArchived && !editing ? (
                    <>
                        <PanelExclusionDrawerButton id={layer.layerId} kind="layer" />
                        {!membersLocked && (
                            <>
                                {isOwner && (
                                    <DrawerToolbarButton
                                        label={t("adminActions.editLayer")}
                                        icon={<FaPencilAlt />}
                                        onClick={toEdit}
                                    />
                                )}
                                <DrawerToolbarButton
                                    label={t("layers.importFile")}
                                    icon={<FaUpload />}
                                    disabled={!canManageUsers}
                                    disabledReason={t("layers.adminOnlyImport")}
                                    onClick={() =>
                                        onOpenModal({ type: "importFile", layerId: layer.layerId })
                                    }
                                />
                                <DrawerToolbarButton
                                    label={t("layers.duplicateLayer")}
                                    icon={<FaCopy />}
                                    disabled={!canManageUsers}
                                    disabledReason={t("layers.adminOnlyDuplicate")}
                                    onClick={() => onOpenModal({ type: "duplicateLayer", layer })}
                                />
                                <DrawerToolbarButton
                                    label={t("common.export")}
                                    icon={<FaDownload />}
                                    onClick={() =>
                                        onOpenModal({
                                            type: "exportLayers",
                                            subjects: [
                                                { id: layer.layerId, name: layer.layerName },
                                            ],
                                        })
                                    }
                                />
                                <DrawerToolbarButton
                                    label={t("common.archive")}
                                    icon={<FaArchive />}
                                    disabled={!isOwner}
                                    disabledReason={t("layers.ownerOnlyDelete")}
                                    onClick={() => onOpenModal({ type: "deleteLayer", layer })}
                                />
                            </>
                        )}
                    </>
                ) : undefined
            }
            sections={sections}
            editContent={editing ? <LayerEditContent layer={layer} onDone={toView} /> : undefined}
        />
    );
}
