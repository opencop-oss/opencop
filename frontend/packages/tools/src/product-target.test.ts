// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import {
    ELEMENT_STORE_ID,
    SESSION_STORE_ID,
    producedElementType,
    productionError,
    styleError,
} from "./product-target";

const style = [{ label: "Color" }];

describe("producedElementType", () => {
    it("is the type of a product in the element store, and undefined anywhere else", () => {
        expect(producedElementType({ store: ELEMENT_STORE_ID, type: "line" })).toBe("line");
        expect(producedElementType({ store: SESSION_STORE_ID, type: "m:ruler" })).toBeUndefined();
        expect(producedElementType(undefined)).toBeUndefined();
    });
});

describe("productionError", () => {
    it("accepts no product, and a valid store and type", () => {
        expect(productionError(undefined)).toBeNull();
        expect(productionError({ store: ELEMENT_STORE_ID, type: "line" })).toBeNull();
    });

    it("refuses a session type without a namespace", () => {
        expect(productionError({ store: SESSION_STORE_ID, type: "ruler" })).toMatch(/namespaced/);
    });
});

describe("styleError", () => {
    it("accepts style on a tool whose product is an element", () => {
        expect(styleError({ store: ELEMENT_STORE_ID, type: "line" }, style)).toBeNull();
    });

    it("accepts a tool that declares no style, whatever its product", () => {
        expect(styleError({ store: SESSION_STORE_ID, type: "m:ruler" }, undefined)).toBeNull();
        expect(styleError({ store: SESSION_STORE_ID, type: "m:ruler" }, [])).toBeNull();
    });

    it("refuses style on a tool whose product is not an element", () => {
        expect(styleError({ store: SESSION_STORE_ID, type: "m:ruler" }, style)).toMatch(
            /not an element/,
        );
        expect(styleError(undefined, style)).toMatch(/not an element/);
    });
});
