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
import { useViewMode } from "~/features/drawers/use-view-mode.hook";
import { formatDateOnly } from "~/date.util";
import type { MyLayerGroupModalState } from "../MyLayerGroupDrawer";
import type { MyLayerGroup } from "../layer-groups.queries";
import { getLayerGroupPermissions } from "../layer-groups.util";
import { ScopeIndicator } from "~/features/layers/ScopeIndicator";
import { TypeIndicator } from "~/features/layers/TypeIndicator";
import { AdminLayerGroupEditContent } from "./AdminLayerGroupEditContent";
import { LayerGroupMembersSection } from "./LayerGroupMembersSection";
import { LayerGroupUserGroupsSection } from "./LayerGroupUserGroupsSection";
import { LayerGroupLayersSection } from "./LayerGroupLayersSection";
import { useIsAdmin } from "~/features/auth/use-is-admin.hook";

interface MyLayerGroupDrawerContentProps {
    group: MyLayerGroup;
    onOpenModal: (modal: MyLayerGroupModalState) => void;
}

export function MyLayerGroupDrawerContent({
    group,
    onOpenModal,
}: MyLayerGroupDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";
    const isAdmin = useIsAdmin();
    const { canAttachLayers, canManageMembers, canEditGroup, canDeleteGroup } =
        getLayerGroupPermissions(group.containerRole, isAdmin);

    // The shared edit form expects the admin `LayerGroup` shape, so project the
    // `MyLayerGroup` down to it (same conversion the wrapper previously did).
    const groupAsLayerGroup = {
        id: group.id,
        name: group.name,
        description: group.description,
        accessScope: group.accessScope,
        createdAt: group.createdAt,
    };

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

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.layerGroup")),
        {
            key: "members",
            label: t("adminLayerGroups.members"),
            icon: <TbUserFilled />,
            content: <LayerGroupMembersSection groupId={group.id} readOnly={!canManageMembers} />,
        },
        {
            key: "userGroups",
            label: t("adminLayerGroups.userGroups"),
            icon: <TbUsers />,
            content: (
                <LayerGroupUserGroupsSection
                    groupId={group.id}
                    // Global admins bypass the server's role ceiling, so they get every option.
                    actorRole={isAdmin ? undefined : group.containerRole}
                    readOnly={!canManageMembers}
                />
            ),
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
                    layerScope={isAdmin ? "admin" : "my"}
                    readOnly={!canAttachLayers}
                />
            ),
        },
    ];

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
                            disabled={!canEditGroup}
                            disabledReason={t("adminLayerGroups.adminOrOwnerOnlyEdit")}
                            onClick={toEdit}
                        />
                        <DrawerToolbarButton
                            label={t("adminLayerGroups.deleteGroup")}
                            icon={<FaTrash />}
                            destructive
                            disabled={!canDeleteGroup}
                            disabledReason={t("adminLayerGroups.ownerOnlyDelete")}
                            onClick={() => onOpenModal({ type: "deleteGroup", group })}
                        />
                    </>
                )
            }
            sections={sections}
            editContent={
                editing && canEditGroup ? (
                    <AdminLayerGroupEditContent group={groupAsLayerGroup} onDone={toView} />
                ) : undefined
            }
        />
    );
}
