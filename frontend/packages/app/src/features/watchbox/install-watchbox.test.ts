// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { noop } from "lodash";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ContributionContext } from "@opencop/contributions";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installNativeSubjects } from "~/features/contributions/native-install";
import { __resetToolRegistry, getTool } from "~/features/contributions/tool-registry";
import type { subscribeBackendFeature } from "~/features/admin/runtime-features/backend-feature.subscribe";
import { installWatchbox } from "./install-watchbox";
import { WATCHBOX_TOOL_ID } from "./watchbox.descriptor";
import { WATCHBOX_STORE_ID } from "./watchbox.store";

const watchboxContext = contributionContextFor("opencop.watchbox");

/**
 * Availability is registration: the watchbox descriptor and click response
 * exist in the registries exactly while the backend reports the feature on.
 * The flag source is injected so the test drives it directly.
 */

function fakeFlag() {
    let listener: ((enabled: boolean) => void) | undefined;
    const unsubscribe = vi.fn();
    const subscribeFeature = vi.fn<typeof subscribeBackendFeature>((_id, onChange) => {
        listener = onChange;
        return unsubscribe;
    });
    return {
        subscribeFeature,
        unsubscribe,
        report: (enabled: boolean) => listener?.(enabled),
    };
}

const disposers: (() => void)[] = [];

/** Install against a fake flag; disposed after each test. */
function install() {
    const flag = fakeFlag();
    const dispose = installWatchbox(watchboxContext, { subscribeFeature: flag.subscribeFeature });
    disposers.push(dispose);
    return { flag, dispose };
}

beforeEach(() => __resetToolRegistry());
afterEach(() => {
    disposers.splice(0).forEach((dispose) => dispose());
    __resetToolRegistry();
});

describe("installWatchbox", () => {
    it("subscribes to the watchbox backend feature", () => {
        const { flag } = install();

        expect(flag.subscribeFeature).toHaveBeenCalledWith("watchbox", expect.any(Function));
    });

    it("registers the tool when the feature is on and deregisters it when it turns off", () => {
        const { flag } = install();
        expect(getTool(WATCHBOX_TOOL_ID)).toBeUndefined();

        flag.report(true);
        expect(getTool(WATCHBOX_TOOL_ID)?.id).toBe(WATCHBOX_TOOL_ID);

        flag.report(false);
        expect(getTool(WATCHBOX_TOOL_ID)).toBeUndefined();
    });

    it("registers the click response under the store id with the tool and withdraws it with it", () => {
        const withdraw = vi.fn();
        const registerPickResponse = vi
            .spyOn(watchboxContext, "registerPickResponse")
            .mockReturnValue(withdraw);
        const { flag } = install();

        flag.report(true);
        expect(registerPickResponse).toHaveBeenCalledTimes(1);
        expect(registerPickResponse.mock.calls[0][0]).toBe(WATCHBOX_STORE_ID);
        expect(withdraw).not.toHaveBeenCalled();

        flag.report(false);
        expect(withdraw).toHaveBeenCalledTimes(1);
        registerPickResponse.mockRestore();
    });

    it("registers once across repeated 'on' reports", () => {
        const registerTool = vi.spyOn(watchboxContext, "registerTool");
        const { flag } = install();

        flag.report(true);
        flag.report(true);

        expect(registerTool).toHaveBeenCalledTimes(1);
        registerTool.mockRestore();
    });

    // The flag can be on at install (`subscribeBackendFeature` reports the cached
    // answer synchronously) or turn on later; a refusal must leave nothing registered in both.
    it.each([
        ["turns on after install", false],
        ["is already on at install", true],
    ])("a registration refused when the flag %s leaves nothing registered", (_, onAtInstall) => {
        vi.spyOn(Logger.get("plugins"), "error").mockImplementation(noop);
        const flag = fakeFlag();
        const subscribeFeature: typeof subscribeBackendFeature = (id, onChange) => {
            const unsubscribe = flag.subscribeFeature(id, onChange);
            if (onAtInstall) onChange(true);
            return unsubscribe;
        };
        const registerPickResponse = vi.spyOn(watchboxContext, "registerPickResponse");
        const refusing: ContributionContext = {
            ...watchboxContext,
            registerTool: () => {
                throw new Error("refused");
            },
        };
        disposers.push(
            installNativeSubjects(
                [
                    {
                        id: "opencop.watchbox",
                        install: (ctx) => installWatchbox(ctx, { subscribeFeature }),
                    },
                ],
                () => refusing,
            ),
        );

        if (!onAtInstall) flag.report(true);

        expect(getTool(WATCHBOX_TOOL_ID)).toBeUndefined();
        expect(registerPickResponse).not.toHaveBeenCalled();
        vi.restoreAllMocks();
    });

    it("the disposer unsubscribes from the flag and removes the tool", () => {
        const { flag, dispose } = install();
        flag.report(true);

        dispose();

        expect(flag.unsubscribe).toHaveBeenCalledOnce();
        expect(getTool(WATCHBOX_TOOL_ID)).toBeUndefined();
    });
});
