// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { AuditHistoryItem, Page } from "@opencop/api";
import { describe, expect, it } from "vitest";
import {
    API_BASE,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    userEvent,
    waitFor,
} from "~/test";
import { AuditHistorySection } from "./AuditHistorySection";

const LAYER_ID = "550e8400-e29b-41d4-a716-446655440040";
const ACTOR_ID = "550e8400-e29b-41d4-a716-446655440041";
const MEMBER_ID = "550e8400-e29b-41d4-a716-446655440042";

function page(content: AuditHistoryItem[]): Page<AuditHistoryItem> {
    return {
        content,
        page: 0,
        size: 50,
        totalElements: content.length,
        totalPages: content.length === 0 ? 0 : 1,
    };
}

function row(overrides: Partial<AuditHistoryItem> = {}): AuditHistoryItem {
    return {
        id: 1,
        entityType: "LAYER",
        entityId: LAYER_ID,
        entityName: "North Watch",
        operation: "PATCH",
        occurredAt: "2026-10-15T14:10:00Z",
        actor: { userId: ACTOR_ID, fullName: "Operator One", globalAdmin: true },
        change: { changed: [{ field: "name", before: "North", after: "North Watch" }] },
        changeVersion: 1,
        ...overrides,
    };
}

function serveHistory(body: Page<AuditHistoryItem>, status = 200): void {
    server.use(
        http.get(`${API_BASE}/admin/audit-history`, () =>
            status === 200 ? HttpResponse.json(body) : new HttpResponse(null, { status }),
        ),
    );
}

/** Two pages of one row each, so there is a later page to load. */
function serveTwoPages(): void {
    server.use(
        http.get(`${API_BASE}/admin/audit-history`, ({ request }) => {
            const pageParam = Number(new URL(request.url).searchParams.get("page") ?? 0);
            return HttpResponse.json({
                content: [
                    row({
                        id: pageParam + 1,
                        change: {
                            changed: [
                                {
                                    field: "name",
                                    before: `page ${pageParam}`,
                                    after: `page ${pageParam} after`,
                                },
                            ],
                        },
                    }),
                ],
                page: pageParam,
                size: 1,
                totalElements: 2,
                totalPages: 2,
            });
        }),
    );
}

function renderSection() {
    return render(<AuditHistorySection entityType="LAYER" entityId={LAYER_ID} />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
    });
}

