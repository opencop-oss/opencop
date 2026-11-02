// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { API_BASE, HttpResponse, http, render, screen, server } from "~/test";
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { noop } from "lodash";
import type { EventDetails } from "@opencop/api";
import {
    CircleGeometry,
    Element,
    EllipseGeometry,
    PointGeometry,
    PointStyle,
    PolygonGeometry,
    PolylineGeometry,
} from "@opencop/elements";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installNativeSubjects } from "~/features/contributions/native-install";
import { installShapes } from "~/plugins/shapes/install-shapes";
import type { Editability } from "~/features/map/editability";
import { EventEditForm } from "./EventEditForm";

/**
 * Characterization: which geometry editor the edit form mounts for each element
 * type, and what it hands that editor. The shapes' editors come from the
 * details edit-form surface, so the shapes subject is installed; a point
 * geometry with no form gets the host's position input. The editors themselves
 * are replaced by markers that record their props, so this pins the routing only.
 */

const picked = vi.hoisted(() => ({ element: undefined as Element | undefined }));
const received = vi.hoisted(() => ({ props: [] as Array<{ editor: string; props: object }> }));

vi.mock("~/features/map/use-picked-element.hook", async (importOriginal) => ({
    ...(await importOriginal<typeof import("~/features/map/use-picked-element.hook")>()),
    usePickedElement: () => picked.element,
}));

// Saving is out of scope: the save hook needs the map manager, and its errors
// report through toasts.
vi.mock("../use-event-save.hook", () => ({
    useEventSave: () => vi.fn(),
    MoveRebroadcastError: class MoveRebroadcastError extends Error {},
}));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: vi.fn() }),
}));

function markerEditor(editor: string) {
    return (props: object) => {
        received.props.push({ editor, props });
        return <div data-testid={`geometry-editor-${editor}`} />;
    };
}

vi.mock("./PointInput", () => ({ PointInput: markerEditor("point") }));
vi.mock("~/plugins/shapes/edit-forms/CircleInput", () => ({
    CircleInput: markerEditor("circle"),
}));
vi.mock("~/plugins/shapes/edit-forms/ArrowInput", () => ({ ArrowInput: markerEditor("arrow") }));
vi.mock("~/plugins/shapes/edit-forms/AngleInput", () => ({ AngleInput: markerEditor("angle") }));

const details = {
    displayName: "Alpha",
    id: "id-1",
    objectId: "object-12345678",
    source: "local",
    kind: "test-kind",
    timestampz: { time: Date.UTC(2026, 9, 6, 12), stale: Date.UTC(2026, 9, 6, 13) },
    element: { type: "point" },
} as unknown as EventDetails;

/** A data-source owner: properties editable, geometry not (latent bug 1). */
const propertiesOnly: Editability = {
    canEditGeometry: false,
    canEditProperties: true,
    canDelete: false,
    canBroadcast: false,
};

/** An element of `type` with the geometry that type carries. */
function elementOf(type: string): Element {
    const geometry =
        type === "symbol" || type === "point"
            ? new PointGeometry([0, 0])
            : type === "circle"
              ? new CircleGeometry([0, 0], 10)
              : type === "ellipse"
                ? new EllipseGeometry([0, 0], 10, 5)
                : type === "polygon" || type === "rectangle"
                  ? new PolygonGeometry([0, 0, 1, 0, 1, 1])
                  : new PolylineGeometry([0, 0, 1, 1, 2, 0]);
    return Element.create({
        type,
        geometry,
        style: new PointStyle({ color: "#ffffffff", radius: 3 }),
    });
}

function renderFor(type: string) {
    picked.element = elementOf(type);
    received.props = [];
    return render(<EventEditForm details={details} editability={propertiesOnly} />);
}

let disposeShapes = noop;
beforeAll(() => {
    disposeShapes = installNativeSubjects(
        [{ id: "opencop.shapes", install: installShapes }],
        contributionContextFor,
    );
});
afterAll(() => disposeShapes());

describe("EventEditForm — geometry editors", () => {
    beforeEach(() => {
        server.use(
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json([])),
            http.get(`${API_BASE}/me/sources`, () => HttpResponse.json([])),
        );
    });

    it.each([
        ["point", "point"],
        ["symbol", "point"],
        ["circle", "circle"],
        ["arrow", "arrow"],
        ["angle", "angle"],
    ])("mounts the %s element's %s editor", (type, editor) => {
        renderFor(type);
        expect(screen.getByTestId(`geometry-editor-${editor}`)).toBeInTheDocument();
        expect(screen.getAllByTestId(/^geometry-editor-/)).toHaveLength(1);
    });

    it.each(["line", "polygon", "rectangle", "ellipse", "graphic"])(
        "mounts no geometry editor for a %s",
        (type) => {
            renderFor(type);
            expect(screen.queryAllByTestId(/^geometry-editor-/)).toHaveLength(0);
        },
    );

    // Latent bug 1, fixed: a properties-only editor used to get live geometry
    // inputs. The form now passes the permission down and the inputs are read-only.
    it.each([
        ["circle", "circle"],
        ["point", "point"],
    ])("makes the %s editor read-only when geometry is not editable", (type, editor) => {
        renderFor(type);
        expect(received.props.at(-1)).toEqual({
            editor,
            props: { element: picked.element, readOnly: true },
        });
    });
});
