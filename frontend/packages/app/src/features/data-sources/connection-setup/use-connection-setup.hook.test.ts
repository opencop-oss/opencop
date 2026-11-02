// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { serverErrorSteps } from "./use-connection-setup.hook";

describe("serverErrorSteps", () => {
    it("names the step of every field Core rejected, in step order", () => {
        expect(
            serverErrorSteps({
                customDelimiter: { type: "server", message: "Too long" },
                values: { port: { type: "server", message: "Out of range" } },
                name: { type: "server", message: "Taken" },
            }),
        ).toEqual(["integration", "connection", "mapping"]);
    });

    it("ignores the user's own validation errors", () => {
        expect(
            serverErrorSteps({
                name: { type: "validate", message: "Server Name is required" },
                values: { host: { type: "required", message: "Host is required" } },
            }),
        ).toEqual([]);
    });
});