describe("AuditHistorySection", () => {
    it("shows the operation, the fields it moved and who moved them", async () => {
        serveHistory(page([row()]));
        renderSection();

        expect(await screen.findByText("Edited")).toBeInTheDocument();
        expect(screen.getByText("Name: North → North Watch")).toBeInTheDocument();
        expect(screen.getByText("Operator One")).toBeInTheDocument();
    });

    it("summarises a membership change by exact count", async () => {
        serveHistory(
            page([
                row({
                    id: 2,
                    operation: "MEMBERS_CHANGED",
                    change: {
                        added: {
                            count: 3,
                            entries: [{ id: MEMBER_ID, role: "MEMBER" }],
                            truncated: false,
                        },
                        removed: { count: 1, ids: [MEMBER_ID], truncated: false },
                    },
                }),
            ]),
        );
        renderSection();

        expect(await screen.findByText("Members changed")).toBeInTheDocument();
        expect(screen.getByText("Added: 3")).toBeInTheDocument();
        expect(screen.getByText("Removed: 1")).toBeInTheDocument();
    });

    /** A capped list still knows how many, so the count must not read as the whole list. */
    it("marks a delta whose ids were capped", async () => {
        serveHistory(
            page([
                row({
                    id: 3,
                    operation: "MEMBERS_CHANGED",
                    change: { added: { count: 150, entries: [], truncated: true } },
                }),
            ]),
        );
        renderSection();

        expect(await screen.findByText("Added: 150 (list truncated)")).toBeInTheDocument();
    });

    /**
     * A secret, certificate or plugin setting is recorded by name only. The row must say the field
     * changed rather than implying it was set to nothing.
     */
    it("reports a field recorded without its value as changed, with no values", async () => {
        serveHistory(
            page([
                row({
                    id: 4,
                    operation: "SETUP_REPLACED",
                    change: {
                        changed: [{ field: "clientCertificate" }],
                        valuesOmitted: true,
                    },
                }),
            ]),
        );
        renderSection();

        expect(
            await screen.findByText("clientCertificate changed (value not recorded)"),
        ).toBeInTheDocument();
        expect(screen.queryByText(/→/)).not.toBeInTheDocument();
    });

    it("renders an operation's own details without needing to know the operation", async () => {
        serveHistory(
            page([
                row({
                    id: 5,
                    operation: "EXPORTED",
                    change: {
                        changed: [],
                        format: "CSV",
                        exportId: "550e8400-e29b-41d4-a716-446655440050",
                        subjectCount: 2,
                    },
                }),
            ]),
        );
        renderSection();

        expect(await screen.findByText("Exported")).toBeInTheDocument();
        expect(screen.getByText("Format: CSV")).toBeInTheDocument();
        expect(screen.getByText("Resources in this export: 2")).toBeInTheDocument();
    });

    it("says there are no recorded changes when the ledger is empty", async () => {
        serveHistory(page([]));
        renderSection();

        expect(
            await screen.findByText("No recorded changes for this resource."),
        ).toBeInTheDocument();
    });

    /** A failed read must not read as "never administered" — we do not know. */
    it("distinguishes a failed read from an empty history", async () => {
        serveHistory(page([]), 500);
        renderSection();

        await waitFor(() =>
            expect(
                screen.getByText("Could not load the history for this resource. Try again."),
            ).toBeInTheDocument(),
        );
        expect(
            screen.queryByText("No recorded changes for this resource."),
        ).not.toBeInTheDocument();
    });

    describe("paging", () => {
        it("loads the next page from the footer button", async () => {
            serveTwoPages();
            renderSection();

            expect(await screen.findByText(/page 0 → page 0 after/)).toBeInTheDocument();
            expect(screen.queryByText(/page 1 → page 1 after/)).not.toBeInTheDocument();

            await userEvent.setup().click(screen.getByRole("button", { name: "Load more" }));

            expect(await screen.findByText(/page 1 → page 1 after/)).toBeInTheDocument();
        });

        /** Searching a partial ledger would match only the pages in hand, so it loads the rest. */
        it("loads every remaining page when the reader searches", async () => {
            serveTwoPages();
            renderSection();
            await screen.findByText(/page 0 → page 0 after/);

            await userEvent
                .setup()
                .type(screen.getByPlaceholderText("Filter for any field..."), "page 1");

            expect(await screen.findByText(/page 1 → page 1 after/)).toBeInTheDocument();
            expect(screen.queryByText(/page 0 → page 0 after/)).not.toBeInTheDocument();
            // The footer counts the rows that match, not the rows loaded.
            expect(screen.getByText("1 of 2")).toBeInTheDocument();
            expect(screen.queryByText("2 of 2")).not.toBeInTheDocument();
        });

        /** A failing page must not be re-requested by the auto-load, only by Retry. */
        it("does not loop on a failing page while a search is active", async () => {
            let laterPageRequests = 0;
            server.use(
                http.get(`${API_BASE}/admin/audit-history`, ({ request }) => {
                    const pageParam = Number(new URL(request.url).searchParams.get("page") ?? 0);
                    if (pageParam === 0) {
                        return HttpResponse.json({
                            ...page([row()]),
                            totalElements: 2,
                            totalPages: 2,
                        });
                    }
                    laterPageRequests += 1;
                    return new HttpResponse(null, { status: 500 });
                }),
            );
            renderSection();

            await userEvent
                .setup()
                .type(await screen.findByPlaceholderText("Filter for any field..."), "name");
            await screen.findByRole("button", { name: "Retry" });

            expect(laterPageRequests).toBe(1);
        });

        it("sorts the history by actor", async () => {
            serveHistory(
                page([
                    row({ id: 1, actor: { userId: ACTOR_ID, fullName: "Zed", globalAdmin: true } }),
                    row({ id: 2, actor: { userId: ACTOR_ID, fullName: "Abe", globalAdmin: true } }),
                ]),
            );
            renderSection();
            await screen.findByText("Zed");

            await userEvent.setup().click(screen.getByRole("columnheader", { name: /Changed by/ }));

            const names = screen.getAllByText(/^(Zed|Abe)$/).map((el) => el.textContent);
            expect(names).toEqual(["Abe", "Zed"]);
        });
    });
});
