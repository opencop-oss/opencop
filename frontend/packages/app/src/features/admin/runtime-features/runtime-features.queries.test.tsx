// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    QueryClient,
    useInfiniteQuery,
    useMutation,
    useQuery,
    useQueryClient,
} from "@tanstack/react-query";
import type { RuntimeFeatureHistoryItem } from "@opencop/api";
import { beforeEach, describe, expect, it } from "vitest";
import type { ReactElement } from "react";
import {
    API_BASE,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    waitFor,
} from "~/test";
import { appMetadataQueries } from "~/features/admin/app-metadata.queries";
import { runtimeFeatureMutations, runtimeFeatureQueries } from "./runtime-features.queries";

const FLAG = "themes-page";
const OTHER_FLAG = "watchbox";

/** Server-side catalog, swapped between requests to stand in for another caller's writes. */
let catalog: {
    name: string;
    consumerClass: string;
    audience: { mode: string };
    revision: number;
    updatedAt: string;
}[];
let metadataFetches = 0;
let historyFetches = 0;
let historyFetchesByFeature: Record<string, number>;
let historyRows: RuntimeFeatureHistoryItem[];

function row(name: string) {
    return {
        name,
        consumerClass: "FRONTEND_ONLY",
        audience: { mode: "NONE" },
        revision: 0,
        updatedAt: "2026-08-13T00:00:00Z",
    };
}

function historyRow(id: number): RuntimeFeatureHistoryItem {
    return {
        id,
        featureName: FLAG,
        consumerClass: "FRONTEND_ONLY",
        operation: "PATCH",
        beforeAudience: { mode: "NONE" },
        afterAudience: { mode: "ALL" },
        revisionBefore: 0,
        revisionAfter: 1,
        occurredAt: "2026-08-13T00:01:00Z",
        actor: { userId: "00000000-0000-4000-8000-000000000001", globalAdmin: true },
    };
}

/**
 * Reads the catalog through the same query the admin page does and exposes a Sync button, so an
 * invalidation is observable as a refetch rather than asserted against the query client's internals.
 */
function Host({
    names,
    observeOtherHistory,
}: {
    names: string[];
    observeOtherHistory: boolean;
}): ReactElement {
    const { data } = useQuery(appMetadataQueries.detail());
    const { data: historyData } = useInfiniteQuery(runtimeFeatureQueries.history(FLAG));
    useInfiniteQuery({
        ...runtimeFeatureQueries.history(OTHER_FLAG),
        enabled: observeOtherHistory,
    });
    const queryClient = useQueryClient();
    const { mutate, isError: isReconcileError } = useMutation(
        runtimeFeatureMutations.reconcile(queryClient),
    );
    const { mutate: patch, isError: isPatchError } = useMutation(
        runtimeFeatureMutations.patchAudience(queryClient),
    );
    const registered = data?.admin?.runtimeFeatures?.map((feature) => feature.name) ?? [];
    const selected = data?.admin?.runtimeFeatures?.find((feature) => feature.name === FLAG);
    const historyIds = historyData?.pages.flatMap((page) => page.content.map((item) => item.id));
    return (
        <div>
            <button data-testid="sync" onClick={() => mutate(names)} />
            <button
                data-testid="patch"
                onClick={() => patch({ name: FLAG, patch: { audience: { mode: "ALL" } } })}
            />
            <div data-testid="registered">{registered.join(",")}</div>
            <div data-testid="revision">{selected?.revision}</div>
            <div data-testid="history-ids">{historyIds?.join(",")}</div>
            {isReconcileError && <div data-testid="failed" />}
            {isPatchError && <div data-testid="patch-failed" />}
        </div>
    );
}

function renderHost(names: string[], observeOtherHistory = false) {
    return render(<Host names={names} observeOtherHistory={observeOtherHistory} />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
        queryClient: new QueryClient({
            defaultOptions: {
                queries: { retry: false, staleTime: 0 },
                mutations: { retry: false },
            },
        }),
    });
}

beforeEach(() => {
    catalog = [];
    metadataFetches = 0;
    historyFetches = 0;
    historyFetchesByFeature = {};
    historyRows = [];
    server.use(
        http.get(`${API_BASE}/app-metadata`, () => {
            metadataFetches += 1;
            return HttpResponse.json({ features: [], admin: { runtimeFeatures: catalog } });
        }),
        http.get(`${API_BASE}/admin/runtime-features/history`, ({ request }) => {
            historyFetches += 1;
            const featureName = new URL(request.url).searchParams.get("featureName") ?? "";
            historyFetchesByFeature[featureName] = (historyFetchesByFeature[featureName] ?? 0) + 1;
            return HttpResponse.json({
                content: historyRows,
                page: 0,
                size: 25,
                totalElements: historyRows.length,
                totalPages: historyRows.length === 0 ? 0 : 1,
            });
        }),
    );
});

