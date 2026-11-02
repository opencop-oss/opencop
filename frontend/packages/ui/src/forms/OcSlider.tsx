// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import {
    Input,
    Text,
    Slider,
    type SliderRootProps,
    type SliderThumbProps,
    type SliderTrackProps,
    type TextProps,
} from "@chakra-ui/react";
import { useState, useRef, type ReactElement } from "react";
import { OcInputGroup } from "./OcInputGroup";

interface OcSliderProps {
    min: number;
    max: number;
    step: number;
    label?: string;
    value?: number;
    units?: string;
    defaultValue?: number;
    editable?: boolean;
    ariaLabel?: string;
    rootProps?: SliderRootProps;
    labelProps?: TextProps;
    thumbProps?: SliderThumbProps;
    trackProps?: SliderTrackProps;
    textProps?: TextProps;
    onChange?: (value: number) => void;
}

export function OcSlider(props: OcSliderProps): ReactElement {
    const [v, setV] = useState<number>(props.defaultValue ?? props.min);
    const [isEditing, setIsEditing] = useState(false);
    const [editText, setEditText] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);

    const currentValue = props.value ?? v;

    const handleChange = (newValue: number) => {
        if (props.value == null) setV(newValue);
        if (props.onChange) props.onChange(newValue);
    };

    const commitEdit = () => {
        const parsed = parseFloat(editText);
        if (!Number.isNaN(parsed)) {
            const clamped = Math.min(props.max, Math.max(props.min, parsed));
            handleChange(clamped);
        }
        setIsEditing(false);
    };

    const valueDisplay = props.editable ? (
        isEditing ? (
            <Input
                ref={inputRef}
                size="xs"
                fontFamily="mono"
                fontSize="xs"
                w="14"
                p="1"
                h="auto"
                textAlign="right"
                value={editText}
                onChange={(e) => setEditText(e.target.value)}
                onBlur={commitEdit}
                onKeyDown={(e) => {
                    if (e.key === "Enter") commitEdit();
                    if (e.key === "Escape") setIsEditing(false);
                }}
            />
        ) : (
            <Text
                fontFamily="mono"
                textStyle="xs"
                cursor="text"
                onClick={() => {
                    setEditText(String(currentValue));
                    setIsEditing(true);
                    setTimeout(() => inputRef.current?.select(), 0);
                }}
                _hover={{ color: "active", textDecoration: "underline dotted" }}
            >
                {currentValue.toFixed(2) + (props.units ?? "")}
            </Text>
        )
    ) : (
        <Text fontFamily="mono" textStyle="xs">
            {currentValue.toFixed(2) + (props.units ?? "")}
        </Text>
    );

    return props.label ? (
        <OcInputGroup
            label={props.label}
            groupProps={{ bg: "none", border: "none" }}
            labelEnd={valueDisplay}
        >
            <_Slider {...props} value={currentValue} onChange={handleChange} />
        </OcInputGroup>
    ) : (
        <_Slider {...props} />
    );
}

function _Slider({
    defaultValue,
    min,
    max,
    step,
    onChange,
    value,
    ...rest
}: OcSliderProps): ReactElement {
    return (
        <Slider.Root
            justifyContent="center"
            w="full"
            h="10"
            value={value != null ? [value] : undefined}
            defaultValue={value == null && defaultValue != null ? [defaultValue] : undefined}
            onValueChange={(e) => {
                if (onChange) onChange(e.value[0]);
            }}
            min={min}
            max={max}
            step={step}
            gap="1"
            {...rest.rootProps}
        >
            <Slider.Control>
                <Slider.Track bg="border.muted" borderRadius="0" {...rest.trackProps}>
                    <Slider.Range />
                </Slider.Track>
                <Slider.Thumbs
                    borderRadius="0"
                    w="2"
                    borderWidth="1px"
                    borderColor="border"
                    bg="active"
                    boxShadow="active"
                    aria-label={rest.ariaLabel}
                    {...rest.thumbProps}
                />
            </Slider.Control>
        </Slider.Root>
    );
}
