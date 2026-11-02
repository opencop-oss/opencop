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
import type { AdminGroupModalState } from "../AdminGroupDrawer";
import type { Group } from "../groups.queries";
import { AdminGroupEditContent } from "./AdminGroupEditContent";
import { GroupMembersSection } from "./GroupMembersSection";
import { GroupLayersSection } from "./GroupLayersSection";

interface AdminGroupDrawerContentProps {
    group: Group;
    onOpenModal: (modal: AdminGroupModalState) => void;
}

export function AdminGroupDrawerContent({
    group,
    onOpenModal,
}: AdminGroupDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const { mode, toView, toEdit } = useViewMode();
    const editing = mode === "edit";

    const rows = [
        { key: "name", label: t("adminGroups.name"), value: group.name },
        {
            key: "description",
            label: t("adminGroups.description"),
            value: group.description ?? "-",
        },
        {
            key: "createdAt",
            label: t("adminGroups.createdAt"),
            value: group.createdAt ? formatDateOnly(group.createdAt) : "-",
        },
    ];

    const essentials = <DetailTable rows={rows} compact />;

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.userGroup")),
        {
            key: "members",
            label: t("adminGroups.members"),
            icon: <TbUserFilled />,
            content: <GroupMembersSection groupId={group.id} />,
        },
        {
            key: "layers",
            label: t("adminGroups.layers"),
            icon: <FaLayerGroup />,
            content: <GroupLayersSection groupId={group.id} />,
        },
    ];

    return (
        <DrawerSideNavLayout
            name={group.name}
            badgeLabel={t("adminUsers.group")}
            navLabel={t("adminNav.groups")}
            icon={<TbUsers />}
            actions={
                editing ? undefined : (
                    <>
                        <DrawerToolbarButton
                            label={t("adminGroups.editGroup")}
                            icon={<FaPencilAlt />}
                            onClick={toEdit}
                        />
                        <DrawerToolbarButton
                            label={t("adminGroups.deleteGroup")}
                            icon={<FaTrash />}
                            destructive
                            onClick={() => onOpenModal({ type: "deleteGroup", group })}
                        />
                    </>
                )
            }
            sections={sections}
            editContent={
                editing ? <AdminGroupEditContent group={group} onDone={toView} /> : undefined
            }
        />
    );
}
