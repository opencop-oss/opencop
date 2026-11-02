// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// Real-browser regression test: switching between two rows only reproduces
// with genuine coordinate-based clicks (Playwright's `page`, via
// `vitest/browser`), not `@testing-library/user-event`, which dispatches
// events directly on the passed element reference regardless of any layout
// shift in between. See PredicateRow's onPointerDown comment for the
// underlying mechanism this guards against.
//
// Runs under vitest.browser.config.ts, a separate project from the harness's
// happy-dom config: no setupFiles, and — per docs/best-practices/testing/
// integration-tests.md's global-stubs table — `@opencop/websocket` is
// deliberately left real here (browser-mode tests "have real layout and a
// real socket stack"). `~/test`'s render mounts WebSocketProvider, which
// would try to open a real socket in this project. This component needs
// only Chakra + i18n + react-hook-form, so it skips the harness rather than
// fight it.
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { page } from "vitest/browser";
import { createElement, Fragment } from "react";
import { useForm } from "react-hook-form";
import { Box } from "@chakra-ui/react";
import "~/i18n/config";
import { OcThemeProvider as ThemeProvider } from "../../theme/OcThemeProvider";
import { FilterRulesBuilder, type FilterRulesFormValues } from "./FilterRulesBuilder";
import type { FilterDraft } from "./predicate.model";
import type { FilterField } from "./filter-field.model";
import { FilterFieldsProvider, type FilterFields } from "./FilterFieldsContext";

// Two plain fields; this test is about row layout, not about any field.
const FIELDS: FilterFields = {
    fields: [
        {
            id: "displayName",
            labelKey: "filter.fields.displayName",
            path: "properties.opencop.displayName",
            order: 20,
        },
        { id: "code", labelText: "Code", path: "properties.opencop.code", order: 30 },
    ] satisfies FilterField[],
    defaultFieldId: "displayName",
};

// A caller's form is wider than the builder's `{ filter }` slice; the extra field
// proves the builder accepts one.
type HostFormValues = FilterRulesFormValues & { name: string };

const BASE_VALUES: HostFormValues = {
    name: "Test",
    filter: { combinator: "ALL", predicates: [] },
};

const TWO_COMPLETE: FilterDraft = {
    combinator: "ALL",
    predicates: [
        { id: "p1", field: "displayName", operator: "EQUALS", value: "Alpha" },
        { id: "p2", field: "code", operator: "EXISTS", value: undefined },
    ],
};

const MANY_COMPLETE: FilterDraft = {
    combinator: "ALL",
    predicates: Array.from({ length: 5 }, (_, i) => ({
        id: `p${i}`,
        field: "displayName",
        operator: "EQUALS",
        value: `Alpha ${i}`,
    })),
};

function TestHost() {
    const { control } = useForm<HostFormValues>({
        defaultValues: { ...BASE_VALUES, filter: TWO_COMPLETE },
    });
    return createElement(FilterRulesBuilder<HostFormValues>, { control });
}

function renderBuilder() {
    return render(
        createElement(
            ThemeProvider,
            null,
            createElement(FilterFieldsProvider, {
                value: FIELDS,
                children: createElement(TestHost),
            }),
        ),
    );
}

function ManyRowsTestHost() {
    const { control } = useForm<HostFormValues>({
        defaultValues: { ...BASE_VALUES, filter: MANY_COMPLETE },
    });
    // Mimics a caller's outer scrollable form body (`overflowY: "auto"`) that
    // the filter builder normally sits inside of — the row list's own
    // `overflowY: "auto"` only takes effect once an ancestor gives it a
    // bounded height, which this test needs for the scroll-clamp race below
    // to reproduce. The 600px filler above the builder stands in
    // for the form's other fields (name/description/layers): it's what
    // makes the scroller overflow while leaving the "Add condition" button
    // itself fully visible at max scroll — matching the real bug, where the
    // button was on-screen the whole time and only shifted position.
    return createElement(
        Box,
        { overflowY: "auto", h: "500px", id: "scroller" },
        createElement(
            Fragment,
            null,
            createElement(Box, { h: "600px" }),
            createElement(FilterRulesBuilder<HostFormValues>, { control }),
        ),
    );
}

function renderManyRows() {
    return render(
        createElement(
            ThemeProvider,
            null,
            createElement(FilterFieldsProvider, {
                value: FIELDS,
                children: createElement(ManyRowsTestHost),
            }),
        ),
    );
}

describe("FilterRulesBuilder — real clicks (browser mode)", () => {
    it("opens the row you click after closing the previously open one", async () => {
        const { getAllByRole } = renderBuilder();

        await page.getByRole("button", { name: "Expand condition" }).nth(0).click();
        expect(getAllByRole("button", { name: "Expand condition" })).toHaveLength(1);

        await page.getByRole("button", { name: "Expand condition" }).click();

        expect(getAllByRole("button", { name: "Expand condition" })).toHaveLength(1);
    });

    it("collapses the open row on a real click outside all rows", async () => {
        const { getAllByRole } = renderBuilder();

        await page.getByRole("button", { name: "Expand condition" }).nth(0).click();
        expect(getAllByRole("button", { name: "Expand condition" })).toHaveLength(1);

        await page.getByRole("button", { name: "Add condition" }).click();

        expect(getAllByRole("button", { name: "Expand condition" })).toHaveLength(2);
        expect(getAllByRole("button", { name: "Remove condition" })).toHaveLength(3);
    });

    it("keeps the row open while selecting an operator from its portaled dropdown", async () => {
        const { getAllByRole } = renderBuilder();

        await page.getByRole("button", { name: "Expand condition" }).nth(0).click();
        await page.getByRole("combobox").nth(1).click();
        await page.getByRole("option", { name: "Exists" }).click();

        expect(getAllByRole("combobox")).toHaveLength(2);
    });

    // Regression for: expanding a summarizable row while scrolled to the
    // bottom of an overflowing container, then clicking "Add condition",
    // used to drop the click. The outside-pointerdown listener collapsed the
    // open row on the button's own mousedown (before mouseup), shrinking the
    // scroller and making the browser clamp scrollTop back — which shifts
    // everything in the scroller, including the button, between mousedown
    // and mouseup. The real click then lands on whatever is now under those
    // fixed screen coordinates instead of the button, so onClick never fires.
    it("adds a condition on click even when collapsing the open row shrinks the scroller between mousedown and mouseup", async () => {
        const { getAllByRole, container } = renderManyRows();
        const scroller = container.querySelector<HTMLElement>("#scroller");
        if (!scroller) throw new Error("scroller not found");

        const rowCount = getAllByRole("button", { name: "Expand condition" }).length;
        await page.getByRole("button", { name: "Expand condition" }).last().click();

        // Simulates the value input's auto-scroll-into-view landing the
        // scroller at its max scrollTop while the row is expanded (taller).
        scroller.scrollTop = scroller.scrollHeight;
        expect(scroller.scrollTop).toBeGreaterThan(0);

        // A real human click has a nonzero mousedown-to-mouseup gap — enough
        // for the pointerdown-triggered collapse to commit and the browser to
        // clamp scrollTop before mouseup fires at the (now stale) coordinates.
        await page.getByRole("button", { name: "Add condition" }).click({ delay: 120 });

        expect(getAllByRole("button", { name: "Remove condition" })).toHaveLength(rowCount + 1);
    });
});
