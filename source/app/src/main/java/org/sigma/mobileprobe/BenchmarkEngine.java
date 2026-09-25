package org.sigma.mobileprobe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

public final class BenchmarkEngine {
    private BenchmarkEngine() {}

    public static final class RunResult {
        public final int configuredWorkers;
        public final int observedThreads;
        public final long iterationsPerWorker;
        public final long operations;
        public final long wallTimeNs;
        public final long workerCpuTimeNs;
        public final long checksum;

        RunResult(int configuredWorkers, int observedThreads, long iterationsPerWorker,
                  long operations, long wallTimeNs, long workerCpuTimeNs, long checksum) {
            this.configuredWorkers = configuredWorkers;
            this.observedThreads = observedThreads;
            this.iterationsPerWorker = iterationsPerWorker;
            this.operations = operations;
            this.wallTimeNs = wallTimeNs;
            this.workerCpuTimeNs = workerCpuTimeNs;
            this.checksum = checksum;
        }

        public double operationsPerSecond() {
            return wallTimeNs <= 0 ? 0.0 : operations * 1_000_000_000.0 / wallTimeNs;
        }
    }

    private static final class WorkerResult {
        final int workerId;
        final long threadId;
        final long cpuNs;
        final long checksum;
        WorkerResult(int workerId, long threadId, long cpuNs, long checksum) {
            this.workerId = workerId;
            this.threadId = threadId;
            this.cpuNs = cpuNs;
            this.checksum = checksum;
        }
    }

    public static final class Session implements AutoCloseable {
        private final int workers;
        private final ExecutorService pool;
        private final LongSupplier threadCpuClock;

        public Session(int workers, LongSupplier threadCpuClock) {
            if (workers < 1) throw new IllegalArgumentException("workers must be >= 1");
            this.workers = workers;
            this.threadCpuClock = threadCpuClock == null ? () -> -1L : threadCpuClock;
            this.pool = Executors.newFixedThreadPool(workers);
        }

        public RunResult run(long iterationsPerWorker) throws Exception {
            if (iterationsPerWorker < 1) throw new IllegalArgumentException("iterations must be >= 1");
            CountDownLatch ready = new CountDownLatch(workers);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<WorkerResult>> futures = new ArrayList<>();
            for (int wid = 0; wid < workers; wid++) {
                final int workerId = wid;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    long cpuStart = threadCpuClock.getAsLong();
                    long checksum = kernel(workerId, iterationsPerWorker);
                    long cpuEnd = threadCpuClock.getAsLong();
                    long cpuDelta = (cpuStart >= 0 && cpuEnd >= cpuStart) ? (cpuEnd - cpuStart) : -1L;
                    return new WorkerResult(workerId, Thread.currentThread().getId(), cpuDelta, checksum);
                }));
            }
            ready.await();
            long wallStart = System.nanoTime();
            start.countDown();
            Set<Long> threads = new HashSet<>();
            long cpuSum = 0L;
            boolean cpuKnown = true;
            long checksum = 0L;
            for (Future<WorkerResult> f : futures) {
                WorkerResult r = f.get();
                threads.add(r.threadId);
                checksum ^= Long.rotateLeft(r.checksum, r.workerId & 63);
                if (r.cpuNs < 0) cpuKnown = false; else cpuSum += r.cpuNs;
            }
            long wall = System.nanoTime() - wallStart;
            return new RunResult(
                    workers,
                    threads.size(),
                    iterationsPerWorker,
                    Math.multiplyExact((long) workers, iterationsPerWorker),
                    wall,
                    cpuKnown ? cpuSum : -1L,
                    checksum);
        }

        @Override public void close() {
            pool.shutdownNow();
        }
    }

    static long kernel(int workerId, long iterations) {
        long x = 0x9E3779B97F4A7C15L ^ (0xD1B54A32D192ED03L * (workerId + 1L));
        for (long i = 0; i < iterations; i++) {
            x ^= (x >>> 12);
            x ^= (x << 25);
            x ^= (x >>> 27);
            x *= 0x2545F4914F6CDD1DL;
            x += i + workerId;
        }
        return x;
    }
}
