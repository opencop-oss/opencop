// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack, IconButton } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";

export type EventsTab = "information" | "media" | "remarks" | "history";
import { RiInformationFill } from "react-icons/ri";
import { MdOutlineAccessTimeFilled, MdPermMedia } from "react-icons/md";
import { IoMdText } from "react-icons/io";

interface ObjectTabsProps {
    activeTab: EventsTab;
    onTabChange: (tab: EventsTab) => void;
}

interface TabDef {
    id: EventsTab;
    labelKey: "events.info" | "events.media" | "events.remarks" | "events.history";
    icon: React.ReactNode;
}

const TABS: TabDef[] = [
    { id: "information", labelKey: "events.info", icon: <RiInformationFill /> },
    { id: "media", labelKey: "events.media", icon: <MdPermMedia /> },
    { id: "remarks", labelKey: "events.remarks", icon: <IoMdText /> },
    { id: "history", labelKey: "events.history", icon: <MdOutlineAccessTimeFilled /> },
];

export function EventsTabs({ activeTab, onTabChange }: ObjectTabsProps) {
    const { t } = useTranslation();

    return (
        <HStack gap="0" pos="relative">
            <Box
                pos="absolute"
                w="1/4"
                h="2px"
                bg="active"
                bottom="-2px"
                left={`calc(25% * ${TABS.findIndex((tab) => tab.id === activeTab)})`}
                transition="left .1s ease-out"
            />
            {TABS.map((tab) => (
                <IconButton
                    key={tab.id}
                    size="xs"
                    w="1/4"
                    flex="1"
                    letterSpacing="1px"
                    transition="all .2s ease-out"
                    boxSizing="content-box"
                    fontWeight="bold"
                    onClick={() => onTabChange(tab.id)}
                    color={activeTab === tab.id ? "active" : "fg.muted"}
                    aria-label={t(tab.labelKey)}
                >
                    {tab.icon}
                    {t(tab.labelKey).toUpperCase()}
                </IconButton>
            ))}
        </HStack>
    );
}
