// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { createSystem, defaultConfig, defineConfig } from "@chakra-ui/react";
import { globalStyles } from "./global-styles.ts";
import { recipes } from "./recipes.ts";
import { semanticTokens } from "./semantic-tokens.ts";
import { slotRecipes } from "./slot-recipes.ts";
import { tokens } from "./tokens.ts";

// After modifying the theme, run `npm run chakra` to regenerate TypeScript
// types for custom tokens, semantic tokens, and recipe variants. The generator
// reads this file, so it must keep exporting the `system`.
export const themeConfig = defineConfig({
    globalCss: globalStyles,
    theme: {
        tokens,
        semanticTokens,
        recipes,
        slotRecipes,
    },
});

export const system = createSystem(defaultConfig, themeConfig);
