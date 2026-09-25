package org.sigma.mobileprobe;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProbeCollector {
    private ProbeCollector() {}

    public static JSONObject collect(Context context) throws Exception {
        JSONObject root = new JSONObject();
        JSONObject readFailures = new JSONObject();
        root.put("schema_version", "SIGMA_MOBILE_PROBE_R1");
        root.put("timestamp_utc", Instant.now().toString());
        root.put("platform", "android");
        root.put("device_model_code", Build.MODEL);
        root.put("manufacturer", Build.MANUFACTURER);
        root.put("architecture", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : JSONObject.NULL);
        root.put("supported_abis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));
        root.put("android_version", Build.VERSION.RELEASE);
        root.put("sdk_int", Build.VERSION.SDK_INT);
        root.put("security_patch", Build.VERSION.SECURITY_PATCH);
        root.put("kernel_version", System.getProperty("os.version", "unknown"));

        int runtimeCpus = Runtime.getRuntime().availableProcessors();
        root.put("logical_cpus_runtime", runtimeCpus);
        String present = readText("/sys/devices/system/cpu/present", readFailures);
        String online = readText("/sys/devices/system/cpu/online", readFailures);
        String possible = readText("/sys/devices/system/cpu/possible", readFailures);
        root.put("cpu_present", nullable(present));
        root.put("cpu_online", nullable(online));
        root.put("cpu_possible", nullable(possible));

        String allowedList = readStatusField("Cpus_allowed_list", readFailures);
        Integer allowedCount = null;
        if (allowedList != null) {
            try { allowedCount = CpuParsers.parseCpuList(allowedList).size(); }
            catch (Exception e) { readFailures.put("Cpus_allowed_list_parse", e.toString()); }
        }
        root.put("cpus_allowed_list", nullable(allowedList));
        root.put("cpu_allowed_count", allowedCount == null ? JSONObject.NULL : allowedCount);

        String cpuMaxV2 = readText("/sys/fs/cgroup/cpu.max", readFailures);
        String quotaV1 = readText("/sys/fs/cgroup/cpu/cpu.cfs_quota_us", readFailures);
        String periodV1 = readText("/sys/fs/cgroup/cpu/cpu.cfs_period_us", readFailures);
        Double quotaCores = null;
        String quotaSource = null;
        if (cpuMaxV2 != null) {
            try {
                quotaCores = CpuParsers.parseCpuMaxCores(cpuMaxV2);
                quotaSource = "cgroup_v2_cpu.max";
            } catch (Exception e) { readFailures.put("cpu.max_parse", e.toString()); }
        } else if (quotaV1 != null && periodV1 != null) {
            try {
                long q = Long.parseLong(quotaV1.trim());
                long per = Long.parseLong(periodV1.trim());
                if (q > 0 && per > 0) quotaCores = (double) q / (double) per;
                quotaSource = "cgroup_v1_cfs";
            } catch (Exception e) { readFailures.put("cgroup_v1_cpu_parse", e.toString()); }
        }
        root.put("cpu_max_v2_raw", nullable(cpuMaxV2));
        root.put("cpu_quota_v1_raw", nullable(quotaV1));
        root.put("cpu_period_v1_raw", nullable(periodV1));
        root.put("cpu_quota_source", nullable(quotaSource));
        root.put("cpu_quota_cores", quotaCores == null ? JSONObject.NULL : quotaCores);
        root.put("effective_cpu_limit", CpuParsers.effectiveCpuLimit(runtimeCpus, allowedCount, quotaCores));

        List<Integer> cpuIds = new ArrayList<>();
        try { if (present != null) cpuIds.addAll(CpuParsers.parseCpuList(present)); }
        catch (Exception e) { readFailures.put("cpu_present_parse", e.toString()); }
        if (cpuIds.isEmpty()) {
            try { if (allowedList != null) cpuIds.addAll(CpuParsers.parseCpuList(allowedList)); }
            catch (Exception e) { readFailures.put("cpu_allowed_parse_for_ids", e.toString()); }
        }
        if (cpuIds.isEmpty()) {
            for (int i = 0; i < runtimeCpus; i++) cpuIds.add(i);
        }
        root.put("cpu_ids_probed", new JSONArray(cpuIds));

        JSONArray perCpu = new JSONArray();
        for (int i : cpuIds) {
            JSONObject c = new JSONObject();
            c.put("cpu", i);
            c.put("cpuinfo_min_freq_khz", nullable(readText("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_min_freq", readFailures)));
            c.put("cpuinfo_max_freq_khz", nullable(readText("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq", readFailures)));
            c.put("scaling_min_freq_khz", nullable(readText("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_min_freq", readFailures)));
            c.put("scaling_max_freq_khz", nullable(readText("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_max_freq", readFailures)));
            perCpu.put(c);
        }
        root.put("per_cpu_frequency", perCpu);

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        JSONObject mem = new JSONObject();
        mem.put("android_total_mem_bytes", mi.totalMem);
        mem.put("android_avail_mem_bytes", mi.availMem);
        mem.put("android_threshold_bytes", mi.threshold);
        mem.put("android_low_memory", mi.lowMemory);
        mem.put("runtime_max_memory_bytes", Runtime.getRuntime().maxMemory());
        mem.put("runtime_total_memory_bytes", Runtime.getRuntime().totalMemory());
        mem.put("runtime_free_memory_bytes", Runtime.getRuntime().freeMemory());
        Map<String,String> procMem = readMeminfo(readFailures);
        mem.put("proc_memtotal_kib", nullable(procMem.get("MemTotal")));
        mem.put("proc_memavailable_kib", nullable(procMem.get("MemAvailable")));
        mem.put("proc_swaptotal_kib", nullable(procMem.get("SwapTotal")));
        mem.put("proc_swapfree_kib", nullable(procMem.get("SwapFree")));
        root.put("memory", mem);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        JSONObject thermal = new JSONObject();
        int thermalStatus = Build.VERSION.SDK_INT >= 29 ? pm.getCurrentThermalStatus() : -1;
        thermal.put("status_code", thermalStatus);
        thermal.put("status", thermalName(thermalStatus));
        root.put("thermal", thermal);

        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        JSONObject bat = new JSONObject();
        if (battery != null) {
            int tempTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            bat.put("temperature_c", tempTenths == Integer.MIN_VALUE ? JSONObject.NULL : tempTenths / 10.0);
            bat.put("level_percent", (level >= 0 && scale > 0) ? (100.0 * level / scale) : JSONObject.NULL);
            bat.put("status_code", status);
        }
        root.put("battery", bat);

        List<String> requested = requestedPermissions(context);
        JSONObject sandbox = new JSONObject();
        sandbox.put("type", "ANDROID_UID_SANDBOX");
        sandbox.put("app_uid", Process.myUid());
        sandbox.put("requested_permissions", new JSONArray(requested));
        sandbox.put("internet_permission_declared", requested.contains(Manifest.permission.INTERNET));
        sandbox.put("network_test_required", false);
        sandbox.put("isolation_contract_pass", !requested.contains(Manifest.permission.INTERNET));
        root.put("sandbox", sandbox);
        root.put("read_failures", readFailures);
        return root;
    }

    private static List<String> requestedPermissions(Context context) throws Exception {
        PackageInfo info;
        if (Build.VERSION.SDK_INT >= 33) {
            info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
        } else {
            info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        }
        if (info.requestedPermissions == null) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(info.requestedPermissions));
    }

    private static Map<String,String> readMeminfo(JSONObject failures) {
        Map<String,String> out = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(0, colon).trim();
                if (key.equals("MemTotal") || key.equals("MemAvailable") || key.equals("SwapTotal") || key.equals("SwapFree")) {
                    String value = line.substring(colon + 1).trim().replace(" kB", "");
                    out.put(key, value);
                }
            }
        } catch (Exception e) {
            try { failures.put("/proc/meminfo", e.toString()); } catch (Exception ignored) {}
        }
        return out;
    }

    private static String readStatusField(String wanted, JSONObject failures) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(wanted + ":")) return line.substring(line.indexOf(':') + 1).trim();
            }
        } catch (Exception e) {
            try { failures.put("/proc/self/status", e.toString()); } catch (Exception ignored) {}
        }
        return null;
    }

    private static String readText(String path, JSONObject failures) {
        File f = new File(path);
        if (!f.exists() || !f.canRead()) {
            try { failures.put(path, "UNREADABLE_OR_ABSENT"); } catch (Exception ignored) {}
            return null;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            return line == null ? null : line.trim();
        } catch (Exception e) {
            try { failures.put(path, e.toString()); } catch (Exception ignored) {}
            return null;
        }
    }

    private static Object nullable(String s) { return s == null ? JSONObject.NULL : s; }

    public static String thermalName(int code) {
        switch (code) {
            case PowerManager.THERMAL_STATUS_NONE: return "NONE";
            case PowerManager.THERMAL_STATUS_LIGHT: return "LIGHT";
            case PowerManager.THERMAL_STATUS_MODERATE: return "MODERATE";
            case PowerManager.THERMAL_STATUS_SEVERE: return "SEVERE";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "CRITICAL";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "EMERGENCY";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "SHUTDOWN";
            default: return "UNKNOWN";
        }
    }
}
