// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { Page, RuntimeFeatureHistoryItem } from "@opencop/api";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import {
    act,
    API_BASE,
    createTestUser,
    http,
    HttpResponse,
    render,
    screen,
    server,
    waitFor,
} from "~/test";
import { runtimeFeatureQueries } from "../runtime-features.queries";
import { RuntimeFeatureHistory } from "./RuntimeFeatureHistory";

const FLAG = "watchbox";
const ACTOR_ID = "550e8400-e29b-41d4-a716-446655440020";
const GROUP_1 = "10000000-0000-4000-8000-000000000001";
const GROUP_2 = "10000000-0000-4000-8000-000000000002";
const GROUP_3 = "10000000-0000-4000-8000-000000000003";
const GROUP_4 = "10000000-0000-4000-8000-000000000004";
const GROUP_5 = "10000000-0000-4000-8000-000000000005";

function page(
    content: RuntimeFeatureHistoryItem[],
    metadata: Partial<Omit<Page<RuntimeFeatureHistoryItem>, "content">> = {},
): Page<RuntimeFeatureHistoryItem> {
    return {
        content,
        page: 0,
        size: 50,
        totalElements: content.length,
        totalPages: content.length === 0 ? 0 : 1,
        ...metadata,
    };
}

function historyItem(id: number): RuntimeFeatureHistoryItem {
    return {
        id,
        featureName: FLAG,
        consumerClass: "BACKEND_ENFORCED",
        operation: "PATCH",
        beforeAudience: { mode: "NONE" },
        afterAudience: { mode: "ALL" },
        revisionBefore: id - 1,
        revisionAfter: id,
        occurredAt: `2026-08-20T14:${String(id).padStart(2, "0")}:00Z`,
        actor: {
            userId: ACTOR_ID,
            fullName: `Operator ${id}`,
            globalAdmin: true,
        },
    };
}

function renderHistory() {
    return render(<RuntimeFeatureHistory name={FLAG} />, {
        user: createTestUser({ authorities: ["ROLE_admin"] }),
    });
}

// happy-dom retains focus when a control becomes disabled; Chromium moves it to the document.
// Make that browser behavior explicit so these tests exercise the component's restoration logic.
function simulateBrowserDisabledButtonBlur(button: HTMLElement): void {
    expect(button).toBeDisabled();
    expect(button).toHaveFocus();
    document.body.tabIndex = -1;
    document.body.focus();
    document.body.removeAttribute("tabindex");
    expect(document.body).toHaveFocus();
}

