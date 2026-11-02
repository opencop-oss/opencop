// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import Logger from "js-logger";
import { createNativeInstaller } from "@opencop/contributions/host";

export type { NativeSubject, NativeSubjectRecord } from "@opencop/contributions/host";

/** The app's native install path: the one instance of `createNativeInstaller`. */
const installer = createNativeInstaller({ log: Logger.get("plugins") });

export const installNativeSubjects = installer.install;
export const getNativeSubjectRecords = installer.getRecords;
