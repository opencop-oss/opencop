// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it } from "vitest";
import { i18n } from "~/i18n/config";
import { render, screen } from "~/test";
import { SourceSummaryAccessMode } from "./SourceSummaryAccessMode";

const localeExpectations = [
    {
        locale: "en",
        label: "Read & Write",
        warning: "Read & Write is incompatible with Kafka.",
    },
    {
        locale: "fr",
        label: "Lecture et écriture",
        warning: "Lecture et écriture est incompatible avec Kafka.",
    },
    {
        locale: "tw",
        label: "讀取與寫入",
        warning: "讀取與寫入 與 Kafka 不相容。",
    },
] as const;

describe("SourceSummaryAccessMode localization", () => {
    afterEach(async () => {
        await i18n.changeLanguage("en");
    });

    it.each(localeExpectations)(
        "provides $locale exact labels and an accessible warning",
        async ({ locale, label, warning }) => {
            await i18n.changeLanguage(locale);

            render(<SourceSummaryAccessMode access="RW" transportType="KAFKA" />);

            expect(screen.getByText(label)).toBeVisible();
            expect(screen.getByRole("img", { name: warning })).toBeVisible();
        },
    );
});
