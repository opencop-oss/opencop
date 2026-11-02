// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import type { MyLayerInfo } from "@opencop/api";
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from "vitest";
import {
    act,
    API_BASE,
    http,
    HttpResponse,
    render,
    screen,
    server,
    userEvent,
    waitFor,
} from "~/test";
import { useFilterPanelStore } from "~/features/filters/filter-panel.store";
import { layerQueries } from "~/features/layers/layers.queries";
import { i18n } from "~/i18n/config";
import { useCustomAttributesStore } from "~/features/filter-fields/custom-attributes.store";
import { installPlugins, withPlugins } from "~/test/plugins";
import { WatchboxForm } from "./WatchboxForm";
import type { Watchbox } from "./watchbox.model";

const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("~/features/notifications/use-toast.hook", () => ({
    useToast: () => ({ showToast }),
}));

// spin-delay's 500ms threshold would make loading-state assertions dependent
// on real timers — stub it to a pass-through so tests can drive the loading
// text directly off the query's own isLoading/isFetching flag, same pattern
// as HlsPlayer.test.tsx.
const mockUseSpinDelay = vi.fn();
vi.mock("spin-delay", () => ({
    useSpinDelay: (...args: unknown[]) => mockUseSpinDelay(...args),
}));

// OcCombobox is a Chakra v3 (zag.js) widget that does not drive here — same
// narrow double used elsewhere (see RuntimeFeatureDrawerContent.test.tsx) — just
// enough to assert which items the eligibility filter actually handed it and
// that a multi-select click adds to (not replaces) the current selection.
// renderItem is deliberately NOT invoked here: WatchboxForm.tsx's renderItem
// renders the real `Combobox.ItemText`, which throws ("useComboboxStyles
// returned is 'undefined'") outside a real `Combobox.Root` tree — confirmed
// empirically. A renderItem-driven assertion (e.g. the scope indicator on a
// GLOBAL layer's entry) belongs in OcCombobox.test.tsx, where that context
// exists for real.
vi.mock("@opencop/ui", async (importOriginal) => {
    const { createElement } = await import("react");
    interface ComboboxItem {
        label: string;
        value: string;
    }
    interface ComboboxProps {
        items: ComboboxItem[];
        value?: string[];
        multiple?: boolean;
        hideSelectedOptions?: boolean;
        onValueChange?: (value: string[]) => void;
    }
    return {
        ...(await importOriginal<typeof import("@opencop/ui")>()),
        OcCombobox: (props: ComboboxProps) =>
            createElement(
                "div",
                null,
                // Selections, as the real component renders them as tags.
                ...(props.value ?? []).map((val) =>
                    createElement(
                        "span",
                        { key: `tag-${val}` },
                        props.items.find((item) => item.value === val)?.label ?? val,
                    ),
                ),
                ...(props.hideSelectedOptions
                    ? props.items.filter((item) => !(props.value ?? []).includes(item.value))
                    : props.items
                ).map((item) =>
                    createElement(
                        "button",
                        {
                            key: item.value,
                            type: "button",
                            onClick: () =>
                                props.onValueChange?.(
                                    props.multiple
                                        ? [...(props.value ?? []), item.value]
                                        : [item.value],
                                ),
                        },
                        item.label,
                    ),
                ),
            ),
    };
});

const POLYGON = {
    type: "Polygon" as const,
    coordinates: [
        [
            [0, 0],
            [1, 0],
            [1, 1],
            [0, 0],
        ],
    ],
};

const LAYER_WITH_SOURCE: MyLayerInfo = {
    layerId: "layer-with-source",
    layerName: "Layer With Source",
    role: "OWNER",
    accessScope: "PRIVATE",
    defaultVisible: true,
};

const LAYER_WITHOUT_SOURCE: MyLayerInfo = {
    layerId: "layer-without-source",
    layerName: "Layer Without Source",
    role: "OWNER",
    accessScope: "PRIVATE",
    defaultVisible: true,
};

function seed(layers: MyLayerInfo[], sourceCounts: Record<string, number>) {
    const data: Record<string, unknown> = {
        [JSON.stringify(layerQueries.myList().queryKey)]: layers,
    };
    for (const [id, count] of Object.entries(sourceCounts)) {
        data[JSON.stringify(layerQueries.dataSources(id).queryKey)] = Array.from(
            { length: count },
            (_, i) => ({ serverId: `src-${i}` }),
        );
    }
    return data;
}

// The SIDC fields come from the symbology plugin: each describe that reads them installs it.

beforeEach(() => {
    act(() => useCustomAttributesStore.setState({ names: [] }));
});

afterEach(() => {
    vi.clearAllMocks();
    act(() => useFilterPanelStore.setState({ excludedIds: [] }));
});

