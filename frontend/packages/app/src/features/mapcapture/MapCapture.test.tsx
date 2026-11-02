// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { act, render, screen, server, http, HttpResponse, API_BASE } from "~/test";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { useMapCaptureOrderStore } from "./mapcapture-order.store";
import { MapCapture } from "./MapCapture";
import type { MapCaptureResponse } from "@opencop/api";
import { useMapManager } from "../map/MapManagerContext";
import { useCoordFormat } from "~/features/coordinates/CoordFormatContext";

const MOVE_UP_LABEL = (name: string) => `Move ${name} up`;
const MOVE_DOWN_LABEL = (name: string) => `Move ${name} down`;
const CAPTURES_URL = `${API_BASE}/me/map/captures`;

// Remove dnd-kit pointer sensors — happy-dom has no pointer event stack
vi.mock("@dnd-kit/core", () => ({
    DndContext: ({ children }: { children?: ReactNode }) => <>{children}</>,
    closestCenter: undefined,
    PointerSensor: class {},
    KeyboardSensor: class {},
    useSensor: vi.fn(),
    useSensors: vi.fn(() => []),
}));

vi.mock("@dnd-kit/sortable", () => ({
    SortableContext: ({ children }: { children?: ReactNode }) => <>{children}</>,
    arrayMove: (arr: unknown[], from: number, to: number) => {
        const next = [...arr];
        const [item] = next.splice(from, 1);
        next.splice(to, 0, item);
        return next;
    },
    sortableKeyboardCoordinates: undefined,
    verticalListSortingStrategy: undefined,
}));

// Replace SortableCaptureEntry with a testable stub that exposes move buttons
vi.mock("./SortableCaptureEntry", () => ({
    SortableCaptureEntry: ({
        children,
        onMoveUp,
        onMoveDown,
        canMoveUp,
        canMoveDown,
        id,
        captureName,
    }: {
        children?: ReactNode;
        onMoveUp?: () => void;
        onMoveDown?: () => void;
        canMoveUp?: boolean;
        canMoveDown?: boolean;
        id?: string;
        captureName?: string;
    }) => (
        <div data-testid={`capture-entry-${id}`}>
            <button
                type="button"
                aria-label={MOVE_UP_LABEL(captureName ?? "")}
                onClick={onMoveUp}
                disabled={!canMoveUp}
            />
            <button
                type="button"
                aria-label={MOVE_DOWN_LABEL(captureName ?? "")}
                onClick={onMoveDown}
                disabled={!canMoveDown}
            />
            {children}
        </div>
    ),
}));

// MapManagerProvider is not in the test harness (WebGL dependency)
vi.mock("../map/MapManagerContext", () => ({
    useMapManager: vi.fn(),
}));

// CoordFormatProvider wraps MapView, not the test harness
vi.mock("~/features/coordinates/CoordFormatContext", () => ({
    useCoordFormat: vi.fn(),
}));

// DropNotificationProvider is not in the test harness
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: vi.fn(() => ({ showToast: vi.fn() })),
}));

function makeCapture(id: string, name: string): MapCaptureResponse {
    return {
        id,
        name,
        mapView: { latitude: 10, longitude: 20, zoom: 5, bearing: 0, pitch: 0 },
        createdAt: "2026-01-01T00:00:00.000Z",
        updatedAt: "2026-01-01T00:00:00.000Z",
    };
}

const alpha = makeCapture("alpha-id", "Alpha");
const bravo = makeCapture("bravo-id", "Bravo");
const charlie = makeCapture("charlie-id", "Charlie");

function stubCaptures(captures: MapCaptureResponse[]) {
    server.use(http.get(CAPTURES_URL, () => HttpResponse.json(captures)));
}

function captureOrder() {
    return screen
        .getAllByTestId(/^capture-entry-/)
        .map((el) => el.getAttribute("data-testid")?.replace("capture-entry-", ""));
}

beforeEach(() => {
    useMapCaptureOrderStore.setState({ order: [] });
    vi.mocked(useMapManager).mockReturnValue({
        onMap: vi.fn(() => ({ unsub: vi.fn() })),
    } as unknown as ReturnType<typeof useMapManager>);
    vi.mocked(useCoordFormat).mockReturnValue({ format: "DD", setFormat: vi.fn() });
    stubCaptures([]);
});

describe("MapCapture ordering", () => {
    it("renders captures in server order by default", async () => {
        stubCaptures([alpha, bravo, charlie]);
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-alpha-id");
        expect(captureOrder()).toEqual(["alpha-id", "bravo-id", "charlie-id"]);
    });

    it("renders captures in the saved store order", async () => {
        useMapCaptureOrderStore.setState({ order: ["charlie-id", "alpha-id", "bravo-id"] });
        stubCaptures([alpha, bravo, charlie]);
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-charlie-id");
        expect(captureOrder()).toEqual(["charlie-id", "alpha-id", "bravo-id"]);
    });

    it("a capture absent from the saved order appears after the arranged ones", async () => {
        // Charlie is a new capture not yet in the persisted order
        useMapCaptureOrderStore.setState({ order: ["bravo-id", "alpha-id"] });
        stubCaptures([alpha, bravo, charlie]);
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-charlie-id");
        expect(captureOrder()).toEqual(["bravo-id", "alpha-id", "charlie-id"]);
    });

    it("clicking move-up puts that capture before its predecessor", async () => {
        stubCaptures([alpha, bravo]);
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-bravo-id");
        act(() => {
            screen.getByRole("button", { name: MOVE_UP_LABEL("Bravo") }).click();
        });

        expect(captureOrder()).toEqual(["bravo-id", "alpha-id"]);
        expect(useMapCaptureOrderStore.getState().order).toEqual(["bravo-id", "alpha-id"]);
    });

    it("clicking move-down puts that capture after its successor", async () => {
        stubCaptures([alpha, bravo]);
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-alpha-id");
        act(() => {
            screen.getByRole("button", { name: MOVE_DOWN_LABEL("Alpha") }).click();
        });

        expect(captureOrder()).toEqual(["bravo-id", "alpha-id"]);
        expect(useMapCaptureOrderStore.getState().order).toEqual(["bravo-id", "alpha-id"]);
    });

    it("move-up is disabled for the first capture", async () => {
        stubCaptures([alpha, bravo]);
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-alpha-id");
        expect(screen.getByRole("button", { name: MOVE_UP_LABEL("Alpha") })).toBeDisabled();
    });

    it("move-down is disabled for the last capture", async () => {
        stubCaptures([alpha, bravo]);
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-bravo-id");
        expect(screen.getByRole("button", { name: MOVE_DOWN_LABEL("Bravo") })).toBeDisabled();
    });

    it("a capture deleted server-side no longer appears in the panel", async () => {
        // Seed the order with a capture that the server no longer returns
        useMapCaptureOrderStore.setState({ order: ["alpha-id", "bravo-id"] });
        stubCaptures([alpha]); // bravo has been deleted
        render(<MapCapture />);

        await screen.findByTestId("capture-entry-alpha-id");
        expect(screen.queryByTestId("capture-entry-bravo-id")).toBeNull();
    });
});
