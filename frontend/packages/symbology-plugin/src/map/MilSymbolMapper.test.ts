// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { Symbol } from "@opencop/elements";
import { MilSymbolMapper } from "./MilSymbolMapper";

describe("MilSymbolMapper", () => {
    it("draws a SIDC through the symbology renderer at the selected size", () => {
        const element = Symbol.create({ position: [0, 0], sidc: "10031000001211000000" });

        const [icon] = new MilSymbolMapper().map(element, 40);

        expect(icon.style).toMatchObject({ type: "icon", size: 40 });
    });

    it("labels a named symbol and draws a course arrow when it has a course", () => {
        const element = Symbol.create({
            position: [0, 0],
            sidc: "10031000001211000000",
            meta: { displayName: "Alpha", course: 90 },
        });

        const ids = new MilSymbolMapper().map(element, 40).map((p) => p.id);

        expect(ids).toEqual(["symbol", "label", "course"]);
    });
});
