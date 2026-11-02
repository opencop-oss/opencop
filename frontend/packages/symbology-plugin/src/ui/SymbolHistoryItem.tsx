// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, IconButton } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { LuX, LuStar } from "react-icons/lu";
import { FaStar } from "react-icons/fa6";
import { Renderer, type SIDC } from "@opencop/symbology";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { HistoryEntry } from "./history.util";
import { parseSIDC } from "./history.util";

interface SymbolHistoryItemProps {
    config: HistoryEntry;
    onSelect: (sidc: SIDC, modifiers: Record<string, string>) => void;
    onRemove?: (id: string) => void;
    onAddToFavorites?: (id: string) => void;
    onRemoveFromFavorites?: (id: string) => void;
    showRemove?: boolean;
    showAddToFavorites?: boolean;
    isFavorited?: boolean;
    isSelected?: boolean;
}

export function SymbolHistoryItem({
    config,
    onSelect,
    onRemove,
    onAddToFavorites,
    onRemoveFromFavorites,
    showRemove = false,
    showAddToFavorites = false,
    isFavorited = false,
    isSelected = false,
}: SymbolHistoryItemProps) {
    const { t } = useTranslation(["translation", "symbology"]);
    const [isHovered, setIsHovered] = useState(false);
    const sidc = useMemo(() => parseSIDC(config.sidc), [config.sidc]);
    const imgSrc = useMemo(
        () => (sidc ? (Renderer.renderPreview(sidc.toString()) ?? "") : ""),
        [sidc],
    );

    if (!sidc) return null;

    const label = config.label ?? sidc.toString().slice(0, 8);

    return (
        <Box
            position="relative"
            display="inline-block"
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
        >
            <Tooltip content={label}>
                <Box
                    as="button"
                    width="48px"
                    height="48px"
                    borderWidth="2px"
                    borderRadius="md"
                    cursor="pointer"
                    display="flex"
                    alignItems="center"
                    justifyContent="center"
                    bg="bg"
                    transition="all 0.15s"
                    borderColor={isSelected ? "fg" : "border"}
                    boxShadow={isSelected ? "0 0 0 1px {colors.fg}" : undefined}
                    _hover={{
                        bg: "bg.subtle",
                        borderColor: isSelected ? "fg" : "border.emphasized",
                        transform: "scale(1.05)",
                    }}
                    _active={{
                        transform: "scale(0.98)",
                    }}
                    onClick={() => onSelect(sidc, config.modifiers)}
                >
                    {imgSrc && (
                        <img
                            src={imgSrc}
                            alt={label}
                            style={{ maxWidth: "40px", maxHeight: "40px" }}
                        />
                    )}
                </Box>
            </Tooltip>
            {showRemove && onRemove && isHovered && (
                <IconButton
                    aria-label={t("symbology:symbolHistory.removeFavorite")}
                    size="2xs"
                    position="absolute"
                    top="-6px"
                    right="-6px"
                    variant="plain"
                    p="0"
                    onClick={(e) => {
                        e.stopPropagation();
                        onRemove(config.id);
                    }}
                >
                    <LuX />
                </IconButton>
            )}
            {showAddToFavorites && (isFavorited || isHovered) && (
                <IconButton
                    aria-label={
                        isFavorited
                            ? t("symbology:symbolHistory.removeFavorite")
                            : t("symbology:symbolHistory.addToFavorites")
                    }
                    size="2xs"
                    position="absolute"
                    top="-6px"
                    right="-6px"
                    variant="plain"
                    onClick={(e) => {
                        e.stopPropagation();
                        if (isFavorited && onRemoveFromFavorites) {
                            onRemoveFromFavorites(config.id);
                        } else if (onAddToFavorites) {
                            onAddToFavorites(config.id);
                        }
                    }}
                >
                    {isFavorited ? <FaStar /> : <LuStar />}
                </IconButton>
            )}
        </Box>
    );
}
