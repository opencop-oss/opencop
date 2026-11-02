// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { render, waitFor } from "~/test";
import type { MapTile } from "@opencop/api";
import { TilePreviewImage } from "./TilePreviewImage";

vi.mock("./render-vector-tile-preview.util", () => ({
    renderVectorTilePreview: vi.fn(),
}));

// Default: treat tiles as VECTOR so renderVectorTilePreview is exercised.
// Tests that need RASTER use vi.mocked(getTileKind).mockReturnValue("RASTER").
vi.mock("./tile-config.util", () => ({
    getTileKind: vi.fn(() => "VECTOR"),
}));

import { renderVectorTilePreview } from "./render-vector-tile-preview.util";
import { getTileKind } from "./tile-config.util";

const mockRender = vi.mocked(renderVectorTilePreview);
const PREVIEW_SRC = "data:image/jpeg;base64,/9j/preview";

let tileSeq = 0;
function vectorTile(): MapTile {
    return { id: `tile-${++tileSeq}` } as unknown as MapTile;
}

afterEach(() => vi.clearAllMocks());

describe("TilePreviewImage — render queue", () => {
    it("survives a rejected render so subsequent tiles still resolve to img", async () => {
        mockRender
            .mockRejectedValueOnce(new Error("style fetch failed"))
            .mockResolvedValue(PREVIEW_SRC);

        const tileA = vectorTile();
        const tileB = vectorTile();

        // tileA's render rejects — without the .catch() this poisons the queue.
        render(<TilePreviewImage tile={tileA} />);

        // tileB is queued behind tileA and must still resolve.
        const { container } = render(<TilePreviewImage tile={tileB} />);

        await waitFor(() => {
            const img = container.querySelector("img");
            expect(img).not.toBeNull();
            expect(img!.getAttribute("src")).toBe(PREVIEW_SRC);
        });
    });
});

describe("TilePreviewImage — RASTER token preview", () => {
    const TILE_ID = "token-raster";
    const tile = {
        id: TILE_ID,
        url: "https://tile.example.com/{z}/{x}/{y}.png?key={key}",
        token: null,
    } as unknown as MapTile;

    beforeEach(() => {
        vi.mocked(getTileKind).mockReturnValue("RASTER");
    });
    afterEach(() => {
        vi.mocked(getTileKind).mockReturnValue("VECTOR");
    });

    it("keeps the token preview across a re-render", () => {
        const { container, rerender } = render(<TilePreviewImage tile={tile} />, {
            queryData: {
                [JSON.stringify(["tiles", "detail", TILE_ID])]: { ...tile, token: "REALTOKEN" },
            },
        });
        expect(container.querySelector("img")?.getAttribute("src")).toContain("key=REALTOKEN");

        rerender(<TilePreviewImage tile={tile} />);
        // Fails at 488333348 without the setPreview fix — guard finds cache populated,
        // falls back to preview state still at { kind:"loading" }, renders Skeleton permanently.
        expect(container.querySelector("img")).not.toBeNull();
    });
});

describe("TilePreviewImage — abort guard", () => {
    it("does not update state when the signal is aborted before the render resolves", async () => {
        let resolveRender!: (src: string) => void;
        mockRender.mockReturnValue(
            new Promise<string>((res) => {
                resolveRender = res;
            }),
        );

        const tile = vectorTile();
        const { unmount, container } = render(<TilePreviewImage tile={tile} />);

        // Unmount aborts the controller signal before the render completes.
        unmount();

        // Resolve after unmount — must not throw or log an unhandled rejection.
        resolveRender(PREVIEW_SRC);
        await new Promise((r) => setTimeout(r, 10));

        // The result is still cached (cache set before the abort check), so a
        // fresh mount of the same tile immediately shows the img without a render.
        const { container: container2 } = render(<TilePreviewImage tile={tile} />);
        expect(container2.querySelector("img")).not.toBeNull();

        // The original mount's container is gone — no orphaned img there.
        expect(container.querySelector("img")).toBeNull();
    });
});
