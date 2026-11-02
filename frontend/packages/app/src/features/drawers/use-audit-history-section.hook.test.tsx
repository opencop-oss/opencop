// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from "react";
import { describe, expect, it } from "vitest";
import { createTestUser, render, screen } from "~/test";
import { useAuditHistorySection } from "./use-audit-history-section.hook";

const LAYER_ID = "550e8400-e29b-41d4-a716-446655440060";

function Probe(): ReactElement {
    const section = useAuditHistorySection("LAYER", LAYER_ID);
    return <span data-testid="section-key">{section?.key}</span>;
}

describe("useAuditHistorySection", () => {
    it("offers the section to a global administrator", () => {
        render(<Probe />, { user: createTestUser({ authorities: ["ROLE_admin"] }) });

        expect(screen.getByTestId("section-key")).toHaveTextContent("auditHistory");
    });

    /**
     * The read endpoint is administrator-only, so a non-administrator gets no section rather than
     * one whose only possible outcome is an error.
     */
    it("offers nothing to anyone else", () => {
        render(<Probe />, { user: createTestUser({ authorities: ["ROLE_user"] }) });

        expect(screen.getByTestId("section-key")).toBeEmptyDOMElement();
    });
});
