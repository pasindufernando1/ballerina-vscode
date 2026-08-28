/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

// A union that mixes singleton constants with records or primitives — e.g. the AWS
// `StaticAuthConfig|...|DEFAULT_CREDENTIALS` auth field, or `"INFER_TOOL_COUNT"|int` —
// is sent by the LS as three `types` entries. The switcher used to keep only the first
// and last, so the SINGLE_SELECT in between never reached the user.

import React from "react";
import { fireEvent, screen } from "@testing-library/react";
import type { InputType } from "@wso2/ballerina-core";
import { renderWithForm } from "./formHarness";
import ModeSwitcher from "../components/ModeSwitcher";
import { getRenderingTypes } from "../components/editors/FieldFactory";
import { InputMode } from "../components/editors/MultiModeExpressionEditor/ChipExpressionEditor/types";

const type = (fieldType: string, extra: Record<string, unknown> = {}): InputType =>
    ({ fieldType, selected: false, ...extra } as unknown as InputType);

// ballerinax/aws.sqs `auth`: six record members, the DEFAULT_CREDENTIALS constant, then the fallback
const AUTH_TYPES: InputType[] = [
    type("RECORD_MAP_EXPRESSION", { ballerinaType: "auth:StaticAuthConfig|auth:ProfileAuthConfig" }),
    type("SINGLE_SELECT", { options: [{ label: "DEFAULT_CREDENTIALS", value: '"DEFAULT_CREDENTIALS"' }] }),
    type("EXPRESSION", { ballerinaType: "auth:AuthConfig" }),
];

// ai agent `maxIter`: "INFER_TOOL_COUNT"|int
const MAX_ITER_TYPES: InputType[] = [
    type("SINGLE_SELECT", { options: [{ label: "INFER_TOOL_COUNT", value: '"INFER_TOOL_COUNT"' }] }),
    type("NUMBER", { ballerinaType: "int" }),
    type("EXPRESSION", { ballerinaType: '"INFER_TOOL_COUNT"|int' }),
];

const TWO_MODE_TYPES: InputType[] = [
    type("RECORD_MAP_EXPRESSION", { ballerinaType: "aws:EndpointConfig" }),
    type("EXPRESSION", { ballerinaType: "aws:EndpointConfig" }),
];

const renderSwitcher = (types: InputType[], value: InputMode, onChange = jest.fn()) => {
    const utils = renderWithForm(
        <ModeSwitcher
            fieldKey="auth"
            value={value}
            isRecordTypeField={false}
            onChange={onChange}
            types={types}
        />,
        { defaultValues: { auth: "" } }
    );
    return { ...utils, onChange };
};

describe("getRenderingTypes", () => {
    it("keeps the SINGLE_SELECT that sits between the narrowed type and the fallback", () => {
        expect(getRenderingTypes(AUTH_TYPES).map(t => t.fieldType))
            .toEqual(["RECORD_MAP_EXPRESSION", "SINGLE_SELECT", "EXPRESSION"]);
    });

    it("keeps a middle NUMBER member alongside a leading SINGLE_SELECT", () => {
        expect(getRenderingTypes(MAX_ITER_TYPES).map(t => t.fieldType))
            .toEqual(["SINGLE_SELECT", "NUMBER", "EXPRESSION"]);
    });

    it("REGRESSION: leaves the common two-type field untouched", () => {
        expect(getRenderingTypes(TWO_MODE_TYPES)).toEqual(TWO_MODE_TYPES);
    });

    it("REGRESSION: a single-type field stays single", () => {
        expect(getRenderingTypes([TWO_MODE_TYPES[0]])).toEqual([TWO_MODE_TYPES[0]]);
    });

    it("drops a middle member whose mode already has a segment", () => {
        const duplicate = [
            type("TEXT", { ballerinaType: "string" }),
            type("TEXT", { ballerinaType: "string:Char" }),
            type("EXPRESSION", { ballerinaType: "string|string:Char" }),
        ];
        expect(getRenderingTypes(duplicate).map(t => t.fieldType)).toEqual(["TEXT", "EXPRESSION"]);
    });
});

describe("ModeSwitcher", () => {
    it("INVARIANT: renders one segment per declared type", () => {
        renderSwitcher(AUTH_TYPES, InputMode.RECORD);
        expect(screen.getByText(InputMode.RECORD)).toBeInTheDocument();
        expect(screen.getByText(InputMode.SELECT)).toBeInTheDocument();
        expect(screen.getByText(InputMode.EXP)).toBeInTheDocument();
    });

    it("switches to the middle mode when its segment is clicked", () => {
        const { onChange } = renderSwitcher(AUTH_TYPES, InputMode.RECORD);
        fireEvent.click(screen.getByText(InputMode.SELECT));
        expect(onChange).toHaveBeenCalledWith(InputMode.SELECT);
    });

    it("REGRESSION: two-mode fields keep the primary-mode / expression-mode handles", () => {
        renderSwitcher(TWO_MODE_TYPES, InputMode.RECORD);
        expect(screen.getByTestId("primary-mode")).toHaveTextContent(InputMode.RECORD);
        expect(screen.getByTestId("expression-mode")).toHaveTextContent(InputMode.EXP);
        expect(screen.queryByTestId(`mode-${InputMode.SELECT}`)).not.toBeInTheDocument();
    });

    it("keeps the first and last handles addressable when a middle mode exists", () => {
        renderSwitcher(AUTH_TYPES, InputMode.SELECT);
        expect(screen.getByTestId("primary-mode")).toHaveTextContent(InputMode.RECORD);
        expect(screen.getByTestId("expression-mode")).toHaveTextContent(InputMode.EXP);
        expect(screen.getByTestId(`mode-${InputMode.SELECT}`)).toBeInTheDocument();
    });
});