describe("WatchboxForm — layer eligibility", () => {
    withPlugins(["opencop.symbology"]);

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation((loading: boolean) => loading);
    });

    it("shows a checking-eligibility message instead of static helper text while layers are loading", async () => {
        let settle: (layers: MyLayerInfo[]) => void = () => {};
        server.use(
            http.get(`${API_BASE}/me/layers`, async () => {
                const layers = await new Promise<MyLayerInfo[]>((resolve) => {
                    settle = resolve;
                });
                return HttpResponse.json(layers);
            }),
        );

        render(<WatchboxForm polygon={POLYGON} />, {
            // Seeds only the data-source sub-query, so once the held-open
            // layers list resolves below, layersLoading drops immediately
            // instead of chaining into a second, unmocked loading phase.
            queryData: {
                [JSON.stringify(layerQueries.dataSources(LAYER_WITH_SOURCE.layerId).queryKey)]: [
                    { serverId: "src-0" },
                ],
            },
        });

        expect(await screen.findByText("Checking eligible layers...")).toBeInTheDocument();
        expect(
            screen.queryByText(
                "Only layers you own or administer with at least one data source are eligible. Between 1 and 100 layers must be selected.",
            ),
        ).not.toBeInTheDocument();

        settle([LAYER_WITH_SOURCE]);
        await screen.findByText(
            "Only layers you own or administer with at least one data source are eligible. Between 1 and 100 layers must be selected.",
        );
    });

    it("excludes a layer with no data source from the picker, keeps one that has a source", () => {
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE, LAYER_WITHOUT_SOURCE], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [LAYER_WITHOUT_SOURCE.layerId]: 0,
            }),
        });

        expect(screen.getByText("Layer With Source")).toBeInTheDocument();
        expect(screen.queryByText("Layer Without Source")).not.toBeInTheDocument();
    });

    it("excludes a layer the caller only has MEMBER access to", () => {
        const memberLayer: MyLayerInfo = {
            layerId: "member-layer",
            layerName: "Member Layer",
            role: "MEMBER",
            accessScope: "PRIVATE",
            defaultVisible: true,
        };
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE, memberLayer], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [memberLayer.layerId]: 1,
            }),
        });

        expect(screen.getByText("Layer With Source")).toBeInTheDocument();
        expect(screen.queryByText("Member Layer")).not.toBeInTheDocument();
    });

    it("does not offer a layer the user removed from the map panel", () => {
        const other: MyLayerInfo = {
            ...LAYER_WITHOUT_SOURCE,
            layerId: "other",
            layerName: "Other",
        };
        act(() => useFilterPanelStore.setState({ excludedIds: [LAYER_WITH_SOURCE.layerId] }));
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE, other], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [other.layerId]: 1,
            }),
        });

        expect(screen.queryByText("Layer With Source")).not.toBeInTheDocument();
        expect(screen.getByText("Other")).toBeInTheDocument();
    });

    it("shows the no-eligible-layers empty state when every candidate has zero sources", () => {
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITHOUT_SOURCE], { [LAYER_WITHOUT_SOURCE.layerId]: 0 }),
        });

        expect(
            screen.getByText("You need to own or administer a layer that contains a data source."),
        ).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Create watchbox" })).not.toBeInTheDocument();
    });

    it("reports a failed eligibility check without claiming there are no eligible layers", async () => {
        const user = userEvent.setup();
        let attempts = 0;
        server.use(
            http.get(`${API_BASE}/layers/${LAYER_WITH_SOURCE.layerId}/sources`, () => {
                attempts += 1;
                return attempts === 1
                    ? HttpResponse.json(null, { status: 500 })
                    : HttpResponse.json([{ serverId: "source-after-retry" }]);
            }),
        );

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], {}),
        });

        expect(
            await screen.findByText("Could not check layer eligibility.", {
                selector: '[role="alert"]',
            }),
        ).toBeInTheDocument();
        expect(
            screen.queryByText("You don't own or admin any layers eligible for a watchbox."),
        ).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Retry" }));

        expect(await screen.findByText("Layer With Source")).toBeInTheDocument();
    });

    it("shows checking-eligibility feedback (not a silent stuck screen) while a retry is in flight", async () => {
        const user = userEvent.setup();
        let attempts = 0;
        let settleRetry: () => void = () => {};
        server.use(
            http.get(`${API_BASE}/layers/${LAYER_WITH_SOURCE.layerId}/sources`, async () => {
                attempts += 1;
                if (attempts === 1) return HttpResponse.json(null, { status: 500 });
                await new Promise<void>((resolve) => {
                    settleRetry = resolve;
                });
                return HttpResponse.json([{ serverId: "source-after-retry" }]);
            }),
        );

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], {}),
        });

        await screen.findByText("Could not check layer eligibility.", {
            selector: '[role="alert"]',
        });
        await user.click(screen.getByRole("button", { name: "Retry" }));

        // A query with no prior successful data resets to "pending" (not
        // "error") the instant a refetch starts (see TanStack Query's
        // `fetchState`), so the error screen — Retry button included —
        // unmounts immediately in favor of the main form's own loading
        // state. The user-visible feedback during the retry is this
        // checking-eligibility text, not a spinner on the Retry button
        // itself.
        expect(await screen.findByText("Checking eligible layers...")).toBeInTheDocument();

        settleRetry();
        expect(await screen.findByText("Layer With Source")).toBeInTheDocument();
    });
});

describe("WatchboxForm — GLOBAL layers", () => {
    withPlugins(["opencop.symbology"]);

    const GLOBAL_MANAGED_LAYER: MyLayerInfo = {
        layerId: "global-managed-layer",
        layerName: "Global Managed Layer",
        role: "OWNER",
        accessScope: "GLOBAL",
        defaultVisible: true,
    };

    const GLOBAL_MEMBER_LAYER: MyLayerInfo = {
        layerId: "global-member-layer",
        layerName: "Global Member Layer",
        role: "MEMBER",
        accessScope: "GLOBAL",
        defaultVisible: true,
    };

    it("includes a GLOBAL layer the caller owns", async () => {
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE, GLOBAL_MANAGED_LAYER], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [GLOBAL_MANAGED_LAYER.layerId]: 1,
            }),
        });

        expect(screen.getByText("Layer With Source")).toBeInTheDocument();
        expect(await screen.findByText("Global Managed Layer")).toBeInTheDocument();
    });

    // Matches the backend, which requires OWNER/ADMIN on every selected layer
    // regardless of scope.
    it("excludes a GLOBAL layer the caller only has MEMBER access to", async () => {
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE, GLOBAL_MEMBER_LAYER], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [GLOBAL_MEMBER_LAYER.layerId]: 1,
            }),
        });

        expect(screen.getByText("Layer With Source")).toBeInTheDocument();
        expect(screen.queryByText("Global Member Layer")).not.toBeInTheDocument();
    });

    it("submits layerIds for a mixed GLOBAL and PRIVATE selection", async () => {
        const user = userEvent.setup();
        const bodies: Record<string, unknown>[] = [];
        server.use(
            http.post(`${API_BASE}/watchboxes`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json({
                    id: "wb-new",
                    name: "Mixed Watchbox",
                    description: null,
                    enabled: true,
                    direction: "BOTH",
                    polygon: POLYGON,
                    filter: { combine: "ALL", predicates: [] },
                    layerIds: [LAYER_WITH_SOURCE.layerId, GLOBAL_MANAGED_LAYER.layerId],
                    configEpoch: 1,
                    alertingStatus: "ACTIVE",
                    alertingReasons: [],
                    sourceStatuses: [],
                    capabilities: { canDelete: true, canPatch: true },
                    createdAt: "2026-01-01T00:00:00Z",
                    updatedAt: "2026-01-01T00:00:00Z",
                });
            }),
        );

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE, GLOBAL_MANAGED_LAYER], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [GLOBAL_MANAGED_LAYER.layerId]: 1,
            }),
        });

        await user.type(screen.getByRole("textbox", { name: "Name" }), "Mixed Watchbox");
        await user.click(screen.getByRole("button", { name: LAYER_WITH_SOURCE.layerName }));
        await user.click(await screen.findByRole("button", { name: "Global Managed Layer" }));
        await user.type(screen.getByPlaceholderText("Enter a value"), "Falcon");
        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toMatchObject({
            layerIds: [LAYER_WITH_SOURCE.layerId, GLOBAL_MANAGED_LAYER.layerId],
        });
    });

    it("submits layerIds for a selection made up only of GLOBAL layers", async () => {
        const user = userEvent.setup();
        const bodies: Record<string, unknown>[] = [];
        server.use(
            http.post(`${API_BASE}/watchboxes`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json({
                    id: "wb-new",
                    name: "Global Watchbox",
                    description: null,
                    enabled: true,
                    direction: "BOTH",
                    polygon: POLYGON,
                    filter: { combine: "ALL", predicates: [] },
                    layerIds: [GLOBAL_MANAGED_LAYER.layerId],
                    configEpoch: 1,
                    alertingStatus: "ACTIVE",
                    alertingReasons: [],
                    sourceStatuses: [],
                    capabilities: { canDelete: true, canPatch: true },
                    createdAt: "2026-01-01T00:00:00Z",
                    updatedAt: "2026-01-01T00:00:00Z",
                });
            }),
        );

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([GLOBAL_MANAGED_LAYER], { [GLOBAL_MANAGED_LAYER.layerId]: 1 }),
        });

        await user.type(screen.getByRole("textbox", { name: "Name" }), "Global Watchbox");
        await user.click(await screen.findByRole("button", { name: "Global Managed Layer" }));
        await user.type(screen.getByPlaceholderText("Enter a value"), "Falcon");
        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toMatchObject({ layerIds: [GLOBAL_MANAGED_LAYER.layerId] });
    });
});

