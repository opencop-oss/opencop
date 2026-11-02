// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, VStack } from "@chakra-ui/react";
import { useMemo, useState, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { SearchInput } from "@opencop/ui";
import type { SIDC } from "@opencop/symbology";
import type { HistoryEntry } from "./history.util";
import { filterHistoryEntries } from "./history.util";
import { SymbolHistoryItem } from "./SymbolHistoryItem";
import { useSidc } from "./SidcContext";
import { SymbolHistoryEmptyState } from "./SymbolHistoryEmptyState";

export interface FavoritesTabProps {
    favorites: HistoryEntry[];
    onRemoveFavorite: (id: string) => void;
    historyKeyPrefix: "symbology:symbolHistory";
}

export function FavoritesTab({ favorites, onRemoveFavorite, historyKeyPrefix }: FavoritesTabProps) {
    const { t } = useTranslation(["translation", "symbology"]);
    const { sidc, setSidcWithModifiers } = useSidc();
    const [searchTerm, setSearchTerm] = useState("");

    const filteredFavorites = useMemo(
        () => filterHistoryEntries(favorites, searchTerm),
        [favorites, searchTerm],
    );

    const handleSelect = useCallback(
        (selectedSidc: SIDC, modifiers: Record<string, string>) => {
            setSidcWithModifiers(selectedSidc, modifiers);
        },
        [setSidcWithModifiers],
    );

    const currentSidcString = sidc.toString();

    if (favorites.length === 0) {
        return (
            <SymbolHistoryEmptyState
                message={t(`${historyKeyPrefix}.noFavorites`)}
                hint={t(`${historyKeyPrefix}.noFavoritesHint`)}
            />
        );
    }

    return (
        <VStack w="full" gap={3} align="start">
            <SearchInput
                value={searchTerm}
                onChange={setSearchTerm}
                placeholder={t(`${historyKeyPrefix}.searchFavorites`)}
                size="sm"
            />
            {filteredFavorites.length === 0 ? (
                <SymbolHistoryEmptyState message={t(`${historyKeyPrefix}.noSearchResults`)} />
            ) : (
                <Box display="flex" gap={3} flexWrap="wrap">
                    {filteredFavorites.map((config) => (
                        <SymbolHistoryItem
                            key={config.id}
                            config={config}
                            onSelect={handleSelect}
                            onRemove={onRemoveFavorite}
                            showRemove
                            isSelected={config.sidc === currentSidcString}
                        />
                    ))}
                </Box>
            )}
        </VStack>
    );
}
