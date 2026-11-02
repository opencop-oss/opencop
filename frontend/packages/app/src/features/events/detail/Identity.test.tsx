// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { API_BASE, HttpResponse, http, render, screen, server } from "~/test";
import { beforeEach, describe, expect, it } from "vitest";
import { Identity } from "./Identity";

const IDENTITY = {
    displayName: "Alpha",
    objectId: "object-12345678",
    id: "id-1",
    kind: "test-kind",
    source: "local",
    version: "1",
};

describe("Identity", () => {
    // The section resolves the event's layer and source names.
    beforeEach(() => {
        server.use(
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
        );
    });

    it("names each copy button after the field it copies", () => {
        render(<Identity identity={IDENTITY} />);

        expect(screen.getByRole("button", { name: "Copy to clipboard: ID" })).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Copy to clipboard: OBJECT ID" }),
        ).toBeInTheDocument();
    });
});