describe("WatchboxForm — title", () => {
    withPlugins(["opencop.symbology"]);

    it("starts with an empty name while showing the default title as guidance", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        expect(screen.getByRole("heading", { name: "NEW WATCHBOX" })).toBeInTheDocument();

        const nameInput = screen.getByRole("textbox", { name: "Name" });
        expect(nameInput).toHaveValue("");
        expect(nameInput).toHaveAttribute("placeholder", "New Watchbox");

        await user.type(nameInput, "Downtown Perimeter");

        expect(screen.getByRole("heading", { name: "DOWNTOWN PERIMETER" })).toBeInTheDocument();
    });

    it("falls back to the default title when the name field is cleared", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        const nameInput = screen.getByRole("textbox", { name: "Name" });
        await user.type(nameInput, "Downtown Perimeter");
        await user.clear(nameInput);

        expect(screen.getByRole("heading", { name: "NEW WATCHBOX" })).toBeInTheDocument();
    });
});

describe("WatchboxForm — Info section", () => {
    withPlugins(["opencop.symbology"]);

    it("does not treat the default-name placeholder as a submittable value", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        expect(await screen.findByText("Name must be between 1 and 64 characters")).toBeVisible();
    });

    it("opens the Info section by default and can be collapsed", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        const infoTrigger = screen.getByRole("button", { name: "Info" });
        expect(infoTrigger).toHaveAttribute("aria-expanded", "true");
        expect(screen.getByRole("textbox", { name: "Name" })).toBeVisible();

        await user.click(infoTrigger);

        // The collapse finishes its close transition asynchronously; aria-expanded
        // only flips once that transition settles (see Zag's collapsible.connect.mjs
        // — visible, which drives aria-expanded, stays true through "closing").
        await waitFor(() => expect(infoTrigger).toHaveAttribute("aria-expanded", "false"));
    });
});

