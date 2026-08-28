/**
 * Copyright (c) 2025, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
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

import React, { useMemo, useState } from 'react';
import { Label, Slider, SwitchWrapper, getSegments } from './styles';
import { InputMode } from '../editors/MultiModeExpressionEditor/ChipExpressionEditor/types';
import { getInputModeFromTypes } from '../editors/MultiModeExpressionEditor/ChipExpressionEditor/utils';
import { InputType } from '@wso2/ballerina-core';
import { getEditorConfiguration } from '../editors/ExpressionField';
import { useFormContext } from '../../context';
import WarningPopup from '../WarningPopup';

interface ModeSwitcherProps {
    value: InputMode;
    //TODO: Should be removed once fields with type field is fixed to
    // update the types property correctly when changing the type.
    isRecordTypeField: boolean;
    onChange: (value: InputMode) => void;
    types: InputType[];
    fieldKey: string;
}

const ModeSwitcher: React.FC<ModeSwitcherProps> = ({ value, isRecordTypeField, onChange, types, fieldKey }) => {

    const { form } = useFormContext();
    const { getValues, setValue } = form;
    const [showWarning, setShowWarning] = useState(false);
    const [pendingMode, setPendingMode] = useState<InputMode | null>(null);

    // One segment per type the field offers. Most fields have two (a narrowed mode and the
    // expression fallback), but a union mixing singletons with records or primitives has three.
    const modes = useMemo(
        //TODO: Should only derive from types once fields with type field is fixed to
        // update the types property correctly when changing the type.
        () => isRecordTypeField
            ? [InputMode.RECORD, InputMode.EXP]
            : (types ?? []).map(getInputModeFromTypes).filter(mode => mode !== undefined),
        [types, isRecordTypeField]
    );

    const segments = useMemo(() => getSegments(modes.length), [modes.length]);

    const handleModeSwitch = (mode: InputMode) => {
        const currentFieldValue = getValues(fieldKey);
        const configForNewMode = getEditorConfiguration(mode);
        let isValueCompatible = true;
        if (mode === InputMode.BOOLEAN) {
            isValueCompatible = false;
        }
        else {
            isValueCompatible = configForNewMode.getIsValueCompatible ? configForNewMode.getIsValueCompatible(currentFieldValue) : true;
        }

        if (!isValueCompatible) {
            setPendingMode(mode);
            setShowWarning(true);
        } else {
            onChange(mode);
        }
    };

    const handleConfirmSwitch = () => {
        if (pendingMode) {
            setValue(fieldKey, "", { shouldDirty: true });
            onChange(pendingMode);
            setPendingMode(null);
        }
        setShowWarning(false);
    };

    const handleCancelSwitch = () => {
        setPendingMode(null);
        setShowWarning(false);
    };

    const activeIndex = Math.max(0, modes.indexOf(value));

    return (
        <>
            <SwitchWrapper segmentCount={modes.length}>
                <Slider
                    segment={segments[activeIndex]}
                    isFirst={activeIndex === 0}
                    data-testid={`mode-switcher-slider-${fieldKey}`}
                >
                    {modes.map((mode, index) => (
                        <Label
                            key={mode}
                            // Kept for the existing two-mode tests, which address the segments by role
                            data-testid={index === 0 ? "primary-mode" : index === modes.length - 1 ? "expression-mode" : `mode-${mode}`}
                            segment={segments[index]}
                            active={index === activeIndex}
                            onClick={() => handleModeSwitch(mode)}
                        >
                            {mode}
                        </Label>
                    ))}
                </Slider>
            </SwitchWrapper>
            <WarningPopup
                isOpen={showWarning}
                onContinue={handleConfirmSwitch}
                onCancel={handleCancelSwitch}
            />
        </>
    );
};

export default ModeSwitcher;