describe("reconcile invalidation", () => {
    /**
     * A run that creates nothing still teaches this browser about rows another caller made. Gating
     * the refetch on `created.length` left the table showing them as Unsynced against a catalog
     * that already had them.
     */
    it("refetches the catalog when reconcile only confirms existing definitions", async () => {
        server.use(
            http.post(`${API_BASE}/admin/runtime-features/reconcile`, () => {
                // Another admin registered it first; this call converges without creating anything.
                catalog = [row(FLAG)];
                return HttpResponse.json({
                    created: [],
                    alreadyRegistered: [row(FLAG)],
                    rejected: [],
                    complete: true,
                });
            }),
        );

        renderHost([FLAG], true);
        await waitFor(() => {
            expect(metadataFetches).toBe(1);
            expect(historyFetches).toBe(2);
        });
        expect(historyFetchesByFeature).toEqual({ [FLAG]: 1, [OTHER_FLAG]: 1 });
        expect(screen.getByTestId("registered")).toHaveTextContent("");

        screen.getByTestId("sync").click();

        await waitFor(() => expect(screen.getByTestId("registered")).toHaveTextContent(FLAG));
        expect(metadataFetches).toBe(2);
        expect(historyFetches).toBe(4);
        expect(historyFetchesByFeature).toEqual({ [FLAG]: 2, [OTHER_FLAG]: 2 });
    });

    /**
     * A failure is ambiguous: the server may have committed an earlier prefix before it stopped, so
     * the cached catalog is of unknown accuracy and has to be reread rather than trusted.
     */
    it("refetches the catalog after a failed reconcile that may have partly committed", async () => {
        server.use(
            http.post(`${API_BASE}/admin/runtime-features/reconcile`, () => {
                catalog = [row("committed-before-the-failure")];
                return HttpResponse.json({ message: "boom" }, { status: 500 });
            }),
        );

        renderHost([FLAG]);
        await waitFor(() => {
            expect(metadataFetches).toBe(1);
            expect(historyFetches).toBe(1);
        });

        screen.getByTestId("sync").click();

        await waitFor(() => expect(screen.getByTestId("failed")).toBeInTheDocument());
        await waitFor(() =>
            expect(screen.getByTestId("registered")).toHaveTextContent(
                "committed-before-the-failure",
            ),
        );
        expect(metadataFetches).toBe(2);
        expect(historyFetches).toBe(2);
    });
});

describe("audience patch invalidation", () => {
    it("refetches metadata and history after a successful patch", async () => {
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, () =>
                HttpResponse.json(row(FLAG)),
            ),
        );

        renderHost([FLAG], true);
        await waitFor(() => {
            expect(metadataFetches).toBe(1);
            expect(historyFetches).toBe(2);
        });
        expect(historyFetchesByFeature).toEqual({ [FLAG]: 1, [OTHER_FLAG]: 1 });

        screen.getByTestId("patch").click();

        await waitFor(() => {
            expect(metadataFetches).toBe(2);
            expect(historyFetches).toBe(3);
        });
        expect(historyFetchesByFeature).toEqual({ [FLAG]: 2, [OTHER_FLAG]: 1 });
    });

    /**
     * The fixture changes durable server state and then returns an error, standing in for Core
     * committing before a proxy/transport failure loses the response. The browser must treat that
     * failed promise as an unknown write outcome rather than proof that its two caches are current.
     */
    it("refetches metadata and history when a committed patch response is lost", async () => {
        catalog = [row(FLAG)];
        server.use(
            http.patch(`${API_BASE}/admin/runtime-features/${FLAG}`, () => {
                catalog = [
                    {
                        ...row(FLAG),
                        audience: { mode: "ALL" },
                        revision: 1,
                        updatedAt: "2026-08-13T00:01:00Z",
                    },
                ];
                historyRows = [historyRow(101)];
                return HttpResponse.json({ message: "response lost" }, { status: 500 });
            }),
        );

        renderHost([FLAG]);
        await waitFor(() => {
            expect(metadataFetches).toBe(1);
            expect(historyFetches).toBe(1);
        });
        expect(screen.getByTestId("revision")).toHaveTextContent("0");
        expect(screen.getByTestId("history-ids")).toHaveTextContent("");

        screen.getByTestId("patch").click();

        expect(await screen.findByTestId("patch-failed")).toBeInTheDocument();
        await waitFor(() => {
            expect(screen.getByTestId("revision")).toHaveTextContent("1");
            expect(screen.getByTestId("history-ids")).toHaveTextContent("101");
        });
        expect(metadataFetches).toBe(2);
        expect(historyFetches).toBe(2);
    });
});