describe("WatchboxForm — filter validation", () => {
    withPlugins(["opencop.symbology"]);

    it("opens the Rules section by default", () => {
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        expect(screen.getByRole("button", { name: "Rules" })).toHaveAttribute(
            "aria-expanded",
            "true",
        );
    });

    it("disables Create when a symbol condition is forced back onto All", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("SIDC: Standard Identity"));

        // Picking the field auto-switches to Any; force it back to All.
        await user.click(screen.getByRole("radio", { name: "All" }));

        expect(await screen.findByText(/won't match anything/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Create watchbox" })).toBeDisabled();
    });

    it("reopens the Rules section when its collapsed filter is invalid", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        // There's already one (incomplete) rule by default, so nothing further
        // is needed to make the filter invalid.
        const advancedTrigger = screen.getByRole("button", { name: "Rules" });
        await user.click(advancedTrigger);

        // See the note in the Info-section test: the close transition settles
        // asynchronously, so aria-expanded needs to be awaited, not asserted inline.
        await waitFor(() => expect(advancedTrigger).toHaveAttribute("aria-expanded", "false"));

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        await waitFor(() => expect(advancedTrigger).toHaveAttribute("aria-expanded", "true"));
        expect(screen.getByRole("button", { name: "Remove condition" })).toBeVisible();
    });

    it("reopens every collapsed section containing validation errors", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        // The default filter is incomplete, and no layer is selected, so both
        // sections contain an error after the Create attempt.
        const infoTrigger = screen.getByRole("button", { name: "Info" });
        const rulesTrigger = screen.getByRole("button", { name: "Rules" });
        await user.click(infoTrigger);
        await user.click(rulesTrigger);
        await waitFor(() => {
            expect(infoTrigger).toHaveAttribute("aria-expanded", "false");
            expect(rulesTrigger).toHaveAttribute("aria-expanded", "false");
        });

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        await waitFor(() => {
            expect(infoTrigger).toHaveAttribute("aria-expanded", "true");
            expect(rulesTrigger).toHaveAttribute("aria-expanded", "true");
        });
        expect(screen.getByText("Select at least one layer")).toBeVisible();
        expect(screen.getByRole("button", { name: "Remove condition" })).toBeVisible();
    });

    it("surfaces a field removed from the live definitions after zod validation", async () => {
        const user = userEvent.setup();
        act(() => useCustomAttributesStore.getState().actions.addAttribute("callsign"));

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        await user.type(screen.getByRole("textbox", { name: "Name" }), "Downtown Perimeter");
        await user.click(screen.getByRole("button", { name: "Layer With Source" }));
        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByText("callsign"));
        await user.type(screen.getByPlaceholderText("Enter a value"), "Falcon");

        const rulesTrigger = screen.getByRole("button", { name: "Rules" });
        await user.click(rulesTrigger);
        await waitFor(() => expect(rulesTrigger).toHaveAttribute("aria-expanded", "false"));

        act(() => useCustomAttributesStore.getState().actions.removeAttribute("callsign"));
        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        await waitFor(() => expect(rulesTrigger).toHaveAttribute("aria-expanded", "true"));
        expect(await screen.findByText("Select a valid field")).toBeVisible();
    });

    it("does not submit the form when Enter is pressed inside a condition's value field", async () => {
        const user = userEvent.setup();

        const { container } = render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });
        const onSubmit = vi.fn((e: Event) => e.preventDefault());
        container.querySelector("form")?.addEventListener("submit", onSubmit);

        // A bare <input> (the default EQUALS value field) submits its nearest
        // <form> on Enter unless something stops it — easy to trigger by
        // habit while finishing a condition's value, and destructive here
        // since a valid submit swaps the whole panel to the watchbox list.
        await user.type(screen.getByPlaceholderText("Enter a value"), "x{Enter}");

        expect(onSubmit).not.toHaveBeenCalled();

        // The explicit button still works — this isn't just a global no-op.
        await user.click(screen.getByRole("button", { name: "Create watchbox" }));
        expect(onSubmit).toHaveBeenCalledOnce();
    });

    it("keeps editing working after adding conditions following a failed submit", async () => {
        const user = userEvent.setup();

        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));
        await screen.findByText("Select at least one layer");

        await user.click(screen.getByRole("button", { name: "Add condition" }));
        await user.click(screen.getByRole("button", { name: "Add condition" }));

        const valueInputs = screen.getAllByPlaceholderText("Enter a value");
        await user.type(valueInputs[0], "x");

        expect(screen.getByRole("heading", { name: "NEW WATCHBOX" })).toBeInTheDocument();
        expect(valueInputs[0]).toHaveValue("x");
    });
});

describe("WatchboxForm — edit mode", () => {
    withPlugins(["opencop.symbology"]);

    const SECOND_LAYER: MyLayerInfo = {
        layerId: "layer-two",
        layerName: "Layer Two",
        role: "ADMIN",
        accessScope: "PRIVATE",
        defaultVisible: true,
    };

    const SAVED: Watchbox = {
        id: "wb-1",
        name: "Saved Watchbox",
        description: "Saved description",
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
        filter: {
            combine: "ALL",
            predicates: [
                { path: "properties.opencop.displayName", operation: "EQUALS", values: ["DOG"] },
            ],
        },
        layerIds: [LAYER_WITH_SOURCE.layerId],
        configEpoch: 1,
        alertingStatus: "ACTIVE",
        alertingReasons: [],
        sourceStatuses: [],
        capabilities: { canDelete: true, canPatch: true },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
    };

    /** Captures the PATCH body the form actually sends. */
    function capturePatch() {
        const bodies: Record<string, unknown>[] = [];
        server.use(
            http.patch(`${API_BASE}/watchboxes/${SAVED.id}`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json(SAVED);
            }),
            http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([SAVED])),
            http.get(`${API_BASE}/watchboxes/${SAVED.id}`, () => HttpResponse.json(SAVED)),
        );
        return bodies;
    }

    function renderEdit(watchbox: Watchbox = SAVED) {
        return render(<WatchboxForm watchbox={watchbox} />, {
            queryData: seed([LAYER_WITH_SOURCE, SECOND_LAYER], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [SECOND_LAYER.layerId]: 1,
            }),
        });
    }

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation(() => false);
    });

    it("keeps a removed-from-panel layer the watchbox already watches", () => {
        act(() => useFilterPanelStore.setState({ excludedIds: [LAYER_WITH_SOURCE.layerId] }));
        renderEdit();

        expect(screen.getByText("Layer With Source")).toBeInTheDocument();
    });

    it("seeds every field from the saved watchbox, rules included", () => {
        renderEdit();

        expect(screen.getByRole("textbox", { name: "Name" })).toHaveValue(SAVED.name);
        expect(screen.getByRole("textbox", { name: "Description" })).toHaveValue(SAVED.description);
        // A complete condition hydrates collapsed, as its summary sentence.
        expect(screen.getByText(/is equal to/)).toBeInTheDocument();
        expect(screen.getByText("DOG")).toBeInTheDocument();
    });

    it("sends only the field that changed", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit();

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toEqual({ name: "Renamed" });
    });

    // The area is drawn on the map. An unchanged polygon would still be
    // compared server-side, so it is never sent.
    it("never sends the polygon", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit();

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).not.toHaveProperty("polygon");
    });

    it("sends the enabled state when it is toggled", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit();

        await user.click(screen.getByRole("switch", { name: "Enabled" }));
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toEqual({ enabled: false });
    });

    it("sends layerIds when the selection changes", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit();

        // The combobox double accumulates a multi-select click onto the
        // current value, matching the real widget's multi-select semantics.
        await user.click(screen.getByRole("button", { name: SECOND_LAYER.layerName }));
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toEqual({
            layerIds: [LAYER_WITH_SOURCE.layerId, SECOND_LAYER.layerId],
        });
    });

    // Layer order carries no meaning. Sending a reorder would read
    // server-side as a dropped-then-readded set, which resets tracking.
    it("treats a reordered layer set as unchanged", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        const twoLayers = {
            ...SAVED,
            layerIds: [SECOND_LAYER.layerId, LAYER_WITH_SOURCE.layerId],
        };
        render(<WatchboxForm watchbox={{ ...twoLayers }} />, {
            queryData: seed([LAYER_WITH_SOURCE, SECOND_LAYER], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [SECOND_LAYER.layerId]: 1,
            }),
        });

        // Also rename, so a request is sent at all and the assertion stays
        // about layerIds rather than about the no-op short-circuit.
        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).not.toHaveProperty("layerIds");
    });

    // A saved null description renders as "", which is not an edit.
    it("does not send a description that only round-tripped null to empty", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit({ ...SAVED, description: null });

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).not.toHaveProperty("description");
    });

    it("sends a cleared description as null", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit();

        await user.clear(screen.getByRole("textbox", { name: "Description" }));
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toEqual({ description: null });
    });

    // An untouched form has nothing to send: the id travels in the URL, so the
    // request would be an empty body, costing a round trip and an audit row.
    it("sends no request at all when nothing was touched", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit();

        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(screen.getByRole("button", { name: "Save" })).toBeEnabled());
        expect(bodies).toEqual([]);
    });

    it("blocks the patch when validation fails", async () => {
        const user = userEvent.setup();
        const bodies = capturePatch();
        renderEdit();

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() =>
            expect(
                screen.getByText("Name must be between 1 and 64 characters"),
            ).toBeInTheDocument(),
        );
        expect(bodies).toHaveLength(0);
    });

    it("shows every ineligible source name in the update-error toast", async () => {
        const user = userEvent.setup();
        server.use(
            http.patch(`${API_BASE}/watchboxes/${SAVED.id}`, () =>
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
        const { container } = renderEdit();
        const announcement = container.querySelector('[role="alert"][aria-atomic="true"]');
        expect(announcement).toHaveTextContent("");

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith({
                type: "error",
                title: "Failed to update watchbox",
                description:
                    "A mapper with stable object IDs is required for each listed data source: Source A and Source B.",
            }),
        );
        expect(announcement).toHaveTextContent(
            "A mapper with stable object IDs is required for each listed data source: Source A and Source B.",
        );
    });
});

