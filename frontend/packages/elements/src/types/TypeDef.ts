// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Element } from "../Element";
import type { Geometry } from "../geometry/Geometry";
import type { Style } from "../style/Style";

/**
 * Contract for an element type definition namespace.
 *
 * Every element type (e.g. Arrow, Circle, Symbol) exports an object that
 * `satisfies TypeDef<G, S>`, providing at minimum a `type` string and an
 * `is()` type guard. Additional domain methods (e.g. `getBearing`, `getAngle`)
 * are permitted via the index signature.
 *
 * @typeParam G The geometry primitive for this element type.
 * @typeParam S The style for this element type.
 */
export interface TypeDef<G extends Geometry = Geometry, S extends Style = Style> {
    /** The element type string (e.g. `"arrow"`, `"circle"`). */
    readonly type: string;
    /**
     * Type guard that narrows an {@link Element} to `Element<G, S>`.
     * @param element The element to check.
     */
    is(element: Element): element is Element<G, S>;
    /**
     * Factory method signature. Each type has its own props, so the
     * concrete signature varies per implementation.
     */
    create(...args: never[]): Element<G, S>;
    /** Index signature for additional domain methods. */
    [key: string]: unknown;
}

export type ElementOf<T extends TypeDef> = ReturnType<T["create"]>;

/**
 * Optional overrides passed to a TypeDef's `create()` method.
 * @typeParam S The style type for the element being created.
 */
export interface TypeDefProps<S extends Style = Style> {
    /** Override the default style. */
    style?: S;
    /** Override identity metadata (id, objectId, displayName, etc.). */
    meta?: Partial<ElementMeta>;
}

/** Identity metadata for an {@link Element}. */
export interface ElementMeta {
    /** The element type key. */
    type: string;
    /** Unique instance id. */
    id: string;
    /** Shared object id for versioning. */
    objectId: string;
    /** Human-readable display name. */
    displayName: string;
    /** Origin source identifier. */
    source: string;
    /** When the event started, as a Unix timestamp in milliseconds. */
    start?: number | null;
    /** When the event was observed, as a Unix timestamp in milliseconds. */
    time?: number | null;
    /** Stale time as a Unix timestamp in milliseconds. */
    stale: number;
    speed?: number | null;
    hae?: number;
    course?: number | null;
    heading?: number | null;
    /** Pipeline classification that determines key strategy. Defaults to "live". */
    elementClass?: "live" | "historical";
}
