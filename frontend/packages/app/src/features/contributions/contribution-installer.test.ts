// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { afterEach, describe, expect, it, vi } from "vitest";
import { pickResponseFor } from "~/features/map/interaction-runtime/arbitration/pick-responses";
import { __resetContributionFailureLog } from "./contribution-guard";
import { contributionContextFor } from "./contribution-installer";

afterEach(() => {
    __resetContributionFailureLog();
    vi.restoreAllMocks();
});

describe("contributionContextFor", () => {
    it("hands the same context to the same subject every time", () => {
        expect(contributionContextFor("opencop.watchbox")).toBe(
            contributionContextFor("opencop.watchbox"),
        );
    });

    it("hands different subjects different contexts", () => {
        expect(contributionContextFor("opencop.shapes")).not.toBe(
            contributionContextFor("opencop.measure"),
        );
    });

    it("contains a pick response that throws, and attributes it to the subject", () => {
        const error = vi.spyOn(Logger.get("plugins"), "error").mockImplementation(() => undefined);
        const dispose = contributionContextFor("opencop.test").registerPickResponse("test-store", {
            onClick: () => {
                throw new Error("click broke");
            },
        });
        const target = {
            screen: { x: 0, y: 0 },
            hit: { responseId: "test-store", ref: { type: "test-store", key: "k" } },
            elements: [],
        };

        expect(() => pickResponseFor(target)?.onClick(target)).not.toThrow();
        expect(String(error.mock.calls[0]?.[0])).toContain("native:opencop.test");
        dispose();
    });
});
