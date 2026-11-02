// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Box,
    Tabs,
    type TabsIndicatorProps,
    type TabsListProps,
    type TabsRootProps,
    type TabsTriggerProps,
} from "@chakra-ui/react";
import { type ReactElement } from "react";
import { OcInputGroup } from "../forms/OcInputGroup";
import { Tooltip } from "../overlays/Tooltip";

export interface OcTabSelectProps {
    label?: string;
    defaultValue: string;
    options: { label: string; value: string }[];
    value?: string;
    rootProps?: TabsRootProps;
    listProps?: TabsListProps;
    triggerProps?: Partial<TabsTriggerProps>;
    indicatorProps?: TabsIndicatorProps;
    disabled?: boolean;
    /** Explains why the control is disabled, surfaced as a tooltip. Ignored unless `disabled`. */
    disabledReason?: string;
    onChange?: (value: string) => void;
}

export function OcTabSelect(props: OcTabSelectProps): ReactElement {
    const select = props.label ? (
        <OcInputGroup label={props.label}>
            <TabSelect {...props} />
        </OcInputGroup>
    ) : (
        <TabSelect {...props} />
    );

    if (!props.disabled || props.disabledReason == null) return select;

    // Ark disabled controls emit no pointer events, so the tooltip needs this span wrapper;
    // tabIndex keeps the reason keyboard-reachable, and block full width keeps the `w="full"`
    // Tabs.Root from collapsing.
    return (
        <Tooltip content={props.disabledReason} uppercase={false}>
            <Box as="span" tabIndex={0} display="block" w="full">
                {select}
            </Box>
        </Tooltip>
    );
}

function TabSelect({
    value,
    defaultValue,
    options,
    onChange,
    ...rest
}: OcTabSelectProps): ReactElement {
    return (
        <Tabs.Root
            w="full"
            h="10"
            border="none"
            variant="plain"
            defaultValue={defaultValue}
            value={value}
            onValueChange={(e) => {
                if (onChange) onChange(e.value);
            }}
            {...rest.rootProps}
        >
            <Tabs.List
                w="full"
                h="full"
                p="1"
                bg="bg.subtle"
                borderWidth="1px"
                borderColor="slate.border.primary"
                {...rest.listProps}
            >
                {options.map(({ label, value: v }) => (
                    <Tabs.Trigger
                        w="full"
                        key={v}
                        value={v}
                        justifyContent="center"
                        fontSize="xs"
                        minH="0"
                        h="full"
                        p="0"
                        // Selection comes from Ark's own state, not from comparing against the
                        // `value` prop: that prop is optional, so an uncontrolled tab-select left
                        // every trigger on the unselected colour and nothing looked chosen.
                        color="fg"
                        _selected={{ color: "bg" }}
                        fontWeight="medium"
                        transition="all .1s ease-out"
                        disabled={rest.disabled}
                        {...rest.triggerProps}
                    >
                        {label}
                    </Tabs.Trigger>
                ))}
                {/* The selected option carries the active colour so it reads as chosen. On the
                    subtle slate the indicator used to use, the selected label sat at `bg` and
                    looked greyed out — dimmer than the option beside it. */}
                <Tabs.Indicator
                    borderRadius="0"
                    bg="active"
                    borderColor="active"
                    borderWidth="1px"
                    boxShadow="active"
                    {...rest.indicatorProps}
                />
            </Tabs.List>
        </Tabs.Root>
    );
}
