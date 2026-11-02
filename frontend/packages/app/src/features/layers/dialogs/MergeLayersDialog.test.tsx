// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";
import { createElement } from "react";
import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { describe, expect, it, vi } from "vitest";
import type { LayerInfo, MergeResponse, MergeValidateResponse } from "@opencop/api";
import { MergeLayersDialog } from "./MergeLayersDialog";
import type { MergeLayerItem } from "./MergeLayersDialog";
import { layerDirectoryQueries } from "../layer-directory.queries";
import { layerQueries } from "../layers.queries";

// DnD kit requires pointer events that jsdom does not support — replace the
// sortable context with a transparent pass-through so the layer list renders
// without the drag infrastructure.
vi.mock("@dnd-kit/core", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@dnd-kit/core")>();
    return {
        ...actual,
        DndContext: ({ children }: { children: ReactNode }) => createElement("div", null, children),
        useSensor: vi.fn(),
        useSensors: vi.fn(() => []),
        PointerSensor: class {},
        KeyboardSensor: class {},
    };
});
vi.mock("@dnd-kit/sortable", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@dnd-kit/sortable")>();
    return {
        ...actual,
        SortableContext: ({ children }: { children: ReactNode }) =>
            createElement("div", null, children),
        useSortable: () => ({
            attributes: {},
            listeners: {},
            setNodeRef: () => {},
            transform: null,
            transition: undefined,
            isDragging: false,
        }),
    };
});

const LAYERS: MergeLayerItem[] = [
    { id: "l1", name: "Alpha" },
    { id: "l2", name: "Bravo" },
];

const VALIDATE_RESPONSE = {
    layerIds: ["l1", "l2"],
    collisionSummary: { totalCollisions: 0, pairwiseCounts: [] },
    activeFeatureCounts: { l1: 5, l2: 3 },
} satisfies MergeValidateResponse;

const VALIDATE_RESPONSE_WITH_COLLISION = {
    layerIds: ["l1", "l2"],
    collisionSummary: {
        totalCollisions: 2,
        pairwiseCounts: [{ firstLayerId: "l1", secondLayerId: "l2", count: 2 }],
    },
    activeFeatureCounts: { l1: 5, l2: 3 },
} satisfies MergeValidateResponse;

const MERGE_RESPONSE = {
    targetLayer: {
        id: "target-1",
        name: "Alpha-Bravo",
        lifecycleState: "PENDING_MERGE",
        accessScope: "PRIVATE",
        createdAt: "2026-01-01T00:00:00Z",
        archivedAt: null,
        defaultVisible: true,
    },
    sourceLayerIds: ["l1", "l2"],
} satisfies MergeResponse;

const ACTIVE_LAYER = {
    id: "target-1",
    name: "Alpha-Bravo",
    lifecycleState: "ACTIVE",
    status: "ACTIVE",
    accessScope: "PRIVATE",
    createdAt: "2026-01-01T00:00:00Z",
    editing: false,
    defaultVisible: true,
} satisfies LayerInfo;

const PENDING_LAYER = {
    ...ACTIVE_LAYER,
    lifecycleState: "PENDING_MERGE",
    status: "PENDING_MERGE",
} satisfies LayerInfo;

/** Registers the `POST /layers/merge/validate` handler with a fixed response. */
function stubValidateSuccess(response: MergeValidateResponse = VALIDATE_RESPONSE) {
    server.use(http.post(`${API_BASE}/layers/merge/validate`, () => HttpResponse.json(response)));
}

/** Registers a failing `POST /layers/merge/validate` handler. */
function stubValidateError(status: number, body: Record<string, unknown>) {
    server.use(
        http.post(`${API_BASE}/layers/merge/validate`, () => HttpResponse.json(body, { status })),
    );
}

function renderDialog(
    props: Partial<{
        layers: MergeLayerItem[];
        selectionLimitExceeded: boolean;
        onClose: () => void;
    }> = {},
) {
    const onClose = props.onClose ?? vi.fn();

    const result = render(
        <MergeLayersDialog
            isOpen
            layers={props.layers ?? LAYERS}
            selectionLimitExceeded={props.selectionLimitExceeded ?? false}
            onClose={onClose}
        />,
    );

    return { queryClient: result.queryClient, onClose };
}

