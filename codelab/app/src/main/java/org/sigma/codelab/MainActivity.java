package org.sigma.codelab;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final String PREFS = "sigma_codelab";
    private static final String LEGACY_KEY_SOURCE = "source";
    private static final String KEY_SOURCE_SIGMA = "source_sigma";
    private static final String KEY_SOURCE_JS = "source_js";
    private static final String KEY_ENGINE = "engine";
    private static final String KEY_THEME = "theme";

    private static final String ENGINE_SIGMA = "SIGMA";
    private static final String ENGINE_JS = "JAVASCRIPT";

    private static final String THEME_SYSTEM = "SYSTEM";
    private static final String THEME_LIGHT = "LIGHT";
    private static final String THEME_DARK = "DARK";

    private static final ScriptEngine.Limits LIMITS =
            new ScriptEngine.Limits(500_000L, 65_536, 100_000L, 5_000L, 64);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());

    private EditText editor;
    private TextView output;
    private Button runButton;
    private Button stopButton;
    private Button modeButton;
    private Button themeButton;
    private Button symbolButton;
    private Button diagnosticButton;
    private LinearLayout root;
    private LocalJavaScriptEngine jsEngine;
    private SymbolShortcutIndex symbolIndex;

    private int sigmaExampleIndex;
    private int jsExampleIndex;
    private String currentEngine = ENGINE_SIGMA;
    private String currentTheme = THEME_SYSTEM;
    private String lastOutput = "";
    private RunDiagnostic lastDiagnostic;

    private static final String[] SIGMA_EXAMPLES = new String[] {
            "# Arithmetic and variables\n"
                    + "let width = 12\n"
                    + "let height = 5\n"
                    + "let area = width * height\n"
                    + "print \"area = \" + area\n"
                    + "print \"diagonal = \" + sqrt(width * width + height * height)\n",

            "# Lists and aggregate functions\n"
                    + "let values = range(1, 6)\n"
                    + "push(values, 10)\n"
                    + "set(values, 0, 5)\n"
                    + "print values\n"
                    + "print \"sum = \" + sum(values)\n"
                    + "print \"mean = \" + mean(values)\n",

            "# User-defined functions\n"
                    + "fn square(x) { return x * x }\n"
                    + "fn hypotenuse(a, b) { return sqrt(square(a) + square(b)) }\n"
                    + "print hypotenuse(3, 4)\n",

            "# Bounded recursion\n"
                    + "fn fact(n) {\n"
                    + "  if n <= 1 { return 1 }\n"
                    + "  return n * fact(n - 1)\n"
                    + "}\n"
                    + "print fact(6)\n"
    };

    private static final String[] JS_EXAMPLES = new String[] {
            "const width = 12;\n"
                    + "const height = 5;\n"
                    + "const area = width * height;\n"
                    + "console.log(\"area =\", area);\n"
                    + "console.log(\"diagonal =\", Math.sqrt(width * width + height * height));\n",

            "const values = [1, 2, 3, 4, 5];\n"
                    + "values.push(10);\n"
                    + "values[0] = 5;\n"
                    + "const sum = values.reduce((a, b) => a + b, 0);\n"
                    + "console.log(values);\n"
                    + "console.log(\"sum =\", sum);\n"
                    + "console.log(\"mean =\", sum / values.length);\n",

            "function square(x) { return x * x; }\n"
                    + "function hypotenuse(a, b) { return Math.sqrt(square(a) + square(b)); }\n"
                    + "console.log(hypotenuse(3, 4));\n",

            "function fact(n) { return n <= 1 ? 1 : n * fact(n - 1); }\n"
                    + "console.log(fact(6));\n"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        currentTheme = prefs.getString(KEY_THEME, THEME_SYSTEM);
        setTheme(resolveTheme(currentTheme));
        super.onCreate(savedInstanceState);

        boolean dark = isDarkActive(currentTheme);
        configureSystemBars(dark);

        currentEngine = prefs.getString(KEY_ENGINE, ENGINE_SIGMA);
        lastDiagnostic = RunDiagnostic.noRun("0.4.0-dev", currentTheme);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(pad, pad + top, pad, pad + bottom);
            return insets;
        });

        TextView title = new TextView(this);
        title.setText("SIGMA Code Lab 0.4.0 dev");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Offline coding workspace • SIGMA Script + local JavaScript/WebView • no network permission");
        subtitle.setPadding(0, 0, 0, dp(8));
        root.addView(subtitle);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        runButton = new Button(this);
        runButton.setText("Run");
        row1.addView(runButton, weighted());

        stopButton = new Button(this);
        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        row1.addView(stopButton, weighted());

        Button example = new Button(this);
        example.setText("Example");
        row1.addView(example, weighted());
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        Button copyCode = new Button(this);
        copyCode.setText("Copy code");
        row2.addView(copyCode, weighted());

        Button copyOutput = new Button(this);
        copyOutput.setText("Copy output");
        row2.addView(copyOutput, weighted());

        Button reference = new Button(this);
        reference.setText("Reference");
        row2.addView(reference, weighted());
        root.addView(row2);

        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);

        modeButton = new Button(this);
        row3.addView(modeButton, weighted());

        themeButton = new Button(this);
        row3.addView(themeButton, weighted());

        symbolButton = new Button(this);
        symbolButton.setText("Symbol");
        symbolButton.setEnabled(false);
        row3.addView(symbolButton, weighted());

        root.addView(row3);

        LinearLayout row4 = new LinearLayout(this);
        row4.setOrientation(LinearLayout.HORIZONTAL);

        diagnosticButton = new Button(this);
        diagnosticButton.setText("Copy diag JSON");
        row4.addView(diagnosticButton, weighted());
        root.addView(row4);

        editor = new EditText(this);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(15f);
        editor.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        editor.setHorizontallyScrolling(true);
        editor.setHorizontalScrollBarEnabled(true);
        editor.setVerticalScrollBarEnabled(true);
        editor.setSingleLine(false);
        editor.setMaxLines(Integer.MAX_VALUE);
        editor.setMinLines(10);
        editor.setMinWidth(getResources().getDisplayMetrics().widthPixels - dp(24));
        editor.setPadding(dp(8), dp(8), dp(8), dp(8));

        HorizontalScrollView codeScroll = new HorizontalScrollView(this);
        codeScroll.setFillViewport(true);
        codeScroll.setHorizontalScrollBarEnabled(true);
        codeScroll.addView(editor, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(codeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f));

        TextView outputLabel = new TextView(this);
        outputLabel.setText("Output");
        outputLabel.setTypeface(Typeface.DEFAULT_BOLD);
        outputLabel.setPadding(0, dp(8), 0, dp(4));
        root.addView(outputLabel);

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(14f);
        output.setTextIsSelectable(true);
        output.setPadding(dp(8), dp(8), dp(8), dp(8));

        ScrollView outputScroll = new ScrollView(this);
        outputScroll.addView(output);
        root.addView(outputScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.8f));

        setContentView(root);
        root.requestApplyInsets();

        jsEngine = new LocalJavaScriptEngine(this, root);
        loadSymbolIndex();

        String sigmaSaved = prefs.getString(KEY_SOURCE_SIGMA, prefs.getString(LEGACY_KEY_SOURCE, null));
        String jsSaved = prefs.getString(KEY_SOURCE_JS, null);
        if (ENGINE_JS.equals(currentEngine)) {
            editor.setText(jsSaved == null ? JS_EXAMPLES[0] : jsSaved);
        } else {
            editor.setText(sigmaSaved == null ? SIGMA_EXAMPLES[0] : sigmaSaved);
        }
        updateModeButton();
        updateThemeButton();

        runButton.setOnClickListener(v -> runCode());
        stopButton.setOnClickListener(v -> stopExecution());
        example.setOnClickListener(v -> loadNextExample());
        copyCode.setOnClickListener(v -> copy("SIGMA Code Lab source", editor.getText().toString()));
        copyOutput.setOnClickListener(v -> copy("SIGMA Code Lab output", lastOutput));
        reference.setOnClickListener(v -> showReference());
        modeButton.setOnClickListener(v -> toggleEngine());
        themeButton.setOnClickListener(v -> cycleTheme());
        symbolButton.setOnClickListener(v -> insertSymbolShortcut());
        diagnosticButton.setOnClickListener(v -> copyDiagnosticJson());
    }

    private void runCode() {
        saveCurrentSource();
        setRunning(true);
        output.setText("Running…");

        if (ENGINE_JS.equals(currentEngine)) {
            runJavaScript(editor.getText().toString());
        } else {
            runSigma(editor.getText().toString());
        }
    }

    private void runSigma(String source) {
        cancelRequested.set(false);
        executor.submit(() -> {
            long started = System.nanoTime();
            try {
                ScriptEngine.Result result = ScriptEngine.execute(source, LIMITS, cancelRequested::get);
                double ms = result.elapsedNs / 1_000_000.0;
                String text = result.output
                        + (result.output.isEmpty() ? "" : "\n")
                        + "[PASS] engine=SIGMA steps=" + result.steps
                        + " elapsed_ms=" + String.format(java.util.Locale.ROOT, "%.3f", ms);
                lastDiagnostic = RunDiagnostic.sigma(
                        "0.4.0-dev",
                        currentTheme,
                        true,
                        result.elapsedNs,
                        result.steps,
                        source.length(),
                        result.output.length());
                lastOutput = text;
                main.post(() -> output.setText(text));
            } catch (ScriptEngine.ScriptException e) {
                double ms = (System.nanoTime() - started) / 1_000_000.0;
                String text = "[STOPPED] engine=SIGMA " + e.getMessage()
                        + "\nelapsed_ms="
                        + String.format(java.util.Locale.ROOT, "%.3f", ms);
                lastDiagnostic = RunDiagnostic.sigma(
                        "0.4.0-dev",
                        currentTheme,
                        false,
                        System.nanoTime() - started,
                        -1L,
                        source.length(),
                        text.length());
                lastOutput = text;
                main.post(() -> output.setText(text));
            } catch (Throwable t) {
                String text = "[FAIL_CLOSED] engine=SIGMA "
                        + t.getClass().getSimpleName() + ": " + t.getMessage();
                lastDiagnostic = RunDiagnostic.sigma(
                        "0.4.0-dev",
                        currentTheme,
                        false,
                        System.nanoTime() - started,
                        -1L,
                        source.length(),
                        text.length());
                lastOutput = text;
                main.post(() -> output.setText(text));
            } finally {
                main.post(() -> setRunning(false));
            }
        });
    }

    private void runJavaScript(String source) {
        jsEngine.run(source, 5_000L, result -> {
            double ms = result.elapsedNs / 1_000_000.0;
            StringBuilder text = new StringBuilder();
            if (!result.output.isEmpty()) text.append(result.output).append('\n');
            if (result.success) {
                if (result.value != null) text.append("=> ").append(result.value).append('\n');
                text.append("[PASS] engine=JavaScript/WebView");
            } else {
                text.append("[STOPPED] engine=JavaScript/WebView ").append(result.error);
            }
            text.append("\nprovider=").append(result.provider);
            text.append("\nelapsed_ms=")
                    .append(String.format(java.util.Locale.ROOT, "%.3f", ms));

            lastOutput = text.toString();
            lastDiagnostic = RunDiagnostic.javascript(
                    "0.4.0-dev",
                    currentTheme,
                    result.success,
                    result.elapsedNs,
                    result.provider,
                    source.length(),
                    result.output.length());
            output.setText(lastOutput);
            setRunning(false);
        });
    }

    private void stopExecution() {
        if (ENGINE_JS.equals(currentEngine)) {
            jsEngine.cancel();
        } else {
            cancelRequested.set(true);
        }
    }

    private void setRunning(boolean running) {
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        editor.setEnabled(!running);
        modeButton.setEnabled(!running);
        themeButton.setEnabled(!running);
        symbolButton.setEnabled(!running && symbolIndex != null);
        diagnosticButton.setEnabled(!running);
    }

    private void toggleEngine() {
        saveCurrentSource();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        if (ENGINE_SIGMA.equals(currentEngine)) {
            currentEngine = ENGINE_JS;
            String saved = prefs.getString(KEY_SOURCE_JS, null);
            editor.setText(saved == null ? JS_EXAMPLES[0] : saved);
        } else {
            currentEngine = ENGINE_SIGMA;
            String saved = prefs.getString(
                    KEY_SOURCE_SIGMA,
                    prefs.getString(LEGACY_KEY_SOURCE, null));
            editor.setText(saved == null ? SIGMA_EXAMPLES[0] : saved);
        }

        prefs.edit().putString(KEY_ENGINE, currentEngine).apply();
        editor.setSelection(editor.getText().length());
        updateModeButton();
        output.setText("Engine mode: " + currentEngine);
    }

    private void loadNextExample() {
        String[] examples;
        int index;

        if (ENGINE_JS.equals(currentEngine)) {
            jsExampleIndex = (jsExampleIndex + 1) % JS_EXAMPLES.length;
            examples = JS_EXAMPLES;
            index = jsExampleIndex;
        } else {
            sigmaExampleIndex = (sigmaExampleIndex + 1) % SIGMA_EXAMPLES.length;
            examples = SIGMA_EXAMPLES;
            index = sigmaExampleIndex;
        }

        editor.setText(examples[index]);
        editor.setSelection(editor.getText().length());
        output.setText("Example " + (index + 1) + " loaded for " + currentEngine + ".");
    }

    private void showReference() {
        if (ENGINE_JS.equals(currentEngine)) {
            showJavaScriptReference();
        } else {
            showSigmaReference();
        }
    }

    private void showSigmaReference() {
        String reference =
                "SIGMA Script 0.2\n\n"
                + "Statements: let, assignment, print, if/else, repeat, while, fn, return\n"
                + "Values: numbers, strings, booleans, lists\n"
                + "Logic: and, or, not\n"
                + "Operators: + - * / % == != < <= > >=\n"
                + "Constants: pi, e\n"
                + "Functions: sqrt abs sin cos tan log exp floor ceil round pow min max clamp\n"
                + "List/data: len str num type get set push pop range sum mean\n\n"
                + "Execution limits: steps, loops, call depth, output and wall time.\n\n"
                + "Editor symbol helper: select or place the cursor after a shortcut such as \\sum, then press SYMBOL.\n"
                + "COPY DIAG JSON exports run metadata only; it does not include source or output text.";
        lastOutput = reference;
        output.setText(reference);
    }

    private void showJavaScriptReference() {
        String reference =
                "Local JavaScript/WebView mode\n\n"
                + "Use standard JavaScript syntax and console.log(...) for output.\n"
                + "The current WebView provider is shown after each run.\n\n"
                + "Containment contract:\n"
                + "  no Android INTERNET permission\n"
                + "  WebView network loads blocked\n"
                + "  file/content access disabled\n"
                + "  DOM storage/database/geolocation disabled\n"
                + "  external navigation blocked\n"
                + "  no JavaScriptInterface bridge\n"
                + "  5 s watchdog with renderer termination attempt\n\n"
                + "This is an execution boundary, not a universal JavaScript sandbox proof.\n\n"
                + "Editor symbol helper is independent of the JavaScript engine: select or place the cursor after a shortcut such as \\sum, then press SYMBOL.\n"
                + "COPY DIAG JSON exports run metadata only; it does not include source or output text.";
        lastOutput = reference;
        output.setText(reference);
    }

    private void loadSymbolIndex() {
        try (InputStreamReader reader = new InputStreamReader(
                getAssets().open("latex_gboard_dictionary.txt"),
                StandardCharsets.UTF_8)) {
            symbolIndex = SymbolShortcutIndex.load(reader);
            symbolButton.setEnabled(true);
        } catch (Throwable t) {
            symbolIndex = null;
            symbolButton.setEnabled(false);
            lastOutput = "[FAIL_CLOSED] symbol index unavailable: "
                    + t.getClass().getSimpleName();
            output.setText(lastOutput);
        }
    }

    private void insertSymbolShortcut() {
        if (symbolIndex == null) {
            lastOutput = "[FAIL_CLOSED] symbol index unavailable";
            output.setText(lastOutput);
            return;
        }

        int selectionStart = Math.max(0, editor.getSelectionStart());
        int selectionEnd = Math.max(0, editor.getSelectionEnd());
        SymbolEditHelper.Result result = SymbolEditHelper.resolve(
                editor.getText().toString(),
                selectionStart,
                selectionEnd,
                symbolIndex);

        switch (result.status) {
            case REPLACE:
                editor.getText().replace(result.start, result.end, result.replacement);
                editor.setSelection(result.start + result.replacement.length());
                lastOutput = "Symbol: " + result.token + " → " + result.replacement;
                break;
            case AMBIGUOUS:
                lastOutput = "Ambiguous symbol shortcut: " + result.token
                        + "\nCandidates: " + String.join("  ", result.candidates)
                        + "\nNo replacement was made.";
                break;
            case NOT_FOUND:
                lastOutput = "No symbol shortcut found for: " + result.token;
                break;
            default:
                lastOutput = "Place the cursor after a shortcut (for example \\sum) "
                        + "or select a shortcut, then press SYMBOL.";
                break;
        }
        output.setText(lastOutput);
    }

    private void copyDiagnosticJson() {
        String json = lastDiagnostic == null
                ? RunDiagnostic.noRun("0.4.0-dev", currentTheme).toJson()
                : lastDiagnostic.toJson();
        copy("SIGMA Code Lab diagnostic JSON", json);
        output.setText("Diagnostic JSON copied. Source/output text are not included.");
    }

    private void cycleTheme() {
        saveCurrentSource();
        if (THEME_SYSTEM.equals(currentTheme)) currentTheme = THEME_LIGHT;
        else if (THEME_LIGHT.equals(currentTheme)) currentTheme = THEME_DARK;
        else currentTheme = THEME_SYSTEM;

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, currentTheme)
                .apply();
        recreate();
    }

    private void updateModeButton() {
        modeButton.setText("Mode: " + (ENGINE_JS.equals(currentEngine) ? "JS" : "SIGMA"));
    }

    private void updateThemeButton() {
        themeButton.setText("Theme: " + currentTheme);
    }

    private void saveCurrentSource() {
        if (editor == null) return;
        String key = ENGINE_JS.equals(currentEngine) ? KEY_SOURCE_JS : KEY_SOURCE_SIGMA;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(key, editor.getText().toString())
                .apply();
    }

    private int resolveTheme(String theme) {
        boolean dark = isDarkActive(theme);
        return dark
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar;
    }

    private boolean isDarkActive(String theme) {
        if (THEME_DARK.equals(theme)) return true;
        if (THEME_LIGHT.equals(theme)) return false;
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void configureSystemBars(boolean dark) {
        getWindow().setStatusBarColor(dark ? Color.BLACK : Color.WHITE);
        getWindow().setNavigationBarColor(dark ? Color.BLACK : Color.WHITE);
        int flags = 0;
        if (!dark) {
            flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void copy(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onPause() {
        saveCurrentSource();
        super.onPause();
    }

    @Override protected void onDestroy() {
        cancelRequested.set(true);
        if (jsEngine != null) jsEngine.destroy();
        executor.shutdownNow();
        super.onDestroy();
    }
}
