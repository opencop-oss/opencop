// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import { Polygon } from "@opencop/elements";
import { QueryClient } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
    act,
    API_BASE,
    createTestUser,
    fireEvent,
    http,
    HttpResponse,
    render,
    screen,
    server,
    userEvent,
    waitFor,
} from "~/test";
import { layerQueries } from "~/features/layers/layers.queries";
import { i18n } from "~/i18n/config";
import { resetInteractionForTest, setSessionForTest } from "~/test/interaction";
import type { EditSession } from "~/features/map/interaction-runtime/session/session";
import { useWatchboxPanelStore } from "./watchbox-panel.store";
import { WATCHBOX_TOOL_ID } from "./watchbox-session";
import { useWatchboxVisibilityStore } from "./watchbox-visibility.store";
import { WATCHBOX_MUTE_STORAGE_KEY, useWatchboxMuteStore } from "./watchbox-mute.store";
import { watchboxQueries } from "./watchbox.queries";
import type { Watchbox } from "./watchbox.model";
import { WatchboxPanel } from "./WatchboxPanel";

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
const { mockActivate, mockCancel, mockSave } = vi.hoisted(() => ({
    mockActivate: vi.fn(),
    mockCancel: vi.fn(),
    mockSave: vi.fn(),
}));

// WatchboxPanel's own job is wiring — which command it calls, with what
// argument, for which click. What each command then does to the session and
// the mapbar panel is `attachWatchboxPanelPolicy`'s job and is pinned in
// watchbox-panel.policy.test.ts, not here.
vi.mock("~/features/map/interaction-runtime/session/commands", () => ({
    activate: (...args: unknown[]) => mockActivate(...args),
    cancel: (...args: unknown[]) => mockCancel(...args),
    save: (...args: unknown[]) => mockSave(...args),
}));

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

// spin-delay's 500ms delay would make loading-state assertions non-deterministic.
const mockUseSpinDelay = vi.fn();
vi.mock("spin-delay", () => ({
    useSpinDelay: (...args: unknown[]) => mockUseSpinDelay(...args),
}));

// Narrow double, not a real combobox — same approach as WatchboxForm.test.tsx.
// Just interactive enough to select one layer by clicking its label.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface ComboboxProps {
        items: { label: string; value: string }[];
        onValueChange?: (value: string[]) => void;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        OcCombobox: (props: ComboboxProps) =>
            createElement(
                "div",
                null,
                ...props.items.map((item) =>
                    createElement(
                        "button",
                        {
                            key: item.value,
                            type: "button",
                            onClick: () => props.onValueChange?.([item.value]),
                        },
                        item.label,
                    ),
                ),
            ),
    };
});

const LAYER_WITH_SOURCE: MyLayerInfo = {
    layerId: "layer-with-source",
    layerName: "Layer With Source",
    role: "OWNER",
    accessScope: "PRIVATE",
    defaultVisible: true,
};

