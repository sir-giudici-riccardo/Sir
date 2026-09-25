import org.sigma.codelab.ScriptEngine;

public final class ScriptEngineTests {
    private static int passed = 0;

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed++;
        System.out.println("PASS " + name);
    }

    private static ScriptEngine.Result run(String source) throws Exception {
        return ScriptEngine.execute(source);
    }

    private static void expectError(String source, String contains, String name) throws Exception {
        boolean ok = false;
        try {
            run(source);
        } catch (ScriptEngine.ScriptException e) {
            ok = e.getMessage().contains(contains);
        }
        check(ok, name);
    }

    public static void main(String[] args) throws Exception {
        check(run("print 2 + 3 * 4").output.equals("14\n"), "arithmetic_precedence");
        check(run("print (2 + 3) * 4").output.equals("20\n"), "parentheses");
        check(run("let x = 7\nx = x + 5\nprint x").output.equals("12\n"), "variables_assignment");
        check(run("print \"hi \" + 5").output.equals("hi 5\n"), "string_concat");
        check(run("if 3 > 2 { print \"yes\" } else { print \"no\" }").output.equals("yes\n"), "if_else");
        check(run("let x = 0\nrepeat 5 { x = x + 2 }\nprint x").output.equals("10\n"), "repeat_loop");
        check(run("let i = 0\nlet s = 0\nwhile i < 5 { s = s + i\ni = i + 1 }\nprint s").output.equals("10\n"), "while_loop");
        check(run("print round(sqrt(81))\nprint max(3, 9)\nprint clamp(12, 0, 10)").output.equals("9\n9\n10\n"), "math_builtins");
        check(run("# comment\nlet x = 5 // inline\nprint x").output.equals("5\n"), "comments");
        check(run("print true and not false\nprint false or true").output.equals("true\ntrue\n"), "boolean_logic");
        check(run("print len(\"sigma\")\nprint type(3)\nprint num(\"12\") + 1").output.equals("5\nnumber\n13\n"), "conversion_builtins");
        check(run("print pi > 3\nprint e > 2").output.equals("true\ntrue\n"), "constants");
        expectError("print missing", "undefined variable", "undefined_variable_error");
        expectError("print 1 / 0", "division by zero", "division_by_zero_error");
        expectError("x = 2", "cannot assign undefined variable", "assignment_requires_existing_var");

        ScriptEngine.Limits loopLimits = new ScriptEngine.Limits(100, 1024, 10, 1000);
        boolean loopLimited = false;
        try {
            ScriptEngine.execute("let i = 0\nwhile true { i = i + 1 }", loopLimits, () -> false);
        } catch (ScriptEngine.ScriptException e) {
            loopLimited = e.getMessage().contains("while loop too long") || e.getMessage().contains("max steps");
        }
        check(loopLimited, "loop_limit");

        ScriptEngine.Limits outputLimits = new ScriptEngine.Limits(1000, 8, 100, 1000);
        boolean outputLimited = false;
        try {
            ScriptEngine.execute("repeat 10 { print \"xx\" }", outputLimits, () -> false);
        } catch (ScriptEngine.ScriptException e) {
            outputLimited = e.getMessage().contains("output too large");
        }
        check(outputLimited, "output_limit");

        boolean cancelled = false;
        try {
            ScriptEngine.execute("print 1", ScriptEngine.Limits.defaults(), () -> true);
        } catch (ScriptEngine.ScriptException e) {
            cancelled = e.getMessage().contains("cancelled");
        }
        check(cancelled, "cancellation");

        check(run("print \"a\" < \"b\"").output.equals("true\n"), "string_comparison");
        expectError("repeat -1 { print 1 }", "non-negative integer", "repeat_rejects_negative");

        System.out.println("TOTAL_PASS=" + passed);
    }
}
