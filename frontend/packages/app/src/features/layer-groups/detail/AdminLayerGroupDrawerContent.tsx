// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaLayerGroup, FaPencilAlt, FaTrash } from "react-icons/fa";
import { TbUserFilled, TbUsers } from "react-icons/tb";
import { DetailTable, DrawerSideNavLayout } from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { useAuditHistorySection } from "~/features/drawers/use-audit-history-section.hook";
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import { formatDateOnly } from "~/date.util";
import type { AdminLayerGroupModalState } from "../AdminLayerGroupDrawer";
import type { LayerGroup } from "../layer-groups.queries";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { TypeIndicator } from "~/features/layers/TypeIndicator";
import { AdminLayerGroupEditContent } from "./AdminLayerGroupEditContent";
import { LayerGroupMembersSection } from "./LayerGroupMembersSection";
import { LayerGroupUserGroupsSection } from "./LayerGroupUserGroupsSection";
import { LayerGroupLayersSection } from "./LayerGroupLayersSection";

interface AdminLayerGroupDrawerContentProps {
    group: LayerGroup;
    onOpenModal: (modal: AdminLayerGroupModalState) => void;
}

export function AdminLayerGroupDrawerContent({
    group,
    onOpenModal,
}: AdminLayerGroupDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";

    const rows = [
        { key: "name", label: t("adminLayerGroups.name"), value: group.name },
        {
            key: "accessScope",
            label: t("adminLayers.accessScope"),
            value: <ScopeIndicator scope={group.accessScope} />,
        },
        {
            key: "description",
            label: t("adminLayerGroups.description"),
            value: group.description ?? "-",
        },
        {
            key: "createdAt",
            label: t("adminLayerGroups.createdAt"),
            value: group.createdAt ? formatDateOnly(group.createdAt) : "-",
        },
    ];

    const essentials = <DetailTable rows={rows} compact />;

    const auditSection = useAuditHistorySection("LAYER_GROUP", group.id);

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.layerGroup")),
        {
            key: "members",
            label: t("adminLayerGroups.members"),
            icon: <TbUserFilled />,
            content: <LayerGroupMembersSection groupId={group.id} />,
        },
        {
            key: "userGroups",
            label: t("adminLayerGroups.userGroups"),
            icon: <TbUsers />,
            content: <LayerGroupUserGroupsSection groupId={group.id} />,
        },
        {
            key: "layers",
            label: t("adminLayerGroups.layers"),
            icon: <FaLayerGroup />,
            content: (
                <LayerGroupLayersSection
                    groupId={group.id}
                    groupName={group.name}
                    accessScope={group.accessScope}
                    layerScope="admin"
                />
            ),
        },
    ];

    if (auditSection) sections.push(auditSection);

    return (
        <DrawerSideNavLayout
            name={group.name}
            badgeLabel={t("adminLayerGroups.layerGroup")}
            navLabel={t("adminNav.layers")}
            icon={<TypeIndicator type="layer-group" />}
            actions={
                editing ? undefined : (
                    <>
                        <DrawerToolbarButton
                            label={t("adminLayerGroups.editGroup")}
                            icon={<FaPencilAlt />}
                            onClick={toEdit}
                        />
                        <DrawerToolbarButton
                            label={t("adminLayerGroups.deleteGroup")}
                            icon={<FaTrash />}
                            destructive
                            onClick={() => onOpenModal({ type: "deleteGroup", group })}
                        />
                    </>
                )
            }
            sections={sections}
            editContent={
                editing ? <AdminLayerGroupEditContent group={group} onDone={toView} /> : undefined
            }
        />
    );
}
