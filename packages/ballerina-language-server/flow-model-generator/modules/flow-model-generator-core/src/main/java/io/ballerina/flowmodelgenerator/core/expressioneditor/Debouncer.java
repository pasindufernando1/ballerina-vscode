/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.flowmodelgenerator.core.expressioneditor;

import io.ballerina.flowmodelgenerator.core.expressioneditor.services.DebouncedExpressionEditorRequest;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Debouncing ensures that a task is only executed after a specified delay has passed since its last invocation,
 * cancelling any pending executions in between. This class follows the Singleton pattern, ensuring only one instance
 * exists across the application.
 *
 * @since 1.0.0
 */
public class Debouncer {

    // Time unit for the delay
    private static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    // Map to hold scheduled tasks
    private final ConcurrentHashMap<String, ScheduledTaskHolder<?>> delayedMap;

    // Single-thread scheduler to debounce tasks.
    private final ScheduledExecutorService scheduler;

    private Debouncer() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        delayedMap = new ConcurrentHashMap<>();
    }

    /**
     * Debounce the given DebouncedExpressionEditorApi request by scheduling it to execute after the provided delay.
     * Any previously scheduled task with the same key is cancelled.
     */
    public <T> CompletableFuture<T> debounce(DebouncedExpressionEditorRequest<T> request) {
        long delay = request.getDelay();
        String key = request.getKey();
        CompletableFuture<T> promise = new CompletableFuture<>();
        AtomicReference<ScheduledTaskHolder<T>> holder = new AtomicReference<>();

        // Schedule the task to run after the specified delay.
        Future<?> scheduledFuture = scheduler.schedule(() -> {
            try {
                T result = request.call();
                promise.complete(result);
            } catch (Exception ex) {
                promise.completeExceptionally(ex);
            } finally {
                if (promise.isCompletedExceptionally()) {
                    request.revertDocument();
                }
                // Only drop this task's own entry, as a newer task may already hold the key
                delayedMap.remove(key, holder.get());
            }
        }, delay, TIME_UNIT);

        // Replace any existing scheduled task with the new one.
        holder.set(new ScheduledTaskHolder<>(promise, scheduledFuture));
        @SuppressWarnings("unchecked")
        ScheduledTaskHolder<T> prev = (ScheduledTaskHolder<T>) delayedMap.put(key, holder.get());
        if (prev != null) {
            // A task that already started is left to finish, since interrupting it could stop it between modifying
            // the document and reverting it
            prev.future().cancel(false);
            prev.promise().completeExceptionally(new CancellationException("Debounced by a new request"));
        }
        return promise;
    }

    public static Debouncer getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {

        private static final Debouncer INSTANCE = new Debouncer();
    }

    /**
     * Holder for scheduled task information.
     *
     * @param <T>     the type of result promised by the CompletableFuture.
     * @param promise the CompletableFuture that will eventually complete with the result of the scheduled task.
     * @param future  the Future representing the scheduled task, allowing for control over task execution.
     */
    private record ScheduledTaskHolder<T>(CompletableFuture<T> promise, Future<?> future) {
    }
}
