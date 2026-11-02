// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

import { useFormContext, type FieldValues, type Path, type RegisterOptions } from "react-hook-form";

/**
 * `register` that clears the field's error on each keystroke, so a user fixing a value is not shown
 * a stale error while typing; `mode: "onBlur"` re-validates on the next blur. Takes the same rules
 * as `register`, and runs any `onChange` given after clearing.
 */
export function useRegisterField<T extends FieldValues>() {
    const { register, clearErrors } = useFormContext<T>();
    return <N extends Path<T>>(name: N, options?: RegisterOptions<T, N>) =>
        register(name, {
            ...options,
            onChange: (event) => {
                clearErrors(name);
                return options?.onChange?.(event);
            },
        });
}
