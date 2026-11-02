// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The registry watch — a session cannot outlive its descriptor. When a tool is
 * deregistered (a plugin unloads, a feature's backend flag turns off) while a
 * session runs on it, the runner would keep handlers bound to a descriptor
 * that no longer resolves. Exiting the session is the only consistent answer:
 * the draft is aborted (or the edit rolled back) by the commands, the runner
 * rebinds to idle, and nothing is stranded.
 */
import type { InteractionEnvironment } from "../contract";
import { getInteractionState } from "./interaction.store";
import { exit } from "./commands";

export function attachRegistryWatch(environment: InteractionEnvironment): () => void {
    return environment.subscribeTools(() => {
        const { session } = getInteractionState();
        if (session.kind === "idle" || !session.descriptorId) return;
        if (!environment.tool(session.descriptorId)) exit();
    });
}
