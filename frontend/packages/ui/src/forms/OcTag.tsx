// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Tag as ChakraTag } from "@chakra-ui/react";
import * as React from "react";

export interface OcTagProps extends ChakraTag.RootProps {
    startElement?: React.ReactNode;
    endElement?: React.ReactNode;
    onClose?: VoidFunction;
    closable?: boolean;
    /**
     * Accessible name for the close button. It renders as an icon alone, so without this a screen
     * reader announces only "button" and which tag it dismisses is unrecoverable.
     */
    closeLabel?: string;
}

export function OcTag({ ref, ...props }: OcTagProps & { ref?: React.Ref<HTMLSpanElement> }) {
    const {
        startElement,
        endElement,
        onClose,
        closable = !!onClose,
        closeLabel,
        children,
        ...rest
    } = props;

    return (
        <ChakraTag.Root ref={ref} {...rest}>
            {startElement && <ChakraTag.StartElement>{startElement}</ChakraTag.StartElement>}
            <ChakraTag.Label>{children}</ChakraTag.Label>
            {endElement && <ChakraTag.EndElement>{endElement}</ChakraTag.EndElement>}
            {closable && (
                <ChakraTag.EndElement>
                    <ChakraTag.CloseTrigger aria-label={closeLabel} onClick={onClose} />
                </ChakraTag.EndElement>
            )}
        </ChakraTag.Root>
    );
}
