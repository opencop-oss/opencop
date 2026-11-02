// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { getTimezoneOptions } from "./timezone.util";

export const LANGUAGE_CODES = ["en", "tw", "fr"] as const;

export const MEASUREMENT_SYSTEMS = ["IMPERIAL", "METRIC"] as const;

export const TEMPERATURE_UNITS = ["FAHRENHEIT", "CELSIUS"] as const;

export const ZOOM_BEHAVIORS = ["CENTER", "MOUSE"] as const;

export const TIMEZONE_OPTIONS = getTimezoneOptions();
