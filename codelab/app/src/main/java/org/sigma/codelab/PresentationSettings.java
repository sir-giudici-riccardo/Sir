package org.sigma.codelab;

/** UI-only settings shared by contract with WEB-1. They do not alter SIGMA semantics. */
public final class PresentationSettings {
    private PresentationSettings() {}

    public enum Theme { SYSTEM, LIGHT, DARK }
    public enum TextScale {
        NORMAL(1.00f), LARGE(1.15f), XL(1.30f);
        public final float factor;
        TextScale(float factor) { this.factor = factor; }
    }

    public static float editorSp(TextScale scale) {
        if (scale == null) scale = TextScale.NORMAL;
        return 15.0f * scale.factor;
    }

    public static float outputSp(TextScale scale) {
        if (scale == null) scale = TextScale.NORMAL;
        return 14.0f * scale.factor;
    }

    public static String boundaries() {
        return "PRESENTATION_SETTING != LANGUAGE_SEMANTICS / THEME_OR_TEXT_SCALE != NUMERICAL_RESULT";
    }
}
