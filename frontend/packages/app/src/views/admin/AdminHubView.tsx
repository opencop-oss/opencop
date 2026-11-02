// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import {
    TbUsers,
    TbStack2,
    TbServer,
    TbPhoto,
    TbSpeakerphone,
    TbToggleRight,
    TbPlug,
    TbTransform,
} from "react-icons/tb";
import { Hub, HubCard } from "@opencop/ui";
import { BreadcrumbLayout } from "~/layouts/BreadcrumbLayout";
import { AdminBreadcrumbs } from "~/features/admin/AdminBreadcrumbs";
import { AdminBuildFooter } from "~/features/admin/AdminBuildFooter";

const cards = [
    { key: "users", titleKey: "adminNav.users" as const, icon: <TbUsers />, path: "/admin/users" },
    {
        key: "layers",
        titleKey: "adminNav.layers" as const,
        icon: <TbStack2 />,
        path: "/admin/layers",
    },
    {
        key: "data-sources",
        titleKey: "adminNav.dataSource" as const,
        icon: <TbServer />,
        path: "/admin/data-sources",
    },
    {
        key: "mappers",
        titleKey: "adminNav.mappers" as const,
        icon: <TbTransform />,
        path: "/admin/mappers",
    },
    {
        key: "imagery",
        titleKey: "adminNav.imagery" as const,
        icon: <TbPhoto />,
        path: "/admin/imagery",
    },
    {
        key: "banners",
        titleKey: "adminNav.banners" as const,
        icon: <TbSpeakerphone />,
        path: "/admin/banners",
    },
    {
        key: "runtime-features",
        titleKey: "adminNav.runtimeFeatures" as const,
        icon: <TbToggleRight />,
        path: "/admin/runtime-features",
    },
    {
        key: "plugins",
        titleKey: "adminNav.plugins" as const,
        icon: <TbPlug />,
        path: "/admin/plugins",
    },
];

export function AdminHubView() {
    return (
        <BreadcrumbLayout
            titleKey="adminPanel.title"
            defaultSubtitleKey="adminPanel.subtitleHub"
            componentName="AdminPanel"
            subNavigation={<AdminBreadcrumbs />}
        />
    );
}

export function AdminHubCards() {
    const { t } = useTranslation();
    const navigate = useNavigate();

    return (
        <Box flex="1" minH="0" overflowY="auto" mx={-1} px={1} pb={4}>
            <Hub>
                {cards.map(({ key, titleKey, icon, path }) => (
                    <HubCard
                        key={key}
                        title={t(titleKey)}
                        icon={icon}
                        onClick={() => navigate(path)}
                    />
                ))}
            </Hub>
            <AdminBuildFooter />
        </Box>
    );
}
