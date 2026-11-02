// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    ArrowStyle,
    ShapeStyle,
    StrokeStyle,
    StrokePattern,
    TextBoxStyle,
} from "@opencop/elements";
import type { ElementClass, ElementMeta } from "@opencop/elements";
import type { ElementMessage } from "@opencop/proto";
import * as uuid from "uuid";

/** Options that influence how an ElementMessage is decoded into an Element. */
export interface DecodeOptions {
    /**
     * Pipeline classification for the resulting Element. Controls keying
     * strategy in the Element constructor. Defaults to "live".
     */
    elementClass?: ElementClass;
    /**
     * When provided and non-empty, appended to the proto id as
     * "<uuid>:<idSuffix>" to produce a scope-specific element key.
     * Absent for live decoding; live elements continue to key by source + objectId.
     */
    idSuffix?: string;
}

function normalizeTimestamp(value: unknown): number | undefined {
    if (value == null) return undefined;
    const timestamp = Number(value);
    return Number.isFinite(timestamp) && timestamp > 0 ? timestamp : undefined;
}

export function pattern(string: string): StrokePattern {
    let pattern: StrokePattern;

    switch (string) {
        case "solid":
            pattern = StrokePattern.Solid;
            break;
        case "dashed":
            pattern = StrokePattern.Dashed;
            break;
        case "dotted":
            pattern = StrokePattern.Dotted;
            break;
        default:
            pattern = StrokePattern.Outlined;
    }

    return pattern;
}

export function stroke(message: ElementMessage): StrokeStyle {
    return StrokeStyle.fromProperties({
        strokeColor: message.values["strokeColor"].string,
        strokeWeight: message.values["strokeWeight"].double,
        strokeStyle: message.values["strokeStyle"].string,
    });
}

export function arrow(message: ElementMessage): ArrowStyle {
    return ArrowStyle.fromProperties({
        strokeColor: message.values["strokeColor"].string,
        strokeWeight: message.values["strokeWeight"].double,
        strokeStyle: message.values["strokeStyle"].string,
        size: message.values["size"]?.double,
    });
}

export function shape(message: ElementMessage): ShapeStyle {
    return ShapeStyle.fromProperties({
        fillColor: message.values["fillColor"].string,
        strokeColor: message.values["strokeColor"].string,
        strokeWeight: message.values["strokeWeight"].double,
        strokeStyle: message.values["strokeStyle"].string,
    });
}

/** Shape style plus the box's content and how it is drawn; every field defaults if absent. */
export function textBox(message: ElementMessage): TextBoxStyle {
    return TextBoxStyle.fromProperties({
        fillColor: message.values["fillColor"]?.string,
        strokeColor: message.values["strokeColor"]?.string,
        strokeWeight: message.values["strokeWeight"]?.double,
        strokeStyle: message.values["strokeStyle"]?.string,
        text: message.values["text"]?.string,
        textColor: message.values["textColor"]?.string,
        textSize: message.values["textSize"]?.double,
        textVisible: message.values["textVisible"]?.bool,
    });
}

/**
 * Stringify a 16-byte UUID field, tolerating an absent or malformed value.
 *
 * `ElementMessage`s can arrive with an empty or wrong-length `id`/`source` byte
 * field (historical test/replay data does this); `uuid.stringify` throws a
 * `TypeError("Stringified UUID is invalid")` on those. Returning `undefined`
 * instead lets the `Element` constructor fall back (`id ?? uuid.v4()`,
 * `source ?? localEventSource`) so a single bad field neither throws nor drops
 * the element. This matters most for the historical drain, which decodes an
 * entire batch inside one microtask — an escaped throw there loses every element
 * queued in that batch (and surfaces as an uncaught error), not just the bad one.
 */
function safeStringify(bytes: Uint8Array | undefined): string | undefined {
    if (!bytes || bytes.byteLength !== 16) return undefined;
    try {
        return uuid.stringify(bytes);
    } catch {
        return undefined;
    }
}

export function buildMeta(message: ElementMessage, options?: DecodeOptions): Partial<ElementMeta> {
    const baseId = safeStringify(message.id);
    const id =
        baseId != null && options?.idSuffix != null && options.idSuffix !== ""
            ? `${baseId}:${options.idSuffix}`
            : baseId;
    return {
        id,
        objectId: message.objectId,
        displayName: message.displayName,
        source: safeStringify(message.source),
        start: normalizeTimestamp(message.start),
        time: normalizeTimestamp(message.time),
        stale: Number(message.stale),
        speed: message.values["speed"]?.double ?? undefined,
        hae: message.values["hae"]?.double ?? undefined,
        course: message.values["course"]?.double ?? undefined,
        heading: message.values["heading"]?.double ?? undefined,
        elementClass: options?.elementClass,
    };
}
