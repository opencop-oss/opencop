// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Button, IconButton, Box } from "@chakra-ui/react";
import { FaSync } from "react-icons/fa";
import { useState } from "react";

const MIN_SPIN_MS = 1000;
import { useTranslation } from "react-i18next";
import { Tooltip } from "../overlays/Tooltip.tsx";

interface RefreshButtonProps {
    onRefresh: () => Promise<unknown>;
    tooltip?: string;
    isRefreshing?: boolean;
    size?: "xs" | "sm" | "md" | "lg" | "xl";
    variant?: "outline" | "ghost" | "plain" | "solid" | "subtle" | "toolbar";
    colorPalette?: string;
    active?: "icon" | "iconHover" | "full";
    showLabel?: boolean;
}

export function RefreshButton({
    onRefresh,
    tooltip,
    isRefreshing: externalRefreshing,
    size = "md",
    variant: variantProp,
    colorPalette: colorPaletteProp,
    active: activeProp,
    showLabel,
}: RefreshButtonProps) {
    const { t } = useTranslation();
    const resolvedTooltip = tooltip ?? t("common.refresh");
    const [spinning, setSpinning] = useState(false);

    const isRefreshing = spinning || (externalRefreshing ?? false);

    const handleClick = () => {
        if (isRefreshing) return;

        setSpinning(true);
        setTimeout(() => setSpinning(false), MIN_SPIN_MS);
        onRefresh();
    };

    const icon = (
        <Box animation={isRefreshing ? "spin 1s linear infinite" : undefined}>
            <FaSync />
        </Box>
    );

    // showLabel renders a Button with a visible label — colorPalette is not applied on this path.
    if (showLabel) {
        return (
            <Button
                onClick={handleClick}
                size={size}
                variant={variantProp ?? "toolbar"}
                {...(activeProp ? { active: activeProp } : {})}
                cursor={isRefreshing ? "default" : undefined}
            >
                {icon}
                {resolvedTooltip}
            </Button>
        );
    }

    return (
        <Tooltip content={resolvedTooltip}>
            <IconButton
                aria-label={resolvedTooltip}
                onClick={handleClick}
                size={size}
                variant={variantProp ?? "outline"}
                {...(activeProp ? { active: activeProp } : {})}
                colorPalette={colorPaletteProp ?? "gray"}
                cursor={isRefreshing ? "default" : undefined}
            >
                {icon}
            </IconButton>
        </Tooltip>
    );
}
