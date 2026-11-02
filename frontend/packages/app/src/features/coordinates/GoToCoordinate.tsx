// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Flex, IconButton, Input } from "@chakra-ui/react";
import { type ReactElement, useState } from "react";
import { useTranslation } from "react-i18next";
import { LuLocateFixed } from "react-icons/lu";
import { Tooltip } from "@opencop/ui";
import { parseCoordinate } from "@opencop/utils";
import { useMapStore } from "../map/core/map.store";
import { useViewActions } from "../map/core/slices/view/view.hook";

export function GoToCoordinate(): ReactElement {
    const { t } = useTranslation();
    const [value, setValue] = useState("");
    const [error, setError] = useState(false);
    const { flyTo } = useViewActions();

    const parsed = value.trim() ? parseCoordinate(value) : null;
    const isValid = parsed !== null;

    function handleGo() {
        if (!parsed) {
            setError(true);
            return;
        }
        setError(false);
        const { zoom: currentZoom, pitch, bearing } = useMapStore.getState();
        const zoom = Math.max(currentZoom ?? 1, 10);
        flyTo({
            center: parsed,
            zoom,
            pitch: pitch ?? 0,
            bearing: bearing ?? 0,
            duration: 1200,
        });
        setValue("");
    }

    return (
        <Flex alignItems="center" mx="auto" pl="4">
            <Input
                aria-label={t("coordinates.goToTooltip")}
                aria-invalid={error || undefined}
                size="xs"
                w={{ base: "36", lg: "56" }}
                h="7"
                fontFamily="mono"
                fontSize="2xs"
                placeholder={t("coordinates.placeholder")}
                value={value}
                onChange={(e) => {
                    setValue(e.target.value);
                    if (error) setError(false);
                }}
                onKeyDown={(e) => {
                    if (e.key === "Enter") handleGo();
                }}
                borderColor={error ? "red.500" : undefined}
                _focus={
                    error
                        ? {
                              borderColor: "red.500",
                              boxShadow: "0 0 6px 1px var(--shadow-color)",
                              shadowColor: "red.focusRing",
                          }
                        : undefined
                }
            />
            <Tooltip
                content={
                    isValid ? t("coordinates.goToTooltip") : t("coordinates.invalidCoordinate")
                }
            >
                <IconButton
                    size={{ base: "compact", lg: "md" }}
                    active="iconHover"
                    border="none"
                    aria-label={t("coordinates.goToTooltip")}
                    disabled={!isValid}
                    onClick={handleGo}
                >
                    <LuLocateFixed />
                </IconButton>
            </Tooltip>
        </Flex>
    );
}
