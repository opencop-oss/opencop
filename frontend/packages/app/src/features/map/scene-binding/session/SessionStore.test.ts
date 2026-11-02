// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { Line, Polygon, type DraftObject } from "@opencop/elements";
import { ELEMENT_STORE_ID, createSequentialGrammar, type ToolDescriptor } from "@opencop/tools";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { measureDescriptor } from "~/plugins/measure/measure.descriptor";
import { lineMeasurement } from "~/plugins/measure/measurement.model";
import type { Session } from "../../interaction-runtime/session/session";
import { createSessionStore, type SessionObject } from "./SessionStore";

const polygon: ToolDescriptor = {
    id: "polygon",
    label: "polygon",
    icon: () => null,
    produces: { store: ELEMENT_STORE_ID, type: "polygon" },
    draw: {
        grammar: createSequentialGrammar({
            min: 3,
            construction: "path",
            create: (cursor) => Polygon.create({ positions: [...cursor] }),
        }),
    },
};
const tools: Record<string, ToolDescriptor> = {
    [measureDescriptor.id]: measureDescriptor,
    [polygon.id]: polygon,
};

function drawing(descriptorId: string, draft: DraftObject): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: false,
        locked: 1,
        draft: { object: draft, placed: true },
    } as Session;
}

/** A connected store over the running session, with every batch it emits. */
function connected() {
    const store = createSessionStore((id) => tools[id]);
    const events: { event: string; objects: SessionObject[] }[] = [];
    for (const event of ["add", "update", "remove"] as const)
        store.on(event, (objects) => events.push({ event, objects }));
    disconnects.push(store.connect());
    return { store, events };
}

const disconnects: (() => void)[] = [];

afterEach(() => {
    disconnects.splice(0).forEach((disconnect) => disconnect());
    resetInteractionForTest();
});

describe("createSessionStore", () => {
    it("holds nothing while idle", () => {
        const { store, events } = connected();

        expect([...store.getAll()]).toEqual([]);
        expect(events).toEqual([]);
    });

    it("adds the session's product when it is placed, updates it on each change, and removes it when the session ends", () => {
        const { store, events } = connected();
        const measurement = lineMeasurement([0, 0]);

        setSessionForTest(drawing(measureDescriptor.id, measurement));
        expect(events.map((e) => e.event)).toEqual(["add"]);
        expect(store.get("current")?.product).toEqual({
            tool: measureDescriptor,
            draft: measurement,
        });

        setSessionForTest(drawing(measureDescriptor.id, measurement));
        expect(events.map((e) => e.event)).toEqual(["add", "update"]);

        resetInteractionForTest();
        expect(events.map((e) => e.event)).toEqual(["add", "update", "remove"]);
        expect([...store.getAll()]).toEqual([]);
    });

    it("holds an element draft's construction path, but not the draft itself", () => {
        const { store } = connected();

        setSessionForTest(drawing(polygon.id, Line.create({ positions: [0, 0, 1, 1] })));

        expect(store.get("current")).toEqual({
            key: "current",
            product: null,
            construction: [0, 0, 1, 1],
        });
    });
});
