// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * Custody — holding a persisted element for the whole DRAW→EDIT session.
 *
 * An interaction that modifies contested persistent state must hold custody
 * of it for the duration of the operation. For a tool that is ONE rule:
 * **a descriptor that produces into the element store produces an
 * ElementStore citizen and takes custody of it; one that produces anywhere
 * else (measure, lasso, watchbox) produces a product the element store never
 * sees, and the commands skip every function in this file.** Measure needs no custody because nothing
 * else can write its product — not because it is "ephemeral".
 *
 * - `begin` = hand the element to the store as its draft (`setDraft`). Store events for
 *   the draft's key are suppressed, so a WebSocket update cannot overwrite the edit.
 * - `commit` = write the draft into the store (`commitDraft`), then persist `updated`.
 * - `abort` = drop the draft (`clearDraft`); the stored element is re-emitted, or removed if
 *   it is absent or has expired.
 *
 * ONLY these functions may create, draft, or destroy in-flight elements —
 * never React cleanup. That single rule is what makes StrictMode enableable
 * and kills the params-teardown defect class.
 */
import {
    CircleGeometry,
    elementRefsEqual,
    getElementKey,
    toElementRef,
    type DraftObject,
    type Element,
    type ElementRef,
} from "@opencop/elements";
import { type ToolDescriptor, producedElementType } from "@opencop/tools";
import type { InteractionHost } from "../contract";

/**
 * Whether a session on this descriptor takes custody of what it draws. The
 * whole rule: a product in the element store is a persisted element.
 */
export function hasCustody(descriptor: ToolDescriptor | undefined): boolean {
    return producedElementType(descriptor?.produces) !== undefined;
}

/** First placement: hand the element to the store as its draft. */
export function beginCustody(host: InteractionHost, element: Element): void {
    const { custody } = host;
    custody.draft(element);
}

/**
 * Save: commit the draft, persist the (possibly replaced) element, run the
 * domain bookkeeping. `ref` is the element's identity when the session began.
 */
export function commitCustody(
    host: InteractionHost,
    updated: Element | undefined,
    ref: ElementRef,
    onCommit?: (element: Element) => void,
): void {
    const { custody, actions } = host;
    custody.commit();
    if (!updated) return;

    const newRef = toElementRef(updated);
    const sourceChanged = !elementRefsEqual(ref, newRef);

    // When the source changes (e.g. local → layer broadcast), the element gets
    // a new composite key. Add the new element, re-select it under the new
    // identity, and delete the stale local key — the persistence subscriber
    // handles IndexedDB.
    custody.add([updated]);
    if (sourceChanged) {
        actions.selectElement(updated);
        custody.delete(getElementKey(ref));
    }
    onCommit?.(updated);
}

/** Discard the draft (draw escape / cancel of an edit). */
export function abortCustody(host: InteractionHost): void {
    const { custody } = host;
    custody.clear();
}

/** Capture a draft's geometry positions. */
export function captureOriginalPositions(draft: DraftObject): number[] {
    const geo = draft.geometry;
    // For circles getAllPos() only returns the origin — append the radius.
    if (geo instanceof CircleGeometry) return [...geo.getAllPos(), geo.getRadius()];
    return [...geo.getAllPos()];
}