const CREATED_WATCHBOX: Watchbox = {
    id: "wb-1",
    name: "New Watchbox",
    description: null,
    enabled: true,
    direction: "BOTH",
    polygon: {
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
    filter: { combine: "ALL", predicates: [] },
    layerIds: [LAYER_WITH_SOURCE.layerId],
    configEpoch: 1,
    alertingStatus: "ACTIVE",
    alertingReasons: [],
    sourceStatuses: [],
    capabilities: { canDelete: true, canPatch: true },
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
};

const WATCHBOXES_URL = `${API_BASE}/watchboxes`;

function makeWatchbox(overrides: Partial<Watchbox> = {}): Watchbox {
    return {
        ...CREATED_WATCHBOX,
        name: "My Watchbox",
        layerIds: ["layer-1"],
        ...overrides,
    };
}

const OTHER_WATCHBOX: Watchbox = {
    ...CREATED_WATCHBOX,
    id: "wb-2",
    name: "Other Watchbox",
};

/** The finished polygon exactly as the runtime hands it to the panel: an EDIT session over a custody-less product. */
function finishedWatchboxSession(): EditSession {
    const element = Polygon.create({ positions: [0, 0, 1, 0, 1, 1] });
    return {
        kind: "edit",
        descriptorId: WATCHBOX_TOOL_ID,
        epoch: 1,
        product: element,
        custody: false,
        params: {},
        isNew: true,
        sub: { hotspot: null, dragging: null },
    };
}

// Which watchbox is open now lives in a module-singleton store, so it outlives
// the render that set it: without this reset the test that opens a detail view
// leaves `selected` set and every later render starts on that detail instead of
// the list.
beforeEach(() => {
    useWatchboxPanelStore.setState(useWatchboxPanelStore.getInitialState(), true);
    mockUseSpinDelay.mockImplementation((loading: boolean) => loading);
});

afterEach(() => {
    act(() => useWatchboxVisibilityStore.getState().actions.setHideDisabled(false));
    act(() => resetInteractionForTest());
    vi.clearAllMocks();
});

describe("WatchboxPanel", () => {
    it("shows the creation form, titled for the new watchbox, once a polygon is finished", () => {
        act(() => setSessionForTest(finishedWatchboxSession()));

        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [],
                [JSON.stringify(layerQueries.myList().queryKey)]: [],
            },
        });

        expect(screen.getByRole("heading", { name: "NEW WATCHBOX" })).toBeInTheDocument();
    });

    it("shows the empty state, not the creation form, when there are no watchboxes and no polygon is finished", () => {
        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [],
            },
        });

        expect(screen.queryByRole("heading", { name: "NEW WATCHBOX" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Create watchbox" })).toBeInTheDocument();
    });

    it("renders a card for each existing watchbox instead of the empty state", () => {
        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
            },
        });

        expect(screen.getByText(CREATED_WATCHBOX.name)).toBeInTheDocument();
        expect(screen.queryByText("No watchboxes yet")).not.toBeInTheDocument();
    });

    it("activates the watchbox tool by id from the empty state's Create watchbox button", async () => {
        const user = userEvent.setup();

        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [],
            },
        });

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        expect(mockActivate).toHaveBeenCalledWith(WATCHBOX_TOOL_ID);
    });

    it("activates the watchbox tool by id from the list header's Create watchbox button", async () => {
        const user = userEvent.setup();

        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
            },
        });

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        expect(mockActivate).toHaveBeenCalledWith(WATCHBOX_TOOL_ID);
    });

    // Regression guard: onSaved and onCancel used to be a single handler that
    // always ended the session the same way. They're now split so a successful
    // save calls `save()` (the policy leaves the panel open on the refreshed
    // list) while Cancel calls `cancel()` (the policy closes it) — the same
    // split Escape gets over a finished, custody-less polygon. If either
    // branch calls the wrong command, or the two are swapped, no other test in
    // this file catches it.
    describe("draw-session wiring", () => {
        it("cancels the session, not saves it, when Cancel is clicked", async () => {
            const user = userEvent.setup();
            act(() => setSessionForTest(finishedWatchboxSession()));

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [],
                    [JSON.stringify(layerQueries.myList().queryKey)]: [],
                },
            });

            await user.click(screen.getByRole("button", { name: "Cancel" }));

            expect(mockCancel).toHaveBeenCalledOnce();
            expect(mockSave).not.toHaveBeenCalled();
        });

        it("saves the session, not cancels it, when a watchbox is saved successfully", async () => {
            const user = userEvent.setup();
            act(() => setSessionForTest(finishedWatchboxSession()));
            server.use(
                http.post(`${API_BASE}/watchboxes`, () => HttpResponse.json(CREATED_WATCHBOX)),
                // The create mutation invalidates the list on success, so it refetches.
                http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([CREATED_WATCHBOX])),
            );

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [],
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                    [JSON.stringify(layerQueries.dataSources(LAYER_WITH_SOURCE.layerId).queryKey)]:
                        [{ serverId: "src-0" }],
                },
            });

            await user.type(screen.getByRole("textbox", { name: "Name" }), "Test Watchbox");
            await user.click(screen.getByRole("button", { name: "Layer With Source" }));
            await user.type(screen.getByPlaceholderText("Enter a value"), "x");
            await user.click(screen.getByRole("button", { name: "Create watchbox" }));

            // No argument: the runtime holds no custody, so there is no
            // `updated` element to apply — passing one is refused.
            await waitFor(() => expect(mockSave).toHaveBeenCalledWith());
            expect(mockCancel).not.toHaveBeenCalled();
        });
    });
    describe("list <-> detail navigation", () => {
        it("opens the clicked watchbox's detail view instead of staying on the list", async () => {
            const user = userEvent.setup();

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
                    [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                        CREATED_WATCHBOX,
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            );

            expect(
                screen.getByRole("heading", { name: CREATED_WATCHBOX.name.toUpperCase() }),
            ).toBeInTheDocument();
        });

        it("opens the detail view from anywhere in the row, not just the name", async () => {
            const user = userEvent.setup();
            const watchboxWithDescription = { ...CREATED_WATCHBOX, description: "Some notes" };

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [
                        watchboxWithDescription,
                    ],
                    [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                        watchboxWithDescription,
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(screen.getByText("Some notes"));

            expect(
                screen.getByRole("heading", { name: CREATED_WATCHBOX.name.toUpperCase() }),
            ).toBeInTheDocument();
        });

        it("toggles enabled from the list row without opening the detail view", async () => {
            const user = userEvent.setup();
            server.use(
                http.patch(`${WATCHBOXES_URL}/${CREATED_WATCHBOX.id}`, () =>
                    HttpResponse.json({ ...CREATED_WATCHBOX, enabled: false }),
                ),
                http.get(WATCHBOXES_URL, () =>
                    HttpResponse.json([{ ...CREATED_WATCHBOX, enabled: false }]),
                ),
            );

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(await screen.findByRole("switch", { name: /^Toggle / }));

            expect(
                screen.queryByRole("heading", { name: CREATED_WATCHBOX.name.toUpperCase() }),
            ).not.toBeInTheDocument();
        });

        it("returns to the list from the detail view's breadcrumb", async () => {
            const user = userEvent.setup();

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
                    [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                        CREATED_WATCHBOX,
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            );
            await user.click(screen.getByRole("button", { name: "Watchbox" }));

            expect(
                screen.queryByRole("heading", { name: CREATED_WATCHBOX.name.toUpperCase() }),
            ).not.toBeInTheDocument();
            expect(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            ).toBeInTheDocument();
        });

        it("deletes from the list row without opening the detail view", async () => {
            const user = userEvent.setup();
            server.use(
                http.delete(`${API_BASE}/watchboxes/${CREATED_WATCHBOX.id}`, () =>
                    HttpResponse.json(null, { status: 204 }),
                ),
                http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([])),
            );

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(
                screen.getByRole("button", {
                    name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
                }),
            );
            await user.click(await screen.findByRole("menuitem", { name: "Delete watchbox" }));
            await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

            expect(
                screen.queryByRole("heading", { name: CREATED_WATCHBOX.name.toUpperCase() }),
            ).not.toBeInTheDocument();
        });
        it("returns to the list after deleting from the detail view", async () => {
            const user = userEvent.setup();
            server.use(
                http.delete(`${API_BASE}/watchboxes/${CREATED_WATCHBOX.id}`, () =>
                    HttpResponse.json(null, { status: 204 }),
                ),
                http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([])),
            );

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
                    [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                        CREATED_WATCHBOX,
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            );
            await user.click(
                screen.getByRole("button", {
                    name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
                }),
            );
            await user.click(await screen.findByRole("menuitem", { name: "Delete watchbox" }));
            await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

            await waitFor(() =>
                expect(
                    screen.queryByRole("heading", { name: CREATED_WATCHBOX.name.toUpperCase() }),
                ).not.toBeInTheDocument(),
            );
        });

        // A failed delete leaves the watchbox in place, so returning to the
        // list would wrongly imply it worked.
        it("stays on the detail view when the delete fails", async () => {
            const user = userEvent.setup();
            server.use(
                http.delete(`${API_BASE}/watchboxes/${CREATED_WATCHBOX.id}`, () =>
                    HttpResponse.json(
                        { code: "WATCHBOX_NOT_FOUND", message: "", fields: [], reasons: [] },
                        { status: 409 },
                    ),
                ),
                // Failed deletes refresh the list because the row's capabilities
                // may have changed since it was loaded.
                http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([CREATED_WATCHBOX])),
            );

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
                    [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                        CREATED_WATCHBOX,
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            );
            await user.click(
                screen.getByRole("button", {
                    name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
                }),
            );
            await user.click(await screen.findByRole("menuitem", { name: "Delete watchbox" }));
            await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

            await waitFor(() =>
                expect(
                    screen.getByRole("heading", { name: CREATED_WATCHBOX.name.toUpperCase() }),
                ).toBeInTheDocument(),
            );
        });
        it("keeps a slow detail delete open until it settles", async () => {
            const user = userEvent.setup();
            let releaseDelete: () => void = () => {};
            const deleteReleased = new Promise<void>((resolve) => {
                releaseDelete = resolve;
            });
            server.use(
                http.delete(`${API_BASE}/watchboxes/${CREATED_WATCHBOX.id}`, async () => {
                    await deleteReleased;
                    return HttpResponse.json(null, { status: 204 });
                }),
                http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([OTHER_WATCHBOX])),
            );

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [
                        CREATED_WATCHBOX,
                        OTHER_WATCHBOX,
                    ],
                    [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                        CREATED_WATCHBOX,
                    [JSON.stringify(watchboxQueries.watchbox(OTHER_WATCHBOX.id).queryKey)]:
                        OTHER_WATCHBOX,
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            );
            await user.click(
                screen.getByRole("button", {
                    name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
                }),
            );
            await user.click(await screen.findByRole("menuitem", { name: "Delete watchbox" }));
            await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

            await waitFor(() =>
                expect(
                    screen.getByRole("button", { name: i18n.t("common.saving") }),
                ).toBeDisabled(),
            );
            await user.click(screen.getByRole("button", { name: i18n.t("common.cancel") }));
            await user.keyboard("{Escape}");

            expect(
                screen.getByText(
                    i18n.t("watchbox.confirmDeleteMessage", { name: CREATED_WATCHBOX.name }),
                ),
            ).toBeInTheDocument();

            releaseDelete();

            await waitFor(() =>
                expect(
                    screen.queryByText(i18n.t("watchbox.confirmDeleteTitle")),
                ).not.toBeInTheDocument(),
            );
            expect(
                screen.getByRole("button", { name: new RegExp(`^${OTHER_WATCHBOX.name}`) }),
            ).toBeEnabled();
        });

        // data-fetching.md: removeQueries for deleted entities, so the detail
        // key cannot serve a deleted watchbox from cache later.
        it("evicts the deleted watchbox's detail entry without refetching it", async () => {
            const user = userEvent.setup();
            const detailRequests: string[] = [];
            const queryClient = new QueryClient({
                defaultOptions: { queries: { retry: false, staleTime: Infinity } },
            });
            server.use(
                http.delete(`${API_BASE}/watchboxes/${CREATED_WATCHBOX.id}`, () =>
                    HttpResponse.json(null, { status: 204 }),
                ),
                http.get(`${API_BASE}/watchboxes/${CREATED_WATCHBOX.id}`, () => {
                    detailRequests.push(CREATED_WATCHBOX.id);
                    return HttpResponse.json(CREATED_WATCHBOX);
                }),
                http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([])),
            );
            queryClient.setQueryData(watchboxQueries.watchboxes().queryKey, [CREATED_WATCHBOX]);
            queryClient.setQueryData(
                watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey,
                CREATED_WATCHBOX,
            );
            queryClient.setQueryData(layerQueries.myList().queryKey, [LAYER_WITH_SOURCE]);

            render(<WatchboxPanel />, { queryClient });

            await user.click(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            );
            await user.click(
                screen.getByRole("button", {
                    name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
                }),
            );
            await user.click(await screen.findByRole("menuitem", { name: "Delete watchbox" }));
            await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

            await waitFor(() =>
                expect(
                    queryClient.getQueryData(
                        watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey,
                    ),
                ).toBeUndefined(),
            );
            expect(detailRequests).toEqual([]);
        });
        // The delete IconButton used to sit inside a role="button" card, which
        // nests interactive controls and suppressed the row's own content from
        // its accessible name. Delete now lives behind the actions menu, but the
        // same nesting hazard applies to that menu's trigger.
        it("exposes the row opener and actions menu as sibling buttons", () => {
            // A static (non-canPatch) row is the one that folds enabled/health context
            // into the opener's accessible name — a canPatch row relies on the switch.
            const staticRowWatchbox: Watchbox = {
                ...CREATED_WATCHBOX,
                capabilities: { canDelete: true, canPatch: false },
            };
            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [staticRowWatchbox],
                },
            });

            const opener = screen.getByRole("button", {
                name: new RegExp(`^${CREATED_WATCHBOX.name}`),
            });
            const menuTrigger = screen.getByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
            });

            expect(opener.contains(menuTrigger)).toBe(false);
            expect(menuTrigger.contains(opener)).toBe(false);
            // A static row keeps enabled and health context in the opener's accessible name.
            expect(opener).toHaveAccessibleName(/Enabled/);
            expect(opener).toHaveAccessibleName(/Active/);
            // The actions menu sits beside the badges; its label must not join the
            // opener's name.
            expect(opener).not.toHaveAccessibleName(/More actions/);
        });

        it("moves focus to the list when the detail view is left", async () => {
            const user = userEvent.setup();

            render(<WatchboxPanel />, {
                queryData: {
                    [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
                    [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                        CREATED_WATCHBOX,
                    [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
                },
            });

            await user.click(
                screen.getByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
            );
            await user.click(screen.getByRole("button", { name: "Watchbox" }));

            await waitFor(() => expect(document.body).not.toHaveFocus());
        });
    });
});

