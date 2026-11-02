// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { OCBaseElement, PartialOCFeature } from "./event.ts";
import type { Geometry } from "./geometry/Geometry.ts";
import type { Style } from "./style/Style.ts";
import { getElementHistoricalKey, getElementKey, localEventSource } from "./identity.ts";
import { Reactive } from "./Reactive.ts";
import * as uuid from "uuid";

/**
 * Indicates which data pipeline produced this element, and determines how
 * its key is constructed:
 * - `"live"` (default) → key = source + objectId; store dedupes per entity
 * - `"historical"` → key = source + id; every chunk's version coexists
 */
export type ElementClass = "live" | "historical";

/**
 * Generate a default stale time (one day from now).
 * @returns A Unix timestamp in milliseconds.
 */
export function defaultStale(): number {
    return Date.now() + 8.64e7; // one day from now
}

/**
 * (GMT) Wednesday, January 1, 3000 at 12:00:00 PM
 */
export const neverStale: number = 32503723200000;
/** 1 year epsilon for safety */
const NEVER_EPSILON: number = 31557600000;
export function isNeverStale(epoch: number): boolean {
    return Math.abs(neverStale - epoch) < NEVER_EPSILON;
}

/** Construction properties for an {@link Element}. */
export interface ElementProps<G extends Geometry = Geometry, S extends Style = Style> {
    /** The element type key (e.g. `"arrow"`, `"circle"`, `"symbol"`). */
    type: string;
    /** Unique instance id. Auto-generated if omitted. */
    id?: string;
    /** Shared object id for versioning. Auto-generated if omitted. */
    objectId?: string;
    /** Origin source identifier. Defaults to {@link localEventSource}. */
    source?: string;
    /** Human-readable display name. Defaults to `"New {type}"`. */
    displayName?: string;
    /** When the event started, as a Unix timestamp in milliseconds. */
    start?: number | null;
    /** When the event was observed, as a Unix timestamp in milliseconds. */
    time?: number | null;
    /** Stale time as a Unix timestamp in milliseconds. Defaults to {@link neverStale}. */
    stale?: number;
    /** Speed of entity */
    speed?: number | null;
    /** Course of entity, direction of actual movement */
    course?: number | null;
    /** Heading of entity, direction facing */
    heading?: number | null;
    /** Height above ellipsoid */
    hae?: number;
    /** Optional code identifier (e.g. SIDC, icon key). */
    code?: string;
    /** Optional modifiers for sidc. */
    modifiers?: Record<string, string>;
    /** The spatial geometry primitive. */
    geometry: G;
    /** The visual style. */
    style: S;
    /** Custom key-value metadata. */
    attributes?: Record<string, unknown>;
    /**
     * Pipeline classification that determines key strategy.
     * @default "live"
     */
    elementClass?: ElementClass;
}

/**
 * Core map element combining identity, spatial {@link Geometry}, and visual {@link Style}.
 *
 * Parameterized by `G` (geometry primitive) and `S` (style). Most generic code
 * uses the defaults (`Element`); type-specific code uses the full form
 * (e.g. `Element<Circle, ShapeStyle>`). A TypeDef's `is()` guard narrows both
 * generics in one check.
 *
 * @typeParam G The geometry primitive type.
 * @typeParam S The style type.
 */
export class Element<G extends Geometry = Geometry, S extends Style = Style> extends Reactive {
    /** Signal keys emitted by {@link Element}. */
    static Signals = {
        DisplayName: "displayName",
        Style: "style",
        Code: "code",
        Modifiers: "modifiers",
        FilterValue: "filterValue",
    } as const;

