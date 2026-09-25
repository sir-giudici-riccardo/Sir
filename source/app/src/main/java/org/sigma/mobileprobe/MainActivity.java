package org.sigma.mobileprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView output;
    private Button sweepButton;
    private Button stopButton;
    private Button summaryButton;
    private volatile String lastJson = "{}";
    private volatile String lastSummaryJson = "{}";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        Button probe = new Button(this);
        probe.setText("1 — Run read-only probe");
        root.addView(probe);

        Button copy = new Button(this);
        copy.setText("Copy full JSON");
        root.addView(copy);

        summaryButton = new Button(this);
        summaryButton.setText("Copy compact summary");
        summaryButton.setEnabled(false);
        root.addView(summaryButton);

        sweepButton = new Button(this);
        sweepButton.setText("2 — Run bounded parallelism sweep");
        sweepButton.setEnabled(false);
        root.addView(sweepButton);

        stopButton = new Button(this);
        stopButton.setText("Stop benchmark");
        stopButton.setEnabled(false);
        root.addView(stopButton);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        probe.setOnClickListener(v -> runProbe());
        copy.setOnClickListener(v -> copyJson());
        summaryButton.setOnClickListener(v -> copySummary());
        sweepButton.setOnClickListener(v -> runSweep());
        stopButton.setOnClickListener(v -> stopRequested.set(true));
        output.setText("Run the read-only probe first. No benchmark starts automatically.");
    }

    private void runProbe() {
        setOutput("Collecting read-only probe…");
        controlExecutor.submit(() -> {
            try {
                JSONObject probe = ProbeCollector.collect(this);
                lastJson = probe.toString(2);
                mainHandler.post(() -> {
                    output.setText(lastJson);
                    try {
                        boolean pass = probe.getJSONObject("sandbox").getBoolean("isolation_contract_pass");
                        sweepButton.setEnabled(pass);
                    } catch (Exception e) {
                        sweepButton.setEnabled(false);
                    }
                });
            } catch (Exception e) {
                setOutput("FAIL_CLOSED: " + e);
            }
        });
    }

    private void runSweep() {
        stopRequested.set(false);
        sweepButton.setEnabled(false);
        summaryButton.setEnabled(false);
        stopButton.setEnabled(true);
        setOutput("Running bounded sweep…\nUse Stop at any time.");
        controlExecutor.submit(() -> {
            try {
                JSONObject probe = ProbeCollector.collect(this);
                int thermalStart = probe.getJSONObject("thermal").getInt("status_code");
                if (thermalStart >= PowerManager.THERMAL_STATUS_SEVERE) {
                    throw new IllegalStateException("THERMAL_GATE_SEVERE_AT_START");
                }
                int limit = probe.getInt("effective_cpu_limit");
                int[] requested = new int[]{1,2,4,6,8};
                long iterations = calibrateIterations();

                JSONObject result = new JSONObject();
                result.put("schema_version", "SIGMA_MOBILE_SWEEP_R2");
                result.put("timestamp_utc", Instant.now().toString());
                result.put("probe", probe);
                result.put("calibrated_iterations_per_worker", iterations);
                result.put("warmup_runs_per_level", 3);
                result.put("measured_trials_per_level", 5);
                result.put("thermal_abort_threshold", "SEVERE");
                JSONArray groups = new JSONArray();

                for (int req : requested) {
                    if (stopRequested.get()) break;
                    int workers = Math.min(req, limit);
                    if (workers < 1) continue;
                    boolean duplicate = false;
                    for (int gi = 0; gi < groups.length(); gi++) {
                        if (groups.getJSONObject(gi).getInt("effective_workers") == workers) duplicate = true;
                    }
                    if (duplicate) continue;

                    JSONObject group = new JSONObject();
                    group.put("requested_workers", req);
                    group.put("effective_workers", workers);
                    JSONArray trials = new JSONArray();
                    try (BenchmarkEngine.Session session = new BenchmarkEngine.Session(
                            workers, () -> Debug.threadCpuTimeNanos())) {
                        for (int i = 0; i < 3; i++) {
                            if (stopRequested.get()) break;
                            session.run(iterations);
                        }
                        for (int i = 0; i < 5; i++) {
                            if (stopRequested.get()) break;
                            JSONObject before = ProbeCollector.collect(this);
                            int thermal = before.getJSONObject("thermal").getInt("status_code");
                            if (thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
                                group.put("aborted", "THERMAL_GATE_SEVERE");
                                stopRequested.set(true);
                                break;
                            }
                            BenchmarkEngine.RunResult r = session.run(iterations);
                            JSONObject after = ProbeCollector.collect(this);
                            JSONObject t = new JSONObject();
                            t.put("trial", i + 1);
                            t.put("configured_workers", r.configuredWorkers);
                            t.put("observed_threads", r.observedThreads);
                            t.put("iterations_per_worker", r.iterationsPerWorker);
                            t.put("operations", r.operations);
                            t.put("wall_time_ns", r.wallTimeNs);
                            t.put("worker_cpu_time_ns", r.workerCpuTimeNs < 0 ? JSONObject.NULL : r.workerCpuTimeNs);
                            t.put("operations_per_second", r.operationsPerSecond());
                            t.put("checksum", Long.toUnsignedString(r.checksum));
                            t.put("thermal_before", before.getJSONObject("thermal"));
                            t.put("thermal_after", after.getJSONObject("thermal"));
                            t.put("battery_before", before.getJSONObject("battery"));
                            t.put("battery_after", after.getJSONObject("battery"));
                            t.put("memory_before", before.getJSONObject("memory"));
                            t.put("memory_after", after.getJSONObject("memory"));
                            trials.put(t);
                            Thread.sleep(200L);
                        }
                    }
                    group.put("trials", trials);
                    groups.put(group);
                    Thread.sleep(1000L);
                }
                result.put("groups", groups);
                result.put("user_stop_requested", stopRequested.get());
                lastJson = result.toString(2);
                lastSummaryJson = buildCompactSummary(result).toString(2);
                setOutput(lastJson);
                mainHandler.post(() -> summaryButton.setEnabled(true));
            } catch (Exception e) {
                setOutput("FAIL_CLOSED: " + e);
            } finally {
                mainHandler.post(() -> {
                    stopButton.setEnabled(false);
                    sweepButton.setEnabled(true);
                });
            }
        });
    }

    private long calibrateIterations() throws Exception {
        final long targetNs = 800_000_000L;
        final long minNs = 500_000_000L;
        final long maxNs = 2_000_000_000L;
        final long minIterations = 50_000L;
        final long maxIterations = 200_000_000L;

        long candidate = 250_000L;
        try (BenchmarkEngine.Session session = new BenchmarkEngine.Session(1, () -> Debug.threadCpuTimeNanos())) {
            session.run(candidate); // JIT/runtime warmup; not measured.

            for (int step = 0; step < 6; step++) {
                BenchmarkEngine.RunResult r = session.run(candidate);
                if (r.wallTimeNs >= minNs && r.wallTimeNs <= maxNs) {
                    return candidate;
                }
                if (r.wallTimeNs <= 0) {
                    throw new IllegalStateException("CALIBRATION_INVALID_WALL_TIME");
                }

                double rawScale = (double) targetNs / (double) r.wallTimeNs;
                double boundedScale = Math.max(0.25, Math.min(16.0, rawScale));
                long next = Math.round(candidate * boundedScale / 1000.0) * 1000L;
                next = Math.max(minIterations, Math.min(maxIterations, next));

                if (next == candidate) {
                    return candidate;
                }
                candidate = next;
            }

            // Final bounded candidate. It remains safe even if the device is so fast/slow
            // that the target window could not be reached within the calibration budget.
            return candidate;
        }
    }

    private JSONObject buildCompactSummary(JSONObject result) throws Exception {
        JSONObject summary = new JSONObject();
        summary.put("schema_version", "SIGMA_MOBILE_SWEEP_SUMMARY_R1");
        summary.put("timestamp_utc", result.getString("timestamp_utc"));

        JSONObject probe = result.getJSONObject("probe");
        summary.put("device_model_code", probe.optString("device_model_code", "unknown"));
        summary.put("architecture", probe.optString("architecture", "unknown"));
        summary.put("cpus_allowed_list", probe.opt("cpus_allowed_list"));
        summary.put("effective_cpu_limit", probe.getInt("effective_cpu_limit"));
        summary.put("calibrated_iterations_per_worker", result.getLong("calibrated_iterations_per_worker"));
        summary.put("expected_trials_per_level", result.getInt("measured_trials_per_level"));
        summary.put("user_stop_requested", result.getBoolean("user_stop_requested"));

        JSONArray compactGroups = new JSONArray();
        JSONArray groups = result.getJSONArray("groups");
        for (int gi = 0; gi < groups.length(); gi++) {
            JSONObject group = groups.getJSONObject(gi);
            JSONArray trials = group.getJSONArray("trials");
            int n = trials.length();

            JSONObject out = new JSONObject();
            out.put("requested_workers", group.getInt("requested_workers"));
            out.put("effective_workers", group.getInt("effective_workers"));
            out.put("completed_trials", n);
            out.put("group_complete", n == result.getInt("measured_trials_per_level"));
            if (group.has("aborted")) out.put("aborted", group.get("aborted"));

            if (n > 0) {
                long[] wall = new long[n];
                double[] ops = new double[n];
                long[] cpu = new long[n];
                boolean cpuKnown = true;
                String firstChecksum = null;
                boolean checksumConsistent = true;
                int observedThreadsMin = Integer.MAX_VALUE;
                int observedThreadsMax = Integer.MIN_VALUE;
                int maxThermal = Integer.MIN_VALUE;
                double minBatteryTemp = Double.POSITIVE_INFINITY;
                double maxBatteryTemp = Double.NEGATIVE_INFINITY;

                for (int i = 0; i < n; i++) {
                    JSONObject t = trials.getJSONObject(i);
                    wall[i] = t.getLong("wall_time_ns");
                    ops[i] = t.getDouble("operations_per_second");
                    if (t.isNull("worker_cpu_time_ns")) {
                        cpuKnown = false;
                    } else {
                        cpu[i] = t.getLong("worker_cpu_time_ns");
                    }

                    String checksum = t.getString("checksum");
                    if (firstChecksum == null) firstChecksum = checksum;
                    else if (!firstChecksum.equals(checksum)) checksumConsistent = false;

                    int observed = t.getInt("observed_threads");
                    observedThreadsMin = Math.min(observedThreadsMin, observed);
                    observedThreadsMax = Math.max(observedThreadsMax, observed);

                    int tb = t.getJSONObject("thermal_before").getInt("status_code");
                    int ta = t.getJSONObject("thermal_after").getInt("status_code");
                    maxThermal = Math.max(maxThermal, Math.max(tb, ta));

                    Object btb = t.getJSONObject("battery_before").opt("temperature_c");
                    Object bta = t.getJSONObject("battery_after").opt("temperature_c");
                    if (btb instanceof Number) {
                        double v = ((Number) btb).doubleValue();
                        minBatteryTemp = Math.min(minBatteryTemp, v);
                        maxBatteryTemp = Math.max(maxBatteryTemp, v);
                    }
                    if (bta instanceof Number) {
                        double v = ((Number) bta).doubleValue();
                        minBatteryTemp = Math.min(minBatteryTemp, v);
                        maxBatteryTemp = Math.max(maxBatteryTemp, v);
                    }
                }

                out.put("median_wall_time_ns", Stats.median(wall));
                out.put("median_operations_per_second", Stats.median(ops));
                out.put("median_worker_cpu_time_ns", cpuKnown ? Stats.median(cpu) : JSONObject.NULL);
                out.put("checksum_consistent", checksumConsistent);
                out.put("checksum", firstChecksum == null ? JSONObject.NULL : firstChecksum);
                out.put("observed_threads_min", observedThreadsMin);
                out.put("observed_threads_max", observedThreadsMax);
                out.put("max_thermal_status_code", maxThermal);
                out.put("battery_temperature_min_c",
                        Double.isFinite(minBatteryTemp) ? minBatteryTemp : JSONObject.NULL);
                out.put("battery_temperature_max_c",
                        Double.isFinite(maxBatteryTemp) ? maxBatteryTemp : JSONObject.NULL);
            }
            compactGroups.put(out);
        }
        summary.put("groups", compactGroups);
        return summary;
    }

    private void copyJson() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("SIGMA Mobile JSON", lastJson));
    }

    private void copySummary() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("SIGMA Mobile Summary", lastSummaryJson));
    }

    private void setOutput(String text) {
        mainHandler.post(() -> output.setText(text));
    }

    @Override protected void onDestroy() {
        stopRequested.set(true);
        controlExecutor.shutdownNow();
        super.onDestroy();
    }
}
