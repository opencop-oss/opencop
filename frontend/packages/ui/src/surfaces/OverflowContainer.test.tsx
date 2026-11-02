// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import { I18nextProvider } from "react-i18next";
import { afterEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OverflowContainer } from "./OverflowContainer";

const ITEM_WIDTH = 40;
const ITEMS = ["a", "b", "c", "d", "e"];

function stubLayout(containerWidth: number) {
    class ResizeObserverStub implements ResizeObserver {
        private readonly callback: ResizeObserverCallback;
        constructor(callback: ResizeObserverCallback) {
            this.callback = callback;
        }
        observe() {
            this.callback(
                [{ contentRect: { width: containerWidth } } as ResizeObserverEntry],
                this,
            );
        }
        unobserve() {}
        disconnect() {}
    }
    vi.stubGlobal("ResizeObserver", ResizeObserverStub);
    vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
        width: ITEM_WIDTH,
    } as DOMRect);
}

function renderRow(pinned?: string) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OverflowContainer
                    items={ITEMS}
                    pinned={pinned}
                    renderItem={(item) => (
                        <button key={item} type="button">
                            {item}
                        </button>
                    )}
                    renderOverflow={(hidden) => <span>more {hidden.join(",")}</span>}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

function visibleButtons() {
    return screen
        .queryAllByRole("button")
        .map((button) => button.textContent)
        .sort();
}

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("OverflowContainer", () => {
    it("folds trailing items into the overflow when only some fit", () => {
        stubLayout(140);
        renderRow();

        expect(visibleButtons()).toEqual(["a", "b"]);
        expect(screen.getByText("more c,d,e")).toBeInTheDocument();
    });

    it("keeps the pinned item on the row and folds one other item in its place", () => {
        stubLayout(140);
        renderRow("e");

        expect(visibleButtons()).toEqual(["a", "e"]);
        expect(screen.getByText("more b,c,d")).toBeInTheDocument();
    });

    it("leaves a visible pinned item where it is", () => {
        stubLayout(140);
        renderRow("a");

        expect(visibleButtons()).toEqual(["a", "b"]);
        expect(screen.getByText("more c,d,e")).toBeInTheDocument();
    });

    it("still shows the pinned item when nothing else fits beside the trigger", () => {
        stubLayout(60);
        renderRow("e");

        expect(visibleButtons()).toEqual(["e"]);
        expect(screen.getByText("more a,b,c,d")).toBeInTheDocument();
    });
});
