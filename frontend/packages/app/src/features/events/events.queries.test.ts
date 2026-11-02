// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import type { UrlAttachment } from "@opencop/api";
import { selectUrlAttachments } from "./events.queries";

function stream(id: string, createdAt: string): UrlAttachment {
    return {
        kind: "URL",
        record: {
            id,
            url: `https://cam.example.com/${id}`,
            streamKind: "HLS",
            access: "LOGGED_IN",
            creatorId: "user-1",
            ownerId: null,
            createdAt,
            usage: "EVENT_DETAIL",
        },
    };
}

describe("selectUrlAttachments", () => {
    it("lists streams oldest first, so the default stream is the first one added", () => {
        const newer = stream("b", "2026-10-09T12:00:00Z");
        const older = stream("a", "2026-10-09T11:00:00Z");

        expect(selectUrlAttachments([newer, older]).map((a) => a.record.id)).toEqual(["a", "b"]);
    });
});
