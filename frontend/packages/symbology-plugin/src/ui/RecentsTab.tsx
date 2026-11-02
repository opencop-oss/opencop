// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, useDisclosure, VStack } from "@chakra-ui/react";
import { useState, useMemo, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { SearchInput } from "@opencop/ui";
import type { SIDC } from "@opencop/symbology";
import type { HistoryEntry } from "./history.util";
import { filterHistoryEntries } from "./history.util";
import { SymbolHistoryItem } from "./SymbolHistoryItem";
import { useSidc } from "./SidcContext";
import { SymbolHistoryEmptyState } from "./SymbolHistoryEmptyState";
import { FavoriteLabelDialog } from "./FavoriteLabelDialog";

export interface RecentsTabProps {
    recents: HistoryEntry[];
    favorites: HistoryEntry[];
    onPromoteRecentToFavorite: (id: string, label?: string) => void;
    onRemoveFavoriteByCode: (sidc: string) => void;
    onUpdateRecentLabel: (sidc: string, label?: string) => void;
    historyKeyPrefix: "symbology:symbolHistory";
}

export function RecentsTab({
    recents,
    favorites,
    onPromoteRecentToFavorite,
    onRemoveFavoriteByCode,
    onUpdateRecentLabel,
    historyKeyPrefix,
}: RecentsTabProps) {
    const { t } = useTranslation(["translation", "symbology"]);
    const { sidc, setSidcWithModifiers } = useSidc();

    const { open, onOpen, onClose } = useDisclosure();
    const [label, setLabel] = useState("");
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [searchTerm, setSearchTerm] = useState("");

    const filteredRecents = useMemo(
        () => filterHistoryEntries(recents, searchTerm),
        [recents, searchTerm],
    );

    const handleAddToFavorites = useCallback(
        (id: string) => {
            const recent = recents.find((r) => r.id === id);
            setSelectedId(id);
            setLabel(recent?.label || "");
            onOpen();
        },
        [recents, onOpen],
    );

    const handleRemoveFromFavorites = useCallback(
        (id: string) => {
            const recent = recents.find((r) => r.id === id);
            if (recent) {
                onRemoveFavoriteByCode(recent.sidc);
            }
        },
        [recents, onRemoveFavoriteByCode],
    );

    const handleConfirm = useCallback(
        (confirmedLabel: string) => {
            if (selectedId) {
                const recent = recents.find((r) => r.id === selectedId);
                onPromoteRecentToFavorite(selectedId, confirmedLabel || undefined);
                if (recent && confirmedLabel) {
                    onUpdateRecentLabel(recent.sidc, confirmedLabel);
                }
                setLabel("");
                setSelectedId(null);
                onClose();
            }
        },
        [selectedId, recents, onPromoteRecentToFavorite, onUpdateRecentLabel, onClose],
    );

    const handleSelect = useCallback(
        (selectedSidc: SIDC, modifiers: Record<string, string>) => {
            setSidcWithModifiers(selectedSidc, modifiers);
        },
        [setSidcWithModifiers],
    );

    const isFavorite = useCallback(
        (sidcString: string) => favorites.some((f) => f.sidc === sidcString),
        [favorites],
    );

    const currentSidcString = sidc.toString();

    if (recents.length === 0) {
        return (
            <SymbolHistoryEmptyState
                message={t(`${historyKeyPrefix}.noRecents`)}
                hint={t(`${historyKeyPrefix}.noRecentsHint`)}
            />
        );
    }

    return (
        <>
            <VStack w="full" gap={3} align="start">
                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder={t(`${historyKeyPrefix}.searchRecents`)}
                    size="sm"
                />
                {filteredRecents.length === 0 ? (
                    <SymbolHistoryEmptyState message={t(`${historyKeyPrefix}.noSearchResults`)} />
                ) : (
                    <Box display="flex" gap={3} flexWrap="wrap">
                        {filteredRecents.map((config) => (
                            <SymbolHistoryItem
                                key={config.id}
                                config={config}
                                onSelect={handleSelect}
                                onAddToFavorites={handleAddToFavorites}
                                onRemoveFromFavorites={handleRemoveFromFavorites}
                                showAddToFavorites
                                isFavorited={isFavorite(config.sidc)}
                                isSelected={config.sidc === currentSidcString}
                            />
                        ))}
                    </Box>
                )}
            </VStack>

            <FavoriteLabelDialog
                open={open}
                onClose={onClose}
                onSubmit={handleConfirm}
                label={label}
                onLabelChange={setLabel}
                historyKeyPrefix={historyKeyPrefix}
            />
        </>
    );
}
