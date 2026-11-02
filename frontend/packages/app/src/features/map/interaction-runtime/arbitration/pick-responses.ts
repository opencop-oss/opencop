// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * PICK RESPONSES — what a primary click under ambient does when it lands on
 * a thing a feature drew that is not an element.
 *
 * The pointer's three owners are closed (`pointer-owners.ts`). Within
 * ambient, dispatch is by what the hit was drawn for: a scene primitive
 * answers with the id of the store whose object it draws, and the subject
 * that owns the store registers the response for that id — `{ onClick(target) }`,
 * behaviour only — from where it composes (`ctx.registerPickResponse`). A
 * response says nothing about which hits are its (the store already said)
 * and nothing about rank: there is none. deck's depth pick resolved the stack
 * before this module is consulted, so one hit carries one id, and two
 * responses for one id is a registration collision, not a tie to break at
 * click time.
 *
 * **Elements first** is arbitration's rule, not a response's. A feature's
 * things are regions and outlines; an element is a small target that may sit
 * inside one. So a click with any element under the pointer is the element
 * response's (`sources/ambient-pointer.ts`), whatever is drawn over it, and a
 * registered response is offered only a click whose nearest hit was drawn
 * from its store and which has no element beneath. Over empty ground nobody is offered
 * anything.
 */
import Logger from "js-logger";
import type { PickResponse, PickTarget } from "@opencop/contributions";
import { createPickResponseRegistry } from "@opencop/contributions/host";

export type { PickResponse, PickTarget, StorePickTarget } from "@opencop/contributions";

const logger = Logger.get("interaction");

/** The app's pick-response registry: the one instance of `createPickResponseRegistry`. */
export const pickResponseRegistry = createPickResponseRegistry({
    log: import.meta.env.DEV ? logger : undefined,
});

/** Register the response for an id. Returns the disposer that withdraws it. */
export const registerPickResponse = pickResponseRegistry.register;

/**
 * The response a primary click on this target is dispatched to, or none:
 * none while an element is under the pointer (elements first), none over
 * empty ground, none for a hit outside any store or from a store nothing
 * has registered a response for.
 */
export function pickResponseFor(target: PickTarget): PickResponse | undefined {
    if (target.elements.length > 0 || !target.hit?.responseId) return undefined;
    return pickResponseRegistry.get(target.hit.responseId);
}
