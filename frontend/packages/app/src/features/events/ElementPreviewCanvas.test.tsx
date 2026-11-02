// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { nativeOwner } from "~/features/contributions/contribution-guard";
import { act, render, waitFor } from "~/test";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { noop } from "lodash";
import {
    IconStyle,
    MediaIconStyle,
    PointStyle,
    ShapeStyle,
    StrokePattern,
    StrokeStyle,
    type Element,
} from "@opencop/elements";
import { cachedImage } from "@opencop/ui";
import { resolveMediaIcon } from "~/plugins/media-icons/media-icon";
import { registerElementMapper } from "~/features/contributions/element-mapper-registry";
import { installNativeSubjects } from "~/features/contributions/native-install";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installShapes } from "~/plugins/shapes/install-shapes";
import { ElementPreviewCanvas } from "./ElementPreviewCanvas";

/**
 * Stands in for whatever renderer claims this element type without a `match` — a
 * plugin, in production. A stub, not the real renderer: these tests assert that
 * the events panel routes a thumbnail through the *owning renderer*, which is
 * exactly the property that must hold whichever subjects happen to be loaded.
 * Importing the real one would couple the host's tests to a plugin.
 */
const fallbackPreview = vi.fn((): CanvasImageSource | null => null);

// The row resolves an element's thumbnail through the renderer registry (the same
// lookup that routes map rendering), so register a `media-icon` renderer and a
// `symbol` renderer the way the subjects contribute them. This is the registry
// state under test, not the real installers (their registration is covered in
// install-*.test); the media preview body matches what the media-icons subject
// registers.
//
// Media-icon URLs come from `resolveMediaIcon`, a pure function of the href and
// `VITE_OPENCOP_CORE_API_URL` ("http://localhost/api" in vitest.config.ts).
const mediaHref = (name: string) => `opencop:url:/v1/layers/abc/media/${name}#32x32`;
const mediaImage = (name: string) =>
    cachedImage(`http://localhost/api/v1/layers/abc/media/${name}`);
/**
 * The test DOM never loads an image, so a test settles one by firing its event.
 * Until then each image must read as still loading; see the `beforeEach` below.
 */
const settle = (image: HTMLImageElement, event: "load" | "error") =>
    act(() => {
        image.dispatchEvent(new Event(event));
    });

beforeAll(() => {
    registerElementMapper(nativeOwner("opencop.test"), {
        type: "media-icon",
        map: () => [],
        preview: (element: Element) =>
            cachedImage(resolveMediaIcon((element.getStyle() as MediaIconStyle).getHref()).url),
    });
    registerElementMapper(nativeOwner("opencop.test"), {
        type: "symbol",
        map: () => [],
        preview: fallbackPreview,
    });
});

function makeIconElement(
    overrides: Partial<Element> & {
        type: string;
        getStyle: Element["getStyle"];
    },
): Element {
    const { getStyle, ...rest } = overrides;
    return {
        key: "test",
        objectId: "test",
        source: "test",
        geometry: { numPositions: 0 },
        getCode: () => "",
        getStyle,
        ...rest,
    } as unknown as Element;
}

/** A 2D context that records the calls made on it, so a test can see what was drawn. */
function spyContext() {
    const ctx = {
        scale: vi.fn(),
        clearRect: vi.fn(),
        beginPath: vi.fn(),
        arc: vi.fn(),
        fill: vi.fn(),
        drawImage: vi.fn(),
    };
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
        ctx as unknown as CanvasRenderingContext2D,
    );
    return ctx;
}

