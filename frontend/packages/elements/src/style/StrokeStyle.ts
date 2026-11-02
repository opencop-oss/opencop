// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Style } from "./Style";

/**
 * A dash pattern for stroked lines.
 *
 * The {@link dashArray} defines the [dash, gap] lengths. Use the static
 * constants ({@link Solid}, {@link Dashed}, {@link Dotted}, {@link Outlined})
 * for common patterns, or construct a custom one.
 */
export class StrokePattern {
    /** The [dash, gap] lengths. `[0, 0]` means a solid line. */
    dashArray: [number, number];
    /** Optional human-readable name (e.g. `"solid"`, `"dashed"`). */
    name?: string;

    /**
     * Look up a built-in pattern by name.
     * @param name One of `"solid"`, `"outlined"`, `"dashed"`, `"dotted"`.
     * @returns The matching {@link StrokePattern}. Falls back to {@link Solid}.
     */
    static fromName(name: string): StrokePattern {
        switch (name) {
            default:
            case "solid":
                return StrokePattern.Solid;
            case "outlined":
                return StrokePattern.Outlined;
            case "dashed":
                return StrokePattern.Dashed;
            case "dotted":
                return StrokePattern.Dotted;
        }
    }

    /**
     * @param dashArray The [dash, gap] lengths.
     * @param name Optional human-readable name.
     */
    constructor(dashArray: [number, number], name?: string) {
        this.dashArray = dashArray;
        this.name = name;
    }

    /** A solid (continuous) line. */
    static get Solid() {
        return new StrokePattern([0, 0], "solid");
    }

    /** A solid line rendered with an outline effect. */
    static get Outlined() {
        return new StrokePattern([0, 0], "outlined");
    }

    /** A dashed line (`[4, 4]`). */
    static get Dashed() {
        return new StrokePattern([4, 4], "dashed");
    }

    /** A dotted line (`[2, 2]`). */
    static get Dotted() {
        return new StrokePattern([2, 2], "dotted");
    }
}

/** Construction properties for a {@link StrokeStyle}. */
export interface StrokeStyleProps {
    /** The stroke color. */
    color: string;
    /** The dash pattern. */
    pattern: StrokePattern;
    /** The stroke weight in pixels. */
    weight: number;
}

/**
 * Style for stroked (line-based) elements such as arrows, lines, and angles.
 */
export class StrokeStyle extends Style {
    /** Signal keys emitted by {@link StrokeStyle}. */
    static readonly Signals = {
        ...Style.Signals,
        Color: "color",
        Pattern: "pattern",
        Weight: "weight",
    } as const;
    private _color;
    private _pattern;
    private _weight;

    /**
     * @param props The stroke style properties.
     */
    constructor({ color, pattern, weight }: StrokeStyleProps) {
        super();
        this._color = color;
        this._pattern = pattern;
        this._weight = weight;
    }

    /** The stroke color. */
    getColor(): string {
        return this._color;
    }

    /**
     * Set the stroke color.
     * @param color The color string.
     */
    setColor(color: string): this {
        this._color = color;
        this.dirty(StrokeStyle.Signals.Color);
        return this;
    }

    /** The {@link StrokePattern} of the stroke. */
    getPattern(): StrokePattern {
        return this._pattern;
    }

    /**
     * Set the stroke pattern.
     * @param pattern The {@link StrokePattern}.
     */
    setPattern(pattern: StrokePattern) {
        this._pattern = pattern;
        this.dirty(StrokeStyle.Signals.Pattern);
        return this;
    }

    /** The stroke weight in pixels. */
    getWeight(): number {
        return this._weight;
    }

    /**
     * Set the stroke weight.
     * @param weight The weight in pixels.
     */
    setWeight(weight: number) {
        this._weight = weight;
        this.dirty(StrokeStyle.Signals.Weight);
        return this;
    }

    /** Clone the stroke style. */
    clone(): StrokeStyle {
        return new StrokeStyle({
            color: this._color,
            pattern: this._pattern,
            weight: this._weight,
        });
    }

    /** Serialize to a plain object for the wire format. */
    toProperties(): Record<string, unknown> {
        return {
            strokeColor: this._color,
            strokeWeight: this._weight,
            strokeStyle: this._pattern.name,
        };
    }

    /** Inverse of {@link toProperties}. Reconstructs a {@link StrokeStyle} from a serialized property bag. */
    static fromProperties(props: Record<string, unknown>): StrokeStyle {
        return new StrokeStyle({
            color: props["strokeColor"] as string,
            weight: typeof props["strokeWeight"] === "number" ? props["strokeWeight"] : 2,
            pattern: StrokePattern.fromName((props["strokeStyle"] as string) ?? "solid"),
        });
    }
}
