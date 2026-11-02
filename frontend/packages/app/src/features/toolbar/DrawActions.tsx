// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack, IconButton } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip } from "@opencop/ui";
import { PiCursorFill } from "react-icons/pi";
import { exit } from "~/features/map/interaction-runtime/session/commands";
import {
    useInteraction,
    isCustodyEdit,
} from "~/features/map/interaction-runtime/session/interaction.store";
import { NativeToolButton } from "./NativeToolButton";
import { INTERACTION_TOOL_GROUP } from "@opencop/tools";
import { useToolsForLocation } from "~/features/contributions/tool-registry";
import { useToolButtonState } from "./use-tool-button-state.hook";

/**
 * The leftmost toolbar cluster: Interact, then the registry's interaction
 * group. Interact is the one hand-written button — it is not a tool but the
 * absence of one (it exits the current session). Lasso and measure are
 * ordinary descriptors that declare `group: "interaction"`, so they exist
 * here exactly while registered. Watchbox used to be a third — it now
 * declares `location: "external"` instead, so it has no button anywhere; its
 * own panel calls `activate()` directly. Every tool's session still obeys the
 * same rules below regardless of location or group — those are
 * placement-only fields the interaction runtime never reads.
 *
 * The tools obey the picker's rules: disabled in historical mode, during any
 * EDIT session and while a draft is in flight — switching tools is not a way
 * to abandon an operation. Interact stays available over a custody-less
 * product (a finished measurement or watchbox polygon: leaving drops it); a
 * custody edit must resolve through Save/Cancel first. A tool stays lit for
 * the whole of its session, its finished product included, which is how the
 * operator knows a measurement on display still belongs to Measure.
 */
export function DrawActions(): ReactElement {
    const { t } = useTranslation();
    const session = useInteraction((s) => s.session);
    const { toolsDisabled, isHistorical, handleToolClick } = useToolButtonState();
    const tools = useToolsForLocation("topBarStart", INTERACTION_TOOL_GROUP);

    // Lit for the whole session, finished product included (the picker lights only a draw).
    const activeId = session.kind === "idle" || isHistorical ? null : session.descriptorId;
    const isInteractActive = session.kind === "idle" || isHistorical;
    const interactDisabled = isHistorical || isCustodyEdit(session);

    const interactLabel = isHistorical
        ? t("toolbar.drawDisabledHistorical")
        : t("toolbar.interact");

    function handleInteractClick() {
        exit();
    }

    return (
        <HStack gap="0">
            <IconButton
                size={{ base: "compact", lg: "md" }}
                fontWeight="bold"
                active={isInteractActive ? "icon" : "iconHover"}
                aria-label={interactLabel}
                disabled={interactDisabled}
                onClick={handleInteractClick}
            >
                <Tooltip content={interactLabel} uppercase={!isHistorical}>
                    <PiCursorFill />
                </Tooltip>
            </IconButton>
            {tools.map((descriptor) => (
                <NativeToolButton
                    key={descriptor.id}
                    descriptor={descriptor}
                    highlighted={activeId === descriptor.id}
                    disabled={toolsDisabled}
                    historical={isHistorical}
                    onSelect={handleToolClick}
                />
            ))}
        </HStack>
    );
}
