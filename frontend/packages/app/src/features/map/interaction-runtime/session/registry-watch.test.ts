// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { ToolDescriptor } from "@opencop/tools";
import { __resetToolRegistry, registerTool } from "~/features/contributions/tool-registry";
import { getInteractionState, type Session } from "./interaction.store";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import { createInteractionEnvironment } from "~/features/map/interaction-environment";
import { attachRegistryWatch } from "./registry-watch";

const fixture: ToolDescriptor = { id: "fixtureTool", label: "fixture", icon: () => null };
const other: ToolDescriptor = { id: "otherTool", label: "other", icon: () => null };

/** A draw session waiting for its first click. */
function armed(descriptorId: string): Session {
    return {
        kind: "draw",
        descriptorId,
        epoch: 1,
        params: {},
        custody: false,
        draft: null,
        locked: 0,
    };
}

let detach: () => void;

beforeEach(() => {
    __resetToolRegistry();
    resetInteractionForTest();
    detach = attachRegistryWatch(createInteractionEnvironment());
});

afterEach(() => {
    detach();
    __resetToolRegistry();
    resetInteractionForTest();
});

describe("registry watch", () => {
    it("exits a session whose descriptor is deregistered", () => {
        const dispose = registerTool(fixture);
        setSessionForTest(armed(fixture.id));

        dispose();

        expect(getInteractionState().session.kind).toBe("idle");
    });

    it("leaves the session alone when a different descriptor is deregistered", () => {
        registerTool(fixture);
        const disposeOther = registerTool(other);
        setSessionForTest(armed(fixture.id));

        disposeOther();

        expect(getInteractionState().session).toEqual(armed(fixture.id));
    });

    it("does nothing while idle", () => {
        const dispose = registerTool(fixture);
        dispose();
        expect(getInteractionState().session.kind).toBe("idle");
    });
});
