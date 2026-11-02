// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, type ButtonProps } from "@chakra-ui/react";
import { useTranslation } from "react-i18next";
import { FaPlus } from "react-icons/fa";
import { Tooltip } from "@opencop/ui";
import { activate, exit } from "~/features/map/interaction-runtime/session/commands";
import { useToolButtonState } from "~/features/toolbar/use-tool-button-state.hook";
import { WATCHBOX_TOOL_ID } from "./watchbox-session";

export type WatchboxCreateButtonProps = Pick<
    ButtonProps,
    "size" | "variant" | "colorPalette" | "borderRadius" | "px" | "fontSize" | "color"
>;

/**
 * The one "start a watchbox draw" control, shared by the empty state and the
 * list header. Follows `useToolButtonState()` — the same state the toolbar's
 * own tool buttons read — so it disables for the same reasons (historical
 * mode, another tool's edit, another tool's draft in flight) and reports the
 * same historical tooltip. It diverges from the toolbar's click rule in one
 * place: the toolbar disables a tool's own button once its draft is in
 * flight (switching tools must not abandon one — Escape does that), but this
 * is the dedicated cancel affordance for watchbox's own draft, so it stays
 * enabled and calls `exit()` directly rather than through the shared
 * click handler.
 */
export function WatchboxCreateButton(props: WatchboxCreateButtonProps) {
    const { t } = useTranslation();
    const { toolsDisabled, activeDrawId, isHistorical } = useToolButtonState();
    const isDrawing = activeDrawId === WATCHBOX_TOOL_ID;
    const disabled = !isDrawing && toolsDisabled;
    const label = isDrawing ? t("watchbox.cancelDrawing") : t("watchbox.create");

    function handleClick() {
        if (isDrawing) {
            exit();
            return;
        }
        if (toolsDisabled) return;
        activate(WATCHBOX_TOOL_ID);
    }

    const showHistoricalReason = isHistorical && !isDrawing;

    return (
        <Tooltip
            content={t("toolbar.drawDisabledHistorical")}
            uppercase={false}
            disabled={!showHistoricalReason}
        >
            {/* A disabled button emits no pointer events, so the tooltip's own
                trigger would never fire for a keyboard user tabbing to it — the
                span is what the tab order and the tooltip actually attach to.
                Same shape as DrawerToolbarButton.tsx. */}
            <Box as="span" tabIndex={disabled ? 0 : -1} display="inline-flex">
                <Button {...props} onClick={handleClick} disabled={disabled}>
                    {!isDrawing && <FaPlus />}
                    {label}
                </Button>
            </Box>
        </Tooltip>
    );
}
