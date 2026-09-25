package org.sigma.mobileprobe;

import java.util.Arrays;

public final class Stats {
    private Stats() {}

    public static long median(long[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must be non-empty");
        }
        long[] copy = values.clone();
        Arrays.sort(copy);
        int n = copy.length;
        if ((n & 1) == 1) return copy[n / 2];
        long a = copy[n / 2 - 1];
        long b = copy[n / 2];
        return a + (b - a) / 2L;
    }

    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must be non-empty");
        }
        double[] copy = values.clone();
        Arrays.sort(copy);
        int n = copy.length;
        if ((n & 1) == 1) return copy[n / 2];
        return (copy[n / 2 - 1] + copy[n / 2]) / 2.0;
    }
}
