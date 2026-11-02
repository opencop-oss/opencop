// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { QueryClient } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { API_BASE, http, HttpResponse, server, waitFor } from "~/test";
import { subscribeBackendFeature } from "./backend-feature.subscribe";

function metadata(enabled: boolean) {
    return { features: [{ name: "watchbox", enabled }] };
}

function freshClient() {
    return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

describe("subscribeBackendFeature", () => {
    it("answers fail-closed at once, then with the server's answer", async () => {
        server.use(http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json(metadata(true))));
        const client = freshClient();
        const onChange = vi.fn();

        const unsubscribe = subscribeBackendFeature("watchbox", onChange, client);

        // Nothing fetched yet: disabled, synchronously — the same reading the hook gives.
        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange).toHaveBeenLastCalledWith(false);

        await waitFor(() => expect(onChange).toHaveBeenLastCalledWith(true));
        expect(onChange).toHaveBeenCalledTimes(2);
        unsubscribe();
    });

    it("reports each change once and goes quiet after unsubscribe", async () => {
        server.use(http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json(metadata(true))));
        const client = freshClient();
        const onChange = vi.fn();
        const unsubscribe = subscribeBackendFeature("watchbox", onChange, client);
        await waitFor(() => expect(onChange).toHaveBeenLastCalledWith(true));

        // A refetch that carries the same answer is not a change.
        client.setQueryData(["app-metadata"], metadata(true));
        await client.invalidateQueries({ queryKey: ["app-metadata"], refetchType: "none" });
        expect(onChange).toHaveBeenCalledTimes(2);

        client.setQueryData(["app-metadata"], metadata(false));
        await waitFor(() => expect(onChange).toHaveBeenLastCalledWith(false));
        expect(onChange).toHaveBeenCalledTimes(3);

        unsubscribe();
        client.setQueryData(["app-metadata"], metadata(true));
        await new Promise((r) => setTimeout(r, 20));
        expect(onChange).toHaveBeenCalledTimes(3);
    });

    it("reads a query that fails before ever resolving as disabled", async () => {
        server.use(
            http.get(`${API_BASE}/app-metadata`, () => HttpResponse.json({}, { status: 500 })),
        );
        const client = freshClient();
        const onChange = vi.fn();
        const unsubscribe = subscribeBackendFeature("watchbox", onChange, client);

        await waitFor(() => expect(client.getQueryState(["app-metadata"])?.status).toBe("error"));
        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange).toHaveBeenLastCalledWith(false);
        unsubscribe();
    });

    it("reads a feature the server does not list as disabled", async () => {
        server.use(
            http.get(`${API_BASE}/app-metadata`, () =>
                HttpResponse.json({ features: [{ name: "rtsp", enabled: true }] }),
            ),
        );
        const client = freshClient();
        const onChange = vi.fn();
        const unsubscribe = subscribeBackendFeature("watchbox", onChange, client);

        await waitFor(() => expect(client.getQueryState(["app-metadata"])?.status).toBe("success"));
        expect(onChange).toHaveBeenCalledTimes(1);
        expect(onChange).toHaveBeenLastCalledWith(false);
        unsubscribe();
    });
});
