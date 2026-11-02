// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach } from "vitest";
import symbologyPlugin from "@opencop/symbology-plugin";
import { PluginManager } from "@opencop/contributions/host";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { nativeSubject } from "~/features/contributions/install-built-in-subjects";
import {
    getNativeSubjectRecords,
    installNativeSubjects,
    type NativeSubject,
} from "~/features/contributions/native-install";
import { getAllTools } from "~/features/contributions/tool-registry";
import { getElementMapperEntries } from "~/features/contributions/element-mapper-registry";
import { getRegisteredFilterFields } from "~/features/contributions/filter-field-registry";
import { getDetailsSurfaceEntries } from "~/features/contributions/details-registry";
import { getContextMenuEntries } from "~/features/contributions/context-menu-registry";
import { createPluginHostServices } from "~/features/plugin-manager/plugin-services";

/**
 * The standard plugins an app test may install, by id. The one place app tests reach a
 * plugin package: the app may not import one anywhere else, tests included.
 */
const STANDARD_PLUGINS: Readonly<Record<string, unknown>> = {
    "opencop.symbology": symbologyPlugin,
};

/** The registries a plugin's install adds to, read as the host's readers read them. */
function registered(): readonly unknown[] {
    return [
        ...getAllTools(),
        ...getElementMapperEntries(),
        ...getRegisteredFilterFields(),
        ...getDetailsSurfaceEntries(),
        ...getContextMenuEntries(),
    ];
}

/**
 * Install plugins, by owner id, the way app start does: a native from the composition
 * root's own list through the native installer, a standard plugin through a real
 * {@link PluginManager} with the real host services. For an app test whose feature needs
 * a real plugin present; a plugin's own tests use its test host instead.
 *
 * Fails loudly: an unknown id, a native the installer marked failed, or a plugin the
 * manager did not load throws, with every reason. Returns the uninstall, which throws if
 * a disposer threw or anything this install added is still registered after it. Pick
 * responses have no reader, so a leaked one goes unseen.
 */
export function installPlugins(ids: readonly string[]): () => void {
    const unknown = ids.filter((id) => !nativeSubject(id) && !(id in STANDARD_PLUGINS));
    if (unknown.length > 0)
        throw new Error(
            `installPlugins: no native or standard plugin ${unknown.map((id) => `"${id}"`).join(", ")}`,
        );

    const before = new Set(registered());
    const natives = ids.map(nativeSubject).filter((subject): subject is NativeSubject => !!subject);
    const disposeNatives = installNativeSubjects(natives, contributionContextFor);
    // The native installer records a failure rather than throwing, so the app keeps running.
    const failed = getNativeSubjectRecords().filter((record) => record.status === "failed");
    if (failed.length > 0)
        throw withUndone(
            new Error(
                `installPlugins: ${failed.map((record) => `${record.id} failed: ${record.error}`).join("; ")}`,
            ),
            disposeNatives(),
        );

    const manager = new PluginManager(createPluginHostServices());
    const loaded: string[] = [];
    for (const id of ids.filter((candidate) => candidate in STANDARD_PLUGINS)) {
        const record = manager.load(STANDARD_PLUGINS[id]);
        if (record.status !== "active") {
            const undone = [...loaded].reverse().flatMap((done) => manager.unload(done));
            // The failed record is the manager's too; it holds nothing, but it is listed.
            undone.push(...manager.unload(id), ...disposeNatives());
            throw withUndone(
                new Error(`installPlugins: ${id} ${record.status}: ${String(record.error)}`),
                undone,
            );
        }
        loaded.push(id);
    }
    // What this install added, by identity — so a fake the test registers later is never blamed here.
    const added = registered().filter((entry) => !before.has(entry));

    return () => {
        const errors = [...loaded].reverse().flatMap((id) => manager.unload(id));
        errors.push(...disposeNatives());
        const now = new Set(registered());
        const left = added.filter((entry) => now.has(entry));
        if (left.length > 0)
            errors.push(
                new Error(
                    `installPlugins: ${ids.join(", ")} left ${left.length === 1 ? "an entry" : "entries"} registered after uninstall`,
                ),
            );
        if (errors.length === 1) throw errors[0];
        if (errors.length > 1)
            throw new AggregateError(
                errors,
                `installPlugins: ${ids.join(", ")} did not uninstall cleanly`,
            );
    };
}

/** `error`, or — when undoing a refused install threw too — both, so neither is lost. */
function withUndone(error: Error, undone: readonly unknown[]): Error {
    return undone.length === 0 ? error : new AggregateError([error, ...undone], error.message);
}

/** Install `ids` before each test of the enclosing `describe`, and uninstall after it. */
export function withPlugins(ids: readonly string[]): void {
    let uninstall: (() => void) | undefined;
    beforeEach(() => {
        uninstall = installPlugins(ids);
    });
    afterEach(() => {
        uninstall?.();
        uninstall = undefined;
    });
}
