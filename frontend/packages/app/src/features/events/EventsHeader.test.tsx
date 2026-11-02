// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { nativeOwner } from "~/features/contributions/contribution-guard";
import { act, render, screen } from "~/test";
import { withPlugins } from "~/test/plugins";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import {
    CircleGeometry,
    Element,
    EllipseGeometry,
    MediaIcon,
    MediaIconStyle,
    PointGeometry,
    PointStyle,
    Polygon,
    PolygonGeometry,
    PolylineGeometry,
    type PartialOCFeature,
} from "@opencop/elements";
import { registerDetailsHeader } from "~/features/contributions/details-registry";
import { resolveMediaIcon } from "~/plugins/media-icons/media-icon";
import { registerElementMapper } from "~/features/contributions/element-mapper-registry";
import type { ReactNode } from "react";
import { cachedImage } from "@opencop/ui";

/**
 * Stands in for whatever renderer claims this element type without a `match` — a
 * plugin, in production. A stub, not the real renderer: these tests assert that
 * the events panel routes a thumbnail through the *owning renderer*, which is
 * exactly the property that must hold whichever subjects happen to be loaded.
 * Importing the real one would couple the host's tests to a plugin.
 */
const fallbackPreview = vi.fn((): CanvasImageSource | null => null);
import { i18n } from "~/i18n/config";
import { EventsHeader } from "./EventsHeader";

vi.mock("./detail/LayerIndicator", () => ({
    LayerIndicator: () => null,
}));

// Records the frame colour the header asks for, which the real box only turns
// into a style.
vi.mock("@opencop/ui", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@opencop/ui")>()),
    EntityIconBox: ({ borderColor, children }: { borderColor?: string; children: ReactNode }) => (
        <div data-testid="icon-frame" data-border={borderColor ?? "active"}>
            {children}
        </div>
    ),
}));

// Media-icon URLs are resolved by `resolveMediaIcon`, a pure function of the
// href and `VITE_OPENCOP_CORE_API_URL` ("http://localhost/api" in vitest.config.ts).
const mediaHref = (name: string) => `opencop:url:/v1/layers/abc/media/${name}#32x32`;
const mediaIcon = (name: string) => MediaIcon.create({ position: [0, 0], href: mediaHref(name) });
const mediaImage = (name: string) =>
    cachedImage(`http://localhost/api/v1/layers/abc/media/${name}`);
/**
 * The test DOM never loads an image, so a test settles one by firing its event.
 * Until then each image must read as still loading; see the `beforeEach` below.
 */
const fail = (image: HTMLImageElement) =>
    act(() => {
        image.dispatchEvent(new Event("error"));
    });

const details = {
    displayName: "Alpha",
    kind: "test-kind",
    objectId: "object-12345678",
    source: "local",
} as unknown as PartialOCFeature["properties"]["opencop"];

/** An element of `type` with the geometry that type carries, and an optional code. */
function elementOf(type: string, code?: string): Element {
    const geometry =
        type === "symbol" || type === "point"
            ? new PointGeometry([0, 0])
            : type === "circle"
              ? new CircleGeometry([0, 0], 10)
              : type === "ellipse"
                ? new EllipseGeometry([0, 0], 10, 5)
                : type === "polygon" || type === "rectangle"
                  ? new PolygonGeometry([0, 0, 1, 0, 1, 1])
                  : new PolylineGeometry([0, 0, 1, 1]);
    return Element.create({
        type,
        geometry,
        style: new PointStyle({ color: "#ffffffff", radius: 3 }),
        ...(code ? { code } : null),
    });
}

function renderHeader(element: Element) {
    return render(<EventsHeader details={details} element={element} />);
}

// The header resolves its thumbnail through the renderer registry and its glyph
// and frame through the details-header registry, so the subjects must be
// registered: the real shapes subject, and stand-ins for what media icons and
// symbology register (the symbol renderers and the graphic's frameless header).
const disposers: Array<() => void> = [];

