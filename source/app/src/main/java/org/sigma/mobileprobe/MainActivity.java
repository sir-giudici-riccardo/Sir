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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final long SUSTAINED_BLOCK_COMPUTE_NS = 12_000_000_000L;
    private static final long SUSTAINED_PRECONDITION_COMPUTE_NS = 3_000_000_000L;
    private static final long SUSTAINED_RECOVERY_MS = 8_000L;
    private static final double SUSTAINED_BATTERY_GATE_C = 42.0;

    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView output;
    private Button sweepButton;
    private Button sustainedButton;
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

        sustainedButton = new Button(this);
        sustainedButton.setText("3 — Run sustained 4↔8 A/B");
        sustainedButton.setEnabled(false);
        root.addView(sustainedButton);

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
        sustainedButton.setOnClickListener(v -> runSustained());
        stopButton.setOnClickListener(v -> stopRequested.set(true));
        output.setText("Run the read-only probe first. No benchmark starts automatically.");
    }

    private void runProbe() {
        setOutput("Collecting read-only probe…");
        controlExecutor.submit(() -> {
            try {
                JSONObject probe = ProbeCollector.collect(this);
                lastJson = probe.toString(2);
                lastSummaryJson = "{}";
                mainHandler.post(() -> {
                    output.setText(lastJson);
                    try {
                        boolean pass = probe.getJSONObject("sandbox").getBoolean("isolation_contract_pass");
                        sweepButton.setEnabled(pass);
                        sustainedButton.setEnabled(pass);
                        summaryButton.setEnabled(false);
                    } catch (Exception e) {
                        sweepButton.setEnabled(false);
                        sustainedButton.setEnabled(false);
                    }
                });
            } catch (Exception e) {
                setOutput("FAIL_CLOSED: " + e);
            }
        });
    }

    private void setRunControls(boolean running) {
        mainHandler.post(() -> {
            sweepButton.setEnabled(!running);
            sustainedButton.setEnabled(!running);
            stopButton.setEnabled(running);
            if (running) summaryButton.setEnabled(false);
        });
    }

    private void runSweep() {
        stopRequested.set(false);
        setRunControls(true);
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
                setRunControls(false);
            }
        });
    }

    private void runSustained() {
        stopRequested.set(false);
        setRunControls(true);
        setOutput("Running sustained 4↔8 A/B…\nAbout 1–2 minutes. Use Stop at any time.");
        controlExecutor.submit(() -> {
            try {
                JSONObject baseline = ProbeCollector.collect(this);
                String gate = sustainedGate(baseline);
                if (gate != null) throw new IllegalStateException(gate + "_AT_START");

                long iterations = calibrateIterations();
                JSONObject result = new JSONObject();
                result.put("schema_version", "SIGMA_MOBILE_SUSTAINED_AB_R1");
                result.put("timestamp_utc", Instant.now().toString());
                result.put("baseline_probe", baseline);
                result.put("calibrated_iterations_per_worker", iterations);
                result.put("block_compute_target_ns", SUSTAINED_BLOCK_COMPUTE_NS);
                result.put("precondition_compute_target_ns", SUSTAINED_PRECONDITION_COMPUTE_NS);
                result.put("recovery_ms", SUSTAINED_RECOVERY_MS);
                result.put("sequence", new JSONArray(new int[]{4,8,4,8}));
                result.put("thermal_abort_threshold", "MODERATE");
                result.put("battery_temperature_experiment_gate_c", SUSTAINED_BATTERY_GATE_C);

                JSONObject precondition = runPrecondition(iterations);
                result.put("precondition", precondition);
                JSONObject postPrecondition = ProbeCollector.collect(this);
                result.put("post_precondition_probe", postPrecondition);

                if (postPrecondition.getInt("effective_cpu_limit") < 8) {
                    result.put("status", "BLOCKED_CPUSET_LT_8_AFTER_PRECONDITION");
                    result.put("user_stop_requested", stopRequested.get());
                    lastJson = result.toString(2);
                    lastSummaryJson = buildSustainedSummary(result).toString(2);
                    setOutput(lastJson);
                    mainHandler.post(() -> summaryButton.setEnabled(true));
                    return;
                }

                JSONArray blocks = new JSONArray();
                JSONArray recoveries = new JSONArray();
                int[] sequence = new int[]{4,8,4,8};
                boolean aborted = false;

                for (int i = 0; i < sequence.length; i++) {
                    if (stopRequested.get()) break;
                    JSONObject block = runSustainedBlock(i + 1, sequence[i], iterations);
                    blocks.put(block);
                    if (!"COMPLETE".equals(block.optString("status"))) {
                        aborted = true;
                        break;
                    }

                    if (i < sequence.length - 1 && !stopRequested.get()) {
                        recoveries.put(runRecovery(i + 1));
                        String postRecoveryGate = sustainedGate(ProbeCollector.collect(this));
                        if (postRecoveryGate != null) {
                            aborted = true;
                            result.put("abort_reason", postRecoveryGate + "_AFTER_RECOVERY");
                            break;
                        }
                    }
                }

                result.put("blocks", blocks);
                result.put("recoveries", recoveries);
                result.put("user_stop_requested", stopRequested.get());
                result.put("status",
                        stopRequested.get() ? "USER_STOPPED" :
                        aborted ? "ABORTED_BY_GATE_OR_CPUSET" :
                        blocks.length() == 4 ? "COMPLETE" : "INCOMPLETE");

                lastJson = result.toString(2);
                lastSummaryJson = buildSustainedSummary(result).toString(2);
                setOutput(lastJson);
                mainHandler.post(() -> summaryButton.setEnabled(true));
            } catch (Exception e) {
                setOutput("FAIL_CLOSED: " + e);
            } finally {
                setRunControls(false);
            }
        });
    }

    private JSONObject runPrecondition(long iterations) throws Exception {
        JSONObject startProbe = ProbeCollector.collect(this);
        String gate = sustainedGate(startProbe);
        if (gate != null) throw new IllegalStateException(gate + "_PRECONDITION_START");
        int workers = Math.min(4, startProbe.getInt("effective_cpu_limit"));
        if (workers < 1) throw new IllegalStateException("NO_EFFECTIVE_CPU_FOR_PRECONDITION");

        SustainedAccumulator acc = new SustainedAccumulator();
        long elapsedStart = System.nanoTime();
        try (BenchmarkEngine.Session session = new BenchmarkEngine.Session(workers, () -> Debug.threadCpuTimeNanos())) {
            while (!stopRequested.get() && acc.computeWallNs() < SUSTAINED_PRECONDITION_COMPUTE_NS) {
                BenchmarkEngine.RunResult r = session.run(iterations);
                acc.add(r);
                JSONObject after = ProbeCollector.collect(this);
                String afterGate = sustainedGate(after);
                if (afterGate != null) throw new IllegalStateException(afterGate + "_PRECONDITION");
            }
        }
        JSONObject endProbe = ProbeCollector.collect(this);
        JSONObject out = accumulatorJson(acc);
        out.put("workers", workers);
        out.put("elapsed_wall_ns", System.nanoTime() - elapsedStart);
        out.put("probe_before", startProbe);
        out.put("probe_after", endProbe);
        return out;
    }

    private JSONObject runSustainedBlock(int index, int requestedWorkers, long iterations) throws Exception {
        JSONObject before = ProbeCollector.collect(this);
        JSONObject block = new JSONObject();
        block.put("sequence_index", index);
        block.put("requested_workers", requestedWorkers);
        block.put("probe_before", before);

        String startGate = sustainedGate(before);
        if (startGate != null) {
            block.put("status", "ABORTED_GATE");
            block.put("abort_reason", startGate);
            return block;
        }

        int effectiveLimit = before.getInt("effective_cpu_limit");
        block.put("effective_cpu_limit_at_start", effectiveLimit);
        if (effectiveLimit < requestedWorkers) {
            block.put("status", "BLOCKED_CPUSET_LT_REQUESTED");
            return block;
        }

        SustainedAccumulator acc = new SustainedAccumulator();
        JSONArray rounds = new JSONArray();
        long elapsedStart = System.nanoTime();
        String abortReason = null;

        try (BenchmarkEngine.Session session = new BenchmarkEngine.Session(
                requestedWorkers, () -> Debug.threadCpuTimeNanos())) {
            while (!stopRequested.get() && acc.computeWallNs() < SUSTAINED_BLOCK_COMPUTE_NS) {
                BenchmarkEngine.RunResult r = session.run(iterations);
                acc.add(r);
                JSONObject afterRound = ProbeCollector.collect(this);

                JSONObject rr = new JSONObject();
                rr.put("round", acc.rounds());
                rr.put("wall_time_ns", r.wallTimeNs);
                rr.put("worker_cpu_time_ns", r.workerCpuTimeNs < 0 ? JSONObject.NULL : r.workerCpuTimeNs);
                rr.put("operations", r.operations);
                rr.put("operations_per_second", r.operationsPerSecond());
                rr.put("checksum", Long.toUnsignedString(r.checksum));
                rr.put("observed_threads", r.observedThreads);
                rr.put("cpus_allowed_list_after", afterRound.opt("cpus_allowed_list"));
                rr.put("effective_cpu_limit_after", afterRound.getInt("effective_cpu_limit"));
                rr.put("thermal_after", afterRound.getJSONObject("thermal"));
                rr.put("battery_after", afterRound.getJSONObject("battery"));
                rounds.put(rr);

                abortReason = sustainedGate(afterRound);
                if (abortReason != null) break;
                if (afterRound.getInt("effective_cpu_limit") < requestedWorkers) {
                    abortReason = "CPUSET_SHRANK_BELOW_REQUESTED";
                    break;
                }
            }
        }

        JSONObject after = ProbeCollector.collect(this);
        block.put("rounds", rounds);
        block.put("aggregate", accumulatorJson(acc));
        block.put("elapsed_wall_ns", System.nanoTime() - elapsedStart);
        block.put("probe_after", after);
        if (stopRequested.get()) {
            block.put("status", "USER_STOPPED");
        } else if (abortReason != null) {
            block.put("status", "ABORTED_GATE_OR_CPUSET");
            block.put("abort_reason", abortReason);
        } else if (acc.computeWallNs() >= SUSTAINED_BLOCK_COMPUTE_NS) {
            block.put("status", "COMPLETE");
        } else {
            block.put("status", "INCOMPLETE");
        }
        return block;
    }

    private JSONObject runRecovery(int afterBlockIndex) throws Exception {
        JSONObject before = ProbeCollector.collect(this);
        long start = System.nanoTime();
        long remaining = SUSTAINED_RECOVERY_MS;
        while (remaining > 0 && !stopRequested.get()) {
            long step = Math.min(1000L, remaining);
            Thread.sleep(step);
            remaining -= step;
        }
        JSONObject after = ProbeCollector.collect(this);
        JSONObject out = new JSONObject();
        out.put("after_block_index", afterBlockIndex);
        out.put("requested_ms", SUSTAINED_RECOVERY_MS);
        out.put("elapsed_ns", System.nanoTime() - start);
        out.put("probe_before", before);
        out.put("probe_after", after);
        return out;
    }

    private JSONObject accumulatorJson(SustainedAccumulator acc) throws Exception {
        JSONObject out = new JSONObject();
        out.put("rounds", acc.rounds());
        out.put("total_operations", acc.totalOperations());
        out.put("compute_wall_ns", acc.computeWallNs());
        out.put("worker_cpu_time_ns", acc.workerCpuTimeNs() == null ? JSONObject.NULL : acc.workerCpuTimeNs());
        out.put("aggregate_operations_per_second", acc.aggregateOperationsPerSecond());
        out.put("median_round_operations_per_second", acc.medianRoundOperationsPerSecond());
        out.put("cpu_to_compute_wall_ratio",
                Double.isNaN(acc.cpuToComputeWallRatio()) ? JSONObject.NULL : acc.cpuToComputeWallRatio());
        out.put("checksum_consistent", acc.checksumConsistent());
        out.put("checksum", acc.checksum() == null ? JSONObject.NULL : acc.checksum());
        out.put("observed_threads_min", acc.observedThreadsMin());
        out.put("observed_threads_max", acc.observedThreadsMax());
        return out;
    }

    private String sustainedGate(JSONObject probe) throws Exception {
        int thermal = probe.getJSONObject("thermal").getInt("status_code");
        if (thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
            return "THERMAL_STATUS_MODERATE_OR_HIGHER";
        }
        Object temp = probe.getJSONObject("battery").opt("temperature_c");
        if (temp instanceof Number && ((Number) temp).doubleValue() >= SUSTAINED_BATTERY_GATE_C) {
            return "BATTERY_TEMP_EXPERIMENT_GATE_42C";
        }
        return null;
    }

    private long calibrateIterations() throws Exception {
        final long targetNs = 800_000_000L;
        final long minNs = 500_000_000L;
        final long maxNs = 2_000_000_000L;
        final long minIterations = 50_000L;
        final long maxIterations = 200_000_000L;

        long candidate = 250_000L;
        try (BenchmarkEngine.Session session = new BenchmarkEngine.Session(1, () -> Debug.threadCpuTimeNanos())) {
            session.run(candidate);

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
                if (next == candidate) return candidate;
                candidate = next;
            }
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
                    if (t.isNull("worker_cpu_time_ns")) cpuKnown = false;
                    else cpu[i] = t.getLong("worker_cpu_time_ns");

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

    private JSONObject buildSustainedSummary(JSONObject result) throws Exception {
        JSONObject summary = new JSONObject();
        summary.put("schema_version", "SIGMA_MOBILE_SUSTAINED_SUMMARY_R1");
        summary.put("timestamp_utc", result.getString("timestamp_utc"));
        summary.put("status", result.optString("status", "UNKNOWN"));
        summary.put("calibrated_iterations_per_worker", result.getLong("calibrated_iterations_per_worker"));
        summary.put("block_compute_target_ns", result.getLong("block_compute_target_ns"));
        summary.put("recovery_ms", result.getLong("recovery_ms"));

        JSONObject post = result.optJSONObject("post_precondition_probe");
        if (post != null) {
            summary.put("cpus_allowed_list_after_precondition", post.opt("cpus_allowed_list"));
            summary.put("effective_cpu_limit_after_precondition", post.opt("effective_cpu_limit"));
        }

        JSONArray compact = new JSONArray();
        List<Double> four = new ArrayList<>();
        List<Double> eight = new ArrayList<>();
        double minBattery = Double.POSITIVE_INFINITY;
        double maxBattery = Double.NEGATIVE_INFINITY;
        int maxThermal = Integer.MIN_VALUE;

        JSONArray blocks = result.optJSONArray("blocks");
        if (blocks != null) {
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject b = blocks.getJSONObject(i);
                JSONObject out = new JSONObject();
                out.put("sequence_index", b.getInt("sequence_index"));
                out.put("requested_workers", b.getInt("requested_workers"));
                out.put("status", b.getString("status"));
                out.put("effective_cpu_limit_at_start", b.opt("effective_cpu_limit_at_start"));

                JSONObject before = b.optJSONObject("probe_before");
                JSONObject after = b.optJSONObject("probe_after");
                if (before != null) out.put("cpus_allowed_before", before.opt("cpus_allowed_list"));
                if (after != null) out.put("cpus_allowed_after", after.opt("cpus_allowed_list"));

                JSONObject agg = b.optJSONObject("aggregate");
                if (agg != null) {
                    out.put("rounds", agg.getInt("rounds"));
                    out.put("total_operations", agg.getLong("total_operations"));
                    out.put("compute_wall_ns", agg.getLong("compute_wall_ns"));
                    out.put("elapsed_wall_ns", b.getLong("elapsed_wall_ns"));
                    out.put("aggregate_operations_per_second", agg.getDouble("aggregate_operations_per_second"));
                    out.put("median_round_operations_per_second", agg.getDouble("median_round_operations_per_second"));
                    out.put("cpu_to_compute_wall_ratio", agg.opt("cpu_to_compute_wall_ratio"));
                    out.put("checksum_consistent", agg.getBoolean("checksum_consistent"));
                    out.put("observed_threads_min", agg.getInt("observed_threads_min"));
                    out.put("observed_threads_max", agg.getInt("observed_threads_max"));
                    if ("COMPLETE".equals(b.getString("status"))) {
                        if (b.getInt("requested_workers") == 4) four.add(agg.getDouble("aggregate_operations_per_second"));
                        if (b.getInt("requested_workers") == 8) eight.add(agg.getDouble("aggregate_operations_per_second"));
                    }
                }

                if (before != null) {
                    int t = before.getJSONObject("thermal").getInt("status_code");
                    maxThermal = Math.max(maxThermal, t);
                    Object temp = before.getJSONObject("battery").opt("temperature_c");
                    if (temp instanceof Number) {
                        double v = ((Number) temp).doubleValue();
                        minBattery = Math.min(minBattery, v);
                        maxBattery = Math.max(maxBattery, v);
                    }
                }
                if (after != null) {
                    int t = after.getJSONObject("thermal").getInt("status_code");
                    maxThermal = Math.max(maxThermal, t);
                    Object temp = after.getJSONObject("battery").opt("temperature_c");
                    if (temp instanceof Number) {
                        double v = ((Number) temp).doubleValue();
                        minBattery = Math.min(minBattery, v);
                        maxBattery = Math.max(maxBattery, v);
                    }
                }
                if (b.has("abort_reason")) out.put("abort_reason", b.get("abort_reason"));
                compact.put(out);
            }
        }

        summary.put("blocks", compact);
        summary.put("max_thermal_status_code", maxThermal == Integer.MIN_VALUE ? JSONObject.NULL : maxThermal);
        summary.put("battery_temperature_min_c", Double.isFinite(minBattery) ? minBattery : JSONObject.NULL);
        summary.put("battery_temperature_max_c", Double.isFinite(maxBattery) ? maxBattery : JSONObject.NULL);

        if (four.size() == 2 && eight.size() == 2) {
            double f4 = Stats.median(toDoubleArray(four));
            double f8 = Stats.median(toDoubleArray(eight));
            JSONObject derived = new JSONObject();
            derived.put("median_4_worker_aggregate_ops_s", f4);
            derived.put("median_8_worker_aggregate_ops_s", f8);
            derived.put("absolute_throughput_ratio_8_vs_4", f8 / f4);
            derived.put("absolute_throughput_gain_percent_8_vs_4", (f8 / f4 - 1.0) * 100.0);
            derived.put("throughput_per_worker_4", f4 / 4.0);
            derived.put("throughput_per_worker_8", f8 / 8.0);
            derived.put("per_worker_efficiency_ratio_8_vs_4", (f8 / 8.0) / (f4 / 4.0));
            summary.put("derived", derived);
        }
        return summary;
    }

    private static double[] toDoubleArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
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
