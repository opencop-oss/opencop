// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { render, screen, waitFor, userEvent, server, http, HttpResponse, API_BASE } from "~/test";
import { describe, expect, it, vi } from "vitest";
import { noop } from "lodash";
import { i18n } from "~/i18n/config";
import { ConfirmRevokeGroupDialog } from "./ConfirmRevokeGroupDialog";

const GROUP = { id: "g1", name: "Operations" };

describe("ConfirmRevokeGroupDialog", () => {
    it("does not fetch the member list while closed", async () => {
        let calls = 0;
        server.use(
            http.get(`${API_BASE}/admin/user-groups/${GROUP.id}/members`, () => {
                calls++;
                return HttpResponse.json([]);
            }),
        );

        render(
            <ConfirmRevokeGroupDialog
                isOpen={false}
                onClose={vi.fn()}
                onConfirm={vi.fn()}
                group={GROUP}
                currentUserId="u1"
            />,
        );

        await new Promise((r) => setTimeout(r, 0));
        expect(calls).toBe(0);
    });

    // The member fetch only populates this dialog's own message — it is not an action the user
    // started and cannot cancel, so it must not block Cancel/Escape dismissal. The revoke itself
    // is different: it's a destructive action already in flight, so dismissal is blocked until it
    // settles (see ConfirmDialog's `dismissDuringLoading` prop).
    it("can still be cancelled while the member list is loading", async () => {
        server.use(
            http.get(`${API_BASE}/admin/user-groups/${GROUP.id}/members`, () => new Promise(noop)),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();

        render(
            <ConfirmRevokeGroupDialog
                isOpen
                onClose={onClose}
                onConfirm={vi.fn()}
                group={GROUP}
                currentUserId="u1"
            />,
        );

        const cancel = await screen.findByRole("button", { name: i18n.t("common.cancel") });
        expect(cancel).not.toBeDisabled();
        await user.click(cancel);
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("can still be dismissed with Escape while the member list is loading", async () => {
        server.use(
            http.get(`${API_BASE}/admin/user-groups/${GROUP.id}/members`, () => new Promise(noop)),
        );
        const onClose = vi.fn();
        const user = userEvent.setup();

        render(
            <ConfirmRevokeGroupDialog
                isOpen
                onClose={onClose}
                onConfirm={vi.fn()}
                group={GROUP}
                currentUserId="u1"
            />,
        );

        await screen.findByRole("button", { name: i18n.t("common.cancel") });
        await user.keyboard("{Escape}");
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it("cannot be dismissed while the revoke itself is in flight", async () => {
        server.use(
            http.get(`${API_BASE}/admin/user-groups/${GROUP.id}/members`, () =>
                HttpResponse.json([]),
            ),
        );
        let resolveConfirm: (() => void) | undefined;
        const onClose = vi.fn();
        const onConfirm = vi.fn(
            () =>
                new Promise<void>((resolve) => {
                    resolveConfirm = resolve;
                }),
        );
        const user = userEvent.setup();

        render(
            <ConfirmRevokeGroupDialog
                isOpen
                onClose={onClose}
                onConfirm={onConfirm}
                group={GROUP}
                currentUserId="u1"
            />,
        );

        await user.click(await screen.findByRole("button", { name: i18n.t("common.delete") }));

        const cancel = screen.getByRole("button", { name: i18n.t("common.cancel") });
        await waitFor(() => expect(cancel).toBeDisabled());
        await user.keyboard("{Escape}");
        expect(onClose).not.toHaveBeenCalled();

        resolveConfirm?.();
        await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    });
});
