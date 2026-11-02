// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { StrokePattern } from "./StrokeStyle";
import { Style } from "./Style";

/** Construction properties for a {@link ShapeStyle}. */
export interface ShapeStyleProps {
    /** The interior fill color. */
    fillColor: string;
    /** The outline stroke color. */
    strokeColor: string;
    /** The outline dash pattern. */
    strokePattern: StrokePattern;
    /** The outline weight in pixels. */
    strokeWeight: number;
}

/**
 * Style for filled shape elements such as circles, ellipses, polygons,
 * and rectangles.
 */
export class ShapeStyle extends Style {
    /** Signal keys emitted by {@link ShapeStyle}. */
    static readonly Signals = {
        ...Style.Signals,
        FillColor: "fillColor",
        StrokeColor: "strokeColor",
        StrokePattern: "strokePattern",
        StrokeWeight: "strokeWeight",
    } as const;

    private _fillColor: string;
    private _strokeColor: string;
    private _strokePattern: StrokePattern;
    private _strokeWeight: number;

    /**
     * @param props The shape style properties.
     */
    constructor({ fillColor, strokeColor, strokePattern, strokeWeight }: ShapeStyleProps) {
        super();
        this._fillColor = fillColor;
        this._strokeColor = strokeColor;
        this._strokePattern = strokePattern;
        this._strokeWeight = strokeWeight;
    }

    /** The interior fill color. */
    getFillColor(): string {
        return this._fillColor;
    }

    /**
     * Set the fill color.
     * @param color The color string.
     */
    setFillColor(color: string): this {
        this._fillColor = color;
        this.dirty(ShapeStyle.Signals.FillColor);
        return this;
    }

    /** The outline stroke color. */
    getStrokeColor(): string {
        return this._strokeColor;
    }

    /**
     * Set the outline stroke color.
     * @param color The color string.
     */
    setStrokeColor(color: string): this {
        this._strokeColor = color;
        this.dirty(ShapeStyle.Signals.StrokeColor);
        return this;
    }

    /** The outline {@link StrokePattern}. */
    getStrokePattern(): StrokePattern {
        return this._strokePattern;
    }

    /**
     * Set the outline stroke pattern.
     * @param pattern The {@link StrokePattern}.
     */
    setStrokePattern(pattern: StrokePattern) {
        this._strokePattern = pattern;
        this.dirty(ShapeStyle.Signals.StrokePattern);
        return this;
    }

    /** The outline weight in pixels. */
    getStrokeWeight(): number {
        return this._strokeWeight;
    }

    /**
     * Set the outline stroke weight.
     * @param weight The weight in pixels.
     */
    setStrokeWeight(weight: number) {
        this._strokeWeight = weight;
        this.dirty(ShapeStyle.Signals.StrokeWeight);
        return this;
    }

    /** Clone the shape style. */
    clone(): ShapeStyle {
        return new ShapeStyle({
            fillColor: this._fillColor,
            strokeColor: this._strokeColor,
            strokeWeight: this._strokeWeight,
            strokePattern: this._strokePattern,
        });
    }

    /** Serialize to a plain object for the wire format. */
    toProperties(): Record<string, unknown> {
        return {
            fillColor: this._fillColor,
            strokeColor: this._strokeColor,
            strokeWeight: this._strokeWeight,
            strokeStyle: this._strokePattern.name,
        };
    }

    /** Inverse of {@link toProperties}. Reconstructs a {@link ShapeStyle} from a serialized property bag. */
    static fromProperties(props: Record<string, unknown>): ShapeStyle {
        return new ShapeStyle({
            fillColor: props["fillColor"] as string,
            strokeColor: props["strokeColor"] as string,
            strokeWeight: typeof props["strokeWeight"] === "number" ? props["strokeWeight"] : 2,
            strokePattern: StrokePattern.fromName((props["strokeStyle"] as string) ?? "solid"),
        });
    }
}