    /** The element type key used for routing (e.g. `"arrow"`, `"circle"`). */
    readonly type: string;
    /** Unique instance id. */
    readonly id: string;
    /** Shared object id across versions of the same element. */
    readonly objectId: string;
    /** The source that created this element. */
    readonly source: string;
    /**
     * Pipeline classification that determines key strategy.
     * Set from `ElementProps.elementClass` at construction time.
     */
    readonly elementClass: ElementClass;
    /**
     * Stable internal identity key.
     * - `"live"` class → `source + objectId` (store dedupes by entity)
     * - `"historical"` class → `source + id` (each version coexists)
     */
    readonly key: string;
    /** Unix timestamp (ms) when the event was observed, if known. */
    readonly time?: number | null;
    /** Speed of entity in meters per second. */
    readonly speed?: number | null;
    /** Course of entity, direction of actual movement. */
    readonly course?: number | null;
    /** Heading of entity, direction facing. */
    readonly heading?: number | null;
    /** Height above ellipsoid in meters. */
    readonly hae?: number;
    private _start?: number | null;
    private _stale: number;
    private _displayName: string;

    /** The spatial geometry primitive. */
    readonly geometry: G;
    private _style: S;

    /** Optional classification code (e.g. SIDC for mil-std symbols). */
    private _code?: string;
    /** Optional modifiers for SIDC. */
    private _modifiers: Record<string, string>;
    /** Custom key-value metadata attributes. */
    private _attributes: Record<string, unknown>;
    /**
     * **Transitional — slated for removal.**
     *
     * Per-element scalar fed to deck.gl's `DataFilterExtension` via the
     * `filterValue` vertex attribute on every binary source. Currently lives
     * on `Element` only because there is no per-element render-record layer
     * between the domain entity and `BinarySource`; once that wrapper exists
     * this field, its setter, getter, and `Signals.FilterValue` move to the
     * record and disappear from `Element`.
     *
     * Contract while transitional:
     * - Only historical mode writes it (via `decodeHistoricalElement`).
     * - Default stays `0`; no feature should depend on the field's presence
     *   here or expand its surface area (no JSON serialization, no public
     *   API beyond the existing setter, no consumers outside the binary
     *   pipeline's schema descriptors).
     */
    private _filterValue: number = 0;

    /**
     * Construct an element.
     * @param props The element properties.
     */
    constructor(props: ElementProps<G, S>) {
        super();
        this.type = props.type;
        this._style = props.style;
        this._code = props.code;
        this._modifiers = props.modifiers ?? {};
        this._displayName = props.displayName ?? `New ${props.type}`;
        this.id = props.id ?? uuid.v4();
        this.objectId = props.objectId ?? uuid.v4();
        this.source = props.source ?? localEventSource;
        this.elementClass = props.elementClass ?? "live";
        this.key =
            this.elementClass === "historical"
                ? getElementHistoricalKey(this)
                : getElementKey(this);
        this._start = props.start;
        this.time = props.time;
        this.heading = props.heading;
        this.course = props.course;
        this.speed = props.speed;
        this.hae = props.hae;
        this._stale = props.stale ?? neverStale;
        this.geometry = props.geometry;
        this._attributes = props.attributes ?? {};
    }

    /**
     * Construct an element.
     * @param props The element properties.
     */
    static create<G extends Geometry, S extends Style>(props: ElementProps<G, S>): Element<G, S> {
        return new Element(props);
    }

    getCode(): string | undefined {
        return this._code;
    }

    setCode(code: string): this {
        this._code = code;
        this.dirty(Element.Signals.Code);
        return this;
    }

    getModifiers(): Readonly<Record<string, string>> {
        return this._modifiers;
    }

    setModifiers(modifiers: Record<string, string>) {
        this._modifiers = { ...modifiers };
        this.dirty(Element.Signals.Modifiers);
        return this;
    }

    /** The visual style. */
    getStyle(): S {
        return this._style;
    }

    /**
     * Set the visual style.
     * @param style The new style.
     */
    setStyle(style: S): this {
        this._style = style;
        this.dirty(Element.Signals.Style);
        return this;
    }