// happy-dom reports an image it never loads as complete with no width, which
// the preview reads as broken. A real browser reports it as still loading.
beforeEach(() => {
    vi.spyOn(HTMLImageElement.prototype, "complete", "get").mockReturnValue(false);
});

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("ElementPreviewCanvas", () => {
    it("draws a media icon's image once it loads, routed through its owning renderer", async () => {
        fallbackPreview.mockClear();
        const ctx = spyContext();
        const element = makeIconElement({
            type: "media-icon",
            getStyle: () => new MediaIconStyle({ href: mediaHref("loads") }),
        });

        render(<ElementPreviewCanvas element={element} />);
        expect(ctx.drawImage).not.toHaveBeenCalled(); // still loading

        settle(mediaImage("loads"), "load");

        await waitFor(() => expect(ctx.drawImage).toHaveBeenCalled());
        expect(ctx.drawImage.mock.calls[0]?.[0]).toBe(mediaImage("loads"));
        expect(fallbackPreview).not.toHaveBeenCalled();
    });

    it("draws the placeholder when the image fails to load", async () => {
        const ctx = spyContext();
        const element = makeIconElement({
            type: "media-icon",
            getStyle: () => new MediaIconStyle({ href: mediaHref("fails") }),
        });

        render(<ElementPreviewCanvas element={element} />);
        settle(mediaImage("fails"), "error");

        await waitFor(() => expect(ctx.arc).toHaveBeenCalled());
        expect(ctx.drawImage).not.toHaveBeenCalled();
    });

    it("tries the new image when the same element changes to a new href", async () => {
        const ctx = spyContext();
        const element = makeIconElement({
            type: "media-icon",
            getStyle: () => new MediaIconStyle({ href: mediaHref("before") }),
        });
        const { rerender } = render(<ElementPreviewCanvas element={element} />);
        settle(mediaImage("before"), "error");

        element.getStyle = () => new MediaIconStyle({ href: mediaHref("after") });
        rerender(<ElementPreviewCanvas element={element} />);
        settle(mediaImage("after"), "load");

        await waitFor(() => expect(ctx.drawImage).toHaveBeenCalled());
        expect(ctx.drawImage.mock.calls[0]?.[0]).toBe(mediaImage("after"));
    });

    it("draws the placeholder, and loads no image, for an element no renderer draws", async () => {
        const ctx = spyContext();
        // Any construction of Image at all is a regression here.
        const ImageSpy = vi.fn();
        vi.stubGlobal("Image", ImageSpy);

        render(
            <ElementPreviewCanvas
                element={makeIconElement({
                    type: "point",
                    getStyle: () => new IconStyle({ size: 32 }),
                })}
            />,
        );

        await waitFor(() => expect(ctx.arc).toHaveBeenCalled());
        expect(ctx.drawImage).not.toHaveBeenCalled();
        expect(ImageSpy).not.toHaveBeenCalled();
    });
});

/**
 * Characterization: the canvas operations each element type drew before the
 * per-type drawing moved out of the host. The shapes subject now draws them into
 * a canvas of its own through its renderers' `preview` (scaled 2×), which the row
 * then draws; an element no renderer draws, or one its renderer declines, gets
 * the host's placeholder. Only the operation names and the properties set are
 * recorded, so the pin survives coordinate rounding.
 */
