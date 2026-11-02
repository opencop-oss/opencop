// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { ResourceTable } from "./ResourceTable";
import { clickableRoleColumn, groupNameColumn, type GroupSource } from "./table-columns.util";

// ─── Fixtures ─────────────────────────────────────────────────────────────────

interface Member {
    userId: string;
    username: string;
    role: string;
}

interface GroupRow {
    id: string;
    name: string;
    source?: GroupSource;
}

const OPTIONS = [
    { value: "OWNER", label: "Owner" },
    { value: "EDITOR", label: "Editor" },
];

const SCIM_LABEL = "IdP-managed";

// ─── Render helpers ───────────────────────────────────────────────────────────

/**
 * Renders a column through a real ResourceTable — the only consumer of these helpers — so cells
 * render with genuine TanStack context rather than a faked CellContext.
 */
function renderTable<T extends object>(
    data: T[],
    column: Parameters<typeof ResourceTable<T>>[0]["columns"][number],
    getRowId: (row: T) => string,
) {
    render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <ResourceTable data={data} columns={[column]} getRowId={getRowId} />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

/** The helper takes plain label strings, so no i18n is threaded into it. */
function renderRoleColumn(opts: {
    members: Member[];
    onClick?: (id: string) => void;
    isRowLocked?: (row: Member) => boolean;
}) {
    const onClick = opts.onClick ?? vi.fn();
    const col = clickableRoleColumn<Member>({
        header: "Role",
        options: OPTIONS,
        getRowId: (r) => r.userId,
        getEditAriaLabel: (r) => `Change role for ${r.username}`,
        onClick,
        isRowLocked: opts.isRowLocked,
    });
    renderTable(opts.members, col, (r) => r.userId);
    return { onClick, user: userEvent.setup() };
}

function renderGroupNameColumn(rows: GroupRow[]) {
    renderTable(rows, groupNameColumn<GroupRow>("Name", SCIM_LABEL), (r) => r.id);
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("clickableRoleColumn", () => {
    it("renders an unlocked role as a per-member button that fires onClick with the row id", async () => {
        const { onClick, user } = renderRoleColumn({
            members: [{ userId: "u1", username: "Alice", role: "EDITOR" }],
        });

        // aria-label names the specific member; the visible text is the current role.
        const button = screen.getByRole("button", { name: "Change role for Alice" });
        expect(button.textContent).toContain("Editor");

        await user.click(button);
        expect(onClick).toHaveBeenCalledWith("u1");
    });

    it("renders a locked role as non-interactive text (no button)", () => {
        renderRoleColumn({
            members: [{ userId: "u1", username: "Alice", role: "OWNER" }],
            isRowLocked: () => true,
        });

        // No clickable control for a locked role...
        expect(screen.queryByRole("button", { name: "Change role for Alice" })).toBeNull();
        // ...but the role label still renders as plain text.
        expect(screen.getByText("Owner")).toBeTruthy();
    });

    it("gives same-role rows distinct accessible labels", () => {
        renderRoleColumn({
            members: [
                { userId: "u1", username: "Alice", role: "EDITOR" },
                { userId: "u2", username: "Bob", role: "EDITOR" },
            ],
        });

        expect(screen.getByRole("button", { name: "Change role for Alice" })).toBeTruthy();
        expect(screen.getByRole("button", { name: "Change role for Bob" })).toBeTruthy();
    });
});

describe("groupNameColumn", () => {
    /**
     * The case the column exists for: a local group and a SCIM group may legally share a name, so
     * the name alone cannot say which population a grant would reach.
     */
    it("separates two same-name groups by badging only the provisioned one", () => {
        renderGroupNameColumn([
            { id: "ug1", name: "Operators", source: "OPENCOP" },
            { id: "ug2", name: "Operators", source: "SCIM" },
        ]);

        expect(screen.getAllByText("Operators")).toHaveLength(2);
        expect(screen.getAllByText(SCIM_LABEL)).toHaveLength(1);
    });

    it("leaves a locally-managed group unbadged", () => {
        renderGroupNameColumn([{ id: "ug1", name: "Analysts", source: "OPENCOP" }]);

        expect(screen.getByText("Analysts")).toBeTruthy();
        expect(screen.queryByText(SCIM_LABEL)).toBeNull();
    });

    it("renders a row with no source at all", () => {
        // Callers whose response predates the field still render, just without the marker.
        renderGroupNameColumn([{ id: "ug1", name: "Legacy" }]);

        expect(screen.getByText("Legacy")).toBeTruthy();
        expect(screen.queryByText(SCIM_LABEL)).toBeNull();
    });
});