    /**
     * Whether this element is exempt from stale-based eviction.
     *
     * Historical elements are versioned snapshots of the past — every chunk's
     * version of an entity coexists so the playhead can scrub across them, and
     * their `stale` marks when a version stopped being current, not an expiry.
     * They must never be swept. Live elements expire normally.
     *
     * Exposed as a neutral property so the store can honor it without knowing
     * what "historical" means; the classification itself lives here on the
     * domain object that owns `elementClass`.
     */
    get exemptFromStale(): boolean {
        return this.elementClass === "historical";
    }

    /** Current filter-value tag (see {@link _filterValue}). */
    get filterValue(): number {
        return this._filterValue;
    }

    /** Update the filter-value tag and notify listeners. */
    setFilterValue(value: number): this {
        this._filterValue = value;
        this.dirty(Element.Signals.FilterValue);
        return this;
    }

    /** The human-readable display name. */
    getDisplayName(): string {
        return this._displayName;
    }

    /**
     * Set the display name of the element.
     * @param name The name.
     */
    setDisplayName(name: string): this {
        this._displayName = name;
        this.dirty(Element.Signals.DisplayName);
        return this;
    }

    /** Unix timestamp (ms) when the event started, if known. */
    get start(): number | null | undefined {
        return this._start;
    }

    /** Set the start time. */
    setStart(start: number | null | undefined): this {
        this._start = start;
        return this;
    }

    /** Unix timestamp (ms) after which the element is considered stale. */
    get stale(): number {
        return this._stale;
    }

    /** Set the stale time. */
    setStale(stale: number): this {
        this._stale = stale;
        return this;
    }

    /** Custom key-value metadata attributes. */
    getAttributes(): Readonly<Record<string, unknown>> {
        return this._attributes;
    }

    /**
     * Serialize to a GeoJSON {@link PartialOCFeature} for the wire format.
     * Geometry extras (e.g. radius) and style properties are nested under
     * `properties.opencop.element`.
     */
    toFeature(): PartialOCFeature {
        return {
            type: "Feature",
            geometry: this.geometry.toGeoJSONGeometry(),
            properties: {
                opencop: {
                    id: this.id,
                    objectId: this.objectId,
                    displayName: this._displayName,
                    source: this.source,
                    timestampz: {
                        ...(this.start != null && { start: this.start }),
                        ...(this.time != null && { time: this.time }),
                        stale: this.stale,
                    },
                    attributes: this._attributes as Record<string, string | number>,
                    // TODO: Update OCBaseElement type to match new wire format
                    element: {
                        type: this.type,
                        ...(this._code !== undefined && { symbol: this._code }),
                        speed: this.speed,
                        course: this.course,
                        heading: this.heading,
                        hae: this.hae,
                        ...Object.fromEntries(
                            Object.entries(this._modifiers).map(([key, value]) => [
                                "modifier:" + key,
                                value,
                            ]),
                        ),
                        ...this.geometry.toProperties(),
                        ...this._style.toProperties(),
                    } as unknown as OCBaseElement,
                },
            },
        };
    }

    /**
     * Clone the element with a new instance id.
     * @param sameEvent Whether to keep the same objectId. Default is `false`.
     * @param stale Optional stale time. Defaults to `this.stale`.
     */
    clone(sameEvent: boolean = false, stale: number = this.stale): Element<G, S> {
        return new Element({
            type: this.type,
            id: uuid.v4(),
            objectId: sameEvent ? this.objectId : uuid.v4(),
            displayName: this._displayName,
            source: this.source,
            start: this.start,
            time: this.time,
            stale,
            // Preserve the class so a clone keeps the same store-keying scheme
            // (`source+id` for historical, `source+objectId` for live). Live and
            // historical now share one store, so a dropped class would silently
            // re-key a historical clone into the live keyspace.
            elementClass: this.elementClass,
            geometry: this.geometry.clone() as G,
            style: this._style.clone() as S,
            code: this._code,
            speed: this.speed,
            heading: this.heading,
            course: this.course,
            hae: this.hae,
            modifiers: { ...this._modifiers },
            attributes: { ...this._attributes },
        });
    }
}
