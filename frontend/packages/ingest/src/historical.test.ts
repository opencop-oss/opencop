// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { neverStale } from "@opencop/elements";
import { ElementMessage } from "@opencop/proto";
import * as uuid from "uuid";
import { describe, expect, it } from "vitest";

import { decodeHistoricalElement } from "./historical";
import { MessageProcessor } from "./MessageProcessor";
import { SymbolMapper } from "./mappers/SymbolMapper";

function makeProcessor(): MessageProcessor {
    return new MessageProcessor().register("symbol", new SymbolMapper());
}

function makeMessage(): ElementMessage {
    return ElementMessage.create({
        type: "symbol",
        id: uuid.parse("11111111-1111-4111-8111-111111111111"),
        objectId: "carry-forward-entity",
        source: uuid.parse("22222222-2222-4222-8222-222222222222"),
        displayName: "Entity",
        positions: [38, -77],
        stale: 0,
        start: 0,
        time: 0,
        values: {
            symbol: { string: "SFGPUCI----D***" },
            size: { double: 20 },
        },
    });
}

describe("decodeHistoricalElement", () => {
    it("produces distinct id and key for the same proto id at different chunk indices", () => {
        const processor = makeProcessor();
        const message = makeMessage();

        const a = decodeHistoricalElement(message, processor, 19);
        const b = decodeHistoricalElement(message, processor, 20);

        expect(a).toBeDefined();
        expect(b).toBeDefined();
        expect(a!.id).toBe("11111111-1111-4111-8111-111111111111:19");
        expect(b!.id).toBe("11111111-1111-4111-8111-111111111111:20");
        expect(a!.id).not.toBe(b!.id);
        expect(a!.key).not.toBe(b!.key);
        expect(a!.objectId).toBe(b!.objectId);
    });

    it("produces identical id and key when the same chunk index is decoded twice", () => {
        const processor = makeProcessor();
        const message = makeMessage();

        const first = decodeHistoricalElement(message, processor, 19);
        const second = decodeHistoricalElement(message, processor, 19);

        expect(first).toBeDefined();
        expect(second).toBeDefined();
        expect(first!.id).toBe(second!.id);
        expect(first!.key).toBe(second!.key);
    });

    it("returns a historical-class element with stale overridden to neverStale", () => {
        const processor = makeProcessor();
        const message = makeMessage();

        const element = decodeHistoricalElement(message, processor, 0);

        expect(element).toBeDefined();
        expect(element!.elementClass).toBe("historical");
        expect(element!.stale).toBe(neverStale);
    });

    // Regression: replay/test data can carry an empty (or wrong-length) `id`
    // byte field. `uuid.stringify` throws "Stringified UUID is invalid" on those;
    // an escaped throw in the historical drain loses the whole decode batch
    // (it runs in a single microtask). Decode must instead tolerate the bad
    // field and let the Element fall back to a synthesized id.
    it("decodes (does not throw) when the proto id byte field is empty", () => {
        const processor = makeProcessor();
        const message = ElementMessage.create({
            ...makeMessage(),
            id: new Uint8Array(0),
        });

        let element;
        expect(() => {
            element = decodeHistoricalElement(message, processor, 7);
        }).not.toThrow();
        expect(element).toBeDefined();
        // Falls back to a synthesized uuid (no chunk suffix appended to nothing),
        // so it's a valid id string rather than the literal "undefined:7".
        expect(element!.id).not.toContain("undefined");
        expect(element!.id).toMatch(/^[0-9a-f-]{36}$/i);
        // The valid source field still resolves normally.
        expect(element!.source).toBe("22222222-2222-4222-8222-222222222222");
    });

    it("decodes (does not throw) when the proto source byte field is empty", () => {
        const processor = makeProcessor();
        const message = ElementMessage.create({
            ...makeMessage(),
            source: new Uint8Array(0),
        });

        let element;
        expect(() => {
            element = decodeHistoricalElement(message, processor, 3);
        }).not.toThrow();
        expect(element).toBeDefined();
        // Valid id is unaffected; the empty source falls back in the constructor.
        expect(element!.id).toBe("11111111-1111-4111-8111-111111111111:3");
    });
});
