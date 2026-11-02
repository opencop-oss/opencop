// Copyright 2026 NexTech Solutions LLC
// SPDX-License-Identifier: Apache-2.0

// TODO: Dead code — not currently imported anywhere. Needs ../lib/constants module
//       and ToolSlider/ToolSwatch/ToolSelect prop interface updates before reuse.
//
// import { HStack, VStack } from "@chakra-ui/react";
// import ToolTacticalSlider from "./inputs/ToolSlider";
// import ToolTacticalSwatch from "./inputs/ToolSwatch";
// import ToolTacticalSelect from "./inputs/ToolSelect";
// import { ShapeLike } from "@opencop/elements";
// import { strokeStyleOptions } from "../lib/constants";
// import type { StrokeStyle } from "@opencop/elements";
// import type { ReactElement } from "react";
//
// export default function ShapeStyle(): ReactElement {
//     return (
//         <VStack w="full" h="full" justify="center" align="center" gap="4">
//             <HStack w="full" h="full" justify="space-between" align="center" gap="4">
//                 <ToolTacticalSwatch
//                     label="fill color"
//                     onChange={(v, m, e) => {
//                         if (e instanceof ShapeLike) {
//                             e.setFillColor(v);
//                             m.update(e, "getStrokeColor");
//                         }
//                     }}
//                 />
//                 <ToolTacticalSwatch
//                     label="stroke color"
//                     onChange={(v, m, e) => {
//                         if (e instanceof ShapeLike) {
//                             e.setStrokeColor(v);
//                             m.update(e, "getStrokeColor");
//                         }
//                     }}
//                 />
//             </HStack>
//             <ToolTacticalSelect
//                 label="stroke style"
//                 defaultValue={"solid"}
//                 options={strokeStyleOptions}
//                 onChange={(v, m, e) => {
//                     if (e instanceof ShapeLike) {
//                         e.setStrokeStyle(v as StrokeStyle);
//                         m.update(e, "getStrokeStyle");
//                     }
//                 }}
//             />
//             <ToolTacticalSlider
//                 label="stroke weight"
//                 min={1}
//                 max={10}
//                 defaultValue={3}
//                 step={0.01}
//                 onChange={(v, m, e) => {
//                     if (e instanceof ShapeLike) {
//                         e.setStrokeWeight(v);
//                         m.update(e, "getStrokeWeight");
//                     }
//                 }}
//             />
//         </VStack>
//     );
// }