describe("WatchboxPanel — list states", () => {
    it("shows a loading state while the list is pending", () => {
        server.use(http.get(WATCHBOXES_URL, () => new Promise<Response>(() => {})));

        render(<WatchboxPanel />);

        expect(screen.getByRole("status")).toHaveTextContent(i18n.t("common.loading"));
    });

    it("shows an error with retry when the list fails and nothing is cached", async () => {
        let calls = 0;
        server.use(
            http.get(WATCHBOXES_URL, () => {
                calls++;
                return calls === 1
                    ? new HttpResponse(null, { status: 500 })
                    : HttpResponse.json([]);
            }),
        );
        const user = userEvent.setup();
        const { container } = render(<WatchboxPanel />);
        const listAlert = container.querySelector('[role="alert"]:not([aria-atomic="true"])');

        // Assert via the persistent alert region rather than the visible copy — the
        // same text is deliberately duplicated into a screen-reader-only node (see
        // below), so a plain getByText would match twice.
        await waitFor(() =>
            expect(listAlert).toHaveTextContent(i18n.t("watchbox.listUnavailable")),
        );
        expect(container.querySelector('[aria-live="polite"]')).toHaveTextContent("");
        await user.click(screen.getByRole("button", { name: i18n.t("common.retry") }));

        await screen.findByText(i18n.t("watchbox.emptyStateTitle"));
        // The alert region clears rather than unmounting, proving it's the same
        // node throughout — a screen reader watching it sees the update either way.
        await waitFor(() => expect(listAlert).toHaveTextContent(""));
    });

    it("keeps the empty state's Create action when a background refresh fails on an already-empty list", async () => {
        // A list that loaded successfully as empty, then failed a later background
        // refresh, must not be conflated with "never loaded" — it should keep the
        // empty-state's Create action and a refresh warning, not the full-page error.
        server.use(http.get(WATCHBOXES_URL, () => new HttpResponse(null, { status: 500 })));

        const { container } = render(<WatchboxPanel />, {
            queryClient: new QueryClient({
                defaultOptions: { queries: { retry: false, staleTime: 0 } },
            }),
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [],
            },
        });
        const listAlert = container.querySelector('[role="alert"]:not([aria-atomic="true"])');

        await waitFor(() =>
            expect(container.querySelector('[aria-live="polite"]')).toHaveTextContent(
                i18n.t("watchbox.listRefreshFailed"),
            ),
        );
        expect(listAlert).toHaveTextContent("");
        expect(screen.getByRole("button", { name: i18n.t("watchbox.create") })).toBeInTheDocument();
        expect(screen.queryByText(i18n.t("watchbox.listUnavailable"))).not.toBeInTheDocument();
    });
});

