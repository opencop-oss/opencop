// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { type ReactElement, useState } from "react";
import { useTranslation } from "react-i18next";
import { Box, HStack, IconButton, VStack } from "@chakra-ui/react";
import { FaPenToSquare, FaClock, FaStar } from "react-icons/fa6";
import type { IconType } from "react-icons";
import { Entity } from "./entity/Entity";
import { Designation } from "./Designation";
import { SidcProvider } from "./SidcContext";
import { Classification } from "./classification/Classification";
import { ToolEditorShell } from "./ToolEditorShell";
import { FavoritesTab } from "./FavoritesTab";
import { RecentsTab } from "./RecentsTab";
import { useSymbolHistory } from "./symbol-history.store";
import type { SIDC } from "@opencop/symbology";

export interface SymbolEditorProps {
    defaultValue?: SIDC;
    onChange?: (sidc: SIDC) => void;
}

type SymbolTab = "editor" | "recent" | "favorites";

interface TabDef {
    id: SymbolTab;
    labelKey:
        | "symbology:symbolHistory.editor"
        | "symbology:symbolHistory.recent"
        | "symbology:symbolHistory.favorites";
    Icon: IconType;
}

const TABS: TabDef[] = [
    { id: "editor", labelKey: "symbology:symbolHistory.editor", Icon: FaPenToSquare },
    { id: "recent", labelKey: "symbology:symbolHistory.recent", Icon: FaClock },
    { id: "favorites", labelKey: "symbology:symbolHistory.favorites", Icon: FaStar },
];

/** Uncontrolled editor for SIDC. */
export function SymbolEditor(props: SymbolEditorProps): ReactElement {
    const { t } = useTranslation(["translation", "symbology"]);
    const [activeTab, setActiveTab] = useState<SymbolTab>("editor");

    const {
        favorites,
        recents,
        removeFavorite,
        promoteRecentToFavorite,
        removeFavoriteByCode,
        updateRecentLabel,
    } = useSymbolHistory();

    return (
        <SidcProvider {...props}>
            <ToolEditorShell title={t("symbology:symbolWizard.symbolEditor")}>
                <HStack gap="0" pos="relative" justifyContent="space-between" width="100%">
                    <Box
                        pos="absolute"
                        w="1/3"
                        h="2px"
                        bg="active"
                        bottom="-2px"
                        left={`calc(33.333% * ${TABS.findIndex((tab) => tab.id === activeTab)})`}
                        transition="left .1s ease-out"
                    />
                    {TABS.map((tab) => (
                        <IconButton
                            key={tab.id}
                            size="xs"
                            w="1/3"
                            flex="1"
                            letterSpacing="1px"
                            transition="all .2s ease-out"
                            boxSizing="content-box"
                            fontWeight="bold"
                            onClick={() => setActiveTab(tab.id)}
                            color={activeTab === tab.id ? "active" : "fg.muted"}
                            aria-label={t(tab.labelKey)}
                        >
                            <tab.Icon />
                            {t(tab.labelKey).toUpperCase()}
                        </IconButton>
                    ))}
                </HStack>

                <Box py="4" w="full">
                    {activeTab === "editor" && (
                        <VStack w="full" gap="6" align="start">
                            <Classification />
                            <Entity />
                            <Designation />
                        </VStack>
                    )}
                    {activeTab === "recent" && (
                        <RecentsTab
                            recents={recents}
                            favorites={favorites}
                            onPromoteRecentToFavorite={promoteRecentToFavorite}
                            onRemoveFavoriteByCode={removeFavoriteByCode}
                            onUpdateRecentLabel={updateRecentLabel}
                            historyKeyPrefix="symbology:symbolHistory"
                        />
                    )}
                    {activeTab === "favorites" && (
                        <FavoritesTab
                            favorites={favorites}
                            onRemoveFavorite={removeFavorite}
                            historyKeyPrefix="symbology:symbolHistory"
                        />
                    )}
                </Box>
            </ToolEditorShell>
        </SidcProvider>
    );
}
