// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { Input, InputGroup, type InputGroupProps, type InputProps } from "@chakra-ui/react";
import { IoFilterSharp } from "react-icons/io5";

export interface FilterInputProps {
    inputProps?: InputProps;
    groupProps?: InputGroupProps;
}

export function FilterInput(props: FilterInputProps) {
    return (
        <InputGroup startElement={<IoFilterSharp />} {...props.groupProps}>
            <Input {...props.inputProps} />
        </InputGroup>
    );
}
