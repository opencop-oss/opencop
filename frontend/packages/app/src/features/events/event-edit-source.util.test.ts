// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { getAllowedEventSourceIds } from "./event-edit-source.util";

describe("getAllowedEventSourceIds", () => {
    it("keeps local draft validation restricted to layer ids", () => {
        expect(getAllowedEventSourceIds(["layer-1", "layer-2"], "local", false)).toEqual([
            "layer-1",
            "layer-2",
        ]);
    });

    it("allows the locked source id for an existing data-source event", () => {
        expect(getAllowedEventSourceIds(["layer-1", "layer-2"], "server-1", true)).toEqual([
            "layer-1",
            "layer-2",
            "server-1",
        ]);
    });

    it("does not duplicate a layer source that is already allowed", () => {
        expect(getAllowedEventSourceIds(["layer-1", "layer-2"], "layer-2", true)).toEqual([
            "layer-1",
            "layer-2",
        ]);
    });
});
