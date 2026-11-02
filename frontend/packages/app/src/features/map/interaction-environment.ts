// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The app-level environment the interaction runtime requires, gathered in one
 * place: the tool registry, the toolbar's persisted style values, the
 * contribution guard a throwing tool is reported to, and the live/historical
 * mode. The runtime declares the shape (`InteractionEnvironment`) and imports
 * none of these features; this is where the map's composition point satisfies
 * it.
 */
import { getAllTools, getTool, subscribeTools } from "~/features/contributions/tool-registry";
import { editToolFor } from "~/features/contributions/edit-tool";
import {
    clearContributionFailure,
    reportContributionFailure,
    type ContributionOrigin,
} from "~/features/contributions/contribution-guard";
import { ownerOfToolId } from "~/features/plugin-manager/plugin-tool-id";
import { getCurrentStyleValue } from "~/features/toolbar/tool.hooks";
import { useHistoricalStore } from "~/features/historical/data-plane/historical.store";
import type { InteractionEnvironment } from "./interaction-runtime/contract";

/** A tool's `grammar` as the guard attributes it — to the plugin that shipped it, if one did. */
function grammarOrigin(toolId: string): ContributionOrigin {
    return { owner: ownerOfToolId(toolId), contribution: toolId, phase: "grammar" };
}

export function createInteractionEnvironment(): InteractionEnvironment {
    return {
        tool: getTool,
        tools: getAllTools,
        editToolFor,
        subscribeTools: (listener) => subscribeTools(listener),
        styleValue: getCurrentStyleValue,
        reportToolDefect: (toolId, error) =>
            reportContributionFailure(grammarOrigin(toolId), error),
        clearToolDefect: (toolId) => clearContributionFailure(grammarOrigin(toolId)),
        isHistorical: () => useHistoricalStore.getState().mode === "historical",
    };
}