describe("WatchboxPanel — safe deletion", () => {
    /** Delete now lives behind the per-row actions menu rather than a standalone button. */
    async function clickDelete(user: ReturnType<typeof userEvent.setup>, name = "My Watchbox") {
        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.moreActions", { name }),
            }),
        );
        await user.click(await screen.findByRole("menuitem", { name: i18n.t("watchbox.delete") }));
    }

    it("hides delete when the server-supplied canDelete capability is false", async () => {
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        id: "deletable",
                        name: "Deletable",
                        capabilities: { canDelete: true, canPatch: true },
                    }),
                    makeWatchbox({
                        id: "locked",
                        name: "Locked",
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                ]),
            ),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: "Deletable" }),
            }),
        );
        expect(
            await screen.findByRole("menuitem", { name: i18n.t("watchbox.delete") }),
        ).toBeInTheDocument();
        await user.keyboard("{Escape}");

        await user.click(
            screen.getByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: "Locked" }),
            }),
        );
        expect(
            screen.queryByRole("menuitem", { name: i18n.t("watchbox.delete") }),
        ).not.toBeInTheDocument();
    });

    it("requires confirmation before deleting and does not mutate on cancel", async () => {
        let deleteCalls = 0;
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([makeWatchbox()])),
            http.delete(`${WATCHBOXES_URL}/wb-1`, () => {
                deleteCalls++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await clickDelete(user);
        await screen.findByText(i18n.t("watchbox.confirmDeleteTitle"));
        await user.click(screen.getByRole("button", { name: i18n.t("common.cancel") }));

        expect(deleteCalls).toBe(0);
        expect(screen.queryByText(i18n.t("watchbox.confirmDeleteTitle"))).not.toBeInTheDocument();
    });

    it("deletes after confirming and shows a success toast", async () => {
        let deleteCalls = 0;
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([makeWatchbox()])),
            http.delete(`${WATCHBOXES_URL}/wb-1`, () => {
                deleteCalls++;
                return new HttpResponse(null, { status: 204 });
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await clickDelete(user);
        await screen.findByText(i18n.t("watchbox.confirmDeleteTitle"));
        await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

        await waitFor(() => expect(deleteCalls).toBe(1));
        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(expect.objectContaining({ type: "success" })),
        );
    });

    it("focuses the list after a successful delete removes the focused row", async () => {
        server.use(
            http.delete(`${WATCHBOXES_URL}/wb-1`, () => new HttpResponse(null, { status: 204 })),
            http.get(WATCHBOXES_URL, () => HttpResponse.json([])),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [makeWatchbox()],
            },
        });

        // Grab the list container from the (un-portaled) trigger — the menu item itself
        // renders outside the list's DOM subtree via Portal.
        const menuTrigger = screen.getByRole("button", {
            name: i18n.t("watchbox.moreActions", { name: "My Watchbox" }),
        });
        const list = menuTrigger.closest<HTMLElement>('[tabindex="-1"]');
        expect(list).not.toBeNull();

        await clickDelete(user);
        await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

        await waitFor(() =>
            expect(
                screen.queryByText(i18n.t("watchbox.confirmDeleteTitle")),
            ).not.toBeInTheDocument(),
        );
        await waitFor(() => expect(list).toHaveFocus());
    });

    it("shows a useful error and refreshes stale capabilities when delete is denied", async () => {
        let getCalls = 0;
        server.use(
            http.get(WATCHBOXES_URL, () => {
                getCalls++;
                return HttpResponse.json([
                    makeWatchbox({ capabilities: { canDelete: getCalls === 1, canPatch: true } }),
                ]);
            }),
            http.delete(`${WATCHBOXES_URL}/wb-1`, () => HttpResponse.json({}, { status: 403 })),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await clickDelete(user);
        await screen.findByText(i18n.t("watchbox.confirmDeleteTitle"));
        await user.click(screen.getByRole("button", { name: i18n.t("common.delete") }));

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "error",
                    description: expect.stringMatching(/permission/i),
                }),
            ),
        );
        await waitFor(() => expect(getCalls).toBeGreaterThan(1));

        // Reopen the menu: selecting "Delete" already closed it, so the refreshed
        // capabilities can only be observed by opening it again.
        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: "My Watchbox" }),
            }),
        );
        expect(
            screen.queryByRole("menuitem", { name: i18n.t("watchbox.delete") }),
        ).not.toBeInTheDocument();
    });

    it("hides delete during a rolling deploy when an older response omits capabilities", async () => {
        const legacyWatchbox = makeWatchbox();
        delete legacyWatchbox.capabilities;
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json([legacyWatchbox])));
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: "My Watchbox" }),
            }),
        );
        expect(
            screen.queryByRole("menuitem", { name: i18n.t("watchbox.delete") }),
        ).not.toBeInTheDocument();
    });

    it("keeps an active delete open and prevents duplicate submission until it settles", async () => {
        let resolveDelete: ((response: Response) => void) | undefined;
        let deleteCalls = 0;
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([])),
            http.delete(`${WATCHBOXES_URL}/wb-1`, () => {
                deleteCalls++;
                return new Promise<Response>((resolve) => (resolveDelete = resolve));
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [makeWatchbox()],
            },
        });

        await clickDelete(user);
        const confirmDelete = screen.getByRole("button", { name: i18n.t("common.delete") });
        await user.click(confirmDelete);
        await waitFor(() => expect(deleteCalls).toBe(1));

        expect(confirmDelete).toBeDisabled();
        const cancel = screen.getByRole("button", { name: i18n.t("common.cancel") });
        expect(cancel).toBeDisabled();
        await user.keyboard("{Escape}");
        expect(
            screen.getByText(i18n.t("watchbox.confirmDeleteMessage", { name: "My Watchbox" })),
        ).toBeInTheDocument();

        const backdrop = document.querySelector<HTMLElement>('[data-part="backdrop"]');
        expect(backdrop).not.toBeNull();
        fireEvent.pointerDown(backdrop!);
        fireEvent.pointerUp(backdrop!);
        fireEvent.click(backdrop!);
        expect(screen.getByText(i18n.t("watchbox.confirmDeleteTitle"))).toBeInTheDocument();

        await user.click(confirmDelete);
        expect(deleteCalls).toBe(1);

        resolveDelete?.(new HttpResponse(null, { status: 204 }));
        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "success",
                    title: i18n.t("watchbox.deleted"),
                }),
            ),
        );
        await waitFor(() =>
            expect(
                screen.queryByText(i18n.t("watchbox.confirmDeleteTitle")),
            ).not.toBeInTheDocument(),
        );
    });
});

