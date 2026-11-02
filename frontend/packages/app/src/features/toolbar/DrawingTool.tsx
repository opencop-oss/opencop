// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { HStack } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { Divider } from "@opencop/ui";
import { useHistoricalMode } from "~/features/historical/data-plane/historical.store";
import { getTool } from "~/features/contributions/tool-registry";
import { ToolInput } from "./ToolInput";
import { DrawActions } from "./DrawActions";
import { ToolPicker } from "./ToolPicker";
import { useInteraction } from "~/features/map/interaction-runtime/session/interaction.store";

export function DrawingTool(): ReactElement {
    const session = useInteraction((s) => s.session);
    const isHistorical = useHistoricalMode() === "historical";

    // Drawing writes to the live ElementStore, which has no meaning while the
    // map shows a historical snapshot — the draw toolbar is disabled in
    // historical mode (DrawActions/ToolPicker grey out and swap their tooltip
    // to the "unavailable" reason via the same hook).
    // Formatting bar visible during DRAW/EDIT for geometry tools only.
    // Hidden during INTERACT, in historical mode, and for tools that declare
    // their own editor.
    const activeCommitTool =
        session.kind === "draw" || session.kind === "edit" ? session.descriptorId : null;
    const descriptor = activeCommitTool ? getTool(activeCommitTool) : undefined;
    const showFormattingBar =
        !isHistorical && !descriptor?.hideStyleBar && !!descriptor?.style?.length;

    return (
        <HStack gap="0" flex="1" minW="0">
            <DrawActions />
            <Divider orientation="vertical" borderWidth="1px" height="1.5em" />
            <ToolPicker />
            {showFormattingBar ? (
                <>
                    <Divider orientation="vertical" borderWidth="1px" height="1.5em" />
                    <HStack gap="0">
                        {(descriptor?.style ?? []).map((spec) => (
                            <ToolInput
                                key={activeCommitTool! + spec.label}
                                spec={spec}
                                toolKey={activeCommitTool!}
                            />
                        ))}
                    </HStack>
                </>
            ) : null}
        </HStack>
    );
}
