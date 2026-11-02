// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    act,
    fireEvent,
    render,
    screen,
    within,
    waitFor,
    userEvent,
    server,
    http,
    HttpResponse,
    API_BASE,
} from "~/test";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Element as OcElement, getElementKey, IconStyle, PointGeometry } from "@opencop/elements";
import * as opencopApi from "@opencop/api";
import type { MyDataSource, MyLayerInfo } from "@opencop/api";
import { useMapStore } from "~/features/map/core/map.store";
import { useStackedObjectSelectionStore } from "~/features/map/stacked-object-selection.store";
import { useAreaSelectionStore } from "~/features/selection/area-selection.store";
import { useEventsNavigationStore } from "~/features/events/events-navigation.store";
import { layerQueries } from "~/features/layers/layers.queries";
import { dataSourceQueries } from "~/features/data-sources/data-sources.queries";
import { SelectionPanel } from "./SelectionPanel";
import type { Element } from "@opencop/elements";

const { clipboardWriteText } = vi.hoisted(() => ({
    clipboardWriteText: vi.fn(),
}));

const { mockShowToast } = vi.hoisted(() => ({ mockShowToast: vi.fn() }));

const { mockClearSelection, mockSelectElement } = vi.hoisted(() => ({
    mockClearSelection: vi.fn(),
    mockSelectElement: vi.fn(),
}));

const { mockFlyTo, mockFitBounds } = vi.hoisted(() => ({
    mockFlyTo: vi.fn(),
    mockFitBounds: vi.fn(),
}));

vi.mock("~/features/map/MapManagerContext", () => ({
    useMapManager: () => ({
        clearSelection: mockClearSelection,
        selectElement: mockSelectElement,
    }),
}));

vi.mock("~/features/map/core/slices/view/view.hook", () => ({
    useViewActions: () => ({ flyTo: mockFlyTo, fitBounds: mockFitBounds }),
}));

vi.mock("~/features/events/ElementPreviewCanvas", () => ({
    ElementPreviewCanvas: () => null,
}));

vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast: mockShowToast }),
}));

// downloadExport manipulates the DOM (hidden iframe + anchor navigation) instead of
// issuing an HTTP request, so MSW never sees it. A spy (not vi.mock) lets us assert
// the call while keeping the real @opencop/api module intact for createExport/apiFetch.
const downloadExportSpy = vi.spyOn(opencopApi, "downloadExport").mockImplementation(() => {});

const EXPORT_RESPONSE = {
    exportId: "e1",
    filename: "export.csv",
    format: "CSV" as const,
    generatedAt: "2026-01-01T00:00:00Z",
    expiresAt: "2026-01-02T00:00:00Z",
    downloadPath: "/v1/exports/e1/download",
};

/** Registers a POST /exports handler; captures the request body for assertions. */
function stubCreateExport(response: opencopApi.ExportResponse = EXPORT_RESPONSE) {
    const capture: { body: unknown; count: number } = { body: undefined, count: 0 };
    server.use(
        http.post(`${API_BASE}/exports`, async ({ request }) => {
            capture.count += 1;
            capture.body = await request.json();
            return HttpResponse.json(response);
        }),
    );
    return capture;
}

function makeElement(name: string, source = "test"): Element {
    const objectId = `${name.toLowerCase()}-object`;
    return {
        key: getElementKey({ source, objectId }),
        id: `${name.toLowerCase()}-id`,
        objectId,
        source,
        getDisplayName: () => name,
        time: null,
        geometry: new PointGeometry([5, 6]),
    } as unknown as Element;
}

const mockOnFocus = vi.fn();
const mockOnClose = vi.fn();

function resetStores() {
    useMapStore.getState().resetSelection();
    useStackedObjectSelectionStore.getState().dismiss();
    useAreaSelectionStore.getState().clear();
}

/** `makeElement`'s `source` is always `"test"` — a layer with `layerId: "test"` targets it. */
function renderComponent(options?: {
    myLayers?: MyLayerInfo[];
    myDataSources?: MyDataSource[];
    finalFocusEl?: () => HTMLElement | null;
}) {
    return render(
        <SelectionPanel
            onFocus={mockOnFocus}
            onClose={mockOnClose}
            finalFocusEl={options?.finalFocusEl}
        />,
        {
            queryData: {
                [JSON.stringify(layerQueries.myList().queryKey)]: options?.myLayers ?? [],
                [JSON.stringify(dataSourceQueries.myList().queryKey)]: options?.myDataSources ?? [],
            },
        },
    );
}

const OWNER_OF_TEST_LAYER: MyLayerInfo[] = [
    {
        layerId: "test",
        layerName: "Test layer",
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    },
];

const OWNER_OF_TEST_DATA_SOURCE: MyDataSource[] = [
    {
        serverId: "test-source",
        serverName: "Test data source",
        role: "OWNER",
        directRole: "OWNER",
        serverState: "CONNECTED",
        via: "DIRECT",
        access: "RW",
        transportType: "TCP",
        defaultVisible: true,
    },
];

beforeEach(() => {
    downloadExportSpy.mockClear();
    mockShowToast.mockReset();
    mockOnFocus.mockReset();
    mockOnClose.mockReset();
    mockClearSelection.mockReset().mockImplementation(() => {
        useMapStore.getState().resetSelection();
    });
    mockSelectElement.mockReset().mockImplementation((element: Element) => {
        useMapStore.getState().select(getElementKey(element));
    });
    mockFlyTo.mockReset();
    mockFitBounds.mockReset();
    clipboardWriteText.mockReset();
    clipboardWriteText.mockResolvedValue(undefined);
    Object.defineProperty(window.navigator, "clipboard", {
        configurable: true,
        value: {
            writeText: clipboardWriteText,
        },
    });
    act(() => {
        resetStores();
    });
});

afterEach(() => {
    act(() => {
        resetStores();
    });
});

