// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import type { MapTile, UpdateMapTile } from "@opencop/api";
import { DropNotificationProvider } from "~/features/notifications/DropNotificationsContext";
import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { type FormDeltas, type FormTouched, TileDrawerContent } from "./TileDrawerContent";

const tile: MapTile = {
    id: "tile-1",
    primary: false,
    enabled: true,
    accessScope: "GLOBAL",
    name: "Imagery",
    description: "Imagery tile",
    url: "https://tiles.example.com/{z}/{x}/{y}.png",
    token: null,
    attribution: "© Example imagery",
    config: {
        type: "BASEMAP",
        tile: { kind: "RASTER", tileSize: 256, minZoom: null, maxZoom: null },
    },
};

/** The drawer keeps its draft in the parent; this stands in for that parent. */
function Harness() {
    const [deltas, setDeltas] = useState<FormDeltas>({});
    const [touched, setTouched] = useState<FormTouched>({});
    return (
        <DropNotificationProvider>
            <TileDrawerContent
                tileId={tile.id}
                deltas={deltas}
                touched={touched}
                onDeltasChange={setDeltas}
                onTouchedChange={setTouched}
                onDone={() => {}}
            />
        </DropNotificationProvider>
    );
}

describe("TileDrawerContent attribution and token", () => {
    const tooLong = "Attribution must be at most 1000 characters.";
    let patched: UpdateMapTile[];

    beforeEach(() => {
        patched = [];
        server.use(
            http.get(`${API_BASE}/notifications`, () =>
                HttpResponse.json({ content: [], page: { number: 0, size: 20, totalElements: 0 } }),
            ),
            http.get(`${API_BASE}/map/tiles/${tile.id}`, () => HttpResponse.json(tile)),
            http.patch(`${API_BASE}/map/tiles/${tile.id}`, async ({ request }) => {
                patched.push((await request.json()) as UpdateMapTile);
                return HttpResponse.json(tile);
            }),
        );
    });

    it("checks the length on blur and stays neutral while the value is corrected", async () => {
        const user = userEvent.setup();
        render(<Harness />);
        const field = await screen.findByRole("textbox", { name: "Attribution" });

        await user.clear(field);
        await user.paste("x".repeat(1001));
        expect(screen.queryByText(tooLong)).not.toBeInTheDocument();

        await user.tab();
        expect(await screen.findByText(tooLong)).toBeInTheDocument();

        await user.click(field);
        await user.keyboard("{End}{Backspace}");
        expect(screen.queryByText(tooLong)).not.toBeInTheDocument();
    });

    it("clears the credit by sending an empty string", async () => {
        const user = userEvent.setup();
        render(<Harness />);
        const field = await screen.findByRole("textbox", { name: "Attribution" });

        await user.clear(field);
        await user.click(screen.getByRole("button", { name: /save/i }));

        await waitFor(() => expect(patched).toEqual([{ attribution: "" }]));
    });

    it("clears a stored token by sending an empty string, since the API reads null as no change", async () => {
        server.use(
            http.get(`${API_BASE}/map/tiles/${tile.id}`, () =>
                HttpResponse.json({ ...tile, token: "stored-token" }),
            ),
        );
        const user = userEvent.setup();
        render(<Harness />);

        await user.click(await screen.findByRole("button", { name: "Clear token" }));
        await user.click(screen.getByRole("button", { name: /save/i }));

        await waitFor(() => expect(patched).toEqual([{ token: "" }]));
    });

    it.each([false, true])(
        "keeps the stored token after typing and erasing a replacement (pending clear: %s)",
        async (pendingClear) => {
            server.use(
                http.get(`${API_BASE}/map/tiles/${tile.id}`, () =>
                    HttpResponse.json({ ...tile, token: "stored-token" }),
                ),
            );
            const user = userEvent.setup();
            render(<Harness />);
            const field = await screen.findByLabelText("Token");
            const clear = screen.getByRole("button", { name: "Clear token" });
            const save = screen.getByRole("button", { name: /save/i });

            if (pendingClear) await user.click(clear);
            await user.type(field, "x");
            await user.clear(field);

            expect(save).toBeDisabled();
            expect(clear).toBeEnabled();
            await user.click(save);
            expect(patched).toEqual([]);

            // Saving another field must not turn the abandoned replacement into a token clear.
            await user.clear(screen.getByRole("textbox", { name: "Attribution" }));
            await user.click(save);
            await waitFor(() => expect(patched).toEqual([{ attribution: "" }]));
        },
    );
});
