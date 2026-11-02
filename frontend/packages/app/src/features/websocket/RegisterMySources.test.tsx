// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { render } from "~/test";

type LayerInfo = { layerId: string; defaultVisible: boolean };
type DataSourceInfo = { serverId: string; defaultVisible: boolean };
type QueryState<T> = {
    data: T;
    isSuccess: boolean;
    isError: boolean;
};

const mockAdd = vi.fn();
const mockDisable = vi.fn();
const mockRegisterSourceIds = vi.fn<(ids: string[]) => void>();
let mockHistoricalMode: "live" | "historical" = "live";
let mockLayerQuery: QueryState<LayerInfo[]> = {
    data: [],
    isSuccess: true,
    isError: false,
};
let mockDataSourceQuery: QueryState<DataSourceInfo[]> = {
    data: [],
    isSuccess: true,
    isError: false,
};
const mockInitNewSources = vi.fn();
const mockRemoveKnownSources = vi.fn();
let mockKnownSources: string[] = [];
let mockExcludedIds: string[] = [];
let mockPinnedSources: string[] = [];

vi.mock("./WebSocketContext", async (importOriginal) => ({
    ...(await importOriginal<typeof import("./WebSocketContext")>()),
    useWebSocket: () => ({ client: { subscribe: vi.fn(), unsubscribe: vi.fn() } }),
}));

vi.mock("../historical/data-plane/historical.store", () => ({
    useHistoricalMode: () => mockHistoricalMode,
}));

vi.mock("@tanstack/react-query", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@tanstack/react-query")>()),
    useQuery: ({ queryKey }: { queryKey: readonly unknown[] }) => {
        if (queryKey[0] === "layers") return mockLayerQuery;
        if (queryKey[0] === "dataSources") return mockDataSourceQuery;
        return { data: [], isSuccess: true, isError: false };
    },
}));

vi.mock("../layers/layers.queries", () => ({
    layerQueries: { myList: () => ({ queryKey: ["layers", "my"], queryFn: vi.fn() }) },
}));

vi.mock("../data-sources/data-sources.queries", () => ({
    dataSourceQueries: { myList: () => ({ queryKey: ["dataSources", "my"], queryFn: vi.fn() }) },
}));

vi.mock("@opencop/ingest", () => ({
    useElementActions: () => ({
        add: mockAdd,
    }),
    useSourceActions: () => ({ disable: mockDisable }),
    // The local source is part of the desired set, so it shows up in the
    // reconciled ids alongside the WebSocket sources.
    useLocalSource: () => ({ id: "local" }),
    useContributeSources: (_key: string, sources: Iterable<{ id: string }>) => {
        mockRegisterSourceIds(Array.from(sources, (source) => source.id));
    },
    destinationPrefix: "/topic/queue/event/",
    WebSocketSource: class {
        id: string;

        constructor(id: string) {
            this.id = id;
        }

        bind() {
            return this;
        }
    },
}));

vi.mock("../filters/filter-panel.store", () => ({
    useFilterPanelStore: {
        getState: () => ({
            knownSources: mockKnownSources,
            excludedIds: mockExcludedIds,
            pinnedSources: mockPinnedSources,
            initNewSources: mockInitNewSources,
            removeKnownSources: mockRemoveKnownSources,
        }),
    },
}));

import { RegisterMySources } from "./RegisterMySources";

afterEach(() => {
    vi.clearAllMocks();
    mockHistoricalMode = "live";
    mockLayerQuery = { data: [], isSuccess: true, isError: false };
    mockDataSourceQuery = { data: [], isSuccess: true, isError: false };
    mockKnownSources = [];
    mockExcludedIds = [];
    mockPinnedSources = [];
});

