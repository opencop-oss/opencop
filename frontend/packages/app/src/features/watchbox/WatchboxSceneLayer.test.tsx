// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { render } from "~/test";
import { WatchboxSceneLayer } from "./WatchboxSceneLayer";

const mocks = vi.hoisted(() => ({
    enabled: { value: true },
    mode: { value: "live" as "live" | "historical" },
    scene: {},
    sink: {},
    /** Each lifecycle step, in the order it ran. */
    calls: [] as string[],
    pipelineArgs: [] as unknown[],
}));

vi.mock("~/features/admin/runtime-features/use-feature-flag.hook", () => ({
    useBackendFeatureEnabled: () => mocks.enabled.value,
}));

vi.mock("~/features/historical/data-plane/historical.store", () => ({
    useHistoricalMode: () => mocks.mode.value,
}));

// Partial: only the scene handle is stubbed. A total mock drops whatever else
// the module graph pulls from the package — constants a mapper measures text
// with, say — and fails on import rather than on anything this file asserts.
vi.mock("@opencop/scene", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@opencop/scene")>()),
    useScene: () => ({ scene: mocks.scene }),
}));

vi.mock("~/features/map/scene-binding/SceneBindingContext", () => ({
    useSceneBinding: () => ({ sink: mocks.sink }),
}));

vi.mock("./watchbox.store", () => ({
    createWatchboxStore: () => ({
        connect: () => {
            mocks.calls.push("connect");
            return () => mocks.calls.push("disconnect");
        },
    }),
}));

vi.mock("./watchbox-draft", () => ({
    followWatchboxDraft: () => {
        mocks.calls.push("followDraft");
        return () => mocks.calls.push("stopDraft");
    },
}));

vi.mock("./WatchboxPipeline", () => ({
    WatchboxPipeline: class {
        constructor(args: unknown) {
            mocks.pipelineArgs.push(args);
        }
        initialize() {
            mocks.calls.push("initialize");
        }
        erase() {
            mocks.calls.push("erase");
        }
        dispose() {
            mocks.calls.push("dispose");
        }
    },
}));

afterEach(() => {
    mocks.enabled.value = true;
    mocks.mode.value = "live";
    mocks.calls.length = 0;
    mocks.pipelineArgs.length = 0;
});

describe("WatchboxSceneLayer", () => {
    it("draws the watchboxes through the scene and the sink, following the draft", () => {
        render(<WatchboxSceneLayer />);

        expect(mocks.calls).toEqual(["connect", "initialize", "followDraft"]);
        expect(mocks.pipelineArgs).toEqual([
            expect.objectContaining({ scene: mocks.scene, sink: mocks.sink }),
        ]);
    });

    it("tears down in reverse on unmount", () => {
        const { unmount } = render(<WatchboxSceneLayer />);
        mocks.calls.length = 0;

        unmount();

        expect(mocks.calls).toEqual(["stopDraft", "erase", "dispose", "disconnect"]);
    });

    it("draws nothing while the backend does not offer watchboxes", () => {
        mocks.enabled.value = false;

        render(<WatchboxSceneLayer />);

        expect(mocks.calls).toEqual([]);
    });

    it("draws nothing in historical mode", () => {
        mocks.mode.value = "historical";

        render(<WatchboxSceneLayer />);

        expect(mocks.calls).toEqual([]);
    });

    it("tears down on entering historical mode, and draws again on returning live", () => {
        const { rerender } = render(<WatchboxSceneLayer />);
        mocks.calls.length = 0;

        mocks.mode.value = "historical";
        rerender(<WatchboxSceneLayer />);
        expect(mocks.calls).toEqual(["stopDraft", "erase", "dispose", "disconnect"]);

        mocks.calls.length = 0;
        mocks.mode.value = "live";
        rerender(<WatchboxSceneLayer />);
        expect(mocks.calls).toEqual(["connect", "initialize", "followDraft"]);
    });
});
