// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Box, Center, Image, type BoxProps } from "@chakra-ui/react";
import type { ReactElement } from "react";
import { Renderer } from "@opencop/symbology";

/**
 * A live preview of a SIDC being edited — distinct from the events-panel
 * thumbnail, which resolves through the renderer registry from a
 * committed element's `(type, code)`. This one renders a code the operator is
 * still typing, so there is no element and no subject to route to.
 *
 * Lives in `features/symbology` rather than `@opencop/ui`: it calls
 * `@opencop/symbology`'s `Renderer`, and a shared OSS UI kit that imports the
 * MIL-STD stack would drag premium code into every consumer's bundle once
 * symbology becomes a premium plugin. It rides into the plugin with the rest of
 * the symbology UI.
 */
export function SIDCImage({ sidc, ...rest }: { sidc: string } & BoxProps): ReactElement {
    const src = Renderer.renderPreview(sidc);
    return (
        <Box {...rest}>
            <Center bg={rest.bg} w="full" h="full" borderRadius="5%" p="5%">
                {src && <Image src={src} boxSize="full" objectFit="contain" />}
            </Center>
        </Box>
    );
}
