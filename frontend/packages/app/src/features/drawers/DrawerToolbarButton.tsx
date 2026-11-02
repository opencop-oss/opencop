// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, IconButton } from "@chakra-ui/react";
import { Tooltip } from "@opencop/ui";
import type { ReactElement, ReactNode } from "react";
import { Link as RouterLink } from "react-router";

export interface DrawerToolbarButtonProps {
    /** Specific, translated action name. Used verbatim as the aria-label and the enabled tooltip. */
    label: string;
    icon: ReactNode;
    onClick?: () => void;
    /** When set (and not disabled), the button navigates to this route instead of firing only `onClick`. */
    to?: string;
    disabled?: boolean;
    /** When disabled, the tooltip shows this instead of `label`, preserving the "why it's disabled" hint. */
    disabledReason?: string;
    /** Shows a spinner in place of the icon (wire to a pending mutation). */
    loading?: boolean;
    /** Use the destructive red palette instead of the default "active" color. */
    destructive?: boolean;
}

/**
 * A single icon button for a drawer header toolbar.
 *
 * Bakes in the conventions the admin drawers share:
 * - an always-present, action-specific `aria-label` (the text label is gone in icon-only mode);
 * - a label tooltip that swaps to `disabledReason` while disabled;
 * - a `<span>` wrapper so the tooltip still fires on a disabled button (Ark disabled
 *   controls emit no pointer events);
 * - muted by default; reveals its action color on hover (cyan for neutral, red for destructive).
 */
export function DrawerToolbarButton({
    label,
    icon,
    onClick,
    to,
    disabled = false,
    disabledReason,
    loading = false,
    destructive = false,
}: DrawerToolbarButtonProps): ReactElement {
    // A label is shouted like every other tooltip; a reason is prose and stays sentence case.
    const showingReason = Boolean(disabled && disabledReason);
    const tooltipContent = showingReason ? disabledReason : label;
    // Navigate only when there's a target and the button is interactive. A loading or disabled
    // button renders as a real <button> (asChild=false) so its spinner / disabled state applies.
    const linkTo = to != null && !disabled && !loading ? to : undefined;

    return (
        <Tooltip content={tooltipContent} uppercase={!showingReason}>
            <Box as="span" tabIndex={disabled ? 0 : -1} display="inline-flex">
                <IconButton
                    aria-label={label}
                    variant="plain"
                    color="fg.muted"
                    transition="color 0.15s ease-out"
                    _hover={{ color: destructive ? "red.fg" : "active" }}
                    size="sm"
                    disabled={disabled}
                    loading={loading}
                    onClick={onClick}
                    asChild={linkTo != null}
                >
                    {linkTo != null ? <RouterLink to={linkTo}>{icon}</RouterLink> : icon}
                </IconButton>
            </Box>
        </Tooltip>
    );
}
