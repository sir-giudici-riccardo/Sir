package org.sigma.codelab;

import java.time.Instant;
import java.util.Locale;

public final class RunDiagnostic {
    public static final String SCHEMA_VERSION = "SIGMA_CODE_LAB_DIAGNOSTIC_R1";

    private final String timestampUtc;
    private final String appVersion;
    private final String engine;
    private final String status;
    private final long elapsedNs;
    private final long steps;
    private final String provider;
    private final String theme;
    private final int sourceChars;
    private final int outputChars;

    private RunDiagnostic(
            String timestampUtc,
            String appVersion,
            String engine,
            String status,
            long elapsedNs,
            long steps,
            String provider,
            String theme,
            int sourceChars,
            int outputChars) {
        this.timestampUtc = timestampUtc;
        this.appVersion = appVersion;
        this.engine = engine;
        this.status = status;
        this.elapsedNs = Math.max(0L, elapsedNs);
        this.steps = steps;
        this.provider = provider;
        this.theme = theme;
        this.sourceChars = Math.max(0, sourceChars);
        this.outputChars = Math.max(0, outputChars);
    }

    public static RunDiagnostic noRun(String appVersion, String theme) {
        return new RunDiagnostic(
                Instant.now().toString(),
                appVersion,
                "NONE",
                "NO_RUN",
                0L,
                -1L,
                null,
                theme,
                0,
                0);
    }

    public static RunDiagnostic sigma(
            String appVersion,
            String theme,
            boolean success,
            long elapsedNs,
            long steps,
            int sourceChars,
            int outputChars) {
        return new RunDiagnostic(
                Instant.now().toString(),
                appVersion,
                "SIGMA",
                success ? "PASS" : "STOPPED",
                elapsedNs,
                steps,
                null,
                theme,
                sourceChars,
                outputChars);
    }

    public static RunDiagnostic javascript(
            String appVersion,
            String theme,
            boolean success,
            long elapsedNs,
            String provider,
            int sourceChars,
            int outputChars) {
        return new RunDiagnostic(
                Instant.now().toString(),
                appVersion,
                "JAVASCRIPT_WEBVIEW",
                success ? "PASS" : "STOPPED",
                elapsedNs,
                -1L,
                provider,
                theme,
                sourceChars,
                outputChars);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder(384);
        json.append('{');
        field(json, "schema_version", SCHEMA_VERSION).append(',');
        field(json, "timestamp_utc", timestampUtc).append(',');
        field(json, "app_version", appVersion).append(',');
        field(json, "engine", engine).append(',');
        field(json, "status", status).append(',');
        json.append("\"elapsed_ms\":")
                .append(String.format(Locale.ROOT, "%.3f", elapsedNs / 1_000_000.0))
                .append(',');
        if (steps >= 0) json.append("\"steps\":").append(steps);
        else json.append("\"steps\":null");
        json.append(',');
        if (provider == null) json.append("\"provider\":null");
        else field(json, "provider", provider);
        json.append(',');
        field(json, "theme", theme).append(',');
        json.append("\"source_chars\":").append(sourceChars).append(',');
        json.append("\"output_chars\":").append(outputChars).append(',');
        json.append("\"contains_source_text\":false,");
        json.append("\"contains_output_text\":false");
        json.append('}');
        return json.toString();
    }

    private static StringBuilder field(StringBuilder out, String key, String value) {
        return out.append('"').append(escape(key)).append("\":\"")
                .append(escape(value == null ? "" : value)).append('"');
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
