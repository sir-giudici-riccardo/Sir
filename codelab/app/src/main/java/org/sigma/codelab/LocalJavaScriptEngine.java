package org.sigma.codelab;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewRenderProcess;

import org.json.JSONObject;
import org.json.JSONTokener;

public final class LocalJavaScriptEngine {
    public interface Callback {
        void onComplete(Result result);
    }

    public static final class Result {
        public final boolean success;
        public final String output;
        public final String value;
        public final String error;
        public final long elapsedNs;
        public final String provider;
        public final boolean timedOut;
        public final boolean cancelled;

        Result(boolean success, String output, String value, String error,
               long elapsedNs, String provider, boolean timedOut, boolean cancelled) {
            this.success = success;
            this.output = output == null ? "" : output;
            this.value = value;
            this.error = error;
            this.elapsedNs = elapsedNs;
            this.provider = provider;
            this.timedOut = timedOut;
            this.cancelled = cancelled;
        }
    }

    private final Activity activity;
    private final ViewGroup host;
    private final Handler main = new Handler(Looper.getMainLooper());

    private WebView webView;
    private Callback callback;
    private Runnable timeoutTask;
    private long startedNs;
    private boolean finished;
    private boolean evaluationStarted;

    public LocalJavaScriptEngine(Activity activity, ViewGroup host) {
        this.activity = activity;
        this.host = host;
    }

    public void run(String source, long timeoutMs, Callback callback) {
        final String code = source == null ? "" : source;
        if (timeoutMs < 1) throw new IllegalArgumentException("timeoutMs must be positive");

        cancelInternal(false, false);
        this.callback = callback;
        this.finished = false;
        this.evaluationStarted = false;
        this.startedNs = System.nanoTime();

        WebView.setWebContentsDebuggingEnabled(false);

        WebView w = new WebView(activity);
        webView = w;
        w.setVisibility(View.INVISIBLE);

        WebSettings settings = w.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBlockNetworkLoads(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setGeolocationEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request == null
                        || request.getUrl() == null
                        || !"about".equals(request.getUrl().getScheme());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return url == null || !url.startsWith("about:blank");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!finished && !evaluationStarted && "about:blank".equals(url)) {
                    evaluationStarted = true;
                    evaluate(view, code);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                boolean didCrash = detail != null && detail.didCrash();
                int priority = detail == null ? -1 : detail.rendererPriorityAtExit();

                if (!finished) {
                    finishFailure(
                            RendererExitPolicy.message(didCrash, priority),
                            false,
                            false,
                            false);
                } else {
                    cleanupRendererAndView();
                }

                // Returning true tells Android that this WebView's renderer loss was handled.
                return true;
            }
        });

        host.addView(w, new ViewGroup.LayoutParams(1, 1));

        timeoutTask = () -> finishFailure(
                "execution limit: JavaScript deadline exceeded",
                true,
                false,
                true);
        main.postDelayed(timeoutTask, timeoutMs);

        w.loadUrl("about:blank");
    }

    private void evaluate(WebView view, String source) {
        String quoted = JSONObject.quote(source);
        String script =
                "(function(){\n" +
                "  'use strict';\n" +
                "  const __out=[];\n" +
                "  const __fmt=(v)=>{\n" +
                "    if (typeof v === 'string') return v;\n" +
                "    try { const j=JSON.stringify(v); return j === undefined ? String(v) : j; }\n" +
                "    catch (_) { return String(v); }\n" +
                "  };\n" +
                "  const __console={log:(...a)=>__out.push(a.map(__fmt).join(' '))};\n" +
                "  try {\n" +
                "    const __src=" + quoted + ";\n" +
                "    const __fn=new Function(\n" +
                "      'console','fetch','XMLHttpRequest','WebSocket','EventSource','Worker','SharedWorker',\n" +
                "      'navigator','location','document','window','self',\n" +
                "      '\"use strict\";\\n' + __src\n" +
                "    );\n" +
                "    const __value=__fn(__console,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined,undefined);\n" +
                "    return JSON.stringify({ok:true,output:__out.join('\\n'),value:(typeof __value==='undefined'?null:__fmt(__value))});\n" +
                "  } catch (e) {\n" +
                "    return JSON.stringify({ok:false,output:__out.join('\\n'),error:String(e && e.stack ? e.stack : e)});\n" +
                "  }\n" +
                "})()";

        view.evaluateJavascript(script, raw -> {
            if (finished) return;
            try {
                Object outer = new JSONTokener(raw).nextValue();
                String inner = outer instanceof String ? (String) outer : String.valueOf(outer);
                JSONObject result = new JSONObject(inner);
                boolean ok = result.optBoolean("ok", false);
                String output = result.optString("output", "");
                String value = result.isNull("value") ? null : result.optString("value", null);
                String error = result.isNull("error") ? null : result.optString("error", null);
                finish(new Result(
                        ok,
                        output,
                        value,
                        error,
                        System.nanoTime() - startedNs,
                        providerLabel(),
                        false,
                        false));
            } catch (Throwable t) {
                finishFailure(
                        "JavaScript result decode failed: " + t.getClass().getSimpleName(),
                        false,
                        false,
                        false);
            }
        });
    }

    public void cancel() {
        cancelInternal(true, true);
    }

    public void destroy() {
        cancelInternal(false, false);
    }

    private void cancelInternal(boolean notify, boolean userCancelled) {
        if (webView == null && !notify) return;
        if (notify && !finished) {
            finishFailure("execution cancelled", false, userCancelled, true);
            return;
        }
        cleanupRendererAndView();
    }

    private void finishFailure(String error, boolean timedOut, boolean cancelled, boolean terminateRenderer) {
        if (finished) return;
        if (terminateRenderer) terminateRenderer();
        finish(new Result(
                false,
                "",
                null,
                error,
                System.nanoTime() - startedNs,
                providerLabel(),
                timedOut,
                cancelled));
    }

    private void finish(Result result) {
        if (finished) return;
        finished = true;

        if (timeoutTask != null) {
            main.removeCallbacks(timeoutTask);
            timeoutTask = null;
        }

        Callback cb = callback;
        callback = null;
        cleanupRendererAndView();

        if (cb != null) cb.onComplete(result);
    }

    private void terminateRenderer() {
        if (webView == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            WebViewRenderProcess process = webView.getWebViewRenderProcess();
            if (process != null) process.terminate();
        } catch (Throwable ignored) {
            // Best-effort renderer termination; cleanup still destroys this WebView.
        }
    }

    private void cleanupRendererAndView() {
        WebView w = webView;
        webView = null;
        if (w == null) return;

        try { w.stopLoading(); } catch (Throwable ignored) {}
        try {
            if (w.getParent() == host) host.removeView(w);
        } catch (Throwable ignored) {}
        try { w.destroy(); } catch (Throwable ignored) {}
    }

    private String providerLabel() {
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info == null) return "unknown";
            return info.packageName + " " + info.versionName;
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
