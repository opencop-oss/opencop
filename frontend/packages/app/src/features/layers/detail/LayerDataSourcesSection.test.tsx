// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LayerDataSourcesSection } from "./LayerDataSourcesSection";

const { mockUseRelationSection } = vi.hoisted(() => ({
    mockUseRelationSection: vi.fn(),
}));

// Narrow internal-boundary mock: stub the presentational relation section and spy on the hook so
// the test can drive its `onAssign` directly. The section's own data-fetching (candidate pool,
// assign mutation) still runs for real against MSW.
vi.mock("~/features/drawers/DrawerRelationSection", () => ({
    DrawerRelationSection: () => <div data-testid="relation-section" />,
}));
vi.mock("~/features/drawers/use-relation-section.hook", () => ({
    useRelationSection: mockUseRelationSection,
}));

const CANDIDATES_URL = `${API_BASE}/layers/layer-1/source-candidates`;
const ADD_LAYERS_URL = `${API_BASE}/sources/source-1/layers`;

function renderSection(readOnly: boolean, assignmentRole: "ADMIN" | "OWNER" = "ADMIN") {
    render(
        <LayerDataSourcesSection
            layerId="layer-1"
            assignmentRole={assignmentRole}
            readOnly={readOnly}
        />,
    );
}

describe("LayerDataSourcesSection", () => {
    beforeEach(() => {
        mockUseRelationSection.mockReset();
        mockUseRelationSection.mockReturnValue({});
    });

    it("loads the layer-scoped candidate pool when relationship editing is allowed", async () => {
        let candidatesFetched = false;
        server.use(
            http.get(CANDIDATES_URL, () => {
                candidatesFetched = true;
                return HttpResponse.json([]);
            }),
        );
        renderSection(false);

        // The pool query is keyed by the layer id, so the request itself proves the layer-scoped fetch.
        await waitFor(() => expect(candidatesFetched).toBe(true));
    });

    it("does not load candidate data for a read-only relationship section", async () => {
        let candidatesFetched = false;
        server.use(
            http.get(CANDIDATES_URL, () => {
                candidatesFetched = true;
                return HttpResponse.json([]);
            }),
        );
        renderSection(true);

        await screen.findByTestId("relation-section");
        expect(candidatesFetched).toBe(false);
    });

    it.each(["ADMIN", "OWNER"] as const)(
        "sends the explicit %s assignment role required by the backend policy",
        async (assignmentRole) => {
            let postBody: unknown;
            server.use(
                http.get(CANDIDATES_URL, () => HttpResponse.json([])),
                http.post(ADD_LAYERS_URL, async ({ request }) => {
                    postBody = await request.json();
                    return new HttpResponse(null, { status: 204 });
                }),
            );
            renderSection(false, assignmentRole);
            await screen.findByTestId("relation-section");

            const relationOptions = mockUseRelationSection.mock.calls.at(-1)?.[0] as {
                onAssign: (ids: string[]) => Promise<unknown>;
            };
            await relationOptions.onAssign(["source-1"]);

            // The role is encoded by which bucket the layer id lands in (viewers/admins/owners).
            expect(postBody).toEqual(
                assignmentRole === "ADMIN"
                    ? { viewers: [], admins: ["layer-1"], owners: [] }
                    : { viewers: [], admins: [], owners: ["layer-1"] },
            );
        },
    );
});
