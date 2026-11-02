// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "~/test";
import { CopyButton } from "./CopyButton.tsx";

const COPIED_ICON = "copied-icon";
const COPY_ICON = "copy-icon";
const VALUE_LABEL = "Copy to clipboard: 12.5 km";
const COPIED_TEXT = "Copied";

// Partial: the harness loads other modules that draw icons from this package.
vi.mock("react-icons/md", async (importOriginal) => ({
    ...(await importOriginal<typeof import("react-icons/md")>()),
    MdCheck: () => <span>{COPIED_ICON}</span>,
    MdContentCopy: () => <span>{COPY_ICON}</span>,
}));

const writeText = vi.fn<(text: string) => Promise<void>>();

beforeEach(() => {
    vi.useFakeTimers();
    writeText.mockReset().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
        value: { writeText },
        configurable: true,
    });
});

afterEach(() => {
    vi.useRealTimers();
});

/** Clicks the button and lets the clipboard promise settle. */
async function click(name: string | RegExp = "Copy to clipboard") {
    await act(async () => {
        fireEvent.click(screen.getByRole("button", { name }));
    });
}

describe("CopyButton", () => {
    it("copies its value and shows the copied icon", async () => {
        render(<CopyButton value="abc-123" />);

        await click();

        expect(writeText).toHaveBeenCalledWith("abc-123");
        expect(screen.getByText(COPIED_ICON)).toBeTruthy();
    });

    it("goes back to the copy icon after two seconds", async () => {
        render(<CopyButton value="abc-123" />);
        await click();

        act(() => vi.advanceTimersByTime(2000));

        expect(screen.getByText(COPY_ICON)).toBeTruthy();
    });

    it("restarts the two seconds when it is clicked again while showing the copied icon", async () => {
        render(<CopyButton value="abc-123" />);
        await click();
        act(() => vi.advanceTimersByTime(1500));

        await click();
        act(() => vi.advanceTimersByTime(1000));

        expect(screen.getByText(COPIED_ICON)).toBeTruthy();
        act(() => vi.advanceTimersByTime(1000));
        expect(screen.getByText(COPY_ICON)).toBeTruthy();
    });

    it("announces a successful copy in a polite live region that exists before the copy", async () => {
        const { container } = render(<CopyButton value="abc-123" />);
        const region = container.querySelector('[aria-live="polite"]');
        expect(region?.textContent).toBe("");

        await click();

        expect(region?.textContent).toBe(COPIED_TEXT);
    });

    it("announces nothing when the clipboard refuses", async () => {
        writeText.mockRejectedValue(new Error("denied"));
        const { container } = render(<CopyButton value="abc-123" />);

        await click();

        expect(container.querySelector('[aria-live="polite"]')?.textContent).toBe("");
    });

    it("leaves no timer behind when it unmounts while showing the copied icon", async () => {
        const { unmount } = render(<CopyButton value="abc-123" />);
        // The test harness's providers hold timers of their own.
        const harnessTimers = vi.getTimerCount();
        await click();
        expect(vi.getTimerCount()).toBe(harnessTimers + 1);

        unmount();

        expect(vi.getTimerCount()).toBeLessThanOrEqual(harnessTimers);
    });

    it("starts no timer when the copy finishes after it unmounts", async () => {
        let finishCopy = (): void => undefined;
        writeText.mockReturnValue(new Promise<void>((resolve) => (finishCopy = resolve)));
        const { unmount } = render(<CopyButton value="abc-123" />);
        fireEvent.click(screen.getByRole("button", { name: "Copy to clipboard" }));
        unmount();
        // The test harness's providers hold timers of their own.
        const harnessTimers = vi.getTimerCount();

        await act(async () => finishCopy());

        expect(vi.getTimerCount()).toBeLessThanOrEqual(harnessTimers);
    });

    it("stays on the copy icon when the clipboard refuses", async () => {
        writeText.mockRejectedValue(new Error("denied"));
        render(<CopyButton value="abc-123" />);

        await click();

        expect(screen.getByText(COPY_ICON)).toBeTruthy();
    });

    it("does not pass the click to its container", async () => {
        const onRowClick = vi.fn();
        render(
            <div onClick={onRowClick}>
                <CopyButton value="abc-123" />
            </div>,
        );

        await click();

        expect(onRowClick).not.toHaveBeenCalled();
    });

    it("takes a label that names what it copies", async () => {
        render(<CopyButton value="12.5 km" label={VALUE_LABEL} />);

        await click(VALUE_LABEL);

        expect(writeText).toHaveBeenCalledWith("12.5 km");
    });
});
