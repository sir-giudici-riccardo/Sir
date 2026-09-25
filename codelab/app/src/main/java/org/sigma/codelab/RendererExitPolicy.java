package org.sigma.codelab;

public final class RendererExitPolicy {
    private RendererExitPolicy() {}

    public static String classification(boolean didCrash) {
        return didCrash ? "WEBVIEW_RENDERER_CRASH" : "WEBVIEW_RENDERER_KILLED";
    }

    public static String message(boolean didCrash, int rendererPriorityAtExit) {
        return classification(didCrash)
                + " priority_at_exit=" + rendererPriorityAtExit
                + " / execution failed closed";
    }
}
