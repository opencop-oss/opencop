// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { describe, expect, it, vi } from "vitest";
import { Dialog } from "@chakra-ui/react";
import { I18nextProvider } from "react-i18next";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { DialogActions } from "./DialogActions";

// Dialog.Footer reads the Chakra dialog slot context, so it must render inside a Dialog.Root.
function Harness(props: ComponentProps<typeof DialogActions>) {
    return (
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <Dialog.Root open>
                    <DialogActions {...props} />
                </Dialog.Root>
            </OcThemeProvider>
        </I18nextProvider>
    );
}

describe("DialogActions", () => {
    it("disables the close/cancel button when closeDisabled is set", () => {
        render(
            <Harness
                onClose={vi.fn()}
                onAction={vi.fn()}
                actionLabel={i18n.t("common.confirm")}
                closeDisabled
            />,
        );

        expect(screen.getByRole("button", { name: i18n.t("common.cancel") })).toBeDisabled();
    });

    it("leaves the close/cancel button enabled by default", async () => {
        const onClose = vi.fn();
        const user = userEvent.setup();
        render(
            <Harness onClose={onClose} onAction={vi.fn()} actionLabel={i18n.t("common.confirm")} />,
        );

        const cancel = screen.getByRole("button", { name: i18n.t("common.cancel") });
        expect(cancel).not.toBeDisabled();
        await user.click(cancel);
        expect(onClose).toHaveBeenCalledTimes(1);
    });
});
