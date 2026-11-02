// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { ShapeStyle, type ShapeStyleProps } from "./ShapeStyle";
import { StrokePattern } from "./StrokeStyle";

/** Construction properties for a {@link TextBoxStyle}. */
export interface TextBoxStyleProps extends ShapeStyleProps {
    /** The content drawn inside the box. */
    text: string;
    textColor: string;
    /** Glyph size in metres, so it scales with the map. */
    textSize: number;
    textVisible: boolean;
}

/**
 * The most content a textbox will hold.
 *
 * Far past anything a box on a map wants to say, so it is a bound rather than a
 * limit the operator meets. It exists because whoever draws the box re-measures
 * this content on every edit and every move, and that cost grows with its
 * length. Enforced here as well as in the editor, so a wire value cannot
 * bypass the editor's `maxLength`.
 */
export const MAX_TEXT_LENGTH = 4_000;

const DEFAULTS = {
    fillColor: "#00000059",
    strokeColor: "#ffffffff",
    strokeWeight: 2,
    text: "",
    textColor: "#ffffffff",
    textSize: 2000,
    textVisible: true,
} as const;

/**
 * A {@link ShapeStyle} plus the box's content and how it is drawn. Per-type
 * values live on the type's own style here — as `IconStyle` holds a symbol's
 * size and angle — and serialize flat into the element bag.
 */
export class TextBoxStyle extends ShapeStyle {
    static readonly Signals = {
        ...ShapeStyle.Signals,
        Text: "text",
        TextSize: "textSize",
        TextVisible: "textVisible",
    } as const;

    private _text: string;
    private _textSize: number;
    private _textVisible: boolean;

    constructor({ text, textColor, textSize, textVisible, ...shape }: TextBoxStyleProps) {
        super(shape);
        this._text = text;
        this._textSize = textSize;
        this._textVisible = textVisible;
        this.setTextColor(textColor);
    }

    getText(): string {
        return this._text;
    }

    setText(text: string): this {
        this._text = text;
        this.dirty(TextBoxStyle.Signals.Text);
        return this;
    }

    getTextSize(): number {
        return this._textSize;
    }

    setTextSize(size: number): this {
        this._textSize = size;
        this.dirty(TextBoxStyle.Signals.TextSize);
        return this;
    }

    isTextVisible(): boolean {
        return this._textVisible;
    }

    setTextVisible(visible: boolean): this {
        this._textVisible = visible;
        this.dirty(TextBoxStyle.Signals.TextVisible);
        return this;
    }

    clone(): TextBoxStyle {
        return new TextBoxStyle({
            fillColor: this.getFillColor(),
            strokeColor: this.getStrokeColor(),
            strokeWeight: this.getStrokeWeight(),
            strokePattern: this.getStrokePattern(),
            text: this._text,
            textColor: this.getTextColor(),
            textSize: this._textSize,
            textVisible: this._textVisible,
        });
    }

    toProperties(): Record<string, unknown> {
        return {
            ...super.toProperties(),
            text: this._text,
            textColor: this.getTextColor(),
            textSize: this._textSize,
            textVisible: this._textVisible,
        };
    }

    /** Every field defaults, so older wire data decodes to a blank box. */
    static fromProperties(props: Record<string, unknown>): TextBoxStyle {
        const shape = ShapeStyle.fromProperties(props);
        return new TextBoxStyle({
            // `ShapeStyle` casts the colours through rather than defaulting them,
            // so an absent one arrives undefined and `hexToRgba` would throw.
            fillColor: shape.getFillColor() ?? DEFAULTS.fillColor,
            strokeColor: shape.getStrokeColor() ?? DEFAULTS.strokeColor,
            strokeWeight: shape.getStrokeWeight(),
            strokePattern: shape.getStrokePattern(),
            text:
                typeof props["text"] === "string"
                    ? props["text"].slice(0, MAX_TEXT_LENGTH)
                    : DEFAULTS.text,
            textColor:
                typeof props["textColor"] === "string" ? props["textColor"] : DEFAULTS.textColor,
            // Finite and positive, not merely a number: the renderer multiplies
            // by this and the wrap divides by it, so a NaN or a zero from a
            // non-UI client would spread through both. A NaN also defeats the
            // size field's resync, which compares with `!==`, and spins it.
            textSize:
                typeof props["textSize"] === "number" &&
                Number.isFinite(props["textSize"]) &&
                props["textSize"] > 0
                    ? props["textSize"]
                    : DEFAULTS.textSize,
            textVisible:
                typeof props["textVisible"] === "boolean"
                    ? props["textVisible"]
                    : DEFAULTS.textVisible,
        });
    }
}

/** Default style for textbox elements. */
export const DefaultTextBoxStyle = new TextBoxStyle({
    fillColor: DEFAULTS.fillColor,
    strokeColor: DEFAULTS.strokeColor,
    strokeWeight: DEFAULTS.strokeWeight,
    strokePattern: StrokePattern.Solid,
    text: DEFAULTS.text,
    textColor: DEFAULTS.textColor,
    textSize: DEFAULTS.textSize,
    textVisible: DEFAULTS.textVisible,
});
