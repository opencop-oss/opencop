// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { Polygon } from "@opencop/elements";
import { useWatchboxVisibilityStore } from "./watchbox-visibility.store";
import {
    isWatchboxVisible,
    WATCHBOX_DISABLED_DASH,
    WATCHBOX_DISABLED_STROKE_COLOR,
    WATCHBOX_ENABLED_DASH,
    WATCHBOX_ENABLED_STROKE_COLOR,
    watchboxPrimitives,
} from "./watchbox.mapper";
import type { Watchbox } from "./watchbox.model";
import { WATCHBOX_TYPE, watchboxDraftOf, type WatchboxObject } from "./watchbox.store";

const RING = [0, 0, 2, 0, 2, 2];

function saved(enabled = true, polygon = RING): WatchboxObject {
    return {
        key: "wb-1",
        type: WATCHBOX_TYPE,
        watchbox: { id: "wb-1", name: "North Gate", enabled } as Watchbox,
        polygon,
    };
}

afterEach(() => {
    useWatchboxVisibilityStore.setState({ hideDisabled: false });
});

describe("watchboxPrimitives", () => {
    it("draws an enabled watchbox as a closed dashed outline over a fill", () => {
        const [outline, fill, ...rest] = watchboxPrimitives(saved(), false);

        expect(outline).toMatchObject({
            id: "outline",
            geometry: { positions: [0, 0, 0, 2, 0, 0, 2, 2, 0, 0, 0, 0] },
            style: {
                strokeColor: WATCHBOX_ENABLED_STROKE_COLOR,
                strokeDash: WATCHBOX_ENABLED_DASH,
            },
        });
        expect(fill).toMatchObject({ id: "fill" });
        expect(rest).toEqual([]);
    });

    it("draws a disabled watchbox grey, with the disabled dash", () => {
        const [outline] = watchboxPrimitives(saved(false), false);

        expect(outline.style).toMatchObject({
            strokeColor: WATCHBOX_DISABLED_STROKE_COLOR,
            strokeDash: WATCHBOX_DISABLED_DASH,
        });
    });

    it("marks where a drawn bowtie crosses itself", () => {
        const bowtie = { ...saved(), watchbox: null, polygon: [0, 0, 1, 1, 1, 0, 0, 1] };

        expect(
            watchboxPrimitives(bowtie, false).find((p) => p.id === "self-intersection"),
        ).toMatchObject({ geometry: { positions: [0.5, 0.5, 0] } });
    });

    it("marks nothing on a simple drawn square, or on a saved watchbox", () => {
        const square = [0, 0, 1, 0, 1, 1, 0, 1];

        expect(
            watchboxPrimitives({ ...saved(), watchbox: null, polygon: square }, false).map(
                (p) => p.id,
            ),
        ).not.toContain("self-intersection");
        expect(
            watchboxPrimitives(saved(true, [0, 0, 1, 1, 1, 0, 0, 1]), false).map((p) => p.id),
        ).not.toContain("self-intersection");
    });

    it("labels a saved watchbox with its name only when asked", () => {
        expect(watchboxPrimitives(saved(), true).at(-1)).toMatchObject({
            id: "label",
            style: { text: "North Gate" },
        });
        expect(watchboxPrimitives(saved(), false).map((p) => p.id)).not.toContain("label");
    });

    it("draws the watchbox being drawn as enabled and unlabelled", () => {
        const draft = watchboxDraftOf(Polygon.create({ positions: RING }));

        const primitives = watchboxPrimitives(draft, true);

        expect(primitives.map((p) => p.id)).toEqual(["outline", "fill"]);
        expect(primitives[0].style).toMatchObject({ strokeDash: WATCHBOX_ENABLED_DASH });
    });

    it("draws an outline alone for two vertices, and nothing for one", () => {
        expect(watchboxPrimitives(saved(true, [0, 0, 1, 1]), false).map((p) => p.id)).toEqual([
            "outline",
        ]);
        expect(watchboxPrimitives(saved(true, [0, 0]), false)).toEqual([]);
    });
});

describe("isWatchboxVisible", () => {
    it("hides only disabled watchboxes, and only while hide-disabled is on", () => {
        expect(isWatchboxVisible(saved(false))).toBe(true);

        useWatchboxVisibilityStore.setState({ hideDisabled: true });

        expect(isWatchboxVisible(saved(false))).toBe(false);
        expect(isWatchboxVisible(saved(true))).toBe(true);
        expect(isWatchboxVisible(watchboxDraftOf(Polygon.create({ positions: RING })))).toBe(true);
    });
});
