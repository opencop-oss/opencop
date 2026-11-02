// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Style } from "./Style";

/** Construction properties for an {@link IconStyle}. */
export interface IconStyleProps {
    /** Display size in pixels. Defaults to `15`. */
    size?: number;
    /** Rotation angle in degrees. Defaults to `0`. */
    angle?: number;
}

/**
 * Style for icon-based elements such as symbols.
 *
 * The icon image itself is resolved externally, from `element.code`, by
 * whoever renders the element — `@opencop/layers` on the map,
 * `@opencop/symbology`'s `renderPreview` for UI thumbnails. This style holds
 * display preferences only: size and angle.
 *
 * It used to carry a resolved `src`, which is why `@opencop/elements` once
 * depended on a renderer. That field never crossed the wire (see
 * {@link IconStyle.toProperties}) and the map never read it — it resolved the
 * icon from `code` itself — so it was removed along with the dependency.
 */
export class IconStyle extends Style {
    /** Signal keys emitted by {@link IconStyle}. */
    static readonly Signals = {
        ...Style.Signals,
        Source: "source",
        Size: "size",
        Angle: "angle",
    } as const;

    private _size: number;
    private _angle: number;

    /**
     * @param props The icon style properties.
     */
    constructor({ size, angle }: IconStyleProps) {
        super();
        this._size = size ?? 15;
        this._angle = angle ?? 0;
    }

    /** The display size in pixels. */
    getSize(): number {
        return this._size;
    }

    /**
     * Set the display size.
     * @param size The size in pixels.
     */
    setSize(size: number): this {
        this._size = size;
        this.dirty(IconStyle.Signals.Size);
        return this;
    }

    /** The rotation angle in degrees. */
    getAngle(): number {
        return this._angle;
    }

    /**
     * Set the rotation angle.
     * @param angle The angle in degrees.
     */
    setAngle(angle: number): this {
        this._angle = angle;
        this.dirty(IconStyle.Signals.Angle);
        return this;
    }

    /** Clone the icon style. */
    clone(): Style {
        return new IconStyle({ size: this._size, angle: this._angle });
    }

    /** Serialize to a plain object for the wire format. */
    toProperties(): Record<string, unknown> {
        return {
            size: this._size,
            angle: this._angle,
        };
    }

    /** Inverse of {@link toProperties}. Reconstructs an {@link IconStyle} from a serialized property bag. */
    static fromProperties(props: Record<string, unknown>): IconStyle {
        return new IconStyle({
            size: typeof props["size"] === "number" ? props["size"] : undefined,
            angle: typeof props["angle"] === "number" ? props["angle"] : undefined,
        });
    }
}