describe("RuntimeFeatureHistory", () => {
    afterEach(async () => {
        await i18n.changeLanguage("en");
    });

    it.each([
        {
            locale: "fr",
            changedAt: "Date de modification",
            operation: "Public cible mis à jour",
            audience: "Aucun utilisateur → Tous les utilisateurs",
            loadedCount: "1 sur 1",
        },
        {
            locale: "tw",
            changedAt: "時間",
            operation: "已更新適用對象",
            audience: "無人 → 所有人",
            loadedCount: "1 / 1",
        },
    ] as const)(
        "renders reviewed history copy in $locale",
        async ({ locale, changedAt, operation, audience, loadedCount }) => {
            await i18n.changeLanguage(locale);
            server.use(
                http.get(`${API_BASE}/admin/runtime-features/history`, () =>
                    HttpResponse.json(page([historyItem(1)])),
                ),
            );

            renderHistory();

            expect(await screen.findByRole("columnheader", { name: changedAt })).toBeVisible();
            expect(screen.getByText(operation)).toBeVisible();
            expect(screen.getByText(audience)).toBeVisible();
            expect(screen.getByText(loadedCount)).toBeVisible();
        },
    );

    it("renders create and patch facts with event-time actor fallback", async () => {
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, () =>
                HttpResponse.json(
                    page([
                        {
                            id: 4,
                            featureName: FLAG,
                            consumerClass: "FRONTEND_ONLY",
                            operation: "PATCH",
                            beforeAudience: {
                                mode: "USER_GROUPS",
                                userGroupIds: [GROUP_1, GROUP_2, GROUP_3],
                            },
                            afterAudience: {
                                mode: "USER_GROUPS",
                                userGroupIds: [GROUP_3, GROUP_4, GROUP_5],
                            },
                            revisionBefore: 2,
                            revisionAfter: 3,
                            occurredAt: "2026-08-20T14:15:00Z",
                            actor: {
                                userId: ACTOR_ID,
                                fullName: "Group Operator",
                                globalAdmin: true,
                            },
                        },
                        {
                            id: 3,
                            featureName: FLAG,
                            consumerClass: "FRONTEND_ONLY",
                            operation: "PATCH",
                            beforeAudience: { mode: "NONE" },
                            afterAudience: { mode: "ALL" },
                            revisionBefore: 0,
                            revisionAfter: 1,
                            occurredAt: "2026-08-20T14:10:00Z",
                            actor: {
                                userId: ACTOR_ID,
                                fullName: "Alex Operator",
                                email: "alex@example.test",
                                globalAdmin: true,
                            },
                        },
                        {
                            id: 2,
                            featureName: FLAG,
                            consumerClass: "FRONTEND_ONLY",
                            operation: "PATCH",
                            beforeAudience: { mode: "ALL" },
                            afterAudience: { mode: "ALL" },
                            revisionBefore: 1,
                            revisionAfter: 1,
                            occurredAt: "2026-08-20T14:05:00Z",
                            actor: {
                                userId: "550e8400-e29b-41d4-a716-446655440021",
                                email: "fallback@example.test",
                                globalAdmin: true,
                            },
                        },
                        {
                            id: 1,
                            featureName: FLAG,
                            consumerClass: "FRONTEND_ONLY",
                            operation: "CREATE",
                            beforeAudience: null,
                            afterAudience: { mode: "NONE" },
                            revisionBefore: null,
                            revisionAfter: 0,
                            occurredAt: "2026-08-20T14:00:00Z",
                            actor: {
                                userId: "550e8400-e29b-41d4-a716-446655440022",
                                globalAdmin: true,
                            },
                        },
                    ]),
                ),
            ),
        );

        renderHistory();

        expect(await screen.findByText("Created")).toBeInTheDocument();
        expect(screen.getAllByText("Audience updated")).toHaveLength(2);
        expect(screen.getByText("Audience unchanged")).toBeInTheDocument();
        expect(
            screen.getByText("Selected user groups (3) → Selected user groups (3)"),
        ).toBeInTheDocument();
        expect(screen.getByText("Groups added: 2, groups removed: 2")).toBeInTheDocument();
        expect(screen.queryByText(GROUP_1)).not.toBeInTheDocument();
        expect(screen.queryByText(GROUP_4)).not.toBeInTheDocument();
        expect(screen.getByText("— → Nobody")).toBeInTheDocument();
        expect(screen.getByText("Nobody → Everyone")).toBeInTheDocument();
        expect(screen.getByText("Everyone → Everyone")).toBeInTheDocument();
        expect(screen.getByText("— → 0")).toBeInTheDocument();
        expect(screen.getByText("0 → 1")).toBeInTheDocument();
        expect(screen.getByText("1 → 1")).toBeInTheDocument();
        expect(screen.getByText("Alex Operator")).toBeInTheDocument();
        expect(screen.queryByText("alex@example.test")).not.toBeInTheDocument();
        expect(screen.getByText("fallback@example.test")).toBeInTheDocument();
        expect(screen.getByText("550e8400-e29b-41d4-a716-446655440022")).toBeInTheDocument();
    });

    it("distinguishes an empty history from a failed request", async () => {
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, () =>
                HttpResponse.json(page([])),
            ),
        );

        const rendered = renderHistory();
        expect(await screen.findByText("No history recorded")).toBeInTheDocument();

        rendered.unmount();
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, () =>
                HttpResponse.json({ message: "boom" }, { status: 500 }),
            ),
        );
        renderHistory();

        expect(
            await screen.findByText("Recent changes could not be loaded. Try again."),
        ).toBeInTheDocument();
        expect(screen.queryByText("No history recorded")).not.toBeInTheDocument();
    });

    it("retains loaded rows and retries a failed background refresh", async () => {
        let requests = 0;
        let releaseRetryResponse!: () => void;
        const retryResponseGate = new Promise<void>((resolve) => {
            releaseRetryResponse = resolve;
        });
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, async () => {
                requests += 1;
                if (requests === 2) {
                    return HttpResponse.json({ message: "boom" }, { status: 500 });
                }
                if (requests === 3) await retryResponseGate;
                return HttpResponse.json(page([historyItem(requests === 1 ? 1 : 2)]));
            }),
        );

        const rendered = renderHistory();
        expect(await screen.findByText("Operator 1")).toBeInTheDocument();

        await rendered.queryClient.refetchQueries({
            queryKey: runtimeFeatureQueries.history(FLAG).queryKey,
        });

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Recent changes could not be refreshed. Showing the last loaded version.",
        );
        expect(screen.getByText("Operator 1")).toBeInTheDocument();

        const user = userEvent.setup();
        await user.click(screen.getByRole("button", { name: "Retry" }));
        await waitFor(() => expect(requests).toBe(3));
        expect(screen.getByRole("button", { name: "Retry" })).toBeDisabled();
        await act(async () => {
            releaseRetryResponse();
        });

        expect(await screen.findByText("Operator 2")).toBeInTheDocument();
        expect(screen.queryByText("Operator 1")).not.toBeInTheDocument();
        expect(
            screen.queryByText(
                "Recent changes could not be refreshed. Showing the last loaded version.",
            ),
        ).not.toBeInTheDocument();
        expect(requests).toBe(3);
    });

    it("moves keyboard focus to the loaded count after the terminal page", async () => {
        const requestedPages: string[] = [];
        const firstPage = Array.from({ length: 25 }, (_, index) => historyItem(50 - index));
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, ({ request }) => {
                const requestedPage = new URL(request.url).searchParams.get("page") ?? "";
                requestedPages.push(requestedPage);
                return HttpResponse.json(
                    requestedPage === "0"
                        ? page(firstPage, { page: 0, totalElements: 26, totalPages: 2 })
                        : page([historyItem(25)], {
                              page: 1,
                              totalElements: 26,
                              totalPages: 2,
                          }),
                );
            }),
        );

        renderHistory();

        expect(await screen.findByText("25 of 26")).toBeInTheDocument();
        expect(requestedPages).toEqual(["0"]);

        const user = userEvent.setup();
        screen.getByRole("button", { name: "Load more" }).focus();
        await user.keyboard("{Enter}");

        const loadedCount = await screen.findByText("26 of 26");
        await waitFor(() => expect(loadedCount).toHaveFocus());
        expect(loadedCount).toHaveAttribute("tabindex", "-1");
        expect(loadedCount).toHaveAttribute("aria-live", "polite");
        expect(loadedCount).toHaveAttribute("aria-atomic", "true");
        expect(screen.getByText("Operator 25")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Load more" })).not.toBeInTheDocument();
        expect(requestedPages).toEqual(["0", "1"]);
    });

    it("restores focus to the named paging action after a nonterminal page", async () => {
        const requestedPages: string[] = [];
        let releasePageResponse!: () => void;
        const pageResponseGate = new Promise<void>((resolve) => {
            releasePageResponse = resolve;
        });
        const firstPage = Array.from({ length: 25 }, (_, index) => historyItem(75 - index));
        const secondPage = Array.from({ length: 25 }, (_, index) => historyItem(50 - index));
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, async ({ request }) => {
                const requestedPage = new URL(request.url).searchParams.get("page") ?? "";
                requestedPages.push(requestedPage);
                if (requestedPage === "1") await pageResponseGate;
                return HttpResponse.json(
                    page(requestedPage === "0" ? firstPage : secondPage, {
                        page: Number(requestedPage),
                        totalElements: 75,
                        totalPages: 3,
                    }),
                );
            }),
        );

        renderHistory();
        const user = userEvent.setup();
        expect(await screen.findByText("25 of 75")).toBeInTheDocument();

        screen.getByRole("button", { name: "Load more" }).focus();
        const pagingButton = screen.getByRole("button", { name: "Load more" });
        await user.keyboard("{Enter}");

        await waitFor(() => expect(requestedPages).toEqual(["0", "1"]));
        const pendingPagingButton = screen.getByRole("button", { name: "Load more" });
        expect(pendingPagingButton).toBe(pagingButton);
        simulateBrowserDisabledButtonBlur(pendingPagingButton);
        await act(async () => {
            releasePageResponse();
        });

        expect(await screen.findByText("50 of 75")).toBeInTheDocument();
        await waitFor(() => expect(pagingButton).toHaveFocus());
        expect(document.body).not.toHaveFocus();
    });

    it("moves keyboard focus to the loaded count after recovering from paging drift", async () => {
        const requestedPages: string[] = [];
        let firstPageRequests = 0;
        let releaseRecoveryResponse!: () => void;
        const recoveryResponseGate = new Promise<void>((resolve) => {
            releaseRecoveryResponse = resolve;
        });
        const firstPage = Array.from({ length: 25 }, (_, index) => historyItem(50 - index));
        const refreshedFirstPage = Array.from({ length: 25 }, (_, index) =>
            historyItem(51 - index),
        );
        const shiftedSecondPage = Array.from({ length: 25 }, (_, index) => historyItem(26 - index));
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, async ({ request }) => {
                const requestedPage = new URL(request.url).searchParams.get("page") ?? "";
                requestedPages.push(requestedPage);
                if (requestedPage === "0") {
                    firstPageRequests += 1;
                    if (firstPageRequests === 1) {
                        return HttpResponse.json(
                            page(firstPage, {
                                page: 0,
                                totalElements: 50,
                                totalPages: 2,
                            }),
                        );
                    }
                    await recoveryResponseGate;
                    return HttpResponse.json(
                        page(refreshedFirstPage, {
                            page: 0,
                            totalElements: 51,
                            totalPages: 3,
                        }),
                    );
                }
                return HttpResponse.json(
                    page(shiftedSecondPage, {
                        page: 1,
                        totalElements: 51,
                        totalPages: 3,
                    }),
                );
            }),
        );

        renderHistory();
        const user = userEvent.setup();
        expect(await screen.findByText("25 of 50")).toBeInTheDocument();

        screen.getByRole("button", { name: "Load more" }).focus();
        await user.keyboard("{Enter}");

        expect(await screen.findByText("49 of 51")).toBeInTheDocument();
        expect(screen.getAllByText("Operator 26")).toHaveLength(1);
        expect(screen.getByRole("status")).toHaveTextContent(
            "History changed while more rows were loading. Refresh to restart from the newest changes.",
        );
        expect(screen.queryByRole("button", { name: "Load more" })).not.toBeInTheDocument();

        const refresh = screen.getByRole("button", { name: "Refresh" });
        await waitFor(() => expect(refresh).toHaveFocus());
        await user.keyboard("{Enter}");

        await waitFor(() => expect(firstPageRequests).toBe(2));
        const pendingRefresh = screen.getByRole("button", { name: "Refresh" });
        expect(pendingRefresh).toBe(refresh);
        simulateBrowserDisabledButtonBlur(pendingRefresh);
        await act(async () => {
            releaseRecoveryResponse();
        });

        expect(await screen.findByText("Operator 51")).toBeInTheDocument();
        expect(screen.queryByText("Operator 2")).not.toBeInTheDocument();
        const loadedCount = screen.getByText("25 of 51");
        await waitFor(() => expect(loadedCount).toHaveFocus());
        expect(loadedCount).toHaveAttribute("tabindex", "-1");
        expect(screen.getByRole("button", { name: "Load more" })).toBeInTheDocument();
        expect(requestedPages).toEqual(["0", "1", "0"]);
    });

    it("keeps one meaningful focused recovery action when a paging refresh fails", async () => {
        let firstPageRequests = 0;
        let releaseRecoveryFailure!: () => void;
        const recoveryFailureGate = new Promise<void>((resolve) => {
            releaseRecoveryFailure = resolve;
        });
        const firstPage = Array.from({ length: 25 }, (_, index) => historyItem(50 - index));
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, async ({ request }) => {
                const requestedPage = Number(new URL(request.url).searchParams.get("page") ?? "0");
                if (requestedPage === 0) {
                    firstPageRequests += 1;
                    if (firstPageRequests === 1) {
                        return HttpResponse.json(
                            page(firstPage, {
                                page: 0,
                                totalElements: 26,
                                totalPages: 2,
                            }),
                        );
                    }
                    await recoveryFailureGate;
                    return HttpResponse.json({ message: "boom" }, { status: 500 });
                }
                return HttpResponse.json(
                    page([historyItem(25)], {
                        page: 1,
                        totalElements: 27,
                        totalPages: 2,
                    }),
                );
            }),
        );

        renderHistory();
        const user = userEvent.setup();
        expect(await screen.findByText("25 of 26")).toBeInTheDocument();

        screen.getByRole("button", { name: "Load more" }).focus();
        await user.keyboard("{Enter}");
        expect(await screen.findByRole("status")).toHaveTextContent(
            "History changed while more rows were loading. Refresh to restart from the newest changes.",
        );
        expect(screen.getByText("Operator 25")).toBeInTheDocument();
        const refresh = screen.getByRole("button", { name: "Refresh" });
        await waitFor(() => expect(refresh).toHaveFocus());

        await user.keyboard("{Enter}");
        await waitFor(() => expect(firstPageRequests).toBe(2));
        const pendingRefresh = screen.getByRole("button", { name: "Refresh" });
        expect(pendingRefresh).toBe(refresh);
        simulateBrowserDisabledButtonBlur(pendingRefresh);
        await act(async () => {
            releaseRecoveryFailure();
        });

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Recent changes could not be refreshed. Showing the last loaded version.",
        );
        const retry = screen.getByRole("button", { name: "Retry" });
        expect(retry).toBe(refresh);
        expect(retry).toHaveFocus();
        expect(screen.getAllByRole("button", { name: "Retry" })).toHaveLength(1);
        expect(screen.getByText("Operator 25")).toBeInTheDocument();
        expect(document.body).not.toHaveFocus();
    });

    it("moves keyboard focus to Refresh when a terminal page reveals drift", async () => {
        const firstPage = Array.from({ length: 25 }, (_, index) => historyItem(50 - index));
        const secondPage = Array.from({ length: 25 }, (_, index) => historyItem(25 - index));
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, ({ request }) => {
                const requestedPage = Number(new URL(request.url).searchParams.get("page") ?? "0");
                return HttpResponse.json(
                    page(requestedPage === 0 ? firstPage : requestedPage === 1 ? secondPage : [], {
                        page: requestedPage,
                        totalElements: 51,
                        totalPages: 3,
                    }),
                );
            }),
        );

        renderHistory();
        const user = userEvent.setup();
        expect(await screen.findByText("25 of 51")).toBeInTheDocument();

        screen.getByRole("button", { name: "Load more" }).focus();
        await user.keyboard("{Enter}");
        expect(await screen.findByText("50 of 51")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Load more" })).toHaveFocus();

        await user.keyboard("{Enter}");

        expect(await screen.findByRole("status")).toHaveTextContent(
            "History changed while more rows were loading. Refresh to restart from the newest changes.",
        );
        expect(screen.getByText("50 of 51")).toBeInTheDocument();
        await waitFor(() => expect(screen.getByRole("button", { name: "Refresh" })).toHaveFocus());
        expect(screen.queryByRole("button", { name: "Load more" })).not.toBeInTheDocument();
    });

    it("retains focus, announces the error, and retries only a failed later page", async () => {
        let laterPageRequests = 0;
        let releaseFailureResponse!: () => void;
        const failureResponseGate = new Promise<void>((resolve) => {
            releaseFailureResponse = resolve;
        });
        const firstPage = Array.from({ length: 25 }, (_, index) => historyItem(50 - index));
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, async ({ request }) => {
                const requestedPage = new URL(request.url).searchParams.get("page");
                if (requestedPage === "0") {
                    return HttpResponse.json(
                        page(firstPage, { page: 0, totalElements: 26, totalPages: 2 }),
                    );
                }

                laterPageRequests += 1;
                if (laterPageRequests === 1) {
                    await failureResponseGate;
                    return HttpResponse.json({ message: "boom" }, { status: 500 });
                }
                return HttpResponse.json(
                    page([historyItem(25)], {
                        page: 1,
                        totalElements: 26,
                        totalPages: 2,
                    }),
                );
            }),
        );

        renderHistory();
        const user = userEvent.setup();
        expect(await screen.findByText("25 of 26")).toBeInTheDocument();

        screen.getByRole("button", { name: "Load more" }).focus();
        await user.keyboard("{Enter}");

        await waitFor(() => expect(laterPageRequests).toBe(1));
        const pendingPagingButton = screen.getByRole("button", { name: "Load more" });
        simulateBrowserDisabledButtonBlur(pendingPagingButton);
        await act(async () => {
            releaseFailureResponse();
        });

        expect(await screen.findByRole("alert")).toHaveTextContent("Couldn't load more");
        expect(screen.getByText("Operator 50")).toBeInTheDocument();
        expect(screen.getByText("25 of 26")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Retry" })).toHaveFocus();

        await user.keyboard("{Enter}");

        await waitFor(() => expect(laterPageRequests).toBe(2));
        expect(await screen.findByText("26 of 26")).toBeInTheDocument();
        expect(screen.getByText("Operator 25")).toBeInTheDocument();
        expect(screen.queryByText("Couldn't load more")).not.toBeInTheDocument();
    });

    it("reaches Load more by Tab from the search box", async () => {
        server.use(
            http.get(`${API_BASE}/admin/runtime-features/history`, ({ request }) => {
                const requestedPage = Number(new URL(request.url).searchParams.get("page") ?? "0");
                return HttpResponse.json(
                    page([historyItem(50 - requestedPage)], {
                        page: requestedPage,
                        totalElements: 2,
                        totalPages: 2,
                    }),
                );
            }),
        );

        renderHistory();
        const user = userEvent.setup();
        await user.click(await screen.findByPlaceholderText("Filter for any field..."));
        const loadMore = screen.getByRole("button", { name: "Load more" });
        for (let i = 0; i < 25 && document.activeElement !== loadMore; i++) await user.tab();

        expect(loadMore).toHaveFocus();
    });
});
