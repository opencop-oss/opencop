// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { StrokePattern, StrokeStyle, type StrokeStyleProps } from "./StrokeStyle";

/** Construction properties for a {@link ArrowStyle}. */
export interface ArrowStyleProps extends StrokeStyleProps {
    /** Size of the arrow head in pixels. */
    size?: number;
}

/**
 * Style for arrow elements: stroke properties (color, weight, pattern) plus
 * arrow-head size. Angle is derived from the polyline bearing, not stored here.
 */
export class ArrowStyle extends StrokeStyle {
    static readonly Signals = {
        ...StrokeStyle.Signals,
        Size: "size",
    } as const;

    private _size: number;

    constructor(props: ArrowStyleProps) {
        super(props);
        this._size = props.size ?? 15;
    }

    getSize(): number {
        return this._size;
    }

    setSize(size: number): this {
        this._size = size;
        this.dirty(ArrowStyle.Signals.Size);
        return this;
    }

    clone(): ArrowStyle {
        return new ArrowStyle({
            color: this.getColor(),
            pattern: this.getPattern(),
            weight: this.getWeight(),
            size: this._size,
        });
    }

    toProperties(): Record<string, unknown> {
        return {
            ...super.toProperties(),
            size: this._size,
        };
    }

    /** Inverse of {@link toProperties}. Reconstructs an {@link ArrowStyle} from a serialized property bag. */
    static fromProperties(props: Record<string, unknown>): ArrowStyle {
        return new ArrowStyle({
            color: props["strokeColor"] as string,
            weight: typeof props["strokeWeight"] === "number" ? props["strokeWeight"] : 2,
            pattern: StrokePattern.fromName((props["strokeStyle"] as string) ?? "solid"),
            size: props["size"] as number | undefined,
        });
    }
}
