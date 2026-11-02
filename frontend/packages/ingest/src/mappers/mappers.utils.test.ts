// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { ElementMessage } from "@opencop/proto";
import * as uuid from "uuid";
import { DefaultTextBoxStyle } from "@opencop/elements";
import { buildMeta, textBox } from "./mappers.utils";

describe("buildMeta", () => {
    it("maps protobuf start and time fields into element metadata", () => {
        const id = uuid.parse("11111111-1111-4111-8111-111111111111");
        const source = uuid.parse("22222222-2222-4222-8222-222222222222");
        const message = ElementMessage.create({
            id,
            objectId: "event-1",
            source,
            displayName: "Test Event",
            stale: 3_000,
            start: 1_000,
            time: 2_000,
        });

        expect(buildMeta(message)).toEqual({
            id: "11111111-1111-4111-8111-111111111111",
            objectId: "event-1",
            displayName: "Test Event",
            source: "22222222-2222-4222-8222-222222222222",
            start: 1_000,
            time: 2_000,
            stale: 3_000,
        });
    });

    it("includes elementClass when passed in options", () => {
        const message = ElementMessage.create({
            id: uuid.parse("11111111-1111-4111-8111-111111111111"),
            objectId: "event-1",
            source: uuid.parse("22222222-2222-4222-8222-222222222222"),
            displayName: "Test Event",
            stale: 3_000,
        });

        const result = buildMeta(message, { elementClass: "historical" });
        expect(result.elementClass).toBe("historical");
    });

    it("appends idSuffix to the id when a non-empty suffix is given", () => {
        const message = ElementMessage.create({
            id: uuid.parse("11111111-1111-4111-8111-111111111111"),
            objectId: "event-1",
            source: uuid.parse("22222222-2222-4222-8222-222222222222"),
            displayName: "Test Event",
            stale: 3_000,
        });

        const result = buildMeta(message, { idSuffix: "7" });
        expect(result.id).toBe("11111111-1111-4111-8111-111111111111:7");
    });

    it("does not modify the id when idSuffix is an empty string", () => {
        const message = ElementMessage.create({
            id: uuid.parse("11111111-1111-4111-8111-111111111111"),
            objectId: "event-1",
            source: uuid.parse("22222222-2222-4222-8222-222222222222"),
            displayName: "Test Event",
            stale: 3_000,
        });

        const result = buildMeta(message, { idSuffix: "" });
        expect(result.id).toBe("11111111-1111-4111-8111-111111111111");
    });

    it("produces the same id as before when no options are passed", () => {
        const message = ElementMessage.create({
            id: uuid.parse("11111111-1111-4111-8111-111111111111"),
            objectId: "event-1",
            source: uuid.parse("22222222-2222-4222-8222-222222222222"),
            displayName: "Test Event",
            stale: 3_000,
        });

        expect(buildMeta(message).id).toBe(
            uuid.stringify(uuid.parse("11111111-1111-4111-8111-111111111111")),
        );
    });
});

describe("textBox", () => {
    it("decodes the box's content and how it is drawn", () => {
        const message = ElementMessage.create({
            values: {
                fillColor: { string: "#112233ff" },
                strokeColor: { string: "#445566ff" },
                strokeWeight: { double: 4 },
                strokeStyle: { string: "dashed" },
                text: { string: "HOLDING AREA" },
                textColor: { string: "#ff0000ff" },
                textSize: { double: 120 },
                textVisible: { bool: false },
            },
        });

        const style = textBox(message);
        expect(style.getFillColor()).toBe("#112233ff");
        expect(style.getStrokeColor()).toBe("#445566ff");
        expect(style.getStrokeWeight()).toBe(4);
        expect(style.getStrokePattern().name).toBe("dashed");
        expect(style.getText()).toBe("HOLDING AREA");
        expect(style.getTextColor()).toBe("#ff0000ff");
        expect(style.getTextSize()).toBe(120);
        expect(style.isTextVisible()).toBe(false);
    });

    it("falls back to the defaults for a message carrying no values", () => {
        const style = textBox(ElementMessage.create({}));
        expect(style.getFillColor()).toBe(DefaultTextBoxStyle.getFillColor());
        expect(style.getStrokeColor()).toBe(DefaultTextBoxStyle.getStrokeColor());
        expect(style.getText()).toBe(DefaultTextBoxStyle.getText());
        expect(style.getTextColor()).toBe(DefaultTextBoxStyle.getTextColor());
        expect(style.getTextSize()).toBe(DefaultTextBoxStyle.getTextSize());
        expect(style.isTextVisible()).toBe(DefaultTextBoxStyle.isTextVisible());
    });
});