describe("RegisterMySources — source registration", () => {
    it("reconciles to no live sources in historical mode", () => {
        mockHistoricalMode = "historical";
        mockLayerQuery = {
            data: [{ layerId: "layer-1", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };
        mockDataSourceQuery = {
            data: [{ serverId: "datasource-1", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockRegisterSourceIds).toHaveBeenLastCalledWith([]);
    });

    it("updates the desired source set only after both queries succeed", () => {
        mockLayerQuery = {
            data: [{ layerId: "layer-1", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };
        mockDataSourceQuery = {
            data: [],
            isSuccess: false,
            isError: false,
        };

        const { rerender } = render(<RegisterMySources />);
        // Live mode always wants the local source, even before the live queries resolve.
        expect(mockRegisterSourceIds).toHaveBeenLastCalledWith(["local"]);

        mockDataSourceQuery = {
            data: [{ serverId: "datasource-1", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };
        rerender(<RegisterMySources />);

        expect(mockRegisterSourceIds).toHaveBeenLastCalledWith([
            "layer-1",
            "datasource-1",
            "local",
        ]);
    });

    it("keeps the last successful ids during query errors instead of shrinking", () => {
        mockLayerQuery = {
            data: [{ layerId: "layer-1", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };
        mockDataSourceQuery = {
            data: [{ serverId: "datasource-1", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };

        const { rerender } = render(<RegisterMySources />);
        expect(mockRegisterSourceIds).toHaveBeenLastCalledWith([
            "layer-1",
            "datasource-1",
            "local",
        ]);

        mockLayerQuery = {
            data: [],
            isSuccess: false,
            isError: true,
        };
        rerender(<RegisterMySources />);

        expect(mockRegisterSourceIds).toHaveBeenLastCalledWith([
            "layer-1",
            "datasource-1",
            "local",
        ]);
    });
});

describe("RegisterMySources — init visibility effect", () => {
    it("hides a defaultVisible:false source by disabling its subscription", () => {
        mockLayerQuery = {
            data: [{ layerId: "layer-1", defaultVisible: false }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockInitNewSources).toHaveBeenCalledWith([{ id: "layer-1", defaultVisible: false }]);
        expect(mockDisable).toHaveBeenCalledWith("layer-1");
    });

    it("leaves a defaultVisible:true source visible and does not disable it", () => {
        mockLayerQuery = {
            data: [{ layerId: "layer-2", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockInitNewSources).toHaveBeenCalledWith([{ id: "layer-2", defaultVisible: true }]);
        expect(mockDisable).not.toHaveBeenCalled();
    });

    it("skips visibility initialization until both queries succeed", () => {
        mockLayerQuery = {
            data: [{ layerId: "layer-1", defaultVisible: false }],
            isSuccess: true,
            isError: false,
        };
        mockDataSourceQuery = {
            data: [],
            isSuccess: false,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockInitNewSources).not.toHaveBeenCalled();
        expect(mockDisable).not.toHaveBeenCalled();
    });

    it("skips a source already in knownSources on refetch", () => {
        mockKnownSources = ["layer-1"];
        mockLayerQuery = {
            data: [{ layerId: "layer-1", defaultVisible: false }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockInitNewSources).not.toHaveBeenCalled();
        expect(mockDisable).not.toHaveBeenCalled();
    });

    it("does not run in historical mode", () => {
        mockHistoricalMode = "historical";
        mockLayerQuery = {
            data: [{ layerId: "layer-3", defaultVisible: false }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockInitNewSources).not.toHaveBeenCalled();
        expect(mockDisable).not.toHaveBeenCalled();
    });

    it("reconciles stale ids removed from the server response", () => {
        mockKnownSources = ["layer-stale", "layer-current"];
        mockLayerQuery = {
            data: [{ layerId: "layer-current", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockRemoveKnownSources).toHaveBeenCalledWith(["layer-stale"]);
    });
});

describe("RegisterMySources — excluded id pruning", () => {
    it("prunes excluded ids missing from the resolved lists", () => {
        mockExcludedIds = ["gone", "kept"];
        mockLayerQuery = {
            data: [{ layerId: "kept", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockRemoveKnownSources).toHaveBeenCalledWith(["gone"]);
    });

    it("prunes pinned ids missing from the resolved lists", () => {
        mockPinnedSources = ["gone", "kept"];
        mockLayerQuery = {
            data: [{ layerId: "kept", defaultVisible: true }],
            isSuccess: true,
            isError: false,
        };

        render(<RegisterMySources />);

        expect(mockRemoveKnownSources).toHaveBeenCalledWith(["gone"]);
    });

    it("prunes even when both resolved lists are empty", () => {
        mockExcludedIds = ["gone"];

        render(<RegisterMySources />);

        expect(mockRemoveKnownSources).toHaveBeenCalledWith(["gone"]);
        expect(mockInitNewSources).not.toHaveBeenCalled();
    });

    it("does not prune while the lists have not resolved", () => {
        mockExcludedIds = ["gone"];
        mockLayerQuery = { data: [], isSuccess: false, isError: false };

        render(<RegisterMySources />);

        expect(mockRemoveKnownSources).not.toHaveBeenCalled();
    });
});