const TRACKING_RESET_REQUIRED_BODY = {
    code: "WATCHBOX_TRACKING_RESET_CONFIRMATION_REQUIRED",
    message: "",
    fields: ["enabled"],
    reasons: ["REENABLED"],
};

describe("WatchboxPanel — enable/disable", () => {
    it("shows a switch only when capabilities.canPatch is true", async () => {
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        id: "patchable",
                        name: "Patchable",
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                    makeWatchbox({
                        id: "locked",
                        name: "Locked",
                        capabilities: { canDelete: false, canPatch: false },
                    }),
                ]),
            ),
        );

        render(<WatchboxPanel />);

        await screen.findByText("Patchable");
        // Filtered to per-card toggles: the panel's own "Hide Disabled" switch is always present
        // once the list is non-empty and isn't part of what this test is checking.
        expect(screen.getAllByRole("switch", { name: /^Toggle / })).toHaveLength(1);
    });

    it("disables an enabled watchbox directly, without a confirmation dialog", async () => {
        let patchBody: unknown;
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        enabled: true,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                ]),
            ),
            http.patch(`${WATCHBOXES_URL}/wb-1`, async ({ request }) => {
                patchBody = await request.json();
                return HttpResponse.json(
                    makeWatchbox({
                        enabled: false,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                );
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));

        await waitFor(() => expect(patchBody).toEqual({ enabled: false }));
        expect(screen.queryByText(i18n.t("watchbox.confirmReenableTitle"))).not.toBeInTheDocument();
        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "success",
                    title: i18n.t("watchbox.disableSuccess"),
                }),
            ),
        );
    });

    it("keeps every watchbox with an in-flight patch disabled until its own request settles", async () => {
        let resolvePatchA: ((response: Response) => void) | undefined;
        let resolvePatchB: ((response: Response) => void) | undefined;
        const watchboxA = makeWatchbox({
            id: "wb-a",
            name: "Watchbox A",
            capabilities: { canDelete: false, canPatch: true },
        });
        const watchboxB = makeWatchbox({
            id: "wb-b",
            name: "Watchbox B",
            capabilities: { canDelete: false, canPatch: true },
        });
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([watchboxA, watchboxB])),
            http.patch(
                `${WATCHBOXES_URL}/wb-a`,
                () => new Promise<Response>((resolve) => (resolvePatchA = resolve)),
            ),
            http.patch(
                `${WATCHBOXES_URL}/wb-b`,
                () => new Promise<Response>((resolve) => (resolvePatchB = resolve)),
            ),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        const [switchA, switchB] = await screen.findAllByRole("switch", { name: /^Toggle / });
        await user.click(switchA);
        await waitFor(() => expect(switchA).toBeDisabled());
        await user.click(switchB);

        await waitFor(() => {
            expect(switchA).toBeDisabled();
            expect(switchB).toBeDisabled();
        });

        resolvePatchB?.(HttpResponse.json({ ...watchboxB, enabled: false }));
        await waitFor(() => expect(switchB).not.toBeDisabled());
        expect(switchA).toBeDisabled();

        resolvePatchA?.(HttpResponse.json({ ...watchboxA, enabled: false }));
        await waitFor(() => expect(switchA).not.toBeDisabled());
    });

    it("does not let a slower 409 replace another watchbox's re-enable confirmation", async () => {
        let resolvePatchA: ((response: Response) => void) | undefined;
        let resolvePatchB: ((response: Response) => void) | undefined;
        const watchboxA = makeWatchbox({
            id: "wb-a",
            name: "Watchbox A",
            enabled: false,
            capabilities: { canDelete: false, canPatch: true },
        });
        const watchboxB = makeWatchbox({
            id: "wb-b",
            name: "Watchbox B",
            enabled: false,
            capabilities: { canDelete: false, canPatch: true },
        });
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([watchboxA, watchboxB])),
            http.patch(
                `${WATCHBOXES_URL}/wb-a`,
                () => new Promise<Response>((resolve) => (resolvePatchA = resolve)),
            ),
            http.patch(
                `${WATCHBOXES_URL}/wb-b`,
                () => new Promise<Response>((resolve) => (resolvePatchB = resolve)),
            ),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        const [switchA, switchB] = await screen.findAllByRole("switch", { name: /^Toggle / });
        await user.click(switchA);
        await waitFor(() => expect(switchA).toBeDisabled());
        await user.click(switchB);
        await waitFor(() => expect(switchB).toBeDisabled());

        resolvePatchB?.(HttpResponse.json(TRACKING_RESET_REQUIRED_BODY, { status: 409 }));
        await screen.findByText(i18n.t("watchbox.confirmReenableIntro", { name: watchboxB.name }));

        resolvePatchA?.(HttpResponse.json(TRACKING_RESET_REQUIRED_BODY, { status: 409 }));
        await waitFor(() => expect(switchA).not.toBeDisabled());

        expect(
            screen.getByText(i18n.t("watchbox.confirmReenableIntro", { name: watchboxB.name })),
        ).toBeInTheDocument();
        expect(
            screen.queryByText(i18n.t("watchbox.confirmReenableIntro", { name: watchboxA.name })),
        ).not.toBeInTheDocument();
    });

    // toggleEnabled's happy path handles a bare enable success (no 409) directly, distinct from
    // confirmReenable's success after a tracking-reset confirmation — the backend may not always
    // require confirmation to re-enable (e.g. a watchbox disabled and re-enabled before its
    // tracking state ever went stale). If a future backend change removed this branch entirely,
    // nothing else in this file would catch it disappearing.
    it("enables a disabled watchbox directly when the backend requires no tracking-reset confirmation", async () => {
        let patchBody: unknown;
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        enabled: false,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                ]),
            ),
            http.patch(`${WATCHBOXES_URL}/wb-1`, async ({ request }) => {
                patchBody = await request.json();
                return HttpResponse.json(
                    makeWatchbox({
                        enabled: true,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                );
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));

        await waitFor(() => expect(patchBody).toEqual({ enabled: true }));
        expect(screen.queryByText(i18n.t("watchbox.confirmReenableTitle"))).not.toBeInTheDocument();
        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "success",
                    title: i18n.t("watchbox.enableSuccess"),
                }),
            ),
        );
    });

    it("shows tracking-reset reasons and retries with confirmTrackingReset after confirming", async () => {
        const patchCalls: unknown[] = [];
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        enabled: false,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                ]),
            ),
            http.patch(`${WATCHBOXES_URL}/wb-1`, async ({ request }) => {
                const body = await request.json();
                patchCalls.push(body);
                if (!(body as { confirmTrackingReset?: boolean }).confirmTrackingReset) {
                    return HttpResponse.json(TRACKING_RESET_REQUIRED_BODY, { status: 409 });
                }
                return HttpResponse.json(
                    makeWatchbox({
                        enabled: true,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                );
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));
        await screen.findByText(i18n.t("watchbox.confirmReenableTitle"));
        expect(showToast).not.toHaveBeenCalled();

        await user.click(
            screen.getByRole("button", { name: i18n.t("watchbox.confirmReenableAction") }),
        );

        await waitFor(() => expect(patchCalls).toHaveLength(2));
        expect(patchCalls[0]).toEqual({ enabled: true });
        expect(patchCalls[1]).toEqual({ enabled: true, confirmTrackingReset: true });
        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "success",
                    title: i18n.t("watchbox.enableSuccess"),
                }),
            ),
        );
    });

    it("keeps the reenable dialog open with fresh reasons when the confirmed retry also conflicts", async () => {
        const freshReason = "LAYERS_REMOVED";
        const disabledWatchbox = makeWatchbox({
            enabled: false,
            capabilities: { canDelete: false, canPatch: true },
        });
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([disabledWatchbox])),
            http.patch(`${WATCHBOXES_URL}/wb-1`, async ({ request }) => {
                const body = (await request.json()) as { confirmTrackingReset?: boolean };
                return HttpResponse.json(
                    body.confirmTrackingReset
                        ? { ...TRACKING_RESET_REQUIRED_BODY, reasons: [freshReason] }
                        : TRACKING_RESET_REQUIRED_BODY,
                    { status: 409 },
                );
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));
        expect(
            await screen.findByText(i18n.t("watchbox.reenableReasons.REENABLED")),
        ).toBeInTheDocument();

        await user.click(
            screen.getByRole("button", { name: i18n.t("watchbox.confirmReenableAction") }),
        );

        expect(await screen.findByText(freshReason)).toBeInTheDocument();
        expect(screen.getByText(i18n.t("watchbox.confirmReenableTitle"))).toBeInTheDocument();
        expect(
            screen.queryByText(i18n.t("watchbox.reenableReasons.REENABLED")),
        ).not.toBeInTheDocument();
        await waitFor(() =>
            expect(
                screen.getByRole("button", {
                    name: i18n.t("watchbox.confirmReenableAction"),
                }),
            ).toBeEnabled(),
        );
        expect(showToast).not.toHaveBeenCalled();
    });

    it("cancelling the reenable dialog sends no second request", async () => {
        let patchCalls = 0;
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        enabled: false,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                ]),
            ),
            http.patch(`${WATCHBOXES_URL}/wb-1`, () => {
                patchCalls++;
                return HttpResponse.json(TRACKING_RESET_REQUIRED_BODY, { status: 409 });
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));
        await screen.findByText(i18n.t("watchbox.confirmReenableTitle"));
        await user.click(screen.getByRole("button", { name: i18n.t("common.cancel") }));

        expect(patchCalls).toBe(1);
        expect(screen.queryByText(i18n.t("watchbox.confirmReenableTitle"))).not.toBeInTheDocument();
    });

    it("shows a failure toast, not the reenable dialog, for an unrelated patch error", async () => {
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        enabled: false,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                ]),
            ),
            http.patch(`${WATCHBOXES_URL}/wb-1`, () => HttpResponse.json({}, { status: 403 })),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({ type: "error", title: i18n.t("watchbox.patchError") }),
            ),
        );
        expect(screen.queryByText(i18n.t("watchbox.confirmReenableTitle"))).not.toBeInTheDocument();
    });

    it("shows every ineligible source name in the quick reenable error toast", async () => {
        server.use(
            http.get(WATCHBOXES_URL, () =>
                HttpResponse.json([
                    makeWatchbox({
                        enabled: false,
                        capabilities: { canDelete: false, canPatch: true },
                    }),
                ]),
            ),
            http.patch(`${WATCHBOXES_URL}/wb-1`, () =>
                HttpResponse.json(
                    {
                        code: "WATCHBOX_SOURCE_ELIGIBILITY_FAILED",
                        message: "Selected sources require object ID mapping correction",
                        fields: ["layerIds"],
                        reasons: [],
                        context: { sourceNames: ["Source A", "Source B"] },
                    },
                    { status: 409 },
                ),
            ),
        );
        const user = userEvent.setup();
        const { container } = render(<WatchboxPanel />);
        const announcement = container.querySelector('[role="alert"][aria-atomic="true"]');
        expect(announcement).toHaveTextContent("");

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "error",
                    title: i18n.t("watchbox.patchError"),
                    description:
                        "A mapper with stable object IDs is required for each listed data source: Source A and Source B.",
                }),
            ),
        );
        expect(announcement).toHaveTextContent(
            "A mapper with stable object IDs is required for each listed data source: Source A and Source B.",
        );
        expect(screen.queryByText(i18n.t("watchbox.confirmReenableTitle"))).not.toBeInTheDocument();
    });

    it("cannot dismiss a confirmed reenable while its tracking reset is running", async () => {
        let resolveConfirmedPatch: ((response: Response) => void) | undefined;
        const disabledWatchbox = makeWatchbox({
            enabled: false,
            capabilities: { canDelete: false, canPatch: true },
        });
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([disabledWatchbox])),
            http.patch(`${WATCHBOXES_URL}/wb-1`, async ({ request }) => {
                const body = (await request.json()) as { confirmTrackingReset?: boolean };
                if (!body.confirmTrackingReset) {
                    return HttpResponse.json(TRACKING_RESET_REQUIRED_BODY, { status: 409 });
                }
                return new Promise<Response>((resolve) => (resolveConfirmedPatch = resolve));
            }),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));
        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.confirmReenableAction"),
            }),
        );

        const cancel = screen.getByRole("button", { name: i18n.t("common.cancel") });
        await waitFor(() => expect(cancel).toBeDisabled());
        await user.keyboard("{Escape}");
        expect(screen.getByText(i18n.t("watchbox.confirmReenableTitle"))).toBeInTheDocument();

        resolveConfirmedPatch?.(HttpResponse.json({ ...disabledWatchbox, enabled: true }));
        await waitFor(() =>
            expect(
                screen.queryByText(i18n.t("watchbox.confirmReenableTitle")),
            ).not.toBeInTheDocument(),
        );
    });

    // Direct analog of the delete-side "refreshes stale capabilities when delete is denied" test:
    // a denied patch still settles, and `onSettled` still invalidates the list, so the refetch's
    // own `canPatch` is what should govern the control — not whatever the card rendered with
    // before the request went out.
    it("drops the switch for the static badge once a settled patch's refetch reports canPatch: false", async () => {
        let getCalls = 0;
        server.use(
            http.get(WATCHBOXES_URL, () => {
                getCalls++;
                return HttpResponse.json([
                    makeWatchbox({
                        enabled: false,
                        alertingStatus: "DISABLED",
                        capabilities: { canDelete: false, canPatch: getCalls === 1 },
                    }),
                ]);
            }),
            http.patch(`${WATCHBOXES_URL}/wb-1`, () => HttpResponse.json({}, { status: 403 })),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(await screen.findByRole("switch", { name: /^Toggle / }));

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith(
                expect.objectContaining({ type: "error", title: i18n.t("watchbox.patchError") }),
            ),
        );
        await waitFor(() => expect(getCalls).toBeGreaterThan(1));
        await waitFor(() =>
            expect(screen.queryByRole("switch", { name: /^Toggle / })).not.toBeInTheDocument(),
        );
        expect(screen.getByText(i18n.t("watchbox.statuses.DISABLED"))).toBeInTheDocument();
    });
});

