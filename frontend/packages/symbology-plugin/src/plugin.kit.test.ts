// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { createTestHost, validatePlugin } from "@opencop/plugin-sdk/testing";
import symbologyPlugin from "./plugin";

describe("the symbology plugin, through the plugin test kit", () => {
    it("is one the host loads", () => {
        expect(validatePlugin(symbologyPlugin)).toEqual([]);
    });

    it("loads and uninstalls cleanly in a test host", () => {
        const host = createTestHost({ plugin: symbologyPlugin });
        expect(host.problems).toEqual([]);
        expect(host.record.status).toBe("active");
        // What "loads" means: the contributions landed in the host's registries.
        expect(host.contributions.tools.getAll().length).toBeGreaterThan(0);
        expect(host.contributions.elementMappers.getEntries().length).toBeGreaterThan(0);
        expect(host.contributions.filterFields.getEntries().length).toBeGreaterThan(0);
        host.uninstall();
    });
});
