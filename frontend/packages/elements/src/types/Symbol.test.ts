// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Symbol } from "./Symbol";
import { IconStyle } from "../style/IconStyle";

/**
 * Icon *resolution* is deliberately absent from this file.
 *
 * `Symbol.iconSource` / `iconUrl` / `styleFromCode` used to resolve a code to an
 * image here, which is what forced `@opencop/elements` to depend on
 * `@opencop/symbology`. Elements stores codes and never interprets them, so the
 * corresponding coverage lives where the resolution now happens:
 *   - media icons  → the app's media-icons plugin, `media-icon.test.ts`
 *   - SIDC render  → `@opencop/symbology`'s `render.test.ts`
 */

describe("Symbol.create", () => {
    it("leaves the code undefined when sidc is omitted — there is no default", () => {
        // A default here would be a code this package cannot interpret, and the
        // previous one made a wire event with no symbol render as friendly.
        // Absence is now explicit and the renderer draws its unknown placeholder.
        expect(Symbol.create({ position: [0, 0] }).getCode()).toBeUndefined();
    });

    it("stores the supplied code verbatim without interpreting it", () => {
        const mediaCode = "opencop:url:/v1/layers/abc/media/def#32x48";
        expect(Symbol.create({ position: [0, 0], sidc: mediaCode }).getCode()).toBe(mediaCode);
        expect(Symbol.create({ position: [0, 0], sidc: "SFGPUCI----D" }).getCode()).toBe(
            "SFGPUCI----D",
        );
    });

    it("builds an IconStyle carrying display preferences only — no resolved image", () => {
        const element = Symbol.create({ position: [0, 0], sidc: "SFGPUCI----D", size: 25 });
        const style = element.getStyle();

        expect(style).toBeInstanceOf(IconStyle);
        expect(style.getSize()).toBe(25);
        // The icon is resolved from `code` at render time. `IconStyle` carries
        // size and angle and nothing else — see its own doc comment.
        expect(Object.keys(style.toProperties()).sort()).toEqual(["angle", "size"]);
    });

    it("honours an explicitly supplied style", () => {
        const style = new IconStyle({ size: 40, angle: 90 });
        const element = Symbol.create({ position: [0, 0], sidc: "SFGPUCI----D", style });

        expect(element.getStyle()).toBe(style);
        expect(element.getStyle().getAngle()).toBe(90);
    });
});
