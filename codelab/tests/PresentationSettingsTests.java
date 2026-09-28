import org.sigma.codelab.PresentationSettings;

public final class PresentationSettingsTests {
    public static void main(String[] args) {
        if (PresentationSettings.editorSp(PresentationSettings.TextScale.NORMAL) != 15.0f) throw new AssertionError("normal editor scale");
        if (!(PresentationSettings.editorSp(PresentationSettings.TextScale.LARGE) > 15.0f)) throw new AssertionError("large editor scale");
        if (!(PresentationSettings.editorSp(PresentationSettings.TextScale.XL) > PresentationSettings.editorSp(PresentationSettings.TextScale.LARGE))) throw new AssertionError("xl monotonic");
        if (!(PresentationSettings.outputSp(PresentationSettings.TextScale.XL) > 14.0f)) throw new AssertionError("output scale");
        if (!PresentationSettings.boundaries().contains("!= LANGUAGE_SEMANTICS")) throw new AssertionError("semantic boundary");
        System.out.println("PresentationSettingsTests PASS 5/5");
    }
}
