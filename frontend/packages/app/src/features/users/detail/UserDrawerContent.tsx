// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Badge, Box, Flex } from "@chakra-ui/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { User } from "@opencop/api";
import { DetailTable, DrawerSideNavLayout } from "@opencop/ui";
import type { DrawerSideNavSection } from "@opencop/ui";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { FaLayerGroup, FaServer } from "react-icons/fa";
import { LuShield, LuShieldOff } from "react-icons/lu";
import { TbUser } from "react-icons/tb";
import { formatDateOnly } from "~/date.util";
import { useUserTimezone } from "~/features/preferences/use-user-timezone.hook";
import { DrawerToolbarButton } from "~/features/drawers/DrawerToolbarButton";
import { createOverviewSection } from "~/features/drawers/relation-section.util";
import { userMutations } from "~/features/users/users.queries";
import { UserDataSourcesSection } from "./UserDataSourcesSection";
import { UserLayersSection } from "./UserLayersSection";

interface UserDrawerContentProps {
    user: User;
}

export function UserDrawerContent({ user }: UserDrawerContentProps): ReactElement {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const timezone = useUserTimezone();

    const { mutate: toggleAdmin, isPending } = useMutation(userMutations.updateAdmin(queryClient));

    const statusBadge = user.active ? (
        <Badge colorPalette="green">{t("adminUsers.active")}</Badge>
    ) : (
        <Badge colorPalette="gray" variant="subtle">
            {t("adminUsers.inactive")}
        </Badge>
    );

    const rows = [
        { key: "username", label: t("adminUsers.username"), value: user.username },
        { key: "email", label: t("adminUsers.email"), value: user.email },
        { key: "status", label: t("adminUsers.status"), value: statusBadge },
        {
            key: "createdAt",
            label: t("adminUsers.createdAt"),
            value: user.createdAt ? formatDateOnly(user.createdAt, timezone) : "-",
        },
    ];

    const essentials = <DetailTable rows={rows} compact />;

    const sections: DrawerSideNavSection[] = [
        createOverviewSection(t, essentials, t("drawerSections.entity.user")),
        {
            key: "layers",
            label: t("adminUsers.manageLayers"),
            icon: <FaLayerGroup />,
            content: <UserLayersSection userId={user.id} />,
        },
        {
            key: "dataSources",
            label: t("adminUsers.manageDataSources"),
            icon: <FaServer />,
            content: <UserDataSourcesSection userId={user.id} />,
        },
    ];

    return (
        <DrawerSideNavLayout
            name={user.username}
            badgeLabel={user.admin ? t("adminUsers.admin") : t("adminUsers.general")}
            navLabel={t("adminNav.users")}
            icon={
                user.admin ? (
                    <Box position="relative" display="inline-flex">
                        <TbUser />
                        <Flex
                            position="absolute"
                            bottom="-2px"
                            right="-3px"
                            align="center"
                            justify="center"
                            boxSize="15px"
                            borderRadius="full"
                            bg="bg.elevated"
                            color="type.info"
                            fontSize="11px"
                            role="img"
                            aria-label={t("adminUsers.admin")}
                        >
                            <LuShield />
                        </Flex>
                    </Box>
                ) : (
                    <TbUser />
                )
            }
            actions={
                <DrawerToolbarButton
                    label={user.admin ? t("adminUsers.revokeAdmin") : t("adminUsers.makeAdmin")}
                    icon={user.admin ? <LuShieldOff /> : <LuShield />}
                    destructive={user.admin}
                    loading={isPending}
                    onClick={() => toggleAdmin({ userId: user.id, isAdmin: !user.admin })}
                />
            }
            sections={sections}
        />
    );
}
