// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// TODO: Dead code — not currently imported anywhere. Needs ../lib/constants module
//       and ToolSlider/ToolSwatch/ToolSelect prop interface updates before reuse.
//
// import { HStack, VStack } from "@chakra-ui/react";
// import type { ReactElement } from "react";
// import ToolTacticalSlider from "./inputs/ToolSlider";
// import { LineLike } from "@opencop/elements";
// import ToolTacticalSwatch from "./inputs/ToolSwatch";
// import ToolTacticalSelect from "./inputs/ToolSelect";
// import type { StrokeStyle } from "@opencop/elements";
// import { strokeStyleOptions } from "../lib/constants";
//
// export default function LineStyle(): ReactElement {
//     return (
//         <VStack w="full" h="full" justify="center" align="center">
//             <HStack w="full" h="full" justify="space-between" align="center" gap="2">
//                 <ToolTacticalSlider
//                     label="stroke weight"
//                     min={1}
//                     max={10}
//                     defaultValue={3}
//                     step={0.01}
//                     onChange={(v, m, e) => {
//                         if (e instanceof LineLike) {
//                             e.setStrokeWeight(v);
//                             m.update(e, "getStrokeWeight");
//                         }
//                     }}
//                 />
//                 <ToolTacticalSwatch
//                     label="stroke color"
//                     onChange={(v, m, e) => {
//                         if (e instanceof LineLike) {
//                             e.setStrokeColor(v);
//                             m.update(e, "getStrokeColor");
//                         }
//                     }}
//                 />
//             </HStack>
//
//             <ToolTacticalSelect
//                 label="stroke style"
//                 defaultValue={"solid"}
//                 options={strokeStyleOptions}
//                 onChange={(v, m, e) => {
//                     if (e instanceof LineLike) {
//                         e.setStrokeStyle(v as StrokeStyle);
//                         m.update(e, "getStrokeStyle");
//                     }
//                 }}
//             />
//         </VStack>
//     );
// }
