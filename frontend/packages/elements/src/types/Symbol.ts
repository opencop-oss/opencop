// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Point as PointGeometry } from "../geometry/Point";
import { type TypeDef, type TypeDefProps } from "./TypeDef";
import { Element } from "../Element";
import type { Position2D } from "../geometry/Geometry";
import { IconStyle } from "../style/IconStyle";

/** Construction properties for {@link Symbol.create}. */
export interface SymbolProps extends TypeDefProps<IconStyle> {
    /** The symbol position. */
    position: Position2D;
    /** The symbol code — a MIL-STD-2525 SIDC. */
    sidc?: string;
    /** Optional display size override in pixels. */
    size?: number;
}

export type SymbolElement = Element<PointGeometry, IconStyle>;

/**
 * Type definition for military symbol elements.
 *
 * A symbol is a {@link PointGeometry} with {@link IconStyle}. The symbol code —
 * a MIL-STD-2525 SIDC — is stored in `element.code`. An imported image is a
 * different type, `MediaIcon`.
 *
 * **This package resolves codes to images nowhere.** A symbol carries its code;
 * whoever renders it turns that code into pixels (`IconElementLayer` on the map,
 * `Renderer.renderPreview` in UI thumbnails). That is the same rule
 * {@link IconStyle} states for itself and the one `Graphic` — also SIDC-bearing
 * — already follows, and it is what keeps `@opencop/elements` free of a
 * dependency on `@opencop/symbology`.
 *
 * **There is no default code.** A symbol created without one has `code`
 * `undefined`, and the renderer draws its own "unknown" placeholder. This
 * package cannot pick a sensible default because it cannot read a code: the
 * previous `SymbolDefaults.sidc` was a mutable global the symbol editor wrote
 * and the decode path read, so a wire event that omitted its symbol rendered as
 * whatever the operator last selected — usually *friendly*. Absent data must
 * not read as friendly. Callers that want a specific starting code pass one.
 */
export const Symbol = {
    type: "symbol",

    /** Type guard narrowing to a symbol element. */
    is(element: Element): element is SymbolElement {
        return element.type === this.type;
    },

    /**
     * Create a symbol element from a symbol code and position.
     *
     * `sidc` is optional; omitting it leaves `code` `undefined` rather than
     * substituting a default (see the note above).
     *
     * @param props The symbol properties.
     */
    create({ position, sidc, size, style, meta }: SymbolProps): SymbolElement {
        return new Element({
            ...meta,
            type: this.type,
            code: sidc,
            geometry: new PointGeometry(position),
            style: style ?? new IconStyle({ size }),
        });
    },
} as const satisfies TypeDef<PointGeometry, IconStyle>;
