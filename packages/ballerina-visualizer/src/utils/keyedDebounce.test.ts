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

// L1: per-field diagnostics debouncing. A form debounces expression diagnostics per field
// key; a burst on one field must never drop another field's check, a superseded call must
// still settle (callers await it), and a response for an older value must be discarded.

import { createKeyedDebouncer } from "./keyedDebounce";

describe("createKeyedDebouncer", () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    it("INVARIANT: calls on different keys never cancel each other", async () => {
        const debouncer = createKeyedDebouncer(250);
        const ran: string[] = [];
        const keys = ["a", "b", "c"];
        const settled = keys.map((key) => debouncer.schedule(key, () => { ran.push(key); }));

        jest.advanceTimersByTime(250);
        await Promise.all(settled);

        expect(ran.sort()).toEqual(keys);
    });

    it("INVARIANT: only the last call per key runs, and every call settles", async () => {
        const debouncer = createKeyedDebouncer(250);
        const ran: number[] = [];
        const settled = [1, 2, 3].map((value) => debouncer.schedule("a", () => { ran.push(value); }));

        jest.advanceTimersByTime(250);
        await Promise.all(settled);

        expect(ran).toEqual([3]);
    });

    it("INVARIANT: an in-flight run is no longer latest once the key is scheduled again", async () => {
        const debouncer = createKeyedDebouncer(250);
        let release: () => void = () => undefined;
        const gate = new Promise<void>((resolve) => { release = resolve; });
        let firstWasLatest: boolean | undefined;

        const first = debouncer.schedule("a", async (isLatest) => {
            await gate;
            firstWasLatest = isLatest();
        });
        jest.advanceTimersByTime(250);
        const second = debouncer.schedule("a", () => undefined);
        // Another key does not invalidate it.
        const other = debouncer.schedule("b", () => undefined);

        release();
        jest.advanceTimersByTime(250);
        await Promise.all([first, second, other]);

        expect(firstWasLatest).toBe(false);
    });

    it("INVARIANT: a run with no newer call for its key is latest", async () => {
        const debouncer = createKeyedDebouncer(250);
        let latest: boolean | undefined;
        const settled = debouncer.schedule("a", (isLatest) => { latest = isLatest(); });
        debouncer.schedule("b", () => undefined);

        jest.advanceTimersByTime(250);
        await settled;

        expect(latest).toBe(true);
    });

    it("INVARIANT: a failing task and a cancel both settle the caller", async () => {
        const debouncer = createKeyedDebouncer(250);
        const errorSpy = jest.spyOn(console, "error").mockImplementation(() => undefined);
        const failing = debouncer.schedule("a", () => { throw new Error("boom"); });
        jest.advanceTimersByTime(250);
        await expect(failing).resolves.toBeUndefined();
        errorSpy.mockRestore();

        const task = jest.fn();
        const cancelled = debouncer.schedule("b", task);
        debouncer.cancel();
        await expect(cancelled).resolves.toBeUndefined();
        jest.advanceTimersByTime(250);
        expect(task).not.toHaveBeenCalled();
    });
});