beforeAll(() => {
    disposers.push(
        registerElementMapper(nativeOwner("opencop.test"), {
            type: "media-icon",
            map: () => [],
            preview: (element: Element) =>
                cachedImage(resolveMediaIcon((element.getStyle() as MediaIconStyle).getHref()).url),
        }),
        registerElementMapper(nativeOwner("opencop.test"), {
            type: "symbol",
            map: () => [],
            preview: fallbackPreview,
        }),
        registerDetailsHeader(nativeOwner("opencop.test"), { type: "graphic", iconFrame: "none" }),
    );
});

afterAll(() => {
    for (const dispose of disposers.splice(0)) dispose();
});

// happy-dom reports an image it never loads as complete with no width, which
// the header reads as broken. A real browser reports it as still loading.
beforeEach(() => {
    vi.spyOn(HTMLImageElement.prototype, "complete", "get").mockReturnValue(false);
});

afterEach(() => {
    vi.restoreAllMocks();
});

withPlugins(["opencop.shapes"]);

describe("EventsHeader", () => {
    it("shows a media icon's image, and the type icon when the image fails", () => {
        fallbackPreview.mockClear();

        renderHeader(mediaIcon("first"));

        expect(screen.getByRole("img", { name: "media-icon" }).tagName).toBe("CANVAS");
        expect(fallbackPreview).not.toHaveBeenCalled();

        fail(mediaImage("first"));

        expect(screen.queryByRole("img", { name: "media-icon" })).toBeNull();
        expect(screen.getByRole("img", { name: i18n.t("events.iconUnavailable") }).tagName).toBe(
            "svg",
        );
    });

    it("remembers an image that failed when the header comes back to it", () => {
        const iconUnavailableLabel = i18n.t("events.iconUnavailable");
        const { rerender } = renderHeader(mediaIcon("a"));
        fail(mediaImage("a"));
        expect(screen.getByRole("img", { name: iconUnavailableLabel }).tagName).toBe("svg");

        rerender(<EventsHeader details={details} element={mediaIcon("b")} />);

        expect(screen.getByRole("img", { name: "media-icon" }).tagName).toBe("CANVAS");
        fail(mediaImage("b"));
        expect(screen.getByRole("img", { name: iconUnavailableLabel }).tagName).toBe("svg");

        rerender(<EventsHeader details={details} element={mediaIcon("a")} />);

        expect(screen.getByRole("img", { name: iconUnavailableLabel }).tagName).toBe("svg");
        expect(screen.queryByRole("img", { name: "media-icon" })).toBeNull();
    });

    it("shows the same thumbnail a shape's renderer gives the event list", () => {
        // jsdom has no 2D canvas; give the shapes subject one so it can draw.
        vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
            new Proxy({}, { get: () => vi.fn() }) as unknown as CanvasRenderingContext2D,
        );

        renderHeader(
            Polygon.create({
                positions: [
                    [0, 0],
                    [1, 0],
                    [1, 1],
                ],
            }),
        );

        expect(screen.getByRole("img", { name: "polygon" }).tagName).toBe("CANVAS");
    });
});

/**
 * Characterization: the glyph and frame the header draws for each element type
 * when its renderer has no thumbnail (jsdom gives the shapes subject no canvas to
 * draw one). They moved behind the details-header surface: shapes and
 * symbology declare them, and the host draws its defaults for the rest.
 */
describe("EventsHeader — type glyph and frame today", () => {
    it.each([
        ["symbol", "circle", "active"],
        ["polygon", "polygon", "active"],
        ["line", "line", "active"],
        ["ellipse", "ellipse", "active"],
        ["arrow", "polygon", "active"],
        ["point", "ellipse", "active"],
        ["rectangle", "rect", "active"],
        ["circle", "rect", "active"],
        ["angle", "rect", "active"],
        ["graphic", "rect", "transparent"],
    ])("draws a %s as an svg <%s> in a %s frame", (type, glyph, frame) => {
        renderHeader(elementOf(type));

        const box = screen.getByTestId("icon-frame");
        expect(box.dataset.border).toBe(frame);
        expect(box.querySelector("svg")?.firstElementChild?.tagName.toLowerCase()).toBe(glyph);
    });
});
