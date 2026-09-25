import org.sigma.mobileprobe.BenchmarkEngine;
import org.sigma.mobileprobe.CpuParsers;
import org.sigma.mobileprobe.Stats;
import org.sigma.mobileprobe.SustainedAccumulator;

import java.util.Arrays;

public final class PureJvmTests {
    private static int passed = 0;

    private static void check(boolean cond, String name) {
        if (!cond) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) throws Exception {
        check(CpuParsers.parseCpuList("0-3,5,7-8").equals(Arrays.asList(0,1,2,3,5,7,8)), "cpu_list_parse");
        check(Math.abs(CpuParsers.parseCpuMaxCores("400000 100000") - 4.0) < 1e-12, "cpu_max_parse");
        check(CpuParsers.parseCpuMaxCores("max 100000") == null, "cpu_max_unlimited");
        check(CpuParsers.effectiveCpuLimit(8, 6, 4.9) == 4, "effective_cpu_limit");
        check(Stats.median(new long[]{5,1,3}) == 3L, "median_long_odd");
        check(Stats.median(new long[]{10,2,6,4}) == 5L, "median_long_even");
        check(Math.abs(Stats.median(new double[]{1.0,4.0,2.0,3.0}) - 2.5) < 1e-12, "median_double_even");

        long c1;
        long c2;
        try (BenchmarkEngine.Session s = new BenchmarkEngine.Session(2, () -> -1L)) {
            BenchmarkEngine.RunResult a = s.run(50_000);
            BenchmarkEngine.RunResult b = s.run(50_000);
            check(a.operations == 100_000L && b.operations == 100_000L, "benchmark_accounting");
            check(a.observedThreads == 2 && b.observedThreads == 2, "benchmark_observed_threads");
            c1 = a.checksum;
            c2 = b.checksum;

            SustainedAccumulator acc = new SustainedAccumulator();
            acc.add(a);
            acc.add(b);
            check(acc.rounds() == 2, "sustained_round_accounting");
            check(acc.totalOperations() == 200_000L, "sustained_operation_accounting");
            check(acc.computeWallNs() > 0 && acc.aggregateOperationsPerSecond() > 0.0, "sustained_throughput_positive");
            check(acc.checksumConsistent() && acc.observedThreadsMin() == 2 && acc.observedThreadsMax() == 2,
                    "sustained_checksum_and_threads");
        }
        check(c1 == c2, "benchmark_deterministic_checksum");

        boolean rejected = false;
        try { new BenchmarkEngine.Session(0, () -> -1L); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "zero_workers_rejected");

        System.out.println("TOTAL_PASS=" + passed);
    }
}
