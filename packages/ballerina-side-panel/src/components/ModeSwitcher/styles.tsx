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

import styled from "@emotion/styled";
import { ThemeColors } from '@wso2/ui-toolkit';

export interface Segment {
  left: number;
  width: number;
}

const MIN_SEGMENT_WIDTH_PX = 56;

/**
 * Horizontal layout of the switcher's segments, in percentages. The two-mode case keeps its
 * long-standing 40/60 split — the second label is the expression fallback and reads wider —
 * while three or more modes divide the track evenly.
 */
export const getSegments = (count: number): Segment[] => {
  if (count <= 1) {
    return [{ left: 0, width: 100 }];
  }
  if (count === 2) {
    return [{ left: 0, width: 40 }, { left: 40, width: 60 }];
  }
  const width = 100 / count;
  return Array.from({ length: count }, (_, index) => ({ left: index * width, width }));
};

export const getMinTrackWidth = (count: number) => Math.max(2, count) * MIN_SEGMENT_WIDTH_PX;

interface LabelProps {
  active: boolean;
  segment: Segment;
}

export const Label = styled.span<LabelProps>`
  position: absolute;
  text-align: center;
  font-size: 10px;
  z-index: 1;
  transition: all 0.2s ease;
  color: ${props => props.active ? ThemeColors.ON_SURFACE : ThemeColors.ON_SURFACE_VARIANT};
  font-weight: ${props => props.active ? '600' : '500'};
  top: 50%;
  transform: translateY(-50%);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  left: ${props => props.segment.left}%;
  width: ${props => props.segment.width}%;
`;

export const Slider = styled.div<{ segment: Segment; isFirst: boolean }>`
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: ${ThemeColors.SURFACE_CONTAINER};
  color: ${ThemeColors.ON_SURFACE};
  font-weight: 500;
  border-radius: 2px;
  display: flex;
  justify-content: flex-start;
  align-items: center;
  padding: 2px;
  transition: all 0.2s ease;
  border: 1px solid ${ThemeColors.OUTLINE_VARIANT};

  &:before {
    content: "";
    position: absolute;
    height: calc(100% - 4px);
    width: ${props => props.isFirst
      ? `calc(${props.segment.width}% - 2px)`
      : `calc(${props.segment.width}% - 4px)`};
    left: ${props => props.isFirst ? '2px' : `calc(${props.segment.left}% + 2px)`};
    border-radius: 1px;
    background: ${ThemeColors.SURFACE_DIM};
    transition: all 0.25s cubic-bezier(0.4, 0.0, 0.2, 1);
    z-index: 0;
    border: 1px solid ${ThemeColors.OUTLINE};
  }

  &:active:before {
    background: ${ThemeColors.SURFACE_DIM};
    box-shadow:
      0 1px 2px rgba(0, 0, 0, 0.3),
      inset 0 1px 0 rgba(255, 255, 255, 0.05);
    transform: translateY(1px);
  }
`;

export const SwitchWrapper = styled.div<{ segmentCount: number }>`
  font-size: 12px;
  position: relative;
  display: inline-flex;
  align-items: center;
  min-width: ${props => getMinTrackWidth(props.segmentCount)}px;
  width: max-content;
  height: 24px;
  margin-top: 2px;
`;
