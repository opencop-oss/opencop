// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

/**
 * The element store's id. Built into the host: elements draw through their own
 * pipeline, and the product of a tool that produces into it is held in the store
 * from first placement to Save or Cancel.
 */
export const ELEMENT_STORE_ID = "elements";

/**
 * The host's session store id: the home of products that are never stored, such as
 * a measurement or a lasso trace. Only the product of a running session is drawn
 * from it. Its type keys must be namespaced (`<subject>:<name>`) so they can never
 * be mistaken for an element type key.
 */
export const SESSION_STORE_ID = "session";

/** Why a product's `(store, type)` pair is invalid, or `null` when it is valid. */
function storeTypeError(store: string, type: string): string | null {
    if (store.length === 0) return "store is empty";
    if (type.length === 0) return "type is empty";
    if (store === SESSION_STORE_ID && !/^[^:]+:[^:]+$/.test(type)) {
        return `session type "${type}" must be namespaced as "<subject>:<name>"`;
    }
    return null;
}

/**
 * Where a tool's product lives: `store`, a store id, and `type`, the type key the
 * product carries within that store — both string identifiers.
 */
export interface ProductTarget {
    readonly store: string;
    readonly type: string;
}

/**
 * Why a tool's declared product is invalid, or `null`. A tool that declares no
 * product (a popover tool) is valid.
 *
 * Takes the field rather than the tool, so the rule lives beside the tool
 * contract it enforces and every tool registry, native or plugin, applies the same
 * one.
 */
export function productionError(produces: ProductTarget | undefined): string | null {
    if (!produces) return null;
    if (typeof produces.store !== "string" || typeof produces.type !== "string")
        return "produces needs a store and a type";
    return storeTypeError(produces.store, produces.type);
}

/**
 * The element type a tool produces, or `undefined` when its product is not an
 * element. A product in the element store is a persisted element, which the host
 * holds in that store, locked against live updates, from first placement to
 * Save or Cancel; a product anywhere else never is.
 */
export function producedElementType(produces: ProductTarget | undefined): string | undefined {
    return produces?.store === ELEMENT_STORE_ID ? produces.type : undefined;
}

/**
 * Why a tool's formatting-bar `style` is invalid, or `null`. A style input writes
 * the product's element style, so only a tool whose product is an element may
 * declare one.
 */
export function styleError(
    produces: ProductTarget | undefined,
    style: readonly unknown[] | undefined,
): string | null {
    if (!style?.length || producedElementType(produces) !== undefined) return null;
    return "declares style, but its product is not an element";
}
