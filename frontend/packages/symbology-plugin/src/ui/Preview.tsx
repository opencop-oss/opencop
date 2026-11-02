// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, IconButton, useDisclosure } from "@chakra-ui/react";
import { useDeferredValue, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuStar } from "react-icons/lu";
import { FaStar } from "react-icons/fa6";
import { useSidc } from "./SidcContext";
import { Tooltip } from "@opencop/ui";
import { SIDCImage } from "./SIDCImage";
import { SIDCInput } from "./SIDCInput";
import { useHistory } from "./history.store";
import { useSymbolHistoryStore } from "./symbol-history.store";
import { FavoriteLabelDialog } from "./FavoriteLabelDialog";

export function Preview() {
    const { t } = useTranslation(["translation", "symbology"]);
    const { sidc, modifiers } = useSidc();
    const deferredSidc = useDeferredValue(sidc.toString());
    const light = "#8a8a8a";
    const dark = "#6e6e6e";
    const { open, onOpen, onClose } = useDisclosure();
    const [label, setLabel] = useState("");
    const sidcString = sidc.toString();

    const { addFavorite, removeFavoriteByCode, updateRecentLabel, favorites, recents } =
        useHistory(useSymbolHistoryStore);

    const isFavorited = useMemo(
        () => favorites.some((f) => f.sidc === sidcString),
        [favorites, sidcString],
    );
    const favoriteLabel = useMemo(
        () => favorites.find((f) => f.sidc === sidcString)?.label ?? "",
        [favorites, sidcString],
    );
    const recentLabel = useMemo(
        () => recents.find((r) => r.sidc === sidcString)?.label ?? "",
        [recents, sidcString],
    );

    const handleSaveFavorite = (confirmedLabel: string) => {
        addFavorite(sidc, modifiers, confirmedLabel || undefined);
        if (confirmedLabel) {
            updateRecentLabel(sidcString, confirmedLabel);
        }
        setLabel("");
        onClose();
    };

    const handleToggleFavorite = () => {
        if (isFavorited) {
            removeFavoriteByCode(sidcString);
        } else {
            // Pre-fill with existing label from either favorites or recents
            setLabel(favoriteLabel || recentLabel);
            onOpen();
        }
    };

    return (
        <>
            <Flex bg="bg.elevated" w="full" h="32" borderTopWidth="1px" p="2" gap="2">
                <SIDCImage
                    h="full"
                    aspectRatio="square"
                    sidc={deferredSidc}
                    backgroundImage={`
                        linear-gradient(45deg, ${light} 25%, transparent 25%),
                        linear-gradient(-45deg, ${light} 25%, transparent 25%),
                        linear-gradient(45deg, transparent 75%, ${light} 75%),
                        linear-gradient(-45deg, transparent 75%, ${light} 75%)
                    `}
                    backgroundColor={dark}
                    backgroundSize="20px 20px"
                    backgroundPosition="0 0, 0 10px, 10px -10px, -10px 0"
                />
                <SIDCInput />
                <Tooltip
                    content={
                        isFavorited
                            ? t("symbology:symbolHistory.removeFavorite")
                            : t("symbology:symbolHistory.saveFavorite")
                    }
                >
                    <IconButton
                        aria-label={
                            isFavorited
                                ? t("symbology:symbolHistory.removeFavorite")
                                : t("symbology:symbolHistory.saveFavorite")
                        }
                        variant="outline"
                        size="sm"
                        onClick={handleToggleFavorite}
                    >
                        {isFavorited ? <FaStar /> : <LuStar />}
                    </IconButton>
                </Tooltip>
            </Flex>

            <FavoriteLabelDialog
                open={open}
                onClose={onClose}
                onSubmit={handleSaveFavorite}
                label={label}
                onLabelChange={setLabel}
                historyKeyPrefix="symbology:symbolHistory"
            />
        </>
    );
}
