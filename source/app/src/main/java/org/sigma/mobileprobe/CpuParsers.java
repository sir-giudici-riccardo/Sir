package org.sigma.mobileprobe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CpuParsers {
    private CpuParsers() {}

    public static List<Integer> parseCpuList(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        List<Integer> out = new ArrayList<>();
        for (String raw : text.trim().split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            int dash = token.indexOf('-');
            if (dash < 0) {
                out.add(Integer.parseInt(token));
            } else {
                int a = Integer.parseInt(token.substring(0, dash).trim());
                int b = Integer.parseInt(token.substring(dash + 1).trim());
                if (b < a) throw new IllegalArgumentException("descending CPU range: " + token);
                for (int i = a; i <= b; i++) out.add(i);
            }
        }
        return out;
    }

    public static Double parseCpuMaxCores(String cpuMax) {
        if (cpuMax == null) return null;
        String[] parts = cpuMax.trim().split("\\s+");
        if (parts.length < 2 || parts[0].equals("max")) return null;
        long quota = Long.parseLong(parts[0]);
        long period = Long.parseLong(parts[1]);
        if (quota <= 0 || period <= 0) return null;
        return (double) quota / (double) period;
    }

    public static int effectiveCpuLimit(int runtimeCpus, Integer allowedCount, Double quotaCores) {
        int limit = Math.max(1, runtimeCpus);
        if (allowedCount != null && allowedCount > 0) limit = Math.min(limit, allowedCount);
        if (quotaCores != null && quotaCores > 0) {
            int quotaFloor = Math.max(1, (int) Math.floor(quotaCores + 1e-12));
            limit = Math.min(limit, quotaFloor);
        }
        return limit;
    }
}
