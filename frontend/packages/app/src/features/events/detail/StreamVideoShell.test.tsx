// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { describe, expect, it } from "vitest";
import { StreamVideoShell } from "./StreamVideoShell";

describe("StreamVideoShell status announcements", () => {
    it("announces a slow connection through the live region", () => {
        render(<StreamVideoShell streamState="loading" videoRef={null} slow />);

        expect(
            screen.getAllByText("Still connecting — this can take a while.").length,
        ).toBeGreaterThan(0);
        const live = document.querySelector("[aria-live='polite']");
        expect(live).toHaveTextContent("Still connecting — this can take a while.");
    });

    it("keeps the live region empty while a fresh load is in progress", () => {
        render(<StreamVideoShell streamState="loading" videoRef={null} />);

        expect(document.querySelector("[aria-live='polite']")).toBeEmptyDOMElement();
    });
});
