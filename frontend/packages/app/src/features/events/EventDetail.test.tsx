// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "~/test";
import { describe, expect, it, vi } from "vitest";
import type { EventDetails } from "@opencop/api";
import type { Geometry } from "geojson";
import { Graphic, MediaIcon, Point, Polygon, Symbol, type Element } from "@opencop/elements";
import type { Editability } from "~/features/map/editability";
import { EventDetail } from "./EventDetail";

/**
 * Characterization: which events offer Push. It was a type check in
 * `EventDetail`; it is now a host rule that names no element type — a remote
 * event with a point geometry and a code — and these pins hold across the
 * change. Everything around the action row is stubbed.
 */

const current = vi.hoisted(() => ({
    details: undefined as unknown,
    geometry: undefined as unknown,
    element: undefined as unknown,
}));

vi.mock("./use-event-details.hook", () => ({
    useEventDetails: () => ({
        details: current.details,
        geometry: current.geometry,
        element: current.element,
        hasElement: true,
        loading: false,
        error: null,
    }),
}));
vi.mock("~/features/map/interaction-runtime/session/selectors", async (importOriginal) => ({
    ...(await importOriginal<
        typeof import("~/features/map/interaction-runtime/session/selectors")
    >()),
    useIsEditing: () => false,
}));
vi.mock("~/features/map/use-editability.hook", () => ({
    useEditability: (): Editability => ({
        canEditGeometry: true,
        canEditProperties: true,
        canDelete: true,
        canBroadcast: true,
    }),
}));
vi.mock("~/features/map/use-picked-element.hook", async (importOriginal) => ({
    ...(await importOriginal<typeof import("~/features/map/use-picked-element.hook")>()),
    useClearPickedElement: () => vi.fn(),
}));
vi.mock("./events.queries", async (importOriginal) => {
    const actual = await importOriginal<typeof import("./events.queries")>();
    return {
        ...actual,
        eventQueries: {
            ...actual.eventQueries,
            allAttachments: () => ({ queryKey: ["attachments-stub"], queryFn: async () => [] }),
        },
    };
});
vi.mock("./PushMenu", () => ({ PushMenu: () => <div data-testid="push-menu" /> }));
vi.mock("./EventsHeader", () => ({ EventsHeader: () => null }));
vi.mock("./detail/EditButton", () => ({ EditButton: () => null }));
vi.mock("./detail/DeleteDialog", () => ({ DeleteDialog: () => null }));
vi.mock("./sections/InformationSection", () => ({ InformationSection: () => null }));

const SIDC = "10031000001211000000";

/** A type's live element and wire geometry: symbols and graphics carry a code. */
const ELEMENTS: Record<string, () => { element: Element; geometry: Geometry }> = {
    symbol: () => ({
        element: Symbol.create({ position: [0, 0], sidc: SIDC }),
        geometry: { type: "Point", coordinates: [0, 0] },
    }),
    graphic: () => ({
        element: Graphic.create({ sidc: SIDC, controls: [0, 0, 1, 1] }),
        geometry: {
            type: "LineString",
            coordinates: [
                [0, 0],
                [1, 1],
            ],
        },
    }),
    "media-icon": () => ({
        element: MediaIcon.create({
            position: [0, 0],
            href: "opencop:url:/v1/layers/abc/media/def#32x32",
        }),
        geometry: { type: "Point", coordinates: [0, 0] },
    }),
    point: () => ({
        element: Point.create({ position: [0, 0] }),
        geometry: { type: "Point", coordinates: [0, 0] },
    }),
    polygon: () => ({
        element: Polygon.create({
            positions: [
                [0, 0],
                [1, 0],
                [1, 1],
            ],
        }),
        geometry: {
            type: "Polygon",
            coordinates: [
                [
                    [0, 0],
                    [1, 0],
                    [1, 1],
                    [0, 0],
                ],
            ],
        },
    }),
};

function renderFor(source: string, type: string) {
    const { element, geometry } = ELEMENTS[type]();
    current.element = element;
    current.geometry = geometry;
    current.details = {
        displayName: "Alpha",
        id: "id-1",
        objectId: "object-12345678",
        source,
        element: { type, symbol: element.getCode() },
    } as unknown as EventDetails;
    return render(<EventDetail />);
}

describe("EventDetail — Push", () => {
    it("offers Push for a symbol on a remote source", () => {
        renderFor("source-1", "symbol");
        expect(screen.getByTestId("push-menu")).toBeInTheDocument();
    });

    it("does not offer Push for a local symbol", () => {
        renderFor("local", "symbol");
        expect(screen.queryByTestId("push-menu")).not.toBeInTheDocument();
    });

    it.each(["graphic", "media-icon", "point", "polygon"])(
        "does not offer Push for a remote %s",
        (type) => {
            renderFor("source-1", type);
            expect(screen.queryByTestId("push-menu")).not.toBeInTheDocument();
        },
    );
});