describe("WatchboxForm — edit mode with no eligible layers", () => {
    withPlugins(["opencop.symbology"]);

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation(() => false);
    });

    const SAVED_NO_ELIGIBLE: Watchbox = {
        id: "wb-2",
        name: "Still Editable",
        description: null,
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
        filter: { combine: "ALL", predicates: [] },
        layerIds: [LAYER_WITHOUT_SOURCE.layerId],
        configEpoch: 1,
        alertingStatus: "ACTIVE",
        alertingReasons: [],
        sourceStatuses: [],
        capabilities: { canDelete: true, canPatch: true },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
    };

    // Refusing to open is right for create and wrong for edit: the watchbox
    // already has its layers, and its name must stay fixable.
    it("still opens the editor when nothing is currently eligible", () => {
        render(<WatchboxForm watchbox={SAVED_NO_ELIGIBLE} />, {
            queryData: seed([LAYER_WITHOUT_SOURCE], { [LAYER_WITHOUT_SOURCE.layerId]: 0 }),
        });

        expect(screen.getByRole("textbox", { name: "Name" })).toHaveValue("Still Editable");
        expect(
            screen.queryByText(
                "You need to own or administer a layer that contains a data source.",
            ),
        ).not.toBeInTheDocument();
    });

    it("still refuses to create when nothing is eligible", () => {
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITHOUT_SOURCE], { [LAYER_WITHOUT_SOURCE.layerId]: 0 }),
        });

        expect(
            screen.getByText("You need to own or administer a layer that contains a data source."),
        ).toBeInTheDocument();
    });
});

describe("WatchboxForm — edit mode when the layer lookup fails", () => {
    withPlugins(["opencop.symbology"]);

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation(() => false);
        server.use(
            http.get(`${API_BASE}/me/layers`, () => HttpResponse.json(null, { status: 500 })),
        );
    });

    const SAVED: Watchbox = {
        id: "wb-3",
        name: "Editable Anyway",
        description: null,
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
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

    // No lookup means no names, so a saved layer has no item to match and the
    // combobox would otherwise render its raw UUID.
    it("labels a watched layer as unavailable rather than showing its id", async () => {
        render(<WatchboxForm watchbox={SAVED} />);

        await screen.findByText("Could not check layer eligibility.", {
            selector: '[role="alert"]',
        });
        expect(screen.getByText("Layer unavailable")).toBeInTheDocument();
        expect(screen.queryByText(LAYER_WITH_SOURCE.layerId)).not.toBeInTheDocument();
    });

    it("keeps the editor usable and says the picker failed", async () => {
        render(<WatchboxForm watchbox={SAVED} />);

        await screen.findByText("Could not check layer eligibility.", {
            selector: '[role="alert"]',
        });
        // The rest of the configuration is still editable.
        expect(screen.getByRole("textbox", { name: "Name" })).toHaveValue("Editable Anyway");
        expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
    });

    it("still blocks create behind the retry screen", async () => {
        render(<WatchboxForm polygon={POLYGON} />);

        await waitFor(() =>
            expect(screen.getByRole("button", { name: "Retry" })).toBeInTheDocument(),
        );
        expect(screen.queryByRole("textbox", { name: "Name" })).not.toBeInTheDocument();
    });
});

