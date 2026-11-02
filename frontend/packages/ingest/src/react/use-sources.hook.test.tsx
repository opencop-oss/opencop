// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { renderHook } from "@testing-library/react";
import type { PropsWithChildren } from "react";
import { describe, expect, it } from "vitest";
import { IngestProvider } from "./IngestContext";
import { useContributeSources } from "./use-sources.hook";
import { Source } from "../sources/Source";

// Enable every contributed source, mirroring the real policy: sources are
// enabled on registration, so eviction of a still-enabled source runs onDisable.
function Wrapper({ children }: PropsWithChildren) {
    return (
        <IngestProvider policy={{ shouldEnableOnRegister: () => true }}>{children}</IngestProvider>
    );
}

class FakeSource extends Source {
    startCalls = 0;
    stopCalls = 0;
    protected onEnable(): void {
        this.startCalls++;
        this.setState("enabled");
    }
    protected onDisable(): void {
        this.stopCalls++;
    }
}

describe("useContributeSources", () => {
    it("does NOT evict a source that stays in the slice when the array identity changes", () => {
        // The whole point of `reconcile`'s id-diffing: a source still present in
        // the new desired set must be kept, not torn down and re-admitted. Both
        // real call sites rebuild the entire `Source[]` (fresh identities) on any
        // change, so a per-`sources`-change withdrawal would purge every source
        // — including unchanged ones — on every selection change.
        const s1 = new FakeSource("s1");

        const { rerender } = renderHook(
            ({ sources }: { sources: Source[] }) => useContributeSources("test", sources),
            { wrapper: Wrapper, initialProps: { sources: [s1] as Source[] } },
        );

        // A new array identity that still contains s1 (plus a new s2).
        rerender({ sources: [s1, new FakeSource("s2")] });

        // s1 was continuously wanted — it must never have been disabled/evicted.
        expect(s1.stopCalls).toBe(0);
    });

    it("withdraws the whole slice on unmount", () => {
        const s1 = new FakeSource("s1");

        const { unmount } = renderHook(() => useContributeSources("test", [s1] as Source[]), {
            wrapper: Wrapper,
        });

        expect(s1.stopCalls).toBe(0);
        unmount();
        expect(s1.stopCalls).toBe(1);
    });
});
