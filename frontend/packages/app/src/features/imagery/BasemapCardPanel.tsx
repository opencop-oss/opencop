// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, HStack } from "@chakra-ui/react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { MapTile } from "@opencop/api";
import type { BasemapRow } from "./basemap.types";
import { BasemapCard } from "./BasemapCard";

interface BasemapCardPanelProps {
    open: boolean;
    rows: BasemapRow[];
    selectedRowId: string | null;
    onSelect: (tile: MapTile) => void;
}

export function BasemapCardPanel({ open, rows, selectedRowId, onSelect }: BasemapCardPanelProps) {
    const { t } = useTranslation();
    const [hasEverOpened, setHasEverOpened] = useState(open);
    // Latch: keep children mounted once opened so the close animation can play.
    // Called during render (not in useEffect) to avoid a flicker frame on open.
    if (open && !hasEverOpened) {
        setHasEverOpened(true);
    }

    return (
        <Box
            style={{
                maxHeight: open ? "130px" : "0px",
                overflow: "hidden",
                transition: "max-height 0.25s ease",
            }}
            pointerEvents={open ? "auto" : "none"}
        >
            {hasEverOpened && (
                <Box
                    w="full"
                    bg="bg.glass"
                    backdropFilter="blur(4px)"
                    boxShadow="0 4px 12px rgba(0,0,0,0.15)"
                    borderBottomColor="border"
                    borderBottomWidth="1px"
                    overflow="hidden"
                    style={{
                        opacity: open ? 1 : 0,
                        transition: "opacity 0.25s ease",
                    }}
                >
                    <HStack
                        overflowX="auto"
                        gap="2"
                        px="2"
                        py="2"
                        align="flex-start"
                        fontSize="xs"
                        color="fg.muted"
                        css={{
                            scrollbarWidth: "thin",
                            scrollbarColor: "var(--chakra-colors-border-muted) transparent",
                        }}
                    >
                        {rows.length === 0
                            ? t("common.noResults")
                            : rows.map((row) => (
                                  <BasemapCard
                                      key={row.id}
                                      name={row.name}
                                      kindLabel={row.kindLabel}
                                      description={row.description}
                                      tile={row.tile}
                                      selected={row.id === selectedRowId}
                                      onSelect={onSelect}
                                  />
                              ))}
                    </HStack>
                </Box>
            )}
        </Box>
    );
}
