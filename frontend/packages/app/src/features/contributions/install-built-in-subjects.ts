// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { noop } from "lodash";
import { createJournal } from "@opencop/contributions/host";
import { contributionContextFor } from "~/features/contributions/contribution-installer";
import { installNativeSubjects, type NativeSubject } from "~/features/contributions/native-install";
import { installLassoSelection } from "~/plugins/lasso/install-lasso-selection";
import { installShapes } from "~/plugins/shapes/install-shapes";
import { installMediaIcons } from "~/plugins/media-icons/install-media-icons";
import { installMeasure } from "~/plugins/measure/install-measure";
import { installWatchbox } from "~/features/watchbox/install-watchbox";
import { installEvents } from "~/features/events/install-events";
import { installSelection } from "~/features/selection/install-selection";

const logger = Logger.get("plugins");

/**
 * The native plugins, in install order. The one host file allowed to name them.
 * A subject that ships as a plugin is absent: it installs through the plugin
 * manager instead, with the same `install(ctx)` shape.
 */
const NATIVE_SUBJECTS: readonly NativeSubject[] = [
    { id: "opencop.shapes", install: installShapes },
    { id: "opencop.lasso", install: installLassoSelection },
    { id: "opencop.media-icons", install: installMediaIcons },
    { id: "opencop.measure", install: installMeasure },
    { id: "opencop.watchbox", install: installWatchbox },
];

/** The native plugins' owner ids. No plugin may load under one of them. */
export const NATIVE_SUBJECT_IDS: readonly string[] = NATIVE_SUBJECTS.map(({ id }) => id);

/** The native plugin holding owner id `id`, or `undefined`: installs exactly what app start installs. */
export function nativeSubject(id: string): NativeSubject | undefined {
    return NATIVE_SUBJECTS.find((candidate) => candidate.id === id);
}

/**
 * Host features that react to the interaction session. They attach here, on the
 * same lifetime as the subjects, but are not subjects: they register no
 * contribution and get no record, and a throw from one is a host defect.
 */
const HOST_INSTALLERS: ReadonlyArray<() => () => void> = [installEvents, installSelection];

let state: "idle" | "installing" | "installed" = "idle";
let dispose: () => void = noop;
/** Set on the old copy of this module once HMR has replaced it, so it never installs again. */
let retired = false;

/**
 * Composition root: installs every native subject — each self-registers its
 * tools, renderers, stores and pick responses through its own context, as the
 * plugin loader calls a plugin's `install(ctx)` — then the host installers.
 *
 * A subject that fails is rolled back, recorded and logged (see
 * `installNativeSubjects`); the others and the host installers install anyway.
 *
 * Idempotent: a repeat call returns the same disposer. Disposing removes every
 * registration this made and lets a later call install again — the path for
 * HMR, tests and an unmount of the authenticated app.
 */
export function installBuiltInSubjects(): () => void {
    if (retired || state !== "idle") return dispose;
    state = "installing";
    const disposeSubjects = installNativeSubjects(NATIVE_SUBJECTS, contributionContextFor);
    const host = createJournal();
    dispose = () => {
        for (const error of host.rollback())
            logger.error("[native-install]: host undo threw:", error);
        disposeSubjects();
        state = "idle";
        dispose = noop;
    };
    try {
        for (const install of HOST_INSTALLERS) host.add(install());
    } finally {
        state = "installed";
    }
    return dispose;
}

// Editing an installer re-runs this module, which accepts its own updates so
// that the dispose hook below runs. The old copy retires its installation and
// stops installing; the new copy re-seats the natives from the new code at once,
// because the component that first installed them does not re-run under Fast
// Refresh.
if (import.meta.hot) {
    import.meta.hot.accept();
    import.meta.hot.dispose((data: { reinstall?: boolean }) => {
        data.reinstall = state !== "idle";
        dispose();
        retired = true;
    });
    // Vitest defines `import.meta.hot` without `data`.
    if ((import.meta.hot.data as { reinstall?: boolean } | undefined)?.reinstall) {
        installBuiltInSubjects();
    }
}
