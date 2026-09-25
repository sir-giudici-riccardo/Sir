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
    private volatile String lastJson = "{}";

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
        copy.setText("Copy JSON");
        root.addView(copy);

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
                result.put("schema_version", "SIGMA_MOBILE_SWEEP_R1");
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
                setOutput(lastJson);
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
        long iterations = 250_000L;
        try (BenchmarkEngine.Session session = new BenchmarkEngine.Session(1, () -> Debug.threadCpuTimeNanos())) {
            session.run(iterations);
            BenchmarkEngine.RunResult r = session.run(iterations);
            if (r.wallTimeNs <= 0) return iterations;
            double scale = (double) targetNs / (double) r.wallTimeNs;
            scale = Math.max(0.25, Math.min(8.0, scale));
            long candidate = Math.round(iterations * scale / 1000.0) * 1000L;
            candidate = Math.max(50_000L, Math.min(20_000_000L, candidate));
            BenchmarkEngine.RunResult verify = session.run(candidate);
            if (verify.wallTimeNs < 500_000_000L || verify.wallTimeNs > 2_000_000_000L) {
                double second = (double) targetNs / Math.max(1.0, (double) verify.wallTimeNs);
                second = Math.max(0.5, Math.min(2.0, second));
                candidate = Math.round(candidate * second / 1000.0) * 1000L;
                candidate = Math.max(50_000L, Math.min(20_000_000L, candidate));
            }
            return candidate;
        }
    }

    private void copyJson() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("SIGMA Mobile JSON", lastJson));
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
