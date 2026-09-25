package org.sigma.mobileprobe;

import java.util.ArrayList;
import java.util.List;

public final class SustainedAccumulator {
    private long totalOperations;
    private long computeWallNs;
    private long workerCpuTimeNs;
    private boolean workerCpuKnown = true;
    private int rounds;
    private String firstChecksum;
    private boolean checksumConsistent = true;
    private int observedThreadsMin = Integer.MAX_VALUE;
    private int observedThreadsMax = Integer.MIN_VALUE;
    private final List<Double> roundThroughput = new ArrayList<>();

    public void add(BenchmarkEngine.RunResult r) {
        if (r == null) throw new IllegalArgumentException("result must not be null");
        totalOperations = Math.addExact(totalOperations, r.operations);
        computeWallNs = Math.addExact(computeWallNs, r.wallTimeNs);
        if (r.workerCpuTimeNs < 0) workerCpuKnown = false;
        else workerCpuTimeNs = Math.addExact(workerCpuTimeNs, r.workerCpuTimeNs);
        rounds++;
        String checksum = Long.toUnsignedString(r.checksum);
        if (firstChecksum == null) firstChecksum = checksum;
        else if (!firstChecksum.equals(checksum)) checksumConsistent = false;
        observedThreadsMin = Math.min(observedThreadsMin, r.observedThreads);
        observedThreadsMax = Math.max(observedThreadsMax, r.observedThreads);
        roundThroughput.add(r.operationsPerSecond());
    }

    public int rounds() { return rounds; }
    public long totalOperations() { return totalOperations; }
    public long computeWallNs() { return computeWallNs; }
    public Long workerCpuTimeNs() { return workerCpuKnown ? workerCpuTimeNs : null; }
    public boolean checksumConsistent() { return checksumConsistent; }
    public String checksum() { return firstChecksum; }
    public int observedThreadsMin() { return rounds == 0 ? 0 : observedThreadsMin; }
    public int observedThreadsMax() { return rounds == 0 ? 0 : observedThreadsMax; }

    public double aggregateOperationsPerSecond() {
        return computeWallNs <= 0 ? 0.0 : totalOperations * 1_000_000_000.0 / computeWallNs;
    }

    public double medianRoundOperationsPerSecond() {
        if (roundThroughput.isEmpty()) return 0.0;
        double[] values = new double[roundThroughput.size()];
        for (int i = 0; i < values.length; i++) values[i] = roundThroughput.get(i);
        return Stats.median(values);
    }

    public double cpuToComputeWallRatio() {
        if (!workerCpuKnown || computeWallNs <= 0) return Double.NaN;
        return (double) workerCpuTimeNs / (double) computeWallNs;
    }
}
