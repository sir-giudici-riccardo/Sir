package org.sigma.codelab;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final String PREFS = "sigma_codelab";
    private static final String KEY_SOURCE = "source";
    private static final ScriptEngine.Limits LIMITS =
            new ScriptEngine.Limits(500_000L, 65_536, 100_000L, 5_000L);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());

    private EditText editor;
    private TextView output;
    private Button runButton;
    private Button stopButton;
    private int exampleIndex = 0;
    private String lastOutput = "";

    private static final String[] EXAMPLES = new String[] {
            "# Arithmetic and variables\n"
                    + "let width = 12\n"
                    + "let height = 5\n"
                    + "let area = width * height\n"
                    + "print \"area = \" + area\n"
                    + "print \"diagonal = \" + sqrt(width * width + height * height)\n",

            "# Conditions and loops\n"
                    + "let total = 0\n"
                    + "repeat 10 {\n"
                    + "  total = total + 2\n"
                    + "}\n"
                    + "if total >= 20 {\n"
                    + "  print \"target reached: \" + total\n"
                    + "} else {\n"
                    + "  print \"target not reached\"\n"
                    + "}\n",

            "# Small numeric sequence\n"
                    + "let a = 0\n"
                    + "let b = 1\n"
                    + "let next = 0\n"
                    + "let i = 0\n"
                    + "while i < 10 {\n"
                    + "  print a\n"
                    + "  next = a + b\n"
                    + "  a = b\n"
                    + "  b = next\n"
                    + "  i = i + 1\n"
                    + "}\n"
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
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
        title.setText("SIGMA Code Lab 0.1.1 candidate");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Offline coding workspace • local deterministic scripts • no network permission");
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

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_SOURCE, null);
        editor.setText(saved == null ? EXAMPLES[0] : saved);

        runButton.setOnClickListener(v -> runCode());
        stopButton.setOnClickListener(v -> cancelRequested.set(true));
        example.setOnClickListener(v -> loadNextExample());
        copyCode.setOnClickListener(v -> copy("SIGMA Code Lab source", editor.getText().toString()));
        copyOutput.setOnClickListener(v -> copy("SIGMA Code Lab output", lastOutput));
        reference.setOnClickListener(v -> showReference());
    }

    private void runCode() {
        final String source = editor.getText().toString();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SOURCE, source).apply();

        cancelRequested.set(false);
        setRunning(true);
        output.setText("Running…");

        executor.submit(() -> {
            long started = System.nanoTime();
            try {
                ScriptEngine.Result result = ScriptEngine.execute(source, LIMITS, cancelRequested::get);
                double ms = result.elapsedNs / 1_000_000.0;
                String text = result.output
                        + (result.output.isEmpty() ? "" : "\n")
                        + "[PASS] steps=" + result.steps
                        + "  elapsed_ms=" + String.format(java.util.Locale.ROOT, "%.3f", ms);
                lastOutput = text;
                main.post(() -> output.setText(text));
            } catch (ScriptEngine.ScriptException e) {
                double ms = (System.nanoTime() - started) / 1_000_000.0;
                String text = "[STOPPED] " + e.getMessage()
                        + "\nelapsed_ms="
                        + String.format(java.util.Locale.ROOT, "%.3f", ms);
                lastOutput = text;
                main.post(() -> output.setText(text));
            } catch (Throwable t) {
                String text = "[FAIL_CLOSED] " + t.getClass().getSimpleName() + ": " + t.getMessage();
                lastOutput = text;
                main.post(() -> output.setText(text));
            } finally {
                main.post(() -> setRunning(false));
            }
        });
    }

    private void setRunning(boolean running) {
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        editor.setEnabled(!running);
    }

    private void loadNextExample() {
        exampleIndex = (exampleIndex + 1) % EXAMPLES.length;
        editor.setText(EXAMPLES[exampleIndex]);
        editor.setSelection(editor.getText().length());
        output.setText("Example " + (exampleIndex + 1) + " loaded.");
    }

    private void showReference() {
        String reference =
                "SIGMA Script 0.1\n\n"
                + "Statements:\n"
                + "  let x = 10\n"
                + "  x = x + 1\n"
                + "  print x\n"
                + "  if condition { ... } else { ... }\n"
                + "  repeat 10 { ... }\n"
                + "  while condition { ... }\n\n"
                + "Values: numbers, strings, booleans\n"
                + "Logic: and, or, not\n"
                + "Operators: + - * / % == != < <= > >=\n"
                + "Constants: pi, e\n"
                + "Functions:\n"
                + "  sqrt abs sin cos tan log exp\n"
                + "  floor ceil round pow min max clamp\n"
                + "  len str num type\n\n"
                + "Comments: # text   or   // text\n\n"
                + "Execution limits are enforced for steps, loops, output and wall time.";
        lastOutput = reference;
        output.setText(reference);
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
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCE, editor.getText().toString())
                .apply();
        super.onPause();
    }

    @Override protected void onDestroy() {
        cancelRequested.set(true);
        executor.shutdownNow();
        super.onDestroy();
    }
}
