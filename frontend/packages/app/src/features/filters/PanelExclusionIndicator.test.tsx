// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from "vitest";
import { render, screen } from "~/test";
import { PanelExclusionIndicator } from "./PanelExclusionIndicator";

describe("PanelExclusionIndicator", () => {
    it("exposes a named image when the row is excluded", () => {
        render(<PanelExclusionIndicator excluded />);

        expect(screen.getByRole("img", { name: /hidden from the map/i })).toBeInTheDocument();
    });

    it("renders nothing otherwise", () => {
        render(<PanelExclusionIndicator excluded={false} />);

        expect(screen.queryByRole("img")).not.toBeInTheDocument();
    });
});