describe("WatchboxForm — edit mode with a saved layer outside the caller's candidates", () => {
    withPlugins(["opencop.symbology"]);

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation(() => false);
    });

    const SAVED: Watchbox = {
        id: "wb-4",
        name: "Has An Unmanaged Layer",
        description: null,
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
        filter: { combine: "ALL", predicates: [] },
        layerIds: ["layer-no-longer-managed"],
        configEpoch: 1,
        alertingStatus: "ACTIVE",
        alertingReasons: [],
        sourceStatuses: [],
        capabilities: { canDelete: true, canPatch: true },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
    };

    // The layer lookup itself succeeds here — unlike the describe block
    // above, there's no fetch failure. It just doesn't return this one saved
    // layer id (the caller's role on it changed, or someone else's layer was
    // attached before the caller took it over). Only that id should fall
    // back to the placeholder; a real candidate elsewhere in the response
    // should still render normally, and no lookup-failure messaging should
    // appear.
    it("labels only the missing saved layer as unavailable, without reporting a lookup failure", () => {
        render(<WatchboxForm watchbox={SAVED} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        expect(screen.getByText("Layer unavailable")).toBeInTheDocument();
        expect(screen.queryByText("layer-no-longer-managed")).not.toBeInTheDocument();
        expect(screen.queryByText("Could not check layer eligibility.")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Layer With Source" })).toBeInTheDocument();
    });

    // While the lookup is still in flight, every saved layer is unresolved,
    // not confirmed gone — "unavailable" would claim a conclusion the lookup
    // hasn't reached yet. Same held-open-promise pattern as the
    // checking-eligibility test above.
    it("labels a saved layer as loading (not unavailable) while the lookup is still in flight", async () => {
        mockUseSpinDelay.mockImplementation((loading: boolean) => loading);
        let settle: (layers: MyLayerInfo[]) => void = () => {};
        server.use(
            http.get(`${API_BASE}/me/layers`, async () => {
                const layers = await new Promise<MyLayerInfo[]>((resolve) => {
                    settle = resolve;
                });
                return HttpResponse.json(layers);
            }),
        );

        render(<WatchboxForm watchbox={SAVED} />, {
            // Seeds only the data-source sub-query, so once the held-open
            // layers list resolves below, layersLoading drops immediately
            // instead of chaining into a second, unmocked loading phase.
            queryData: {
                [JSON.stringify(layerQueries.dataSources(LAYER_WITH_SOURCE.layerId).queryKey)]: [
                    { serverId: "src-0" },
                ],
            },
        });

        expect(await screen.findByText("Loading...")).toBeInTheDocument();
        expect(screen.queryByText("Layer unavailable")).not.toBeInTheDocument();

        settle([LAYER_WITH_SOURCE]);
        await screen.findByText("Layer unavailable");
    });
});

describe("WatchboxForm — layer picker options", () => {
    withPlugins(["opencop.symbology"]);

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation(() => false);
    });

    const SECOND: MyLayerInfo = {
        layerId: "layer-two",
        layerName: "Second Layer",
        role: "OWNER",
        accessScope: "PRIVATE",
        defaultVisible: true,
    };

    // The selection already shows as a removable tag, so offering it again in
    // the dropdown is noise.
    it("stops offering a layer once it is selected", async () => {
        const user = userEvent.setup();
        render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE, SECOND], {
                [LAYER_WITH_SOURCE.layerId]: 1,
                [SECOND.layerId]: 1,
            }),
        });

        expect(
            screen.getByRole("button", { name: LAYER_WITH_SOURCE.layerName }),
        ).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: LAYER_WITH_SOURCE.layerName }));

        await waitFor(() =>
            expect(
                screen.queryByRole("button", { name: LAYER_WITH_SOURCE.layerName }),
            ).not.toBeInTheDocument(),
        );
        // The unselected one is still on offer.
        expect(screen.getByRole("button", { name: SECOND.layerName })).toBeInTheDocument();
    });
});

describe("WatchboxForm — a poll landing mid-edit", () => {
    withPlugins(["opencop.symbology"]);

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation(() => false);
    });

    const SAVED: Watchbox = {
        id: "wb-poll",
        name: "Original",
        description: null,
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
        // What the server actually returns for "no conditions"; it normalises
        // null away (WatchboxConfig's compact constructor).
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

    // The detail query polls every 30s and keeps this form mounted, so the prop
    // can change under an open editor. Diffing against the live value would
    // re-send an untouched field at its seeded value, reverting the other
    // writer. Everything must be measured against the seed instead.
    it("does not re-send a field another operator changed while the editor was open", async () => {
        const user = userEvent.setup();
        const bodies: Record<string, unknown>[] = [];
        server.use(
            http.patch(`${API_BASE}/watchboxes/${SAVED.id}`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json(SAVED);
            }),
            http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([SAVED])),
            http.get(`${API_BASE}/watchboxes/${SAVED.id}`, () => HttpResponse.json(SAVED)),
        );

        const { rerender } = render(<WatchboxForm watchbox={SAVED} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        // Someone else disables the watchbox; the poll delivers the new value.
        rerender(<WatchboxForm watchbox={{ ...SAVED, enabled: false }} />);

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed Only");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toEqual({ name: "Renamed Only" });
    });
});

