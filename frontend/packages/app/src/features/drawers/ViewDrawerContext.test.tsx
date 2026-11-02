// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useState } from "react";
import { render, screen, userEvent } from "~/test";
import type { ObservedLocation } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { ViewDrawerProvider, useViewDrawerContext } from "./ViewDrawerContext";

type TestModal = { type: "none" } | { type: "confirm" };

const INITIAL_MODAL: TestModal = { type: "none" };
const CONFIRM_MODAL: TestModal = { type: "confirm" };
const DRAWER_ID = "ds-1";
const DRAWER_TYPE = "dataSource";
const NONE = "none";

// Button labels doubling as accessible names (kept out of JSX for the i18n-literal lint rule).
const OPEN = "open";
const OPEN_MODAL = "open-modal";
const CLOSE = "close";
const BUMP = "bump";

/** Minimal consumer that surfaces the context state and drives its actions via buttons. */
function Harness() {
    const { selectedId, selectedType, openDrawer, closeDrawer, modal, setModal } =
        useViewDrawerContext<TestModal>();
    return (
        <div>
            <div data-testid="selection">{`${selectedId ?? NONE}:${selectedType ?? NONE}`}</div>
            <div data-testid="modal">{modal.type}</div>
            <button onClick={() => openDrawer(DRAWER_ID, DRAWER_TYPE)}>{OPEN}</button>
            <button onClick={() => setModal(CONFIRM_MODAL)}>{OPEN_MODAL}</button>
            <button onClick={() => closeDrawer()}>{CLOSE}</button>
        </div>
    );
}

function renderInProvider(onLocationChange?: (loc: ObservedLocation) => void) {
    return render(
        <ViewDrawerProvider<TestModal> initialModal={INITIAL_MODAL}>
            <Harness />
        </ViewDrawerProvider>,
        onLocationChange ? { onLocationChange } : undefined,
    );
}

describe("ViewDrawerProvider / useViewDrawerContext", () => {
    it("round-trips the selected drawer through the URL params", async () => {
        const user = userEvent.setup();
        let location: ObservedLocation | undefined;
        renderInProvider((loc) => {
            location = loc;
        });

        expect(screen.getByTestId("selection")).toHaveTextContent("none:none");

        await user.click(screen.getByRole("button", { name: OPEN }));
        expect(screen.getByTestId("selection")).toHaveTextContent("ds-1:dataSource");
        expect(location?.search).toContain("id=ds-1");
        expect(location?.search).toContain("type=dataSource");

        await user.click(screen.getByRole("button", { name: CLOSE }));
        expect(screen.getByTestId("selection")).toHaveTextContent("none:none");
        expect(location?.search ?? "").not.toContain("id=");
    });

    it("resets the modal on close so a view-level dialog isn't orphaned", async () => {
        const user = userEvent.setup();
        renderInProvider();

        await user.click(screen.getByRole("button", { name: OPEN_MODAL }));
        expect(screen.getByTestId("modal")).toHaveTextContent("confirm");

        // Regression guard: closing the drawer must also clear the modal.
        await user.click(screen.getByRole("button", { name: CLOSE }));
        expect(screen.getByTestId("modal")).toHaveTextContent("none");
    });

    it("throws when used outside a ViewDrawerProvider", () => {
        // React logs the render error to console.error; silence it for a clean test run.
        const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
        expect(() => render(<Harness />)).toThrow(
            "useViewDrawerContext must be used within ViewDrawerProvider",
        );
        errorSpy.mockRestore();
    });
    // Call sites pass `initialModal` inline, so a prop-dependent `closeDrawer` would get a fresh
    // identity each render and defeat the context memo. The seed is frozen at mount instead.
    it("keeps closeDrawer stable across renders when initialModal is an inline literal", async () => {
        const user = userEvent.setup();
        const identities = new Set<() => void>();

        function IdentityProbe() {
            const { closeDrawer } = useViewDrawerContext<TestModal>();
            identities.add(closeDrawer);
            return null;
        }

        function Host() {
            const [, setTick] = useState(0);
            return (
                <ViewDrawerProvider<TestModal> initialModal={{ type: "none" }}>
                    <button onClick={() => setTick((n) => n + 1)}>{BUMP}</button>
                    <IdentityProbe />
                </ViewDrawerProvider>
            );
        }

        render(<Host />);
        await user.click(screen.getByRole("button", { name: BUMP }));
        await user.click(screen.getByRole("button", { name: BUMP }));

        expect(identities.size).toBe(1);
    });
});