describe("ElementPreviewCanvas — per-type drawing today", () => {
    let disposeShapes: () => void = noop;
    beforeAll(() => {
        disposeShapes = installNativeSubjects(
            [{ id: "opencop.shapes", install: installShapes }],
            contributionContextFor,
        );
    });
    afterAll(() => disposeShapes());

    function recordingContext(): { ctx: CanvasRenderingContext2D; ops: string[] } {
        const ops: string[] = [];
        const ctx = new Proxy(
            {},
            {
                get: (_target, prop) => () => ops.push(String(prop)),
                set: (_target, prop) => {
                    ops.push(`set ${String(prop)}`);
                    return true;
                },
            },
        ) as CanvasRenderingContext2D;
        return { ctx, ops };
    }

    const shapeStyle = () =>
        new ShapeStyle({
            fillColor: "#ff000080",
            strokeColor: "#ff0000ff",
            strokePattern: StrokePattern.Solid,
            strokeWeight: 2,
        });
    const strokeStyle = () =>
        new StrokeStyle({ color: "#00ff00ff", pattern: StrokePattern.Solid, weight: 2 });
    const pointStyle = () => new PointStyle({ color: "#0000ffff", radius: 3 });

    function geometryOf(points: Array<[number, number]>): Element["geometry"] {
        return {
            numPositions: points.length,
            getMercatorCoordinate: (i: number) => ({ x: points[i][0], y: points[i][1] }),
        } as unknown as Element["geometry"];
    }

    const TRIANGLE: Array<[number, number]> = [
        [0, 0],
        [1, 0],
        [1, 1],
    ];
    const SEGMENT: Array<[number, number]> = [
        [0, 0],
        [1, 1],
    ];

    const CLOSED_PATH = [
        "set lineWidth",
        "setLineDash",
        "beginPath",
        "moveTo",
        "lineTo",
        "lineTo",
        "closePath",
        "set fillStyle",
        "fill",
        "set strokeStyle",
        "stroke",
    ];
    const OPEN_PATH = [
        "set lineWidth",
        "setLineDash",
        "beginPath",
        "moveTo",
        "lineTo",
        "lineTo",
        "set strokeStyle",
        "stroke",
    ];
    const PLACEHOLDER = ["scale", "beginPath", "arc", "set fillStyle", "fill"];
    /** The shape's canvas, scaled and drawn, then drawn into the row's canvas. */
    const drawnBy = (shape: string[]) => ["scale", ...shape, "scale", "clearRect", "drawImage"];

    it.each<
        [string, string, () => ReturnType<Element["getStyle"]>, Array<[number, number]>, string[]]
    >([
        [
            "point",
            "point",
            pointStyle,
            [[0, 0]],
            drawnBy([
                "beginPath",
                "arc",
                "set fillStyle",
                "fill",
                "set strokeStyle",
                "set lineWidth",
                "stroke",
            ]),
        ],
        [
            "circle",
            "circle",
            shapeStyle,
            [[0, 0]],
            drawnBy([
                "set lineWidth",
                "setLineDash",
                "beginPath",
                "arc",
                "set fillStyle",
                "fill",
                "set strokeStyle",
                "stroke",
            ]),
        ],
        ["polygon", "polygon", shapeStyle, TRIANGLE, drawnBy(CLOSED_PATH)],
        ["rectangle", "rectangle", shapeStyle, TRIANGLE, drawnBy(CLOSED_PATH)],
        ["ellipse", "ellipse", shapeStyle, TRIANGLE, drawnBy(CLOSED_PATH)],
        ["line", "line", strokeStyle, TRIANGLE, drawnBy(OPEN_PATH)],
        ["angle", "angle", strokeStyle, TRIANGLE, drawnBy(OPEN_PATH)],
        [
            "arrow",
            "arrow",
            strokeStyle,
            SEGMENT,
            drawnBy([
                "set lineWidth",
                "setLineDash",
                "beginPath",
                "moveTo",
                "lineTo",
                "set strokeStyle",
                "stroke",
                "beginPath",
                "moveTo",
                "lineTo",
                "lineTo",
                "closePath",
                "set fillStyle",
                "fill",
            ]),
        ],
        ["an unknown type", "mystery", shapeStyle, TRIANGLE, PLACEHOLDER],
        // The renderer scales its canvas, then declines to draw the mismatched style.
        [
            "a point with a mismatched style",
            "point",
            shapeStyle,
            [[0, 0]],
            ["scale", ...PLACEHOLDER],
        ],
    ])("draws %s", async (_label, type, style, points, expected) => {
        const { ctx, ops } = recordingContext();
        vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(ctx);
        render(
            <ElementPreviewCanvas
                element={makeIconElement({ type, getStyle: style, geometry: geometryOf(points) })}
            />,
        );

        await waitFor(() => expect(ops.length).toBeGreaterThan(0));
        expect(ops).toEqual(expected);
    });
});