// Characterization: pins what the rules editor does today, through what the operator
// sees, so the filter-field refactor can be checked against it. Nothing here names the
// filter model's internals.
describe("WatchboxForm — rules (characterization)", () => {
    withPlugins(["opencop.symbology"]);

    const SYMBOL_PATH = "properties.opencop.element.symbol";
    const PERSIST_KEY = "opencop:watchbox-filter";

    /** The fixed 2525D predicate and 2525C companion one Hostile condition saves as. */
    const HOSTILE_PAIR: NonNullable<Watchbox["filter"]>["predicates"] = [
        {
            path: SYMBOL_PATH,
            operation: "SUBSTRING_IN",
            values: ["6"],
            offset: 3,
            length: 1,
            negate: false,
            caseInsensitive: false,
        },
        {
            path: SYMBOL_PATH,
            operation: "SUBSTRING_IN",
            values: ["H", "K"],
            offset: 1,
            length: 1,
            negate: false,
            caseInsensitive: false,
        },
    ];

    function renderCreate() {
        return render(<WatchboxForm polygon={POLYGON} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });
    }

    async function fieldOptions(user: ReturnType<typeof userEvent.setup>, index = 0) {
        await user.click(screen.getAllByPlaceholderText("Select a field")[index]);
        const options = await screen.findAllByRole("option");
        return options.map((option) => option.textContent);
    }

    /** Replaces a row's field text with `name`. Pasted: typed keys race the combobox's
     *  re-renders in this DOM and arrive incomplete. */
    async function enterField(
        user: ReturnType<typeof userEvent.setup>,
        input: HTMLElement,
        name: string,
    ) {
        await user.clear(input);
        await user.paste(name);
    }

    afterEach(async () => {
        localStorage.removeItem(PERSIST_KEY);
        await i18n.changeLanguage("en");
    });

    it("offers the built-in fields in a fixed order", async () => {
        const user = userEvent.setup();
        renderCreate();

        expect(await fieldOptions(user)).toEqual([
            "Kind",
            "Display Name",
            "SIDC",
            "SIDC: Standard Identity",
            "SIDC: Symbol Set",
        ]);
    });

    it("creates a custom attribute from what is typed, keeps it across forms, and removes it", async () => {
        const user = userEvent.setup();
        renderCreate();

        await enterField(user, screen.getByPlaceholderText("Select a field"), "callsign");
        await user.click(await screen.findByRole("option", { name: 'Create "callsign"' }));

        expect(screen.getByPlaceholderText("Select a field")).toHaveValue("callsign");
        expect(localStorage.getItem(PERSIST_KEY)).toContain(
            "properties.opencop.attributes.callsign",
        );

        await user.click(screen.getByRole("button", { name: "Add condition" }));
        expect(await fieldOptions(user, 1)).toEqual([
            "Kind",
            "Display Name",
            "SIDC",
            "SIDC: Standard Identity",
            "SIDC: Symbol Set",
            "callsign",
        ]);

        await user.click(screen.getByRole("button", { name: 'Remove "callsign"' }));

        await waitFor(() =>
            expect(screen.queryByRole("option", { name: "callsign" })).not.toBeInTheDocument(),
        );
        expect(localStorage.getItem(PERSIST_KEY) ?? "").not.toContain("callsign");
    });

    it("saves a symbol condition as its 2525D predicate and 2525C companion, and a custom attribute by its path", async () => {
        const user = userEvent.setup();
        const bodies: Record<string, unknown>[] = [];
        server.use(
            http.post(`${API_BASE}/watchboxes`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json({ id: "wb-new" });
            }),
        );
        renderCreate();

        await user.type(screen.getByRole("textbox", { name: "Name" }), "Harbor Watch");
        await user.click(screen.getByRole("button", { name: "Layer With Source" }));

        await user.click(screen.getByPlaceholderText("Select a field"));
        await user.click(await screen.findByRole("option", { name: "SIDC: Standard Identity" }));
        await user.click(screen.getByPlaceholderText("Select a value"));
        await user.click(await screen.findByRole("option", { name: /Hostile\/Faker/ }));

        await user.click(screen.getByRole("button", { name: "Add condition" }));
        const fieldInputs = screen.getAllByPlaceholderText("Select a field");
        await enterField(user, fieldInputs[fieldInputs.length - 1], "callsign");
        await user.click(await screen.findByRole("option", { name: 'Create "callsign"' }));
        await user.type(screen.getByPlaceholderText("Enter a value"), "Falcon");

        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]?.filter).toEqual({
            // Picking a symbol field switches the combinator to Any.
            combine: "ANY",
            predicates: [
                ...HOSTILE_PAIR,
                {
                    path: "properties.opencop.attributes.callsign",
                    operation: "EQUALS",
                    values: ["Falcon"],
                    negate: false,
                    caseInsensitive: false,
                },
            ],
        });
    });

    it("reopens a saved symbol condition as the one row the operator built", () => {
        const saved: Watchbox = {
            id: "wb-symbol",
            name: "Harbor Watch",
            description: null,
            enabled: true,
            direction: "BOTH",
            polygon: POLYGON,
            filter: { combine: "ANY", predicates: [...HOSTILE_PAIR] },
            layerIds: [LAYER_WITH_SOURCE.layerId],
            configEpoch: 1,
            alertingStatus: "ACTIVE",
            alertingReasons: [],
            sourceStatuses: [],
            capabilities: { canDelete: true, canPatch: true },
            createdAt: "2026-01-01T00:00:00Z",
            updatedAt: "2026-01-01T00:00:00Z",
        };
        mockUseSpinDelay.mockImplementation(() => false);

        render(<WatchboxForm watchbox={saved} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        expect(screen.getAllByRole("button", { name: "Remove condition" })).toHaveLength(1);
        expect(screen.getByText("SIDC: Standard Identity")).toBeInTheDocument();
        expect(screen.getByText("Hostile/Faker")).toBeInTheDocument();
        expect(screen.queryByText(/between characters/)).not.toBeInTheDocument();
    });

    it("labels the symbol fields and their values in the operator's language", async () => {
        const user = userEvent.setup();
        await i18n.changeLanguage("fr");
        renderCreate();

        await user.click(screen.getByPlaceholderText("Sélectionner un champ"));
        await user.click(await screen.findByRole("option", { name: "SIDC: Identité standard" }));
        await user.click(screen.getByPlaceholderText("Sélectionner une valeur"));

        expect(await screen.findByRole("option", { name: /Ami présumé/ })).toBeInTheDocument();
    });
});

// With the symbology plugin absent, a saved symbol rule has no field: its predicates read
// as fallback rows, kept verbatim.
describe("WatchboxForm — rules whose field is unavailable", () => {
    const SAVED: Watchbox = {
        id: "wb-fallback",
        name: "Harbor Watch",
        description: null,
        enabled: true,
        direction: "BOTH",
        polygon: POLYGON,
        filter: {
            combine: "ANY",
            predicates: [
                {
                    path: "properties.opencop.element.symbol",
                    operation: "SUBSTRING_IN",
                    values: ["6"],
                    offset: 3,
                    length: 1,
                    negate: false,
                    caseInsensitive: false,
                },
                {
                    path: "properties.opencop.element.symbol",
                    operation: "SUBSTRING_IN",
                    values: ["H", "K"],
                    offset: 1,
                    length: 1,
                    negate: false,
                    caseInsensitive: false,
                },
            ],
        },
        layerIds: [LAYER_WITH_SOURCE.layerId],
        configEpoch: 1,
        alertingStatus: "ACTIVE",
        alertingReasons: [],
        sourceStatuses: [],
        capabilities: { canDelete: true, canPatch: true },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
    };

    function renderEdit() {
        mockUseSpinDelay.mockImplementation(() => false);
        const bodies: Record<string, unknown>[] = [];
        server.use(
            http.patch(`${API_BASE}/watchboxes/${SAVED.id}`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json(SAVED);
            }),
        );
        render(<WatchboxForm watchbox={SAVED} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });
        return bodies;
    }

    it("shows each predicate as a condition that can be deleted but not edited", () => {
        renderEdit();

        expect(screen.getAllByText(/its field isn't available/)).toHaveLength(2);
        expect(screen.queryByRole("button", { name: "Expand condition" })).not.toBeInTheDocument();
        expect(screen.getAllByRole("button", { name: "Remove condition" })).toHaveLength(2);
    });

    it("sends no filter change when the rest of the watchbox is saved", async () => {
        const user = userEvent.setup();
        const bodies = renderEdit();

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toEqual({ name: "Renamed" });
    });

    it("sends the filter without exactly the deleted predicate", async () => {
        const user = userEvent.setup();
        const bodies = renderEdit();

        await user.click(screen.getAllByRole("button", { name: "Remove condition" })[1]!);
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]?.filter).toEqual({
            combine: "ANY",
            predicates: [SAVED.filter.predicates[0]],
        });
    });
});