describe("SelectionPanel", () => {
    it("renders the event list heading, the selection state, and the toolbar actions", () => {
        act(() => {
            useAreaSelectionStore
                .getState()
                .select([makeElement("Alpha"), makeElement("Bravo")], []);
        });

        renderComponent();

        expect(screen.getByRole("heading", { name: /events list/i })).toBeTruthy();

        const toolbar = screen.getByRole("toolbar", { name: "Events List" });

        expect(within(toolbar).getByRole("button", { name: "Export Selected" })).toBeTruthy();
        expect(within(toolbar).getByRole("button", { name: "Copy to layer" })).toBeTruthy();
        expect(within(toolbar).getByRole("button", { name: "Delete Selected" })).toBeTruthy();
        expect(within(toolbar).getByRole("button", { name: "Cancel" })).toBeTruthy();
        expect(within(toolbar).getByRole("checkbox", { name: "Select All" })).toBeChecked();
        expect(within(toolbar).getByText("2 events selected")).toBeTruthy();
    });

    it("toggles the select-all checkbox between none and all and updates the count", async () => {
        const user = userEvent.setup();
        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        renderComponent();

        const selectAll = screen.getByRole("checkbox", { name: "Select All" });
        expect(selectAll).toBeChecked();

        await user.click(selectAll);

        expect(screen.getByText("0 events selected")).toBeTruthy();
        expect(selectAll).not.toBeChecked();

        await user.click(selectAll);

        expect(screen.getByText("1 event selected")).toBeTruthy();
        expect(selectAll).toBeChecked();
    });

    it("shows the select-all checkbox as partly checked once some items are unticked, and ticks them all on click", async () => {
        const user = userEvent.setup();
        act(() => {
            useAreaSelectionStore
                .getState()
                .select([makeElement("Alpha"), makeElement("Bravo")], []);
        });

        renderComponent();

        const selectAll = screen.getByRole("checkbox", { name: "Select All" });
        expect(selectAll).toBeChecked();

        await user.click(screen.getByRole("checkbox", { name: "Select Bravo" }));

        expect(selectAll).toBePartiallyChecked();
        expect(screen.getByText("1 event selected")).toBeTruthy();

        await user.click(selectAll);

        expect(selectAll).toBeChecked();
        expect(screen.getByText("2 events selected")).toBeTruthy();
    });

    it("labels the icon-only actions with a tooltip as well as an accessible name", async () => {
        const user = userEvent.setup();
        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        renderComponent();

        await user.hover(screen.getByRole("button", { name: "Export Selected" }));

        // The label is not text content anywhere else, so finding it means the tooltip rendered.
        expect(await screen.findByText("Export Selected")).toBeVisible();
    });

    it("cancels the lasso selection, clears map selection, and calls onClose", () => {
        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        renderComponent();

        fireEvent.click(screen.getByRole("button", { name: "Cancel" }));

        expect(mockClearSelection).toHaveBeenCalledOnce();
        expect(useAreaSelectionStore.getState().elements).toBeNull();
        expect(mockOnClose).toHaveBeenCalledOnce();
    });

    it("clicking a row calls onFocus with the picked element", () => {
        const alpha = makeElement("Alpha");
        act(() => {
            useAreaSelectionStore.getState().select([alpha], []);
        });

        renderComponent();

        act(() => {
            fireEvent.click(screen.getByRole("button", { name: "Alpha" }));
        });

        // SelectionPanel delegates the pick to its caller rather than reading
        // MapManager/events-navigation.store directly — see EventList.tsx,
        // which wires onFocus to selectFromList, and EventList.test.tsx,
        // which verifies that real wiring end to end.
        expect(mockOnFocus).toHaveBeenCalledOnce();
        expect(mockOnFocus).toHaveBeenCalledWith(alpha);
    });

    it("clicking the location-pin button selects and pans the camera, without calling onFocus or changing navigation mode", () => {
        const alpha = makeElement("Alpha");
        act(() => {
            useAreaSelectionStore.getState().select([alpha], []);
        });

        renderComponent();
        const modeBefore = useEventsNavigationStore.getState().mode;

        act(() => {
            fireEvent.click(screen.getByRole("button", { name: "Show on map" }));
        });

        expect(mockSelectElement).toHaveBeenCalledWith(alpha);
        expect(mockFlyTo).toHaveBeenCalledOnce();
        expect(mockFitBounds).not.toHaveBeenCalled();
        expect(mockOnFocus).not.toHaveBeenCalled();
        expect(useEventsNavigationStore.getState().mode).toBe(modeBefore);
    });

    it("keeps the checkbox and location-pin actions independent", async () => {
        const alpha = makeElement("Alpha");
        const alphaKey = getElementKey(alpha);
        const user = userEvent.setup();
        act(() => {
            useAreaSelectionStore.getState().select([alpha], []);
        });

        renderComponent();

        await user.click(screen.getByRole("button", { name: "Show on map" }));

        expect(mockSelectElement).toHaveBeenCalledOnce();
        expect(useAreaSelectionStore.getState().checkedKeys.has(alphaKey)).toBe(true);
        expect(mockOnFocus).not.toHaveBeenCalled();

        await user.click(screen.getByRole("checkbox", { name: "Select Alpha" }));

        expect(useAreaSelectionStore.getState().checkedKeys.has(alphaKey)).toBe(false);
        expect(mockSelectElement).toHaveBeenCalledOnce();
        expect(mockOnFocus).not.toHaveBeenCalled();
    });

    it("clicking the layer text on a card's second line still calls onFocus (no dead zone)", () => {
        const alpha = makeElement("Alpha");
        act(() => {
            useAreaSelectionStore.getState().select([alpha], []);
        });

        renderComponent({ myLayers: OWNER_OF_TEST_LAYER });

        act(() => {
            fireEvent.click(screen.getByText("Test layer"));
        });

        expect(mockOnFocus).toHaveBeenCalledOnce();
        expect(mockOnFocus).toHaveBeenCalledWith(alpha);
    });

    it("clicking the copy button copies the ID without selecting the element", async () => {
        const alpha = makeElement("Alpha");
        act(() => {
            useAreaSelectionStore.getState().select([alpha], []);
        });

        renderComponent();

        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Copy ID for Alpha" }));
        });

        expect(clipboardWriteText).toHaveBeenCalledWith("alpha-object");
        expect(mockOnFocus).not.toHaveBeenCalled();
    });

    it("clicking Export Selected calls createExport with the currently checked IDs", async () => {
        const capture = stubCreateExport();

        act(() => {
            useAreaSelectionStore
                .getState()
                .select([makeElement("Alpha"), makeElement("Bravo")], []);
            // Uncheck Bravo so only Alpha's ID should be sent.
            useAreaSelectionStore.getState().toggleChecked(getElementKey(makeElement("Bravo")));
        });

        renderComponent();

        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Export Selected" }));
        });

        await waitFor(() => {
            expect(capture.count).toBe(1);
            expect(capture.body).toEqual({ format: "CSV", elementIds: ["alpha-id"] });
        });
    });

    it("calls downloadExport with the server response on export success", async () => {
        stubCreateExport(EXPORT_RESPONSE);

        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        renderComponent();

        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Export Selected" }));
        });

        await waitFor(() => {
            expect(downloadExportSpy).toHaveBeenCalledWith(EXPORT_RESPONSE);
        });
    });

    it("shows an error toast when export fails", async () => {
        server.use(http.post(`${API_BASE}/exports`, () => new HttpResponse(null, { status: 500 })));

        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        renderComponent();

        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Export Selected" }));
        });

        await waitFor(() => {
            expect(mockShowToast).toHaveBeenCalledWith(expect.objectContaining({ type: "error" }));
        });
    });

    it("silently ignores clipboard write failures", async () => {
        clipboardWriteText.mockRejectedValueOnce(new Error("clipboard denied"));

        act(() => {
            useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
        });

        renderComponent();

        await act(async () => {
            fireEvent.click(screen.getByRole("button", { name: "Copy ID for Alpha" }));
        });

        expect(clipboardWriteText).toHaveBeenCalledWith("alpha-object");
    });

    describe("Copy to layer", () => {
        const EDITOR_LAYER: MyLayerInfo = {
            layerId: "editor-layer",
            layerName: "Editor Layer",
            role: "EDITOR",
            accessScope: "PRIVATE",
            defaultVisible: true,
        };
        const MEMBER_LAYER: MyLayerInfo = {
            layerId: "member-layer",
            layerName: "Member Layer",
            role: "MEMBER",
            accessScope: "PRIVATE",
            defaultVisible: true,
        };

        /** A real element: copying serializes it with `toFeature()`, which the plain-object `makeElement` lacks. */
        function makeCopyable(name: string, source = "test", stale?: number): Element {
            return new OcElement({
                type: "symbol",
                objectId: `${name.toLowerCase()}-object`,
                source,
                displayName: name,
                stale,
                geometry: new PointGeometry([5, 6]),
                style: new IconStyle({ size: 1, angle: 0 }),
            });
        }

        function stubLayerCreate() {
            const calls: { targetLayer: string; source: string }[] = [];
            server.use(
                http.post(
                    `${API_BASE}/events/broadcast/layer/:targetLayer`,
                    async ({ request, params }) => {
                        const body = (await request.json()) as {
                            properties: { opencop: { source: string } };
                        };
                        calls.push({
                            targetLayer: String(params.targetLayer),
                            source: body.properties.opencop.source,
                        });
                        return HttpResponse.json({});
                    },
                ),
            );
            return calls;
        }

        async function openCopyDialog(user: ReturnType<typeof userEvent.setup>) {
            await user.click(screen.getByRole("button", { name: "Copy to layer" }));
            return screen.findByRole("dialog", { name: "Copy to layer" });
        }

        it("disables the button and says why when a checked event is on a layer the user can't edit", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });

            renderComponent(); // Default: no layer or data-source access.

            const copyButton = screen.getByRole("button", { name: "Copy to layer" });
            expect(copyButton).toBeDisabled();
            const tooltipTrigger = copyButton.parentElement as HTMLElement;
            const reason =
                "1 selected event is on a layer or data source you can't edit. Use the Can edit tab or deselect it to copy.";
            expect(tooltipTrigger).toHaveAccessibleName(reason);
            await user.hover(tooltipTrigger);
            expect(await screen.findByText(reason)).toBeVisible();
        });

        it("disables the button when the user can't edit any layer to copy into", async () => {
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha", "local")], []);
            });

            renderComponent({ myLayers: [MEMBER_LAYER] });

            const copyButton = screen.getByRole("button", { name: "Copy to layer" });
            expect(copyButton).toBeDisabled();
            expect(copyButton.parentElement).toHaveAccessibleName(
                "You don't have editor access to any layer to copy into",
            );
        });

        it("offers only layers the user is editor or higher on as destinations", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER, MEMBER_LAYER] });

            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));

            expect(await screen.findByRole("option", { name: /Test layer/ })).toBeVisible();
            expect(screen.getByRole("option", { name: /Editor Layer/ })).toBeVisible();
            expect(screen.queryByRole("option", { name: /Member Layer/ })).toBeNull();
        });

        it("names a global layer's scope in its option, and leaves a private layer's name alone", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            const GLOBAL_LAYER: MyLayerInfo = {
                layerId: "shared-layer",
                layerName: "Shared Layer",
                role: "OWNER",
                accessScope: "GLOBAL",
                defaultVisible: true,
            };
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, GLOBAL_LAYER] });

            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));

            // The globe's tooltip never shows on an option (focus stays on the combobox input),
            // so the scope has to be part of the option's accessible name.
            expect(
                await screen.findByRole("option", { name: /Shared Layer/ }),
            ).toHaveAccessibleName("Global Shared Layer");
            expect(screen.getByRole("option", { name: "Test layer" })).toHaveAccessibleName(
                "Test layer",
            );
        });

        it("opens with no destination chosen after a previous choice was cancelled", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const first = await openCopyDialog(user);
            await user.click(within(first).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            expect(within(first).getByRole("button", { name: "Copy" })).toBeEnabled();
            await user.click(within(first).getByRole("button", { name: "Cancel" }));
            await waitFor(() => {
                expect(screen.queryByRole("dialog", { name: "Copy to layer" })).toBeNull();
            });

            const second = await openCopyDialog(user);
            expect(within(second).getByRole("button", { name: "Copy" })).toBeDisabled();
            expect(calls).toEqual([]);
        });

        it("returns focus to Copy to layer when the dialog is cancelled", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const copyButton = screen.getByRole("button", { name: "Copy to layer" });
            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

            await waitFor(() => {
                expect(screen.queryByRole("dialog", { name: "Copy to layer" })).toBeNull();
            });
            await waitFor(() => {
                expect(copyButton).toHaveFocus();
            });
            expect(calls).toEqual([]);
        });

        it("moves focus to the selected tab after a copy that unchecks everything, which leaves Copy disabled", async () => {
            const user = userEvent.setup();
            stubLayerCreate();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            await user.click(within(dialog).getByRole("button", { name: "Copy" }));

            await waitFor(() => {
                expect(screen.queryByRole("dialog", { name: "Copy to layer" })).toBeNull();
            });
            expect(screen.getByRole("button", { name: "Copy to layer" })).toBeDisabled();
            await waitFor(() => {
                expect(screen.getByRole("tab", { name: /^All/ })).toHaveFocus();
            });
        });

        it("moves focus to the selected tab after copying everything on Can edit, even while read-only items stay checked on the other tab", async () => {
            const user = userEvent.setup();
            stubLayerCreate();
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .select([makeCopyable("Alpha"), makeCopyable("Beta", "other")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            await user.click(screen.getByRole("tab", { name: /^Can edit/ }));
            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            await user.click(within(dialog).getByRole("button", { name: "Copy" }));

            await waitFor(() => {
                expect(screen.queryByRole("dialog", { name: "Copy to layer" })).toBeNull();
            });
            // The read-only element is still ticked, but Copy is scoped to the visible tab.
            expect(useAreaSelectionStore.getState().checkedKeys.size).toBe(1);
            expect(screen.getByRole("button", { name: "Copy to layer" })).toBeDisabled();
            await waitFor(() => {
                expect(screen.getByRole("tab", { name: /^Can edit/ })).toHaveFocus();
            });
        });

        it("returns focus to Copy to layer when the copy fails and the items stay checked", async () => {
            const user = userEvent.setup();
            server.use(
                http.post(
                    `${API_BASE}/events/broadcast/layer/:targetLayer`,
                    () => new HttpResponse(null, { status: 500 }),
                ),
            );
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const copyButton = screen.getByRole("button", { name: "Copy to layer" });
            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            await user.click(within(dialog).getByRole("button", { name: "Copy" }));

            await waitFor(() => {
                expect(screen.queryByRole("dialog", { name: "Copy to layer" })).toBeNull();
            });
            expect(useAreaSelectionStore.getState().checkedKeys.size).toBe(1);
            await waitFor(() => {
                expect(copyButton).toHaveFocus();
            });
        });

        it("keeps Copy disabled until a destination is chosen, then copies into it and closes", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);
            expect(within(dialog).getByRole("button", { name: "Copy" })).toBeDisabled();

            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            await user.click(within(dialog).getByRole("button", { name: "Copy" }));

            await waitFor(() => {
                expect(calls).toEqual([{ targetLayer: "editor-layer", source: "editor-layer" }]);
            });
            await waitFor(() => {
                expect(screen.queryByRole("dialog", { name: "Copy to layer" })).toBeNull();
            });
            expect(mockShowToast).toHaveBeenCalledWith(
                expect.objectContaining({ type: "success", title: "Copied to Editor Layer" }),
            );
            // Copy is additive: the original stays in the panel.
            expect(useAreaSelectionStore.getState().elements).toHaveLength(1);
        });

        it("keeps the dialog's count fixed while the ticked set changes underneath it", async () => {
            const user = userEvent.setup();
            const [alpha, bravo] = [makeCopyable("Alpha"), makeCopyable("Bravo")];
            act(() => {
                useAreaSelectionStore.getState().select([alpha, bravo], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);

            // A copy unticks what it copied; the message must not shrink while that happens.
            act(() => {
                useAreaSelectionStore.getState().setChecked([getElementKey(alpha)], false);
            });

            expect(
                within(dialog).getByText(
                    "Copy 2 selected items to the layer below? The originals stay where they are.",
                ),
            ).toBeVisible();
        });

        it("keeps the same Copy button mounted, and focus on its wrapper, when the disabled reason clears", async () => {
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            const { queryClient } = renderComponent(); // No access yet: Copy is disabled with a reason.

            const copyButton = screen.getByRole("button", { name: "Copy to layer" });
            const wrapper = copyButton.parentElement as HTMLElement;
            wrapper.focus();
            expect(wrapper).toHaveFocus();

            // Access arrives, so the reason clears and Copy enables.
            act(() => {
                queryClient.setQueryData(layerQueries.myList().queryKey, [
                    ...OWNER_OF_TEST_LAYER,
                    EDITOR_LAYER,
                ]);
            });

            await waitFor(() => expect(copyButton).toBeEnabled());
            // Same nodes: the tree changed props only, so nothing was unmounted under the focus.
            expect(copyButton).toBeInTheDocument();
            expect(copyButton.parentElement).toBe(wrapper);
            expect(wrapper).toHaveFocus();
        });

        it("disables Copy and says why when a checked item turns read-only while the dialog is open", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            const { queryClient } = renderComponent({
                myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER],
            });

            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            const copy = within(dialog).getByRole("button", { name: "Copy" });
            expect(copy).toBeEnabled();
            // The live region is mounted before the reason arrives, so the change is announced.
            const status = within(dialog).getByRole("status");
            expect(status).toBeEmptyDOMElement();

            // A membership refetch demotes the user on the layer the element lives on.
            act(() => {
                queryClient.setQueryData(layerQueries.myList().queryKey, [EDITOR_LAYER]);
            });

            await waitFor(() => expect(copy).toBeDisabled());
            const reason =
                "1 selected event is on a layer or data source you can't edit. Use the Can edit tab or deselect it to copy.";
            // The status region is also in the DOM, so pick out the visible copy by its element.
            expect(within(dialog).getByText(reason, { selector: "p" })).toBeVisible();
            expect(status).toHaveTextContent(reason);
            await user.click(copy);

            expect(screen.getByRole("dialog", { name: "Copy to layer" })).toBeVisible();
            expect(calls).toEqual([]);
        });

        it("tells the user how many stale events will be skipped when some of the selection is fresh", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .select(
                        [makeCopyable("Alpha", "test", Date.now() - 60_000), makeCopyable("Beta")],
                        [],
                    );
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);
            expect(
                within(dialog).getByText("1 of these is past its stale time and will be skipped."),
            ).toBeVisible();

            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            await user.click(within(dialog).getByRole("button", { name: "Copy" }));

            await waitFor(() => {
                expect(calls).toEqual([{ targetLayer: "editor-layer", source: "editor-layer" }]);
            });
        });

        it("skips and counts items already on the chosen destination, and copies the rest", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .select(
                        [makeCopyable("Alpha", "test"), makeCopyable("Beta", "editor-layer")],
                        [],
                    );
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);
            expect(within(dialog).queryByText(/already on/)).toBeNull();
            // Mounted and empty before a destination is picked, so the note is announced when it appears.
            const status = within(dialog).getByRole("status");
            expect(status).toBeEmptyDOMElement();
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Test layer/ }));

            const note = "1 of these is already on Test layer and will be skipped.";
            expect(within(dialog).getByText(note, { selector: "p" })).toBeVisible();
            expect(status).toHaveTextContent(note);
            await user.click(within(dialog).getByRole("button", { name: "Copy" }));

            await waitFor(() => {
                expect(calls).toEqual([{ targetLayer: "test", source: "test" }]);
            });
        });

        it("won't offer to copy when everything checked is already on the chosen destination", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .select([makeCopyable("Alpha", "test"), makeCopyable("Beta", "test")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Test layer/ }));

            expect(
                within(dialog).getByText(
                    "2 of these are already on Test layer and will be skipped.",
                    {
                        selector: "p",
                    },
                ),
            ).toBeVisible();
            expect(within(dialog).getByRole("button", { name: "Copy" })).toBeDisabled();
            expect(calls).toEqual([]);
        });

        it("disables Copy and says why when every checked event is past its stale time", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .select([makeCopyable("Alpha", "test", Date.now() - 60_000)], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const copyButton = screen.getByRole("button", { name: "Copy to layer" });
            expect(copyButton).toBeDisabled();
            const tooltipTrigger = copyButton.parentElement as HTMLElement;
            const reason = "The selected event is past its stale time, so it can't be copied.";
            expect(tooltipTrigger).toHaveAccessibleName(reason);
            await user.hover(tooltipTrigger);
            expect(await screen.findByText(reason)).toBeVisible();
            expect(calls).toEqual([]);
        });

        it("tells the user the dialog says remarks and contacts aren't copied", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore.getState().select([makeCopyable("Alpha")], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);

            expect(within(dialog).getByText("Remarks and contacts aren't copied.")).toBeVisible();
        });

        it("closes with a warning toast, not silently, when the checked items vanish before Copy runs", async () => {
            const user = userEvent.setup();
            const calls = stubLayerCreate();
            const alpha = makeCopyable("Alpha");
            act(() => {
                useAreaSelectionStore.getState().select([alpha], []);
            });
            renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, EDITOR_LAYER] });

            const dialog = await openCopyDialog(user);
            await user.click(within(dialog).getByRole("combobox", { name: "Destination layer" }));
            await user.click(await screen.findByRole("option", { name: /Editor Layer/ }));
            // Something unticks the selection while the dialog is open.
            act(() => {
                useAreaSelectionStore.getState().setChecked([getElementKey(alpha)], false);
            });
            await user.click(within(dialog).getByRole("button", { name: "Copy" }));

            await waitFor(() => {
                expect(screen.queryByRole("dialog", { name: "Copy to layer" })).toBeNull();
            });
            expect(mockShowToast).toHaveBeenCalledWith(
                expect.objectContaining({
                    type: "warning",
                    title: "Nothing was copied",
                    description: "The selection changed before the copy could start.",
                }),
            );
            expect(calls).toEqual([]);
        });
    });

    describe("Delete Selected", () => {
        it("disables the button and identifies an event without delete permission", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            renderComponent(); // Default: no layer or data-source access.

            const deleteButton = screen.getByRole("button", { name: "Delete Selected" });
            expect(deleteButton).toBeDisabled();
            expect(deleteButton).not.toHaveAttribute("title");

            const tooltipTrigger = deleteButton.parentElement as HTMLElement;
            expect(tooltipTrigger).toHaveAttribute("tabindex", "0");
            // Accessible name must be present on the focusable trigger itself — the
            // disabled button's own aria-label isn't exposed on this ancestor, and Ark
            // only adds aria-describedby (a description, not a name) once focused.
            expect(tooltipTrigger).toHaveAccessibleName(
                "1 selected event is on a layer or data source you can't edit. Use the Can edit tab or deselect it to delete.",
            );
            await user.hover(tooltipTrigger);

            expect(
                await screen.findByText(
                    "1 selected event is on a layer or data source you can't edit. Use the Can edit tab or deselect it to delete.",
                ),
            ).toBeVisible();
        });

        it("shows that remote permissions are loading instead of claiming permission was denied", async () => {
            const user = userEvent.setup();
            let releaseRequests!: () => void;
            const requestGate = new Promise<void>((resolve) => {
                releaseRequests = resolve;
            });
            server.use(
                http.get(`${API_BASE}/me/layers`, async () => {
                    await requestGate;
                    return HttpResponse.json([]);
                }),
                http.get(`${API_BASE}/me/sources`, async () => {
                    await requestGate;
                    return HttpResponse.json([]);
                }),
            );

            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            render(<SelectionPanel onFocus={mockOnFocus} onClose={mockOnClose} />);

            const deleteButton = screen.getByRole("button", { name: "Delete Selected" });
            await user.hover(deleteButton.parentElement as HTMLElement);

            expect(await screen.findByText("Checking delete permissions…")).toBeVisible();
            expect(
                screen.queryByText(
                    "1 selected event is on a layer or data source you can't edit. Use the Can edit tab or deselect it to delete.",
                ),
            ).not.toBeInTheDocument();

            releaseRequests();
            expect(
                await screen.findByText(
                    "1 selected event is on a layer or data source you can't edit. Use the Can edit tab or deselect it to delete.",
                ),
            ).toBeVisible();
        });

        it("reports a permission lookup failure instead of treating it as denial", async () => {
            const user = userEvent.setup();
            server.use(
                http.get(`${API_BASE}/me/layers`, () => new HttpResponse(null, { status: 500 })),
                http.get(`${API_BASE}/me/sources`, () => new HttpResponse(null, { status: 500 })),
            );

            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            render(<SelectionPanel onFocus={mockOnFocus} onClose={mockOnClose} />);

            const deleteButton = screen.getByRole("button", { name: "Delete Selected" });
            await user.hover(deleteButton.parentElement as HTMLElement);

            expect(await screen.findByText("Unable to verify delete permissions")).toBeVisible();
            expect(
                screen.queryByText(
                    "1 selected event is on a layer or data source you can't edit. Use the Can edit tab or deselect it to delete.",
                ),
            ).not.toBeInTheDocument();
        });

        it("deletes the selected element after confirming, clearing it from the panel", async () => {
            const capture = { count: 0, path: "" };
            server.use(
                http.delete(
                    `${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`,
                    ({ params }) => {
                        capture.count += 1;
                        capture.path = `${params.targetLayer}/${params.objectId}`;
                        return HttpResponse.json({});
                    },
                ),
            );

            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            renderComponent({ myLayers: OWNER_OF_TEST_LAYER });

            fireEvent.click(screen.getByRole("button", { name: "Delete Selected" }));

            const dialog = await screen.findByRole("alertdialog");
            expect(
                within(dialog).getByText("Delete 1 selected item? This cannot be undone."),
            ).toBeTruthy();

            await act(async () => {
                fireEvent.click(within(dialog).getByRole("button", { name: "Delete" }));
            });

            await waitFor(() => {
                expect(capture.count).toBe(1);
                expect(capture.path).toBe("test/alpha-object");
            });

            expect(useAreaSelectionStore.getState().elements).toBeNull();
        });

        it.each(["Cancel", "Escape"])(
            "returns focus to Delete Selected, not the caller-supplied element, after %s",
            async (how) => {
                const user = userEvent.setup();
                const panelToggle = document.createElement("button");
                document.body.appendChild(panelToggle);
                act(() => {
                    useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
                });
                renderComponent({
                    myLayers: OWNER_OF_TEST_LAYER,
                    finalFocusEl: () => panelToggle,
                });

                const deleteButton = screen.getByRole("button", { name: "Delete Selected" });
                await user.click(deleteButton);
                const dialog = await screen.findByRole("alertdialog");
                if (how === "Cancel") {
                    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
                } else {
                    await user.keyboard("{Escape}");
                }

                await waitFor(() => {
                    expect(screen.queryByRole("alertdialog")).toBeNull();
                });
                await waitFor(() => {
                    expect(deleteButton).toHaveFocus();
                });
                expect(useAreaSelectionStore.getState().elements).toHaveLength(1);

                panelToggle.remove();
            },
        );

        it("moves focus to the caller-supplied element after deleting the whole selection, instead of dropping to body", async () => {
            const user = userEvent.setup();
            server.use(
                http.delete(`${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`, () =>
                    HttpResponse.json({}),
                ),
            );
            const panelToggle = document.createElement("button");
            panelToggle.setAttribute("data-testid", "panel-toggle-stand-in");
            document.body.appendChild(panelToggle);

            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            renderComponent({
                myLayers: OWNER_OF_TEST_LAYER,
                finalFocusEl: () => panelToggle,
            });

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");
            await user.click(within(dialog).getByRole("button", { name: "Delete" }));

            await waitFor(() => {
                expect(useAreaSelectionStore.getState().elements).toBeNull();
            });
            // SelectionPanel returns null (and the dialog unmounts with it) as soon
            // as the last element is deleted, rather than the dialog closing first —
            // so focus restoration can't fall back to the button that opened it.
            expect(document.activeElement).toBe(panelToggle);

            panelToggle.remove();
        });

        it("uses the data-source delete endpoint for an owned data-source event", async () => {
            const user = userEvent.setup();
            const capture = { count: 0, path: "" };
            server.use(
                http.delete(
                    `${API_BASE}/events/broadcast/source/:sourceId/:objectId`,
                    ({ params }) => {
                        capture.count += 1;
                        capture.path = `${params.sourceId}/${params.objectId}`;
                        return HttpResponse.json({});
                    },
                ),
            );

            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha", "test-source")], []);
            });

            renderComponent({ myDataSources: OWNER_OF_TEST_DATA_SOURCE });

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");
            await user.click(within(dialog).getByRole("button", { name: "Delete" }));

            await waitFor(() => {
                expect(capture.count).toBe(1);
                expect(capture.path).toBe("test-source/alpha-object");
            });
            expect(useAreaSelectionStore.getState().elements).toBeNull();
        });

        it("warns that a data-source event may re-broadcast before deleting it", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .select([makeElement("Alpha", "test-source"), makeElement("Bravo")], []);
            });

            renderComponent({
                myLayers: OWNER_OF_TEST_LAYER,
                myDataSources: OWNER_OF_TEST_DATA_SOURCE,
            });

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");

            expect(
                within(dialog).getByText("Delete 2 selected items? This cannot be undone."),
            ).toBeTruthy();
            expect(
                within(dialog).getByText(
                    "1 of these comes from a data source that may re-broadcast it and bring it back.",
                ),
            ).toBeTruthy();
        });

        it("keeps the confirm message's counts fixed while the selection shrinks mid-delete", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .select([makeElement("Alpha", "test-source"), makeElement("Bravo")], []);
            });

            renderComponent({
                myLayers: OWNER_OF_TEST_LAYER,
                myDataSources: OWNER_OF_TEST_DATA_SOURCE,
            });

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");

            // A delete wave's echo prunes the selection while the dialog is still showing.
            act(() => {
                useAreaSelectionStore
                    .getState()
                    .removeDeleted(
                        new Set([
                            getElementKey({ source: "test-source", objectId: "alpha-object" }),
                        ]),
                    );
            });

            expect(
                within(dialog).getByText("Delete 2 selected items? This cannot be undone."),
            ).toBeTruthy();
            expect(within(dialog).getByText(/may re-broadcast/)).toBeTruthy();
        });

        it("disables Confirm and says why when a checked item turns read-only while the dialog is open", async () => {
            const user = userEvent.setup();
            const deleted = { count: 0 };
            server.use(
                http.delete(`${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`, () => {
                    deleted.count += 1;
                    return HttpResponse.json({});
                }),
            );
            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            const { queryClient } = renderComponent({ myLayers: OWNER_OF_TEST_LAYER });

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");
            const confirm = within(dialog).getByRole("button", { name: "Delete" });
            expect(confirm).toBeEnabled();

            // A membership refetch demotes the user to a read-only role.
            act(() => {
                queryClient.setQueryData(layerQueries.myList().queryKey, []);
            });

            await waitFor(() => expect(confirm).toBeDisabled());
            expect(
                within(dialog).getByText(/on a layer or data source you can't edit/),
            ).toBeVisible();
            await user.click(confirm);

            expect(screen.getByRole("alertdialog")).toBeVisible();
            expect(deleted.count).toBe(0);
            expect(useAreaSelectionStore.getState().elements).toHaveLength(1);
        });

        it("does not show the data-source note for an all-layer selection", async () => {
            const user = userEvent.setup();
            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            renderComponent({ myLayers: OWNER_OF_TEST_LAYER });

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            await screen.findByRole("alertdialog");

            expect(screen.queryByText(/may re-broadcast/)).not.toBeInTheDocument();
        });

        it("clears map focus when the focused event is successfully deleted", async () => {
            const user = userEvent.setup();
            server.use(
                http.delete(`${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`, () =>
                    HttpResponse.json({}),
                ),
            );
            const alpha = makeElement("Alpha");
            act(() => {
                useAreaSelectionStore.getState().select([alpha], []);
            });

            renderComponent({ myLayers: OWNER_OF_TEST_LAYER });

            await user.click(screen.getByRole("button", { name: "Show on map" }));
            expect(useMapStore.getState().selected).toBe(alpha.key);

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");
            await user.click(within(dialog).getByRole("button", { name: "Delete" }));

            await waitFor(() => {
                expect(useAreaSelectionStore.getState().elements).toBeNull();
            });
            expect(mockClearSelection).toHaveBeenCalledOnce();
            expect(useMapStore.getState().selected).toBeNull();
        });

        it("keeps map focus when that event fails but another selected event is deleted", async () => {
            const user = userEvent.setup();
            server.use(
                http.delete(
                    `${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`,
                    ({ params }) =>
                        params.objectId === "alpha-object"
                            ? new HttpResponse(null, { status: 500 })
                            : HttpResponse.json({}),
                ),
            );
            const alpha = makeElement("Alpha");
            const bravo = makeElement("Bravo");
            act(() => {
                useAreaSelectionStore.getState().select([alpha, bravo], []);
            });

            renderComponent({ myLayers: OWNER_OF_TEST_LAYER });

            await user.click(screen.getAllByRole("button", { name: "Show on map" })[0]);
            expect(useMapStore.getState().selected).toBe(alpha.key);

            await user.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");
            await user.click(within(dialog).getByRole("button", { name: "Delete" }));

            await waitFor(() => {
                expect(useAreaSelectionStore.getState().elements).toEqual([alpha]);
            });
            expect(mockClearSelection).not.toHaveBeenCalled();
            expect(useMapStore.getState().selected).toBe(alpha.key);
        });

        describe("with a mixed selection", () => {
            const OTHER_TEAM_MEMBER: MyLayerInfo = {
                layerId: "other-layer",
                layerName: "Other Team",
                role: "MEMBER",
                accessScope: "PRIVATE",
                defaultVisible: true,
            };

            function selectMixed() {
                const editable = makeElement("Alpha");
                const readOnly = makeElement("Bravo", "other-layer");
                const alsoReadOnly = makeElement("Charlie", "other-layer");
                act(() => {
                    useAreaSelectionStore.getState().select([editable, readOnly, alsoReadOnly], []);
                });
                renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, OTHER_TEAM_MEMBER] });
                return { editable, readOnly, alsoReadOnly };
            }

            function stubLayerDelete() {
                const capture = { count: 0 };
                server.use(
                    http.delete(`${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`, () => {
                        capture.count += 1;
                        return HttpResponse.json({});
                    }),
                );
                return capture;
            }

            it("splits the selection into Can edit, Read-only and All tabs, tagging read-only rows", async () => {
                selectMixed();

                expect(await screen.findByRole("tab", { name: "Can edit 1" })).toBeVisible();
                expect(screen.getByRole("tab", { name: "Read-only 2" })).toBeVisible();
                expect(screen.getByRole("tab", { name: "All 3" })).toHaveAttribute(
                    "aria-selected",
                    "true",
                );
                expect(screen.getAllByText("Read-only", { selector: "span" })).toHaveLength(2);
            });

            it("lets keyboard users reach each read-only badge and read its reason", async () => {
                const user = userEvent.setup();
                selectMixed();

                const badges = await screen.findAllByLabelText(/^Read-only: /);
                expect(badges).toHaveLength(2);
                for (const badge of badges) expect(badge).toHaveAttribute("tabindex", "0");

                // Tab through the panel until the first badge takes focus; no hover involved.
                for (let i = 0; i < 20 && document.activeElement !== badges[0]; i++) {
                    await user.tab();
                }

                expect(badges[0]).toHaveFocus();
                expect(
                    await screen.findByText(/Requires Editor, Admin, or Owner role on this layer/, {
                        selector: '[data-part="content"] *, [role="tooltip"], [role="tooltip"] *',
                    }),
                ).toBeVisible();
            });

            it("disables Delete on the All tab, pointing to the Can edit tab", async () => {
                const user = userEvent.setup();
                selectMixed();

                const deleteButton = screen.getByRole("button", { name: "Delete Selected" });
                expect(deleteButton).toBeDisabled();
                await user.hover(deleteButton.parentElement as HTMLElement);
                expect(
                    await screen.findByText(
                        "2 selected events are on layers or data sources you can't edit. Use the Can edit tab or deselect them to delete.",
                    ),
                ).toBeVisible();
                expect(screen.queryByRole("alert")).toBeNull();
            });

            it("deletes from the Can edit tab while read-only items stay checked but hidden, then switches to All", async () => {
                const user = userEvent.setup();
                const capture = stubLayerDelete();
                const { readOnly, alsoReadOnly } = selectMixed();

                await user.click(await screen.findByRole("tab", { name: "Can edit 1" }));
                expect(screen.queryByRole("button", { name: "Bravo" })).toBeNull();
                expect(screen.getByText("1 event selected")).toBeVisible();

                await user.click(screen.getByRole("button", { name: "Delete Selected" }));
                const dialog = await screen.findByRole("alertdialog");
                // The dialog only states what will be deleted; it says nothing about what's left out.
                expect(within(dialog).queryByText(/not included/)).toBeNull();
                expect(within(dialog).queryByRole("list")).toBeNull();
                expect(within(dialog).queryByText(/Other Team/)).toBeNull();
                await user.click(within(dialog).getByRole("button", { name: "Delete" }));

                await waitFor(() => {
                    expect(capture.count).toBe(1);
                });
                expect(useAreaSelectionStore.getState().elements).toEqual([readOnly, alsoReadOnly]);
                // The delete emptied Can edit, so the panel moves to All and focus follows it
                // rather than leaving the panel.
                await waitFor(() => {
                    expect(screen.queryByRole("alertdialog")).toBeNull();
                });
                const allTab = screen.getByRole("tab", { name: "All 2" });
                expect(allTab).toHaveAttribute("aria-selected", "true");
                expect(allTab).toHaveFocus();
            });

            it("stays on the Can edit tab when a failed delete leaves rows on it", async () => {
                const user = userEvent.setup();
                server.use(
                    http.delete(
                        `${API_BASE}/events/broadcast/layer/:targetLayer/:objectId`,
                        ({ params }) =>
                            params.objectId === "delta-object"
                                ? HttpResponse.json({ message: "boom" }, { status: 500 })
                                : HttpResponse.json({}),
                    ),
                );
                act(() => {
                    useAreaSelectionStore
                        .getState()
                        .select(
                            [
                                makeElement("Alpha"),
                                makeElement("Delta"),
                                makeElement("Bravo", "other-layer"),
                            ],
                            [],
                        );
                });
                renderComponent({ myLayers: [...OWNER_OF_TEST_LAYER, OTHER_TEAM_MEMBER] });

                await user.click(await screen.findByRole("tab", { name: "Can edit 2" }));
                await user.click(screen.getByRole("button", { name: "Delete Selected" }));
                const dialog = await screen.findByRole("alertdialog");
                await user.click(within(dialog).getByRole("button", { name: "Delete" }));

                await waitFor(() => {
                    expect(screen.queryByRole("alertdialog")).toBeNull();
                });
                const canEditTab = screen.getByRole("tab", { name: "Can edit 1" });
                expect(canEditTab).toHaveAttribute("aria-selected", "true");
                expect(canEditTab).toHaveFocus();
                expect(screen.getByRole("button", { name: "Delta" })).toBeVisible();
            });

            it("renders the list as the selected tab's panel", async () => {
                const user = userEvent.setup();
                selectMixed();

                for (const name of ["All 3", "Can edit 1", "Read-only 2"]) {
                    const tab = await screen.findByRole("tab", { name });
                    await user.click(tab);
                    const panel = screen.getByRole("tabpanel");
                    expect(tab).toHaveAttribute("aria-selected", "true");
                    expect(tab).toHaveAttribute("aria-controls", panel.id);
                    expect(panel).toHaveAttribute("aria-labelledby", tab.id);
                }
            });

            it("exports only the checked items on the active tab", async () => {
                const user = userEvent.setup();
                const capture = stubCreateExport();
                selectMixed();

                await user.click(await screen.findByRole("tab", { name: "Can edit 1" }));
                await user.click(screen.getByRole("button", { name: "Export Selected" }));

                await waitFor(() => {
                    expect(capture.count).toBe(1);
                });
                expect(capture.body).toEqual({ format: "CSV", elementIds: ["alpha-id"] });
            });

            it("the select-all checkbox only changes the active tab's items", async () => {
                const user = userEvent.setup();
                const { editable, readOnly, alsoReadOnly } = selectMixed();

                await user.click(await screen.findByRole("tab", { name: "Read-only 2" }));
                const selectAll = screen.getByRole("checkbox", { name: "Select All" });
                await user.click(selectAll);

                expect(useAreaSelectionStore.getState().checkedKeys).toEqual(
                    new Set([getElementKey(editable)]),
                );

                await user.click(selectAll);

                expect(useAreaSelectionStore.getState().checkedKeys).toEqual(
                    new Set([editable, readOnly, alsoReadOnly].map(getElementKey)),
                );
            });

            it("returns to the All tab when a new lasso replaces the selection", async () => {
                const user = userEvent.setup();
                selectMixed();

                await user.click(await screen.findByRole("tab", { name: "Can edit 1" }));
                expect(screen.getByRole("tab", { name: "Can edit 1" })).toHaveAttribute(
                    "aria-selected",
                    "true",
                );

                act(() => {
                    useAreaSelectionStore.getState().select(
                        [makeElement("Delta"), makeElement("Echo", "other-layer")],
                        [
                            [0, 0],
                            [1, 0],
                            [1, 1],
                        ],
                    );
                });

                expect(await screen.findByRole("tab", { name: "All 2" })).toHaveAttribute(
                    "aria-selected",
                    "true",
                );
                expect(screen.getByRole("button", { name: "Echo" })).toBeVisible();
            });

            it("keeps Delete disabled on the Read-only tab but still allows export", async () => {
                const user = userEvent.setup();
                selectMixed();

                await user.click(await screen.findByRole("tab", { name: "Read-only 2" }));

                expect(screen.getByRole("button", { name: "Delete Selected" })).toBeDisabled();
                expect(screen.getByRole("button", { name: "Export Selected" })).toBeEnabled();
                expect(
                    screen.getByText(
                        "You can't edit these layers or data sources, so these items can't be deleted. You can still select and export them.",
                    ),
                ).toBeVisible();
            });
        });

        it("cancelling the confirm dialog deletes nothing", async () => {
            act(() => {
                useAreaSelectionStore.getState().select([makeElement("Alpha")], []);
            });

            renderComponent({ myLayers: OWNER_OF_TEST_LAYER });

            fireEvent.click(screen.getByRole("button", { name: "Delete Selected" }));
            const dialog = await screen.findByRole("alertdialog");

            await act(async () => {
                fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
            });

            await waitFor(() => {
                expect(screen.queryByRole("alertdialog")).toBeNull();
            });
            expect(useAreaSelectionStore.getState().elements).toHaveLength(1);
        });
    });
});
