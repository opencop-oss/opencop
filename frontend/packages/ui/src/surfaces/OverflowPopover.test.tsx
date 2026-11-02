// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { I18nextProvider } from "react-i18next";
import { describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import { OcThemeProvider } from "../theme/OcThemeProvider";
import { OverflowPopover } from "./OverflowPopover";

const LABEL = "More items";
const ITEM_LABEL = i18n.t("common.delete");

function renderPopover() {
    return render(
        <I18nextProvider i18n={i18n}>
            <OcThemeProvider>
                <OverflowPopover
                    label={LABEL}
                    renderItems={(close) => (
                        <button type="button" onClick={close}>
                            {ITEM_LABEL}
                        </button>
                    )}
                />
            </OcThemeProvider>
        </I18nextProvider>,
    );
}

describe("OverflowPopover", () => {
    it("reveals the hidden items when the trigger is opened", async () => {
        const user = userEvent.setup();
        renderPopover();

        await user.click(screen.getByRole("button", { name: LABEL }));

        expect(await screen.findByRole("button", { name: ITEM_LABEL })).toBeInTheDocument();
    });

    it("closes once an item calls the close callback", async () => {
        const user = userEvent.setup();
        renderPopover();

        await user.click(screen.getByRole("button", { name: LABEL }));
        await user.click(await screen.findByRole("button", { name: ITEM_LABEL }));

        await waitFor(() =>
            expect(screen.queryByRole("button", { name: ITEM_LABEL })).not.toBeInTheDocument(),
        );
    });
});
