// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Style } from "./Style";

/** Construction properties for a {@link PointStyle}. */
export interface PointStyleProps {
    /** The fill color. */
    color: string;
    /** The radius in pixels. */
    radius: number;
}

/**
 * Style for simple point elements rendered as colored circles.
 */
export class PointStyle extends Style {
    /** Signal keys emitted by {@link PointStyle}. */
    static readonly Signals = {
        ...Style.Signals,
        Color: "color",
        Radius: "radius",
    } as const;

    private _color: string;
    private _radius: number;

    /**
     * @param props The point style properties.
     */
    constructor({ color, radius }: PointStyleProps) {
        super();
        this._color = color;
        this._radius = radius;
    }

    /** The fill color. */
    getColor(): string {
        return this._color;
    }

    /**
     * Set the fill color.
     * @param color The color string.
     */
    setColor(color: string): this {
        this._color = color;
        this.dirty(PointStyle.Signals.Color);
        return this;
    }

    /** The radius in pixels. */
    getRadius(): number {
        return this._radius;
    }

    /**
     * Set the radius.
     * @param radius The radius in pixels.
     */
    setRadius(radius: number): this {
        this._radius = radius;
        this.dirty(PointStyle.Signals.Radius);
        return this;
    }

    /** Clone the point style. */
    clone(): PointStyle {
        return new PointStyle({
            color: this._color,
            radius: this._radius,
        });
    }

    /** Serialize to a plain object for the wire format. */
    toProperties(): Record<string, unknown> {
        return {
            fillColor: this._color,
            radius: this._radius,
        };
    }

    /** Inverse of {@link toProperties}. Reconstructs a {@link PointStyle} from a serialized property bag. */
    static fromProperties(props: Record<string, unknown>): PointStyle {
        return new PointStyle({
            color: props["fillColor"] as string,
            radius: typeof props["radius"] === "number" ? props["radius"] : 6,
        });
    }
}
