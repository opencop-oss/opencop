// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useRef, useState, type ReactNode } from "react";
import { I18nextProvider } from "react-i18next";
import { describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { ConfirmDialog } from "./ConfirmDialog";

const DELETE_LABEL = i18n.t("common.delete");
const DONE_LABEL = i18n.t("common.done");
const SAVING_LABEL = i18n.t("common.saving");

function renderDialog(ui: ReactNode) {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>{ui}</OcThemeProvider>
        </I18nextProvider>,
    );
}

describe("ConfirmDialog", () => {
    it("keeps the action label exposed while the button is loading", () => {
        renderDialog(
            <ConfirmDialog
                open
                loading
                loadingText={SAVING_LABEL}
                onClose={vi.fn()}
                onConfirm={vi.fn()}
                title={DELETE_LABEL}
                message={DELETE_LABEL}
            />,
        );

        const action = screen.getByRole("button", { name: SAVING_LABEL });
        expect(action).toHaveAttribute("aria-busy", "true");
        expect(
            Array.from(action.querySelectorAll("*")).filter(
                (element) => getComputedStyle(element).visibility === "hidden",
            ),
        ).toHaveLength(0);
    });

    it("remains dismissible while loading by default", async () => {
        const onClose = vi.fn();
        const user = userEvent.setup();

        renderDialog(
            <ConfirmDialog
                open
                loading
                onClose={onClose}
                onConfirm={vi.fn()}
                title={DELETE_LABEL}
                message={DELETE_LABEL}
            />,
        );

        const cancel = screen.getByRole("button", { name: i18n.t("common.cancel") });
        expect(cancel).not.toBeDisabled();
        await user.click(cancel);
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("blocks button and Escape dismissal while loading when opted in", async () => {
        const onClose = vi.fn();
        const user = userEvent.setup();

        renderDialog(
            <ConfirmDialog
                open
                loading
                dismissDuringLoading={false}
                onClose={onClose}
                onConfirm={vi.fn()}
                title={DELETE_LABEL}
                message={DELETE_LABEL}
            />,
        );

        expect(screen.getByRole("button", { name: i18n.t("common.cancel") })).toBeDisabled();
        await user.keyboard("{Escape}");
        expect(onClose).not.toHaveBeenCalled();
    });

    it("focuses the requested surviving element after close", async () => {
        const user = userEvent.setup();

        function Harness() {
            const [open, setOpen] = useState(true);
            const finalFocusRef = useRef<HTMLButtonElement>(null);
            return (
                <>
                    <button ref={finalFocusRef}>{DONE_LABEL}</button>
                    <ConfirmDialog
                        open={open}
                        onClose={() => setOpen(false)}
                        onConfirm={vi.fn()}
                        title={DELETE_LABEL}
                        message={DELETE_LABEL}
                        finalFocusEl={() => finalFocusRef.current}
                    />
                </>
            );
        }

        renderDialog(<Harness />);
        await user.click(screen.getByRole("button", { name: i18n.t("common.cancel") }));

        await waitFor(() => expect(screen.getByRole("button", { name: DONE_LABEL })).toHaveFocus());
    });
});
