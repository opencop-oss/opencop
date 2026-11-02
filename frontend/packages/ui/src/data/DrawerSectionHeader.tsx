// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Flex, Heading, IconButton, Stack } from "@chakra-ui/react";
import type { ReactElement, ReactNode } from "react";
import { use, useEffect, useId } from "react";
import { useTranslation } from "react-i18next";
import { InfoTooltip } from "../forms/InfoTooltip.tsx";
import { Tooltip } from "../overlays/Tooltip.tsx";
import { DrawerPanelHeadingContext } from "../surfaces/DrawerSideNavLayout.tsx";
import { FaArrowLeft } from "react-icons/fa";

export interface DrawerSectionHeaderProps {
    /** Visible heading naming the panel, e.g. "Layer Members". */
    title: string;
    /**
     * One line stating what the panel lists or does, in the user's terms. Disclosed
     * from an info icon beside the title rather than shown inline, so the panel
     * leads with its content instead of a paragraph the reader soon stops seeing.
     */
    description?: string;
    /**
     * Return control for a sub-flow that has taken over the panel, shown as a small
     * arrow ahead of the title. Omit on a section that is simply the active rail item.
     */
    back?: {
        /** Names the destination, e.g. "Back to Members". Both tooltip and accessible name. */
        label: string;
        onBack: () => void;
    };
    /** Controls rendered at the right of the title row (e.g. a close button). */
    actions?: ReactNode;
}

/**
 * The heading block at the top of a drawer content panel: an optional back control,
 * the panel's title, and an info icon disclosing what it holds.
 *
 * Every rail section inside {@link DrawerSideNavLayout} starts with one of these,
 * as does every sub-flow that takes a section's panel over, so the panel states
 * what it is rather than leaving the far-left rail item as the only cue. Panels
 * with modes (assign, change-role) render their own per mode, so the title tracks
 * what the user is actually looking at.
 *
 * Inline `editContent` forms are the exception — they carry their own layout and
 * a Save/Cancel footer, and have no header today.
 *
 * Mounting one also names the enclosing panel: it claims the heading id the
 * layout points `aria-labelledby` at, so entering the panel announces this
 * title rather than the rail label.
 *
 * Renders as `h3` because the drawer's own `Drawer.Title` is the `h2` above it —
 * a panel heading is subordinate to the entity the drawer is about.
 */
export function DrawerSectionHeader({
    title,
    description,
    back,
    actions,
}: DrawerSectionHeaderProps): ReactElement {
    const { t } = useTranslation();
    const panelHeading = use(DrawerPanelHeadingContext);
    // Falls back to a local id when rendered outside a side-nav layout (tests,
    // and any future single-panel drawer) so the heading is still addressable.
    const fallbackId = useId();
    const headingId = panelHeading?.headingId ?? fallbackId;

    const setHasHeading = panelHeading?.setHasHeading;
    useEffect(() => {
        setHasHeading?.(true);
        return () => setHasHeading?.(false);
    }, [setHasHeading]);

    return (
        <Stack gap={1} flexShrink={0} mb={1}>
            <Flex align="center" justify="space-between" gap={2} minW={0}>
                <Flex align="center" gap={1.5} minW={0}>
                    {back && (
                        <Tooltip content={back.label}>
                            {/* `plain` rather than `ghost`: no hover fill behind the arrow, so it
                                reads as part of the heading line. Hover is carried by the icon
                                brightening, the same way the theme's `toolbar` variant does it. */}
                            <IconButton
                                aria-label={back.label}
                                variant="plain"
                                size="xs"
                                px={0}
                                minW="auto"
                                color="active"
                                _hover={{ color: "active", opacity: 0.75 }}
                                onClick={back.onBack}
                            >
                                <FaArrowLeft />
                            </IconButton>
                        </Tooltip>
                    )}
                    <Heading as="h3" id={headingId} size="lg" lineHeight="1.2" truncate>
                        {title}
                    </Heading>
                    {description && (
                        <InfoTooltip
                            content={description}
                            aria-label={t("common.aboutX", { title })}
                        />
                    )}
                </Flex>
                {actions && <Box flexShrink={0}>{actions}</Box>}
            </Flex>
        </Stack>
    );
}