describe("MergeLayersDialog", () => {
    it("renders layer list in Arrange step", () => {
        renderDialog();
        expect(screen.getByText("Alpha")).toBeTruthy();
        expect(screen.getByText("Bravo")).toBeTruthy();
    });

    it("shows selection limit banner when selectionLimitExceeded is true", () => {
        renderDialog({ selectionLimitExceeded: true });
        expect(screen.getByText(/only 10 layers can be merged/i)).toBeTruthy();
    });

    it("advances to Validate step on Next when validate succeeds with no collisions", async () => {
        const user = userEvent.setup();
        stubValidateSuccess();
        renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));

        await screen.findByText(/no overlapping objects/i);
    });

    it("blocks advance and shows error when a layer has zero active features", async () => {
        const user = userEvent.setup();
        stubValidateError(409, {
            code: null,
            message: "All merge source layers must contain active features",
        });
        renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));

        await screen.findByText(/some layers have no active events/i);
        expect(screen.queryByText(/no overlapping objects/i)).toBeNull();
    });

    it("shows collision table when collisions are present and allows going back", async () => {
        const user = userEvent.setup();
        stubValidateSuccess(VALIDATE_RESPONSE_WITH_COLLISION);
        renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByText(/conflicts detected/i);

        await user.click(screen.getByRole("button", { name: /change priority/i }));
        // Back on Arrange step — all steps share the DOM so use getAllByText
        expect(screen.getAllByText("Alpha").length).toBeGreaterThan(0);
    });

    it("advances from Validate to Merge step", async () => {
        const user = userEvent.setup();
        stubValidateSuccess();
        renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByText(/no overlapping objects/i);

        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByPlaceholderText(/enter a name for the merged layer/i);
        await screen.findByLabelText(/name/i);
    });

    it("blocks submit when target name is blank", async () => {
        const user = userEvent.setup();
        stubValidateSuccess();
        let mergeCalled = false;
        server.use(
            http.post(`${API_BASE}/layers/merge`, () => {
                mergeCalled = true;
                return HttpResponse.json(MERGE_RESPONSE);
            }),
        );
        renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByText(/no overlapping objects/i);
        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByPlaceholderText(/enter a name for the merged layer/i);

        await user.click(screen.getByRole("button", { name: /merge/i }));

        await screen.findByText(/please enter a name/i);
        expect(mergeCalled).toBe(false);
    });

    it("shows 409 name-taken error when commit returns conflict", async () => {
        const user = userEvent.setup();
        stubValidateSuccess();
        server.use(
            http.post(`${API_BASE}/layers/merge`, () => HttpResponse.json({}, { status: 409 })),
        );
        renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByText(/no overlapping objects/i);
        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByPlaceholderText(/enter a name for the merged layer/i);

        await user.type(screen.getByPlaceholderText(/enter a name/i), "Alpha-Bravo");
        await user.click(screen.getByRole("button", { name: /merge/i }));

        await screen.findByText(/already exists/i);
    });

    it("shows the accepted-and-waiting message while the merge is pending", async () => {
        const user = userEvent.setup();
        stubValidateSuccess();
        server.use(
            http.post(`${API_BASE}/layers/merge`, () => HttpResponse.json(MERGE_RESPONSE)),
            http.get(`${API_BASE}/layers/target-1`, () => HttpResponse.json(PENDING_LAYER)),
        );

        renderDialog();

        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByText(/no overlapping objects/i);
        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByPlaceholderText(/enter a name for the merged layer/i);

        await user.type(screen.getByPlaceholderText(/enter a name/i), "Alpha-Bravo");
        await user.click(screen.getByRole("button", { name: /merge/i }));

        await screen.findByText(
            /merge request accepted\. waiting for the server to complete the merge/i,
        );
    });

    it("happy path: submit merge → poll → success state → close calls onClose", async () => {
        const user = userEvent.setup();
        stubValidateSuccess();
        server.use(
            http.post(`${API_BASE}/layers/merge`, () => HttpResponse.json(MERGE_RESPONSE)),
            http.get(`${API_BASE}/layers/target-1`, () => HttpResponse.json(ACTIVE_LAYER)),
        );

        const { onClose, queryClient } = renderDialog();
        const invalidateQueriesSpy = vi.spyOn(queryClient, "invalidateQueries");

        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByText(/no overlapping objects/i);
        await user.click(screen.getByRole("button", { name: "Next" }));
        await screen.findByPlaceholderText(/enter a name for the merged layer/i);

        await user.type(screen.getByPlaceholderText(/enter a name/i), "Alpha-Bravo");
        await user.click(screen.getByRole("button", { name: /merge/i }));

        await screen.findByText(/merge complete/i);
        // Directory first (the grid shows the sources merging straight away), then the settle
        // poll refreshes the layer lists and the directory again once the merge is terminal.
        await waitFor(() => expect(invalidateQueriesSpy).toHaveBeenCalledTimes(3));
        expect(invalidateQueriesSpy).toHaveBeenNthCalledWith(1, {
            queryKey: layerDirectoryQueries.lists(),
        });
        expect(invalidateQueriesSpy).toHaveBeenNthCalledWith(2, {
            queryKey: layerQueries.lists(),
        });
        expect(invalidateQueriesSpy).toHaveBeenNthCalledWith(3, {
            queryKey: layerDirectoryQueries.lists(),
        });

        await user.click(screen.getByRole("button", { name: /done/i }));
        expect(onClose).toHaveBeenCalled();
    });
});
