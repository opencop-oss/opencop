// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { i18n } from "~/i18n/config";
import { render, screen, userEvent } from "~/test";
import { DataSourceAccessModeStatus } from "./DataSourceAccessModeStatus";

const localizedStates = [
    {
        locale: "en",
        access: "Read & Write",
        incompatible: "Read & Write is incompatible with UDP.",
        unverified: "Couldn't check write support: this source's details didn't load.",
        retry: "Retry",
    },
    {
        locale: "fr",
        access: "Lecture et écriture",
        incompatible: "Lecture et écriture est incompatible avec UDP.",
        unverified:
            "Impossible de vérifier la prise en charge de l’écriture : les détails de cette source n’ont pas été chargés.",
        retry: "Réessayer",
    },
    {
        locale: "tw",
        access: "讀取與寫入",
        incompatible: "讀取與寫入 與 UDP 不相容。",
        unverified: "無法檢查寫入支援：此來源的詳細資料未載入。",
        retry: "重試",
    },
] as const;

describe("DataSourceAccessModeStatus", () => {
    afterEach(async () => {
        await i18n.changeLanguage("en");
    });

    it.each(localizedStates)(
        "presents exact stored access and an accessible incompatibility warning in $locale",
        async ({ locale, access, incompatible }) => {
            await i18n.changeLanguage(locale);

            render(<DataSourceAccessModeStatus access="RW" transportType="UDP" />);

            expect(screen.getByText(access)).toBeVisible();
            expect(screen.getByRole("img", { name: incompatible })).toBeVisible();
        },
    );

    it("states the incompatibility without offering to change it", () => {
        render(<DataSourceAccessModeStatus access="RW" transportType="UDP" />);

        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    it.each(localizedStates)(
        "presents an accessible unverified state and retry action in $locale",
        async ({ locale, access, unverified, retry }) => {
            await i18n.changeLanguage(locale);
            const onRetry = vi.fn();
            const user = userEvent.setup();

            render(
                <DataSourceAccessModeStatus
                    access="RW"
                    transportType="TCP"
                    capabilityCheck="unverified"
                    onRetryCapability={onRetry}
                />,
            );

            expect(screen.getByText(access)).toBeVisible();
            expect(screen.getByRole("img", { name: unverified })).toBeVisible();
            await user.click(screen.getByRole("button", { name: retry }));
            expect(onRetry).toHaveBeenCalledOnce();
        },
    );

    it("says the check doesn't apply, with no warning or retry, when no mapper is on record", async () => {
        await i18n.changeLanguage("en");
        render(
            <DataSourceAccessModeStatus
                access="RW"
                transportType="TCP"
                capabilityCheck="notApplicable"
                onRetryCapability={vi.fn()}
            />,
        );

        expect(
            screen.getByText("Not applicable: this source has no mapper on record."),
        ).toBeVisible();
        expect(screen.queryByRole("img")).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Retry" })).not.toBeInTheDocument();
    });
});
