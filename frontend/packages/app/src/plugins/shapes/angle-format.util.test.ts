// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { beforeAll, describe, expect, it } from "vitest";
import i18next, { type TFunction } from "i18next";
import { formatAngleValue } from "./angle-format.util";

/** A real `t` with no resources, so each key renders its `defaultValue` template. */
let t: TFunction;

beforeAll(async () => {
    const i18n = i18next.createInstance();
    await i18n.init({ lng: "en", resources: {} });
    t = i18n.t;
});

describe("formatAngleValue", () => {
    it("formats degrees to two decimals", () => {
        expect(formatAngleValue(1.5, "degrees", t)).toBe("1.50 deg");
    });

    it("converts to mils and rounds to one decimal", () => {
        expect(formatAngleValue(1, "mils", t)).toBe("17.8 mils");
    });

    it("joins both units with a separator", () => {
        expect(formatAngleValue(1.5, "both", t)).toBe("1.50 deg | 26.7 mils");
    });

    it("formats zero", () => {
        expect(formatAngleValue(0, "degrees", t)).toBe("0.00 deg");
    });
});
