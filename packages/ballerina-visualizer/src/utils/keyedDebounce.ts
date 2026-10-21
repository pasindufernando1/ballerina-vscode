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

// Debounces work per key, so a burst on one key never cancels another key's pending run.
// Each run gets `isLatest()`, false once a newer call for the same key has been made, so
// a response for an older value can be dropped. Every returned promise settles: a
// superseded or cancelled call resolves without running.

export type KeyedTask = (isLatest: () => boolean) => Promise<void> | void;

export interface KeyedDebouncer {
    schedule: (key: string, task: KeyedTask) => Promise<void>;
    cancel: () => void;
}

interface PendingRun {
    timer: ReturnType<typeof setTimeout>;
    resolve: () => void;
}

export function createKeyedDebouncer(wait: number): KeyedDebouncer {
    const pending = new Map<string, PendingRun>();
    const sequence = new Map<string, number>();

    const schedule = (key: string, task: KeyedTask): Promise<void> => {
        const previous = pending.get(key);
        if (previous) {
            clearTimeout(previous.timer);
            previous.resolve();
        }
        const id = (sequence.get(key) ?? 0) + 1;
        sequence.set(key, id);

        return new Promise<void>((resolve) => {
            const timer = setTimeout(async () => {
                pending.delete(key);
                try {
                    await task(() => sequence.get(key) === id);
                } catch (error) {
                    console.error(">>> Debounced task failed", key, error);
                } finally {
                    resolve();
                }
            }, wait);
            pending.set(key, { timer, resolve });
        });
    };

    const cancel = () => {
        pending.forEach(({ timer, resolve }) => {
            clearTimeout(timer);
            resolve();
        });
        pending.clear();
        // Invalidate in-flight runs so their responses are dropped.
        sequence.forEach((id, key) => sequence.set(key, id + 1));
    };

    return { schedule, cancel };
}
