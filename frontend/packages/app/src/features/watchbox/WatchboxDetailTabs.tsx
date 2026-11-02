// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack, IconButton } from "@chakra-ui/react";
import { MdOutlineNotifications } from "react-icons/md";
import { RiInformationFill } from "react-icons/ri";
import { useTranslation } from "react-i18next";
import type { ReactNode } from "react";

export type WatchboxDetailTab = "information" | "notifications";

type TabDef = {
    id: WatchboxDetailTab;
    labelKey: "watchbox.detail.info" | "watchbox.detail.notifications";
    icon: ReactNode;
};

const TABS: TabDef[] = [
    { id: "information", labelKey: "watchbox.detail.info", icon: <RiInformationFill /> },
    {
        id: "notifications",
        labelKey: "watchbox.detail.notifications",
        icon: <MdOutlineNotifications />,
    },
];

export type WatchboxDetailTabsProps = {
    activeTab: WatchboxDetailTab;
    onTabChange: (tab: WatchboxDetailTab) => void;
};

export function WatchboxDetailTabs({ activeTab, onTabChange }: WatchboxDetailTabsProps) {
    const { t } = useTranslation();

    return (
        <HStack gap="0" pos="relative">
            <Box
                pos="absolute"
                w={`${100 / TABS.length}%`}
                h="2px"
                bg="active"
                bottom="-2px"
                left={`calc(${100 / TABS.length}% * ${TABS.findIndex((tab) => tab.id === activeTab)})`}
                transition="left .1s ease-out"
            />
            {TABS.map((tab) => (
                <IconButton
                    key={tab.id}
                    size="xs"
                    w={`${100 / TABS.length}%`}
                    flex="1"
                    letterSpacing="1px"
                    transition="all .2s ease-out"
                    boxSizing="content-box"
                    fontWeight="bold"
                    onClick={() => onTabChange(tab.id)}
                    color={activeTab === tab.id ? "active" : "fg.muted"}
                    aria-label={t(tab.labelKey)}
                    aria-pressed={activeTab === tab.id}
                >
                    {tab.icon}
                    {t(tab.labelKey).toUpperCase()}
                </IconButton>
            ))}
        </HStack>
    );
}