describe("WatchboxPanel — hide disabled toggle", () => {
    it("filters disabled watchboxes from the list and restores them", async () => {
        const enabledWatchbox = makeWatchbox({ id: "enabled", name: "Enabled watchbox" });
        const disabledWatchbox = makeWatchbox({
            id: "disabled",
            name: "Disabled watchbox",
            enabled: false,
            alertingStatus: "DISABLED",
        });
        server.use(
            http.get(WATCHBOXES_URL, () => HttpResponse.json([enabledWatchbox, disabledWatchbox])),
        );
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        expect(await screen.findByText(enabledWatchbox.name)).toBeInTheDocument();
        expect(screen.getByText(disabledWatchbox.name)).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: i18n.t("watchbox.hideDisabled") }));

        expect(screen.getByText(enabledWatchbox.name)).toBeInTheDocument();
        expect(screen.queryByText(disabledWatchbox.name)).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: i18n.t("watchbox.showDisabled") }));

        expect(screen.getByText(disabledWatchbox.name)).toBeInTheDocument();
    });

    it("does not show the new-user empty state when the filter hides every watchbox", async () => {
        const disabledWatchbox = makeWatchbox({
            enabled: false,
            alertingStatus: "DISABLED",
        });
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json([disabledWatchbox])));
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        await user.click(
            await screen.findByRole("button", { name: i18n.t("watchbox.hideDisabled") }),
        );

        expect(screen.queryByText(disabledWatchbox.name)).not.toBeInTheDocument();
        expect(screen.queryByText(i18n.t("watchbox.emptyStateTitle"))).not.toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: i18n.t("watchbox.showDisabled") }),
        ).toBeInTheDocument();
    });

    it("toggles the shared hideDisabled store value and swaps its label to name the next action", async () => {
        server.use(http.get(WATCHBOXES_URL, () => HttpResponse.json([makeWatchbox()])));
        const user = userEvent.setup();
        render(<WatchboxPanel />);

        const toggle = await screen.findByRole("button", { name: i18n.t("watchbox.hideDisabled") });
        expect(toggle).toHaveAttribute("aria-pressed", "false");
        expect(useWatchboxVisibilityStore.getState().hideDisabled).toBe(false);

        await user.click(toggle);
        expect(toggle).toHaveAttribute("aria-pressed", "true");
        expect(toggle).toHaveTextContent(i18n.t("watchbox.showDisabled"));
        expect(useWatchboxVisibilityStore.getState().hideDisabled).toBe(true);

        await user.click(toggle);
        expect(toggle).toHaveAttribute("aria-pressed", "false");
        expect(toggle).toHaveTextContent(i18n.t("watchbox.hideDisabled"));
        expect(useWatchboxVisibilityStore.getState().hideDisabled).toBe(false);
    });
});

