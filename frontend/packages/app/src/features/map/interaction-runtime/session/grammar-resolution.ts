// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Resolving a descriptor and its draw grammar, contained.
 *
 * A params-derived `grammar` is contributed code fed the operator's current
 * params, so it can throw for two reasons and neither may take the map down.
 * `ToolError` is the contract's declared refusal ("no drawable tool for these
 * params"); anything else is a defect in the subject — reported to the host's
 * environment, which attributes it to the tool's origin, rather than rethrown
 * into MapView's boundary, which would replace the whole map because one tool
 * is broken.
 */
import {
    ToolError,
    resolveGrammar,
    type DrawGrammar,
    type EditModel,
    type ToolDescriptor,
    type ToolParams,
} from "@opencop/tools";
import { getHost } from "../host";
import { genericDragModel } from "./edit-models/generic-drag.model";

/**
 * The descriptor for a session's `descriptorId` — undefined when idle,
 * unregistered, or before the runtime is attached (every command refuses then
 * anyway). Resolved through the attached host's environment: the runtime
 * never imports the registry.
 */
export function descriptorOf(id: string | null): ToolDescriptor | undefined {
    return id === null ? undefined : getHost()?.environment.tool(id);
}

/**
 * The edit model that interprets drags over a descriptor's product — its own
 * `editModel`, else the host's generic vertex-drag model. The one resolution
 * for both halves of an edit: the drag loop hit-tests these control points and
 * the interaction layer draws them, so what is drawn is what can be grabbed.
 */
export function editModelOf(descriptor: ToolDescriptor | undefined): EditModel {
    return descriptor?.draw?.editModel ?? genericDragModel;
}

export type GrammarResolution =
    /** The grammar resolved; `undefined` for a content-only tool. */
    | { readonly kind: "tool"; readonly grammar: DrawGrammar | undefined }
    /** The subject declined these params (`ToolError`) — expected, nothing reported. */
    | { readonly kind: "refused"; readonly message: string }
    /** The subject threw — reported and attributed to the tool. */
    | { readonly kind: "defect" };

export function resolveGrammarContained(
    descriptor: ToolDescriptor,
    params: ToolParams,
): GrammarResolution {
    const environment = getHost()?.environment;
    let grammar;
    try {
        grammar = resolveGrammar(descriptor, params);
        // Reopen the once-per-tool log latch on success, or a second, different
        // defect in the same tool is never reported.
        environment?.clearToolDefect(descriptor.id);
    } catch (err) {
        if (err instanceof ToolError) return { kind: "refused", message: err.message };
        environment?.reportToolDefect(descriptor.id, err);
        return { kind: "defect" };
    }
    return { kind: "tool", grammar };
}
