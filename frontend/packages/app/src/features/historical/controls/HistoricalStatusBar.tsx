// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, Flex, Icon, Text } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import { assertNever } from "assert-never";
import { type ReactElement, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { LuInfo, LuTriangleAlert } from "react-icons/lu";
import { type StatusTier } from "./use-historical-status-model.hook";

/** Height of the status-bar row; also reserved as a spacer in settings mode so
 * toggling the histogram settings doesn't resize the panel. */
export const STATUS_BAR_HEIGHT = "6"; // 24px
const STATUS_BULLET = "·";

/**
 * Underlined text-link button used inside the status bar for Retry / Dismiss /
 * Previous / Reset. The visual treatment is consistent across all four; only
 * `colorPalette` varies.
 */
function StatusBarAction({
    onClick,
    colorPalette,
    children,
    ariaLabel,
}: {
    onClick: () => void;
    colorPalette?: "red";
    children: ReactNode;
    ariaLabel?: string;
}): ReactElement {
    return (
        <Button
            size="xs"
            variant="plain"
            colorPalette={colorPalette}
            onClick={onClick}
            aria-label={ariaLabel}
            px="1"
            h="auto"
            minH="0"
            fontSize="xs"
            color={colorPalette ? undefined : "inherit"}
            textDecoration="underline"
        >
            {children}
        </Button>
    );
}

/**
 * Leading icon for a tier. A `switch` rather than a ternary chain so adding a
 * `StatusTier` variant is a compile error here instead of a silently iconless,
 * textless bar.
 */
function tierIcon(tier: StatusTier): ReactElement | null {
    switch (tier.kind) {
        case "actionable":
            return (
                <Icon
                    as={LuTriangleAlert}
                    boxSize="3.5"
                    color="inherit"
                    aria-hidden
                    focusable={false}
                />
            );
        case "loading":
        case "preparing":
        case "hint":
            return <Icon as={LuInfo} boxSize="3.5" color="inherit" aria-hidden focusable={false} />;
        case "empty":
            return null;
        default:
            return assertNever(tier);
    }
}

/** The bar's headline text for a tier, or null when the tier renders nothing. */
function tierPrimary(tier: StatusTier, t: TFunction): string | null {
    switch (tier.kind) {
        case "actionable":
            return tier.headline;
        case "loading":
            // A single subject makes every chunk one wall-clock slice, so the
            // readout can name the unit the operator sees on the chart. Once the
            // query fans out, chunks outnumber slices and only the generic
            // "data parts" wording stays true.
            return tier.subjects > 1
                ? t("historical.statusLoadingParts", { loaded: tier.loaded, total: tier.total })
                : t("historical.statusLoading", { loaded: tier.loaded, total: tier.total });
        case "preparing":
            return tier.attempt > 1
                ? t("historical.statusRetryingHistogram", {
                      attempt: tier.attempt,
                      total: tier.totalAttempts,
                  })
                : t("historical.statusPreparingHistogram");
        case "hint":
            return tier.message;
        case "empty":
            return null;
        default:
            return assertNever(tier);
    }
}

interface StatusBarProps {
    tier: StatusTier;
    activated: boolean;
    zoomDepth: number;
    playheadSet: boolean;
    rangeStartLabel: string;
    rangeEndLabel: string;
    onRetry: () => void;
    onDismiss: () => void;
    onPopZoom: () => void;
    onResetZoom: () => void;
}

export function HistoricalStatusBar({
    tier,
    activated,
    zoomDepth,
    playheadSet,
    rangeStartLabel,
    rangeEndLabel,
    onRetry,
    onDismiss,
    onPopZoom,
    onResetZoom,
}: StatusBarProps): ReactElement {
    const { t } = useTranslation();

    // Before activation the histogram's own placeholder explains what to do,
    // so the status bar stays empty (height still reserved so the histogram
    // doesn't jump on activation).
    if (!activated && tier.kind !== "actionable") {
        return (
            <Flex mx="3" mb="1" h={STATUS_BAR_HEIGHT} alignItems="center" fontSize="xs" px="1" />
        );
    }

    const isActionable = tier.kind === "actionable";
    const icon = tierIcon(tier);
    const primary = tierPrimary(tier, t);
    const detail = tier.kind === "actionable" ? tier.detail : null;

    const zoomDepthLabel =
        zoomDepth > 0 ? t("historical.statusZoomDepth", { depth: zoomDepth }) : null;

    // Avoid the unused-variable warning when no hint applies. `playheadSet`
    // participates in the parent hook's cascade; we keep it in props so the
    // panel can stay declarative about what the bar reflects.
    void playheadSet;

    return (
        <Flex
            mx="3"
            mb="1"
            h={STATUS_BAR_HEIGHT}
            alignItems="center"
            gap="4"
            fontSize="xs"
            lineHeight="1"
            px="1"
            color={isActionable ? "red.fg" : "fg.muted"}
        >
            <Text as="span" lineHeight="1" color="fg.muted" minW="10" flexShrink="0">
                {rangeStartLabel}
            </Text>
            <Flex
                flex="1"
                alignItems="center"
                justifyContent="center"
                gap="2"
                minW="0"
                px="2"
                role="status"
                aria-live="polite"
                aria-atomic="true"
            >
                {icon && (
                    <Box as="span" color="inherit" display="inline-flex" alignItems="center">
                        {icon}
                    </Box>
                )}
                {primary && (
                    // Headline is always rendered inline; truncates with
                    // ellipsis if the viewport is narrow. Longer explanation
                    // (when a `detail` exists) sits behind an explicit "More"
                    // link with a tooltip — implicit hover-the-text discovery
                    // wasn't obvious enough.
                    // `truncate` sets overflow:hidden for the ellipsis; a tight
                    // lineHeight of 1 would make the line box exactly the font
                    // height and clip glyph descenders (e.g. the tails of "g"),
                    // so give it enough leading to contain them.
                    <Text as="span" lineHeight="1.4" color="inherit" truncate minW="0">
                        {primary}
                    </Text>
                )}
                {detail && (
                    <Tooltip content={detail} uppercase={false} openDelay={150}>
                        <Text
                            as="span"
                            lineHeight="1"
                            color="inherit"
                            textDecoration="underline"
                            cursor="help"
                            flexShrink="0"
                        >
                            {t("historical.more")}
                        </Text>
                    </Tooltip>
                )}
                {!isActionable && primary && zoomDepth > 0 && (
                    <Text as="span" lineHeight="1" color="inherit">
                        {STATUS_BULLET}
                    </Text>
                )}
                {!isActionable && zoomDepth > 0 && (
                    <Flex alignItems="center" gap="2" color="inherit">
                        <Text as="span" lineHeight="1" color="inherit">
                            {zoomDepthLabel}
                        </Text>
                        <StatusBarAction onClick={onPopZoom}>
                            {t("historical.zoomBack")}
                        </StatusBarAction>
                        <StatusBarAction onClick={onResetZoom}>
                            {t("historical.zoomReset")}
                        </StatusBarAction>
                    </Flex>
                )}
                {isActionable && tier.canRetry && (
                    <StatusBarAction onClick={onRetry} colorPalette="red">
                        {t("historical.retry")}
                    </StatusBarAction>
                )}
                {isActionable && tier.dismissible && (
                    <StatusBarAction
                        onClick={onDismiss}
                        colorPalette="red"
                        ariaLabel={t("historical.dismissError")}
                    >
                        {t("historical.dismiss")}
                    </StatusBarAction>
                )}
            </Flex>
            <Text
                as="span"
                lineHeight="1"
                color="fg.muted"
                minW="10"
                textAlign="right"
                flexShrink="0"
            >
                {rangeEndLabel}
            </Text>
        </Flex>
    );
}
