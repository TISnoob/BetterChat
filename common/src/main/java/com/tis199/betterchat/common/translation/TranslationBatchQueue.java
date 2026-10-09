package com.tis199.betterchat.common.translation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Coalesces a short burst of chat into one provider request for the active listener locales. */
public final class TranslationBatchQueue implements AutoCloseable {
    public record Result(String original, Map<String, String> byLanguage) { }
    private record Job(String original, Set<String> targets, String sourceHint, Consumer<Result> callback) { }
    private record BatchKey(Set<String> targets, String sourceHint) { }

    private final TranslationDispatcher dispatcher;
    private final ArrayBlockingQueue<Job> queue;
    private final ScheduledExecutorService timer;
    private final long windowMillis;
    private final int batchSize;
    private volatile boolean closed;

    public TranslationBatchQueue(TranslationDispatcher dispatcher, int capacity, long windowMillis, int batchSize) {
        this.dispatcher = dispatcher;
        this.queue = new ArrayBlockingQueue<>(Math.max(32, capacity));
        this.windowMillis = Math.max(0, windowMillis);
        this.batchSize = Math.max(1, batchSize);
        this.timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "BetterChat-Translation-Batcher");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(this::flush, this.windowMillis, Math.max(10, this.windowMillis), TimeUnit.MILLISECONDS);
    }

    public boolean submit(String message, Set<String> targetLanguages, String probableSourceLanguage, Consumer<Result> callback) {
        if (closed || targetLanguages.isEmpty()) return false;
        return queue.offer(new Job(message, Set.copyOf(targetLanguages), probableSourceLanguage, callback));
    }

    private void flush() {
        if (closed) return;
        List<Job> jobs = new ArrayList<>(batchSize);
        queue.drainTo(jobs, batchSize);
        if (jobs.isEmpty()) return;
        Map<BatchKey, List<Job>> groups = new java.util.LinkedHashMap<>();
        for (Job job : jobs) {
            BatchKey key = new BatchKey(job.targets(), job.sourceHint());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(job);
        }
        groups.forEach((key, batchJobs) -> {
            List<String> texts = batchJobs.stream().map(Job::original).toList();
            dispatcher.translate(texts, List.copyOf(key.targets()), key.sourceHint()).whenComplete((batch, failure) -> {
                for (int index = 0; index < batchJobs.size(); index++) {
                    Job job = batchJobs.get(index);
                    Map<String, String> translations = new java.util.LinkedHashMap<>();
                    if (failure == null) {
                        for (String target : job.targets()) {
                            List<String> rows = batch.get(target);
                            if (rows != null && rows.size() > index) translations.put(target, rows.get(index));
                        }
                    }
                    try {
                        job.callback().accept(new Result(job.original(), Map.copyOf(translations)));
                    } catch (RuntimeException ignored) {
                        // One platform callback must not prevent the rest of a batch from completing.
                    }
                }
            });
        });
    }

    @Override
    public void close() {
        closed = true;
        timer.shutdownNow();
        Job job;
        while ((job = queue.poll()) != null) job.callback().accept(new Result(job.original(), Map.of()));
    }
}