describe("WatchboxPanel — mute wiring", () => {
    const DEFAULT_USER_ID = createTestUser().user.id;

    function resetMuteStore() {
        useWatchboxMuteStore.setState({ mutedIdsByUser: {} });
        localStorage.removeItem(WATCHBOX_MUTE_STORAGE_KEY);
    }

    beforeEach(resetMuteStore);
    afterEach(resetMuteStore);

    it("mutes the clicked list row for the current user, keyed by its own id — not another row's", async () => {
        const user = userEvent.setup();
        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [
                    CREATED_WATCHBOX,
                    OTHER_WATCHBOX,
                ],
            },
        });

        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
            }),
        );
        await user.click(
            await screen.findByRole("menuitem", {
                name: i18n.t("watchbox.mute"),
            }),
        );

        const { isMuted } = useWatchboxMuteStore.getState().actions;
        expect(isMuted(DEFAULT_USER_ID, CREATED_WATCHBOX.id)).toBe(true);
        expect(isMuted(DEFAULT_USER_ID, OTHER_WATCHBOX.id)).toBe(false);
    });

    it("unmutes a list row on a second click", async () => {
        const user = userEvent.setup();
        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [CREATED_WATCHBOX],
            },
        });

        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
            }),
        );
        await user.click(
            await screen.findByRole("menuitem", {
                name: i18n.t("watchbox.mute"),
            }),
        );
        expect(
            useWatchboxMuteStore.getState().actions.isMuted(DEFAULT_USER_ID, CREATED_WATCHBOX.id),
        ).toBe(true);

        await user.click(
            screen.getByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
            }),
        );
        await user.click(
            await screen.findByRole("menuitem", {
                name: i18n.t("watchbox.unmute"),
            }),
        );
        expect(
            useWatchboxMuteStore.getState().actions.isMuted(DEFAULT_USER_ID, CREATED_WATCHBOX.id),
        ).toBe(false);
    });

    it("mutes from the detail view keyed by the open watchbox's own id — not another watchbox's", async () => {
        const user = userEvent.setup();
        render(<WatchboxPanel />, {
            queryData: {
                [JSON.stringify(watchboxQueries.watchboxes().queryKey)]: [
                    CREATED_WATCHBOX,
                    OTHER_WATCHBOX,
                ],
                [JSON.stringify(watchboxQueries.watchbox(CREATED_WATCHBOX.id).queryKey)]:
                    CREATED_WATCHBOX,
                [JSON.stringify(layerQueries.myList().queryKey)]: [LAYER_WITH_SOURCE],
            },
        });

        await user.click(
            await screen.findByRole("button", { name: new RegExp(`^${CREATED_WATCHBOX.name}`) }),
        );
        await user.click(
            await screen.findByRole("button", {
                name: i18n.t("watchbox.moreActions", { name: CREATED_WATCHBOX.name }),
            }),
        );
        await user.click(
            await screen.findByRole("menuitem", {
                name: i18n.t("watchbox.mute"),
            }),
        );

        const { isMuted } = useWatchboxMuteStore.getState().actions;
        expect(isMuted(DEFAULT_USER_ID, CREATED_WATCHBOX.id)).toBe(true);
        expect(isMuted(DEFAULT_USER_ID, OTHER_WATCHBOX.id)).toBe(false);
    });
});
