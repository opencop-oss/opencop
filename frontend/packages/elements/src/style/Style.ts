// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Reactive } from "../Reactive.ts";

/**
 * Clamp a number to the range [min, max].
 * @param value The value to clamp.
 * @param min Minimum bound.
 * @param max Maximum bound.
 */
function clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
}

/**
 * Abstract base class for all element visual styles.
 *
 * Provides shared properties (visibility, opacity, text color, z-index)
 * common to every style variant. Concrete subclasses add shape-specific
 * properties (e.g. fill color, stroke weight, icon size).
 */
export abstract class Style extends Reactive {
    /** Signal keys emitted by {@link Style}. */
    static Signals = {
        Visible: "visible",
        Opacity: "opacity",
        TextColor: "textColor",
        ZIndex: "ZIndex",
    } as const;
    private _visible: boolean = true;
    private _opacity: number = 1.0;
    private _textColor: string = "#ffffffff";
    private _zIndex?: number;

    /** Whether the style is visible. */
    isVisible(): boolean {
        return this._visible;
    }

    /** Toggle visibility of the style. */
    toggleVisible(): this {
        this._visible = !this._visible;
        this.dirty(Style.Signals.Visible);
        return this;
    }

    /** The opacity of the style, ranging [0, 1]. */
    getOpacity(): number {
        return this._opacity;
    }

    /**
     * Set the opacity of the style.
     * @param opacity The opacity, between [0, 1]. Clamped if out of bounds.
     */
    setOpacity(opacity: number): this {
        this._opacity = clamp(opacity, 0, 1);
        this.dirty(Style.Signals.Opacity);
        return this;
    }

    /** The z-index determining draw order relative to other elements. */
    getZIndex(): number | undefined {
        return this._zIndex;
    }

    /**
     * Set the z-index.
     * @param index The z-index value.
     */
    setZIndex(index: number): this {
        this._zIndex = index;
        this.dirty(Style.Signals.ZIndex);
        return this;
    }

    /**
     * Set the text label color.
     * @param color The color string.
     */
    setTextColor(color: string): this {
        this._textColor = color;
        this.dirty(Style.Signals.TextColor);
        return this;
    }

    /** The text label color. */
    getTextColor(): string {
        return this._textColor;
    }

    /** Clone the style. */
    abstract clone(): Style;

    /** Serialize to a plain object for the wire format. */
    abstract toProperties(): Record<string, unknown>;
}