// Plugins load after the app starts. A form opened before symbology registers seeds its
// rows without the SIDC fields; saving it untouched must still send no filter change.
describe("WatchboxForm — a field that registers while the editor is open", () => {
    it("sends no filter change when only the name is edited", async () => {
        mockUseSpinDelay.mockImplementation(() => false);
        const user = userEvent.setup();
        // As the server returns it: unset flags come back as null.
        const saved = {
            id: "wb-race",
            name: "Harbor Watch",
            description: null,
            enabled: true,
            direction: "BOTH",
            polygon: POLYGON,
            filter: {
                combine: "ANY",
                predicates: [
                    {
                        path: "properties.opencop.element.symbol",
                        operation: "EQUALS",
                        values: ["SFGPU----------"],
                        offset: null,
                        length: null,
                        negate: false,
                        caseInsensitive: false,
                    },
                ],
            },
            layerIds: [LAYER_WITH_SOURCE.layerId],
            configEpoch: 1,
            alertingStatus: "ACTIVE",
            alertingReasons: [],
            sourceStatuses: [],
            capabilities: { canDelete: true, canPatch: true },
            createdAt: "2026-01-01T00:00:00Z",
            updatedAt: "2026-01-01T00:00:00Z",
        } as unknown as Watchbox;
        const bodies: Record<string, unknown>[] = [];
        server.use(
            http.patch(`${API_BASE}/watchboxes/${saved.id}`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json(saved);
            }),
        );
        render(<WatchboxForm watchbox={saved} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        act(() => {
            onTestFinished(installPlugins(["opencop.symbology"]));
        });
        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toHaveLength(1));
        expect(bodies[0]).toEqual({ name: "Renamed" });
    });
});

describe("WatchboxForm — submit validation", () => {
    const BOWTIE = {
        type: "Polygon" as const,
        coordinates: [
            [
                [0, 0],
                [1, 1],
                [1, 0],
                [0, 1],
                [0, 0],
            ],
        ],
    };

    beforeEach(() => {
        mockUseSpinDelay.mockImplementation(() => false);
    });

    it("toasts why a self-crossing area can't be saved, and sends nothing", async () => {
        const user = userEvent.setup();
        const bodies: unknown[] = [];
        server.use(
            http.post(`${API_BASE}/watchboxes`, async ({ request }) => {
                bodies.push(await request.json());
                return HttpResponse.json({});
            }),
        );
        render(<WatchboxForm polygon={BOWTIE} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        await user.type(screen.getByRole("textbox", { name: "Name" }), "Crossed");
        await user.click(screen.getByRole("button", { name: LAYER_WITH_SOURCE.layerName }));
        await user.click(screen.getByRole("button", { name: "Create watchbox" }));

        await waitFor(() =>
            expect(showToast).toHaveBeenCalledWith({
                type: "error",
                title: "Failed to create watchbox",
                description: "The area crosses itself — redraw it without overlaps",
            }),
        );
        expect(bodies).toEqual([]);
    });

    it("states the layer limit when more than 100 layers are selected", async () => {
        const user = userEvent.setup();
        const layers: MyLayerInfo[] = Array.from({ length: 101 }, (_, i) => ({
            ...LAYER_WITH_SOURCE,
            layerId: `layer-${i}`,
            layerName: `Layer ${i}`,
        }));
        // Starts from 101 saved layers: selecting that many by click is too slow to be a test.
        const saved: Watchbox = {
            id: "wb-many",
            name: "Many",
            description: null,
            enabled: true,
            direction: "BOTH",
            polygon: POLYGON,
            filter: { combine: "ALL", predicates: [] },
            layerIds: layers.map((layer) => layer.layerId),
            configEpoch: 1,
            alertingStatus: "ACTIVE",
            alertingReasons: [],
            sourceStatuses: [],
            capabilities: { canDelete: true, canPatch: true },
            createdAt: "2026-01-01T00:00:00Z",
            updatedAt: "2026-01-01T00:00:00Z",
        };
        render(<WatchboxForm watchbox={saved} />, {
            queryData: seed(layers, Object.fromEntries(layers.map((layer) => [layer.layerId, 1]))),
        });

        await user.type(screen.getByRole("textbox", { name: "Name" }), " renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        expect(
            await screen.findByText("A watchbox can reference at most 100 layers"),
        ).toBeInTheDocument();
    });

    it("doesn't block editing a saved watchbox over its stored area", async () => {
        const user = userEvent.setup();
        const bodies: Record<string, unknown>[] = [];
        const saved: Watchbox = {
            id: "wb-crossed",
            name: "Legacy",
            description: null,
            enabled: true,
            direction: "BOTH",
            polygon: BOWTIE,
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
        server.use(
            http.patch(`${API_BASE}/watchboxes/${saved.id}`, async ({ request }) => {
                bodies.push((await request.json()) as Record<string, unknown>);
                return HttpResponse.json(saved);
            }),
            http.get(`${API_BASE}/watchboxes`, () => HttpResponse.json([saved])),
            http.get(`${API_BASE}/watchboxes/${saved.id}`, () => HttpResponse.json(saved)),
        );
        render(<WatchboxForm watchbox={saved} />, {
            queryData: seed([LAYER_WITH_SOURCE], { [LAYER_WITH_SOURCE.layerId]: 1 }),
        });

        await user.clear(screen.getByRole("textbox", { name: "Name" }));
        await user.type(screen.getByRole("textbox", { name: "Name" }), "Renamed");
        await user.click(screen.getByRole("button", { name: "Save" }));

        await waitFor(() => expect(bodies).toEqual([{ name: "Renamed" }]));
    });
});
